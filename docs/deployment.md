# MatchForge deployment guide

This guide connects a **Render** Docker web service (free tier) to **Neon** managed PostgreSQL (free tier). No paid resources are required for the portfolio demonstration, but free tiers have cold starts and quotas (see [Limitations](#limitations)).

## Architecture

```text
Browser / Postman / demo script
        │
        ▼
Render Web Service (Docker, Singapore region)
  SPRING_PROFILES_ACTIVE=prod
  PORT (injected by Render)
        │
        │ JDBC + SSL (sslmode=require)
        ▼
Neon PostgreSQL (ap-southeast-1 or nearest region)
  Flyway V1–V3 on startup
```

The repository includes [`render.yaml`](../render.yaml). Render does **not** provision Postgres in this blueprint—use Neon to avoid expiring Render free databases.

## 1. Create a Neon project

1. Sign in at [neon.tech](https://neon.tech) (free tier).
2. Create a project in **Singapore (ap-southeast-1)** or the closest region to your Render service (`singapore` in `render.yaml`).
3. Note the **direct** connection string (not the pooler) for small deployments and Flyway migrations.
4. Ensure the JDBC URL includes TLS, for example:

   `jdbc:postgresql://ep-XXXX.ap-southeast-1.aws.neon.tech/neondb?sslmode=require`

5. Copy **user**, **password**, and database name from the Neon console.

Neon free tier: compute scales to zero when idle, with monthly compute and storage limits. See [Neon pricing](https://neon.com/pricing).

## 2. Configure Render from the Blueprint

1. Push this repository to GitHub (branch `main`).
2. In [Render Dashboard](https://dashboard.render.com), **New → Blueprint** and connect the MatchForge repo.
3. Render reads `render.yaml` and prompts for `sync: false` values:

   | Variable | Source |
   |----------|--------|
   | `DB_URL` | Neon JDBC URL with `sslmode=require` |
   | `DB_USERNAME` | Neon role |
   | `DB_PASSWORD` | Neon password |
   | `MATCHFORGE_ADMIN_USERNAME` | Your chosen admin login (not committed) |

4. Render generates `MATCHFORGE_JWT_SECRET` and `MATCHFORGE_ADMIN_PASSWORD` automatically (`generateValue: true`). Save the generated admin password from the dashboard after first deploy.
5. Leave `MATCHFORGE_FLYWAY_BASELINE=false` for a **new** Neon database.
6. Deploy. Health check path: `/actuator/health`.

Render injects `PORT`; the application binds `0.0.0.0` and `server.port=${PORT:8080}` in the `prod` profile.

## 3. Flyway on a fresh database

On first startup with an empty Neon database:

1. Flyway runs `V1__baseline.sql`, `V2__accounts.sql`, `V3__account_login_lockout.sql`.
2. Hibernate validates the schema (`ddl-auto: validate`).
3. `AdminBootstrapService` creates the admin account if it does not exist.

Use `MATCHFORGE_FLYWAY_BASELINE=true` **only** when adopting a pre-Flyway database after backup (local or legacy). Never enable baseline on an empty production database.

## 4. Verify connectivity

After deploy:

```bash
curl -fsS "https://YOUR-SERVICE.onrender.com/actuator/health"
```

Run the API demo (set admin credentials from Render-generated secrets):

```bash
export MATCHFORGE_BASE_URL=https://YOUR-SERVICE.onrender.com
export DEMO_ADMIN_USERNAME='your-admin-user'
export DEMO_ADMIN_PASSWORD='password-from-render-dashboard'
./scripts/demo-matchforge.sh
```

Expected final line: `DEMO_SUCCESS`.

## 5. Local production-profile smoke

PostgreSQL via Compose, production profile:

```bash
cp .env.example .env
# Set DB_*, MATCHFORGE_JWT_SECRET, MATCHFORGE_ADMIN_* , POSTGRES_PASSWORD

docker compose up -d postgres
export SPRING_PROFILES_ACTIVE=prod
export DB_URL=jdbc:postgresql://localhost:5433/matchforge
# … other env vars from .env
./mvnw spring-boot:run
```

Or build and run the image:

```bash
source scripts/local-docker-path.sh
docker compose up --build
# Add SPRING_PROFILES_ACTIVE=prod to compose only when testing prod locally
```

## Limitations

| Platform | Free tier notes |
|----------|-----------------|
| **Render** | Web service may **spin down** after idle; first request can take tens of seconds (cold start). ~512 MB RAM on free plan—`JAVA_OPTS` uses container-aware heap limits. |
| **Neon** | Compute **scales to zero**; storage and compute monthly caps apply. Use direct connection for migrations; pooler is optional at higher scale. |

Do not use Render’s expiring free Postgres for long-lived data if you need persistence beyond the trial window—Neon is the intended database for this setup.

## Manual steps summary

1. Create Neon project + connection string (SSL).
2. Create Render Blueprint from repo; enter DB and admin username secrets.
3. Save generated JWT/admin passwords from Render.
4. Confirm `/actuator/health` and run `scripts/demo-matchforge.sh`.

No hosted deployment is created automatically by this repository; you must link Render and Neon in your own accounts.