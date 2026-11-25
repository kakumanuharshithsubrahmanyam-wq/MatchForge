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

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
docker compose up -d postgres
./mvnw clean verify
```

Testcontainers migration tests (`FlywayMigrationIT`) run in the **failsafe** phase and require a working Docker daemon:

```bash
./mvnw clean verify              # Surefire + Failsafe (Testcontainers); requires working Docker API
./mvnw clean verify -Punit-only  # Surefire only when Docker/Testcontainers is unavailable
```

Account login lockout (failed password attempts) is persisted on `accounts`; use gateway rate limits for IP-based protection in production.

## Phase 4 — security and operations (summary)

- **Authentication:** `POST /api/v1/auth/register` (public player onboarding) and `POST /api/v1/auth/login` return a short-lived HS256 JWT (`Authorization: Bearer …`). Passwords are BCrypt-hashed in the `accounts` table (separate from `players`).
- **Admin bootstrap:** set `MATCHFORGE_ADMIN_USERNAME` and `MATCHFORGE_ADMIN_PASSWORD` together before first start (see `.env.example`). No public ADMIN self-registration.
- **Authorization:** JWT subject must match path/body `playerId` for player-scoped routes; session complete/cancel require `ADMIN`.
- **Flyway:** `db/migration/V1__baseline.sql` (Phase 0–3 schema), `V2__accounts.sql`. Hibernate `ddl-auto: validate`. Existing DBs: back up, set `MATCHFORGE_FLYWAY_BASELINE=true` once, start app so history is baselined and `V2+` apply.
- **Observability:** `X-Correlation-ID` on requests; Actuator `health`, `metrics`, `prometheus` (metrics endpoints require authentication).
- **OpenAPI:** Swagger UI at `/swagger-ui.html` (permitted without auth in default config — restrict in production).
- **Scheduled matchmaking:** `matchforge.matchmaking.queue-processing-interval-ms` (default 5s) processes queued entries in addition to join-triggered matching.

### Docker credential helper (local)

On Docker Engine 29+ (Docker Desktop 4.52+), Testcontainers requires **1.21.4+** and `src/test/resources/docker-java.properties` (`api.version=1.44`). For local builds/pulls, prepend Docker Desktop’s bin directory:

```bash
source scripts/local-docker-path.sh
```

If `docker build` fails with `docker-credential-desktop not found in $PATH`, the engine is referencing Docker Desktop’s credential helper while the binary is missing from `PATH`. Remediation without editing global credentials: install/start Docker Desktop, or set `"credsStore": ""` in `~/.docker/config.json` only if you accept storing credentials in that file. This is a **host** configuration issue; project `Dockerfile` / `compose.yml` are unchanged.

## Authentication API

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/auth/register` | Public | Register player + account, returns JWT |
| POST | `/api/v1/auth/login` | Public | Login, returns JWT |

## Player API (`/api/v1/players`)

All endpoints require `Authorization: Bearer <JWT>` unless noted. **PLAYER** may access own `playerId` only; **ADMIN** may list/create administratively.

| Method | Path | Role | Description | Success |
|--------|------|------|-------------|---------|
| `POST` | `/api/v1/players` | ADMIN | Create player (ops) | `201 Created` |
| `GET` | `/api/v1/players` | ADMIN | List all players | `200 OK` |
| `GET` | `/api/v1/players/{playerId}` | PLAYER (self) / ADMIN | Get player | `200 OK` |
| `PATCH` | `/api/v1/players/{playerId}` | PLAYER (self) / ADMIN | Update skill rating | `200 OK` |
| `PUT` | `/api/v1/players/{playerId}/availability` | PLAYER (self) / ADMIN | Availability | `200 OK` |

Usernames are trimmed and stored lowercase (3–32 chars, letters, digits, underscore). Default skill rating is **1000**. New players start as **`OFFLINE`**. Availability toggles **`OFFLINE` ↔ `AVAILABLE`**; changes are rejected with **`409`** while status is **`IN_QUEUE`**, **`MATCHED`**, or **`IN_SESSION`**.

### Examples

Register (preferred — creates account + JWT):

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"Player_One","password":"StrongPassword-Example-1","skillRating":1200}'
```

Response (`201`) includes `accessToken`, `playerId`, and `accountId`. Fetch profile:

```bash
TOKEN=... # from register/login
curl -s http://localhost:8080/api/v1/players/{playerId} -H "Authorization: Bearer $TOKEN"
```

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "username": "player_one",
  "skillRating": 1200,
  "status": "OFFLINE",
  "createdAt": "2026-01-15T10:00:00Z",
  "updatedAt": "2026-01-15T10:00:00Z"
}
```

Set availability:

```bash
curl -s -X PUT "http://localhost:8080/api/v1/players/{playerId}/availability" \
  -H 'Content-Type: application/json' \
  -d '{"available":true}'
```

Error envelope (e.g. duplicate username, `409`):

