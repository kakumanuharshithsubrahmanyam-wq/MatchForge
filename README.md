# MatchForge

Multiplayer game backend and matchmaking platform (Java 21, Spring Boot 4.2, PostgreSQL).

## Prerequisites

- JDK 21
- Maven (or use `./mvnw`)
- Docker and Docker Compose (optional, recommended for local Postgres and full stack)

## PostgreSQL port mappings

| Environment | JDBC / host target | Notes |
|-------------|-------------------|--------|
| **macOS local development** (app/tests on host) | `localhost:5433` | Avoids conflict with a system Postgres on `5432` |
| **Docker Compose app container** | `postgres:5432` | Service name + internal container port |
| **GitHub Actions CI** | `localhost:5432` | Service container; overridden via `DB_URL` in workflow |

Defaults in `application.yml` and `application-test.yml` use `localhost:5433`. Override anytime with `DB_URL`.

## Configuration

Runtime settings use environment variables (see `.env.example`):

| Variable | Default (local macOS) | Description |
|----------|----------------------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5433/matchforge` | JDBC URL |
| `DB_USERNAME` | `matchforge` | Database user |
| `DB_PASSWORD` | `matchforge` | Database password |

Copy `.env.example` to `.env` and set a strong `POSTGRES_PASSWORD` / `DB_PASSWORD` before using Docker Compose.

## Run with Docker Compose

```bash
cp .env.example .env
# Edit .env and set POSTGRES_PASSWORD

docker compose up --build
```

- PostgreSQL (from host): `localhost:5433` → container `5432`
- Application: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health`

The app service waits until Postgres passes its health check. The **app** container uses `jdbc:postgresql://postgres:5432/matchforge` (not `localhost:5433`).

## Run locally (app on host, DB in Docker)

```bash
cp .env.example .env
docker compose up -d postgres

export DB_URL=jdbc:postgresql://localhost:5433/matchforge
export DB_USERNAME=matchforge
export DB_PASSWORD=your-password-from-env

./mvnw spring-boot:run
```

## Tests

Integration tests use the `test` profile and default to PostgreSQL at `localhost:5433/matchforge` (override with `DB_URL`).

```bash
docker compose up -d postgres
./mvnw test
```

CI sets `DB_URL=jdbc:postgresql://localhost:5432/matchforge_test` (see `.github/workflows/ci.yml`).

## Documentation

- [PROJECT_SPEC.md](./PROJECT_SPEC.md) — scope, roadmap, API plan
- [ARCHITECTURE.md](./ARCHITECTURE.md) — modular monolith layout and decisions