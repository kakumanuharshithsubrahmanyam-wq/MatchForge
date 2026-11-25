# Post-deployment API verification

Live service (example): `https://matchforge-y4f9.onrender.com`

This guide matches the implemented API under `/api/v1` (see controllers; OpenAPI/Swagger is disabled on production). Use local dev for Swagger UI.

## Artifacts

| File | Purpose |
|------|---------|
| [postman/MatchForge.postman_collection.json](../postman/MatchForge.postman_collection.json) | Requests, test scripts, variable chaining |
| [postman/MatchForge.production.environment.template.json](../postman/MatchForge.production.environment.template.json) | Production `baseUrl` — **no real secrets** |
| [postman/MatchForge.local.environment.template.json](../postman/MatchForge.local.environment.template.json) | Local `baseUrl` template |
| [scripts/post-deploy-readonly.sh](../scripts/post-deploy-readonly.sh) | Shell smoke (read-only) |
| [scripts/demo-matchforge.sh](../scripts/demo-matchforge.sh) | Full E2E with `curl` + `jq` |

## Variable names (Postman vs shell)

| Name | Where | Purpose |
|------|--------|---------|
| `baseUrl` | Postman collection + environment templates | Host root, e.g. `https://matchforge-y4f9.onrender.com` (no path) |
| `MATCHFORGE_BASE_URL` | `post-deploy-readonly.sh`, `demo-matchforge.sh` | Same host for **shell** scripts only — not a Postman variable |

Postman request URLs use `{{baseUrl}}/...`. Import an environment and set `baseUrl` there; it overrides the collection default when the environment is selected.

Collection test scripts use `pm.variables.set(...)` so tokens and IDs persist in the **active environment** (or collection if no environment is selected).

### Postman environment keys (must match collection `{{...}}` names)

| Key | Set by | Used by |
|-----|--------|---------|
| `baseUrl` | Template / you | All requests |
| `playerAUsername`, `playerBUsername`, `outsiderUsername` | Template / you | Register bodies |
| `opsPlayerUsername` | Template / you | Admin **Create player** body (use a **unique** value per production run) |
| `playerPassword` | You (secret) | Register/login |
| `adminUsername`, `adminPassword` | You (secret) | Admin login |
| `playerAToken`, `playerBToken`, `outsiderToken`, `adminToken` | Tests / login | `Authorization` headers |
| `playerAId`, `playerBId`, `outsiderId` | Register tests | Path/body IDs |
| `matchId` | Queue B test | Match GET |
| `sessionId` | Get match test | Session folder |

## Test categories

| Category | What | Safe on production? | How to run |
|----------|------|---------------------|------------|
| **Read-only production** | Health + auth smoke | **Yes** | Postman folder `0`, or `post-deploy-readonly.sh` |
| **Mutating production** | Full API E2E | **No** (creates Neon rows) | Manual: Postman folders `1–5` or `demo-matchforge.sh` with approval |
| **Local integration** | JUnit / Testcontainers | N/A (local DB) | `./mvnw verify` with Postgres |
| **Newman (optional)** | Automated Postman | Folder `0` only on prod | `newman run ... --folder "0 — Operations..."` |
| **Manual only** | Admin metrics, duplicate usernames | Varies | Single requests in Postman |

## Import into Postman

1. Import the collection JSON.
2. Import `MatchForge.production.environment.template.json` and save as a private environment.
3. Set secrets only in Postman (never commit): `playerPassword`, `adminUsername`, `adminPassword`.
4. For mutating production runs, use **unique** `playerAUsername` / `playerBUsername` / `outsiderUsername` / `opsPlayerUsername` per run (e.g. suffix with date or UUID).

## Safe vs mutating requests

### Safe on production (read-only / unauthenticated)

Folder **`0 — Operations (safe on production)`** or:

```bash
MATCHFORGE_BASE_URL=https://matchforge-y4f9.onrender.com ./scripts/post-deploy-readonly.sh
```

| Request | Expected |
|---------|----------|
| `GET /actuator/health` | `200`, `status: UP` |
| `GET /api/v1/players` (no auth) | `401` |
| `GET /actuator/prometheus` (no auth) | `401` |
| `GET /swagger-ui/index.html` (no auth) | `401`, `403`, or `404` |