```json
{
  "timestamp": "2026-01-15T10:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Username already taken: dupe",
  "path": "/api/v1/players"
}
```

## Matchmaking API (`/api/v1/matchmaking`, `/api/v1/matches`)

| Method | Path | Description | Success |
|--------|------|-------------|---------|
| `POST` | `/api/v1/matchmaking/queue` | Join 1v1 queue | `202 Accepted` |
| `DELETE` | `/api/v1/matchmaking/queue/{playerId}` | Leave queue | `200 OK` |
| `GET` | `/api/v1/matchmaking/queue/{playerId}` | Queue / match state | `200 OK` |
| `GET` | `/api/v1/matches/{matchId}` | Match details | `200 OK` |

**Rules:** Only `AVAILABLE` players may join. Rating at enqueue time is snapshotted on `queue_entries`. After join, the service runs **skill-window pairing** (see [ARCHITECTURE.md](./ARCHITECTURE.md)). Successful pairing creates a `game_matches` row and sets players to **`MATCHED`**.

**Configuration** (`application.yml` → `matchforge.matchmaking`):

| Property | Default | Meaning |
|----------|---------|---------|
| `initial-rating-difference` | `100` | Starting skill gap |
| `rating-expansion` | `50` | Widening per interval |
| `expansion-interval-seconds` | `30` | Wait step for widening |
| `max-rating-difference` | `500` | Hard cap |

### Matchmaking examples

Join queue:

```bash
curl -s -X POST http://localhost:8080/api/v1/matchmaking/queue \
  -H 'Content-Type: application/json' \
  -d '{"playerId":"550e8400-e29b-41d4-a716-446655440000"}'
```

Response (`202`, waiting):

```json
{
  "playerId": "550e8400-e29b-41d4-a716-446655440000",
  "state": "QUEUED",
  "queueEntryId": "...",
  "matchId": null,
  "skillRatingAtQueue": 1000,
  "queuedAt": "2026-01-15T10:00:00Z"
}
```

After a second compatible player joins, state becomes **`MATCHED`** and `matchId` is populated.

```bash
curl -s http://localhost:8080/api/v1/matches/{matchId}
```

```json
{
  "id": "...",
  "playerOneId": "...",
  "playerTwoId": "...",
  "playerOneRating": 1000,
  "playerTwoRating": 1200,
  "status": "PENDING",
  "sessionId": "660e8400-e29b-41d4-a716-446655440001",
  "createdAt": "2026-01-15T10:00:01Z"
}
```

End-to-end (two players):

```bash
# Register + set AVAILABLE for two players, then:
curl -s -X POST http://localhost:8080/api/v1/matchmaking/queue -H 'Content-Type: application/json' -d "{\"playerId\":\"$P1\"}"
curl -s -X POST http://localhost:8080/api/v1/matchmaking/queue -H 'Content-Type: application/json' -d "{\"playerId\":\"$P2\"}"
```

## Game Session API (`/api/v1/sessions`)

Sessions are **created automatically** when matchmaking pairs two players. Players stay **`MATCHED`** until they call **join**; join moves them to **`IN_SESSION`**.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/sessions/{sessionId}` | Session details + participants |
| `GET` | `/api/v1/sessions/{sessionId}/participants` | Participant list |
| `POST` | `/api/v1/sessions/{sessionId}/join` | Player joins (`{"playerId":"..."}`) |
| `POST` | `/api/v1/sessions/{sessionId}/heartbeat` | Heartbeat |
| `POST` | `/api/v1/sessions/{sessionId}/disconnect` | Disconnect |
| `POST` | `/api/v1/sessions/{sessionId}/reconnect` | Reconnect |
| `POST` | `/api/v1/sessions/{sessionId}/complete` | Complete session |
| `POST` | `/api/v1/sessions/{sessionId}/cancel` | Cancel (waiting/paused) |

**Session statuses:** `WAITING_FOR_PLAYERS` → `ACTIVE` (both joined) ↔ `PAUSED` (disconnect) → `COMPLETED` / `CANCELLED` / `EXPIRED`.

**Configuration** (`matchforge.session`):

| Property | Default | Meaning |
|----------|---------|---------|
| `heartbeat-timeout-seconds` | `60` | Stale heartbeat threshold |
| `maintenance-interval-ms` | `5000` | Scheduler interval |
| `waiting-expiry-seconds` | `3600` | Expire sessions stuck waiting |

Example join:

```bash
curl -s -X POST "http://localhost:8080/api/v1/sessions/$SESSION_ID/join" \
  -H 'Content-Type: application/json' \
  -d "{\"playerId\":\"$PLAYER_ID\"}"
```

`GET /api/v1/matches/{matchId}` now includes `sessionId` after pairing.

## Documentation

- [PROJECT_SPEC.md](./PROJECT_SPEC.md) — scope, roadmap, API plan
- [ARCHITECTURE.md](./ARCHITECTURE.md) — modular monolith layout and decisions