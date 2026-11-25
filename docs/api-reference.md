# MatchForge API reference

Public documentation for reviewers when Swagger UI is disabled in production (`prod` profile).

- **Version prefix:** `/api/v1`
- **Content-Type:** `application/json`
- **Auth header:** `Authorization: Bearer <accessToken>` (except public auth and health)

## Base URL

| Environment | Example |
|-------------|---------|
| Local Compose | `http://localhost:8080` |
| Hosted (Render) | `https://<service>.onrender.com` |

## Authentication

### Register player

`POST /api/v1/auth/register` — **Public**

Request:

```json
{
  "username": "player_one",
  "password": "StrongPassword-Example-1",
  "skillRating": 1000
}
```

`username`: 3–32 chars, `[A-Za-z0-9_]`. `skillRating` optional (0–10000).

Response `201`:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600,
  "accountId": "uuid",
  "role": "PLAYER",
  "playerId": "uuid"
}
```

### Login

`POST /api/v1/auth/login` — **Public**

```json
{
  "username": "player_one",
  "password": "StrongPassword-Example-1"
}
```

Response `200`: same shape as register.

## Players

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/players` | ADMIN | Create player (ops) |
| GET | `/api/v1/players` | ADMIN | List all players |
| GET | `/api/v1/players/{playerId}` | Self or ADMIN | Get profile |
| PATCH | `/api/v1/players/{playerId}` | Self or ADMIN | Update profile |
| PUT | `/api/v1/players/{playerId}/availability` | Self or ADMIN | Set availability |

Availability body:

```json
{ "available": true }
```

Player `status` values include `AVAILABLE`, `IN_QUEUE`, `IN_MATCH`, `IN_SESSION` (see domain enums).

## Matchmaking

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/matchmaking/queue` | PLAYER | Join queue |
| GET | `/api/v1/matchmaking/queue/{playerId}` | Self or ADMIN | Queue status |
| DELETE | `/api/v1/matchmaking/queue/{playerId}` | Self or ADMIN | Leave queue |

Join body:

```json
{ "playerId": "uuid" }
```

Queue status `state`: `NOT_QUEUED`, `QUEUED`, `MATCHED`, `CANCELLED`. When `MATCHED`, `matchId` is set.

## Matches

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/matches/{matchId}` | Participant or ADMIN | Match details + `sessionId` |

## Sessions

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/sessions/{sessionId}` | Participant or ADMIN | Session details |
| GET | `/api/v1/sessions/{sessionId}/participants` | Participant or ADMIN | Participants |
| POST | `/api/v1/sessions/{sessionId}/join` | Participant | Join waiting session |
| POST | `/api/v1/sessions/{sessionId}/heartbeat` | Participant | Heartbeat |
| POST | `/api/v1/sessions/{sessionId}/disconnect` | Participant | Disconnect |
| POST | `/api/v1/sessions/{sessionId}/reconnect` | Participant | Reconnect |
| POST | `/api/v1/sessions/{sessionId}/complete` | ADMIN | Complete session |
| POST | `/api/v1/sessions/{sessionId}/cancel` | ADMIN | Cancel session |

Participant body:

```json
{ "playerId": "uuid" }
```

Session `status`: `WAITING_FOR_PLAYERS`, `ACTIVE`, `PAUSED`, `COMPLETED`, `CANCELLED`, `EXPIRED`.

## Health

`GET /actuator/health` — **Public** (no JWT). Used by Docker and Render health checks.

## Errors

JSON error body (simplified):

```json
{
  "timestamp": "2026-01-01T00:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Authentication required",
  "path": "/api/v1/players/..."
}
```

In production, sensitive details are omitted from responses.

## Tools

- Postman: [`postman/MatchForge.postman_collection.json`](../postman/MatchForge.postman_collection.json)
- Shell demo: [`scripts/demo-matchforge.sh`](../scripts/demo-matchforge.sh)

## Lifecycle (happy path)

1. Register/login two players.
2. Set `available: true` for both.
3. Join queue for each; second join yields `MATCHED`.
4. `GET /matches/{matchId}` → `sessionId`.
5. Both `POST .../join` → session `ACTIVE`.
6. Optional heartbeats / disconnect / reconnect.
7. ADMIN `POST .../complete` → session `COMPLETED`, players return to `AVAILABLE`.