### Mutating on production

Folders **1–5** create accounts, queue entries, matches, sessions, and may complete sessions. **Requires explicit operator approval.** Does not delete existing users.

## Full E2E execution order (Postman folders 1–5)

Test scripts chain variables via `pm.variables.set`.

```text
1 — Authentication
  Register player A     → playerAToken, playerAId
  Register player B     → playerBToken, playerBId
  Register outsider     → outsiderToken, outsiderId
  Login player A        → playerAToken
  Login administrator   → adminToken

2 — Players
  Get own profile (A)
  Patch own skill rating (A)            → skillRating 1050
  Cross-player forbidden (A reads B)  → 403
  Set availability (A) & (B)          → required before queue
  Create player (ADMIN)               → optional; username {{opsPlayerUsername}}
  List players (ADMIN)

3 — Matchmaking
  Join queue (A)                      → 202 QUEUED
  Join queue (B)                      → 202 MATCHED, matchId
  Queue status (A)
  Leave queue                         → optional; may conflict if already matched

4 — Matches
  Get match                           → sessionId

5 — Sessions
  Outsider cannot view session        → 403
  Get session, participants, join x2, heartbeat, disconnect, reconnect
  Player cannot complete              → 403
  Complete session (ADMIN)            → COMPLETED
  Cancel session (ADMIN)              → only if not completed (conflicts after complete)
```

### Two-account setup

Two **PLAYER** accounts (same `playerPassword`) plus **ADMIN** for completion. Register A, then B, then set both available, queue A then B (second join triggers immediate match in API; scheduler fallback may take up to `queue-processing-interval-ms` if ratings/windows delay pairing).

### curl E2E (`demo-matchforge.sh`)

**Not run against production in CI automation.** When you choose to run it manually:

```bash
export MATCHFORGE_BASE_URL=https://matchforge-y4f9.onrender.com
export DEMO_PLAYER_PASSWORD='your-strong-password'
export DEMO_ADMIN_USERNAME='your-admin-login'
export DEMO_ADMIN_PASSWORD='your-admin-password'
./scripts/demo-matchforge.sh "$MATCHFORGE_BASE_URL"
```

**Behavior notes:**

- Usernames are unique per run (`demo_a_<timestamp>_<random>`) — leaves rows in Neon (accounts, players, match, session history).
- No cleanup/delete at end.
- Skips admin completion with exit `DEMO_PARTIAL_OK` if admin env vars unset.
- Does not cover PATCH profile, disconnect/reconnect, or session cancel.
- Match is synchronous on second queue join in the script (no sleep); aligns with integration tests.

## HTTP status reference

| Operation | Method | Status |
|-----------|--------|--------|
| Register | POST `/api/v1/auth/register` | `201` |
| Login | POST `/api/v1/auth/login` | `200` |
| Patch profile | PATCH `/api/v1/players/{id}` | `200` |
| Join queue | POST `/api/v1/matchmaking/queue` | `202` |
| Set availability | PUT `.../availability` | `200` |
| Get match | GET `/api/v1/matches/{id}` | `200` |
| Join session | POST `.../join` | `200` |
| Complete session | POST `.../complete` (ADMIN) | `200` |
| Cross-player profile | GET other player | `403` |
| Non-participant session | GET session | `403` |
| Player complete | POST `.../complete` | `403` |

## Newman (optional)

Safe folder only on production:

```bash
newman run postman/MatchForge.postman_collection.json \
  --environment postman/MatchForge.production.environment.template.json \
  --folder "0 — Operations (safe on production)"
```

Full E2E needs a **git-ignored** environment file with real secrets — do not commit.

## Optional / not in default E2E

- `DELETE /api/v1/matchmaking/queue/{id}` after match (expect conflict)
- Actuator `/actuator/prometheus` with **ADMIN** token (read-only, safe if authorized)
- `POST .../sessions/{id}/cancel` after `complete` (409 conflict)
- Admin **Create player** is optional; set `opsPlayerUsername` uniquely each run to avoid username conflicts

## Security notes

- JWTs expire (default 3600s in `application.yml`).
- Never commit production passwords or JWTs.
- Shell scripts print only URLs and HTTP status lines, not credentials.