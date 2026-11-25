# MatchForge security

## Authentication

- **Player onboarding:** `POST /api/v1/auth/register` creates an `accounts` row (role `PLAYER`) and linked `players` row; returns a short-lived HS256 JWT.
- **Login:** `POST /api/v1/auth/login` returns JWT for existing accounts.
- **Tokens:** `Authorization: Bearer <accessToken>` on all protected routes.
- **Password storage:** BCrypt hashes in `accounts.password_hash`; passwords are never logged.
- **JWT secret:** Environment variable `MATCHFORGE_JWT_SECRET` (≥ 32 characters). Mandatory when `prod` profile is active.
- **Issuer:** `matchforge` (configurable via `matchforge.security.jwt-issuer`).
- **Lockout:** Failed login attempts persisted on `accounts`; account locked after configurable thresholds (default 5 attempts, 15 minutes).

## Authorization

| Role | Capabilities |
|------|----------------|
| **PLAYER** | Own `playerId` for players, matchmaking, and session participant actions. |
| **ADMIN** | List/create players, complete/cancel sessions, Actuator metrics/prometheus (not public health). |

Method security uses `@PreAuthorize` and `AuthorizationService` (`@authz`) for resource ownership checks.

## Administrator bootstrap

- Set **`MATCHFORGE_ADMIN_USERNAME`** and **`MATCHFORGE_ADMIN_PASSWORD`** together before first startup.
- On startup, `AdminBootstrapService` creates the admin account if missing.
- No public ADMIN registration endpoint.
- In **production**, both variables are **required** at startup.

## Production hardening (`prod` profile)

- Swagger / OpenAPI disabled for anonymous users.
- Actuator **health** remains public for load balancers; **details hidden**.
- Actuator **metrics/prometheus** require ADMIN JWT.
- Error responses omit stack traces and internal messages (`server.error.include-*=never`).
- Hibernate **validate** only; schema changes via Flyway.
- Flyway **baseline-on-migrate: false** for new databases.

## Database and transport

- Use TLS to managed PostgreSQL (`sslmode=require` in JDBC URL for Neon).
- Credentials via `DB_USERNAME` / `DB_PASSWORD` only—never commit `.env` or real secrets.
- Correlation IDs (`X-Correlation-ID`) on requests for tracing; not a substitute for authentication.

## CI and tests

- GitHub Actions uses non-production test JWT and admin defaults aligned with `AuthTestHelper`.
- Security integration tests cover JWT validation, cross-player denial, admin completion, and production profile exposure rules.

## Operator checklist

1. Generate strong `MATCHFORGE_JWT_SECRET` (Render can auto-generate).
2. Choose admin username; use a strong generated admin password.
3. Restrict who can access Render/Neon dashboards.
4. Re-run `./mvnw clean verify` after security-related changes.