# MatchForge deployment guide

This guide connects a **Render** Docker web service (free tier) in **Ohio** to **Neon** managed PostgreSQL (free tier) in **AWS US East (Ohio)**. Align regions to reduce latency and cross-region egress. No paid resources are required for the portfolio demo; free tiers have cold starts and quotas (see [Limitations](#limitations)).

## Architecture

```text
Browser / Postman / demo script
        │
        ▼
Render Web Service (Docker, Ohio / aws-us-east-2)
  SPRING_PROFILES_ACTIVE=prod
  PORT (injected by Render)
        │
        │ JDBC + SSL (sslmode=require)
        ▼
Neon PostgreSQL (aws-us-east-2 — same region as Render)
  Flyway V1–V3 on startup
```

The repository [`render.yaml`](../render.yaml) sets **`region: ohio`** so the web service matches a Neon project in **Ohio (`aws-us-east-2`)**. Render does **not** provision Postgres in this blueprint—use your existing Neon project instead of Render’s expiring free database.

Docker image builds use `docker/maven-settings.xml` with the [Google-hosted Maven Central mirror](https://docs.cloud.google.com/docs/buildpacks/java) and a cached `dependency:go-offline` layer to avoid `repo.maven.apache.org` HTTP **429** rate limits on Render.

## Existing Neon project (operator)

Use the **MatchForge** Neon project you already created in the Neon console (Ohio, **PostgreSQL 18**). Before first deploy:

| Item | Typical value (confirm in Neon console) |
|------|----------------------------------------|
| Region | `aws-us-east-2` (Ohio) |
| Default branch | `production` |
| Database name | `neondb` |
| Owner role | `neondb_owner` (console default) |
| Application schema | `public` — empty until first Flyway migrate |

Do **not** commit connection strings, passwords, or project IDs to git. Copy JDBC settings into Render secret env vars only.

### PostgreSQL 18

MatchForge SQL migrations are tested on **PostgreSQL 16** (CI service + local Compose) and **PostgreSQL 18** (Failsafe `FlywayMigrationPostgres18IT` with Testcontainers). Neon’s default major version for new projects is **18**; the app uses standard SQL and Flyway without version-specific extensions.

### Dedicated application role (recommended)

Avoid running the app as **`neondb_owner`** in production when a least-privilege role is practical.

**Recommended role:** `matchforge_app` (name is an example—choose your own).

**Privileges needed:**

- `CONNECT` on database `neondb`
- `USAGE` and `CREATE` on schema `public` (Flyway on first deploy; `CREATE` can be revoked after migrations if you split migrator/app roles later)
- `SELECT`, `INSERT`, `UPDATE`, `DELETE` on all application tables (via default privileges or explicit grants)

**Do not** grant `SUPERUSER`, `CREATEDB`, or `CREATEROLE`.

**Example SQL** (run once as `neondb_owner` in the Neon SQL editor or MCP `run_sql`—**operator performs this manually**; replace the password placeholder):

```sql
CREATE ROLE matchforge_app WITH LOGIN PASSWORD '<strong-random-password>' NOSUPERUSER NOCREATEDB NOCREATEROLE;
GRANT CONNECT ON DATABASE neondb TO matchforge_app;
GRANT USAGE, CREATE ON SCHEMA public TO matchforge_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO matchforge_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO matchforge_app;
```

**Neon MCP equivalent (when you choose to apply):** `create_postgres_role` with `branch_id` for the default branch, then `run_sql` grants as above. Store the password only in Render secrets (`DB_PASSWORD`), never in the repo.

Until `matchforge_app` exists, you may use `neondb_owner` for the **first** deploy only, then rotate to `matchforge_app`.

## Production database configuration

With `SPRING_PROFILES_ACTIVE=prod`, MatchForge **requires** `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in the process environment (for example Render secret env vars). The `prod` profile binds `spring.datasource.*` directly to those variables with **no localhost fallback**.

`localhost:5433` (or Docker Compose Postgres) is for **local development only** via default `application.yml` when no `prod` profile is active.

If any `DB_*` variable is missing, or `DB_URL` still points at localhost, startup fails immediately with a clear error (`ProductionDatabaseEnvironmentValidator`) before Flyway runs.

Flyway uses the **primary datasource** in production (`spring.flyway.url` / `user` / `password` are not set). Set `MATCHFORGE_FLYWAY_BASELINE=false` on a fresh Neon database.

## Render environment variables

| Variable | Required | Secret | Purpose | Source |
|----------|----------|--------|---------|--------|
| `SPRING_PROFILES_ACTIVE` | Yes | No | Enables `prod` profile | Blueprint: `prod` |
| `JAVA_OPTS` | Yes | No | Container heap (`MaxRAMPercentage`) | Blueprint default |
| `PORT` | Auto | No | HTTP port | Injected by Render |
| `DB_URL` | Yes | Yes | JDBC URL to Neon (`sslmode=require`) | Neon console → Render secret |
| `DB_USERNAME` | Yes | Yes | Postgres role (`matchforge_app` or owner) | Neon → Render secret |
| `DB_PASSWORD` | Yes | Yes | Role password | Neon / you → Render secret |
| `MATCHFORGE_JWT_SECRET` | Yes | Yes | HS256 signing key (≥ 32 chars) | Blueprint `generateValue: true` or custom |
| `MATCHFORGE_ADMIN_USERNAME` | Yes | No* | Bootstrap admin login | Render prompt (`sync: false`) |
| `MATCHFORGE_ADMIN_PASSWORD` | Yes | Yes | Bootstrap admin password | Blueprint `generateValue: true` |
| `MATCHFORGE_FLYWAY_BASELINE` | Yes | No | `false` on empty DB; `true` once for legacy only | Blueprint: `false` |

\*Username is not as sensitive as passwords but treat admin credentials as confidential.

Optional (defaults in `application.yml`): matchmaking/session tuning via `matchforge.*` env bindings—only needed if you override defaults.

## Configure Render from the Blueprint

1. Push this repository to GitHub (`main`).
2. [Render Dashboard](https://dashboard.render.com) → **New → Blueprint** → connect MatchForge.
3. Enter `sync: false` values (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `MATCHFORGE_ADMIN_USERNAME`).
4. Save generated `MATCHFORGE_JWT_SECRET` and `MATCHFORGE_ADMIN_PASSWORD` from the dashboard.
5. Deploy; health check: `/actuator/health`.

**JDBC example shape (no real host):**

`jdbc:postgresql://ep-XXXX.us-east-2.aws.neon.tech/neondb?sslmode=require`

Use Neon’s **direct** endpoint for small deployments and Flyway; pooler optional at higher scale.

## Flyway safety

| Control | Production behavior |
|---------|---------------------|
| Hibernate `ddl-auto` | `validate` only (`application-prod.yml`) |
| Flyway baseline | `false` for empty Neon DB (`MATCHFORGE_FLYWAY_BASELINE=false`) |
| Legacy adoption | `MATCHFORGE_FLYWAY_BASELINE=true` **once** after backup—never on empty Neon |
| Failed migration | Spring Boot fails startup; app does not serve traffic with a broken schema |

Migrations: `V1__baseline.sql`, `V2__accounts.sql`, `V3__account_login_lockout.sql`.

## Verify after deploy

```bash
curl -fsS "https://YOUR-SERVICE.onrender.com/actuator/health"
export MATCHFORGE_BASE_URL=https://YOUR-SERVICE.onrender.com
export DEMO_ADMIN_USERNAME='your-admin-user'
export DEMO_ADMIN_PASSWORD='password-from-render-dashboard'
./scripts/demo-matchforge.sh
```

Expected final line: `DEMO_SUCCESS`.

## Local production-profile smoke

See [README.md](../README.md). Use Compose Postgres on port **5433** when the app runs on the host.

## Limitations

| Platform | Free tier notes |
|----------|-----------------|
| **Render** | Idle spin-down, cold starts, ~512 MB RAM |
| **Neon** | Scale-to-zero, monthly compute/storage caps on free plan |

## Manual checklist (no auto-provision)

1. Confirm Neon project in **Ohio** and empty `public` schema (or planned baseline).
2. (Recommended) Create `matchforge_app` role and grants.
3. Render Blueprint with **Ohio** region; set DB and admin secrets.
4. First deploy → Flyway creates tables → verify health and demo script.

Do not create a second Neon project for MatchForge if you already have one.