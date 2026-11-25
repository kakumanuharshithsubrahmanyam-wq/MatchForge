# MatchForge — engineering case study

## Problem statement

Skill-based multiplayer backends must match players fairly, coordinate session lifecycle safely, and expose a clear API for clients—without coupling to a specific game engine or transport.

## Why skill-based matchmaking matters

Pairing players with similar ratings improves match quality. Static queues fail when the player pool is sparse; widening search over time balances wait time against fairness.

## Engineering challenges

- **Concurrency:** Multiple players joining the queue simultaneously must not create duplicate active queue rows or inconsistent match state.
- **Consistency:** Matches, sessions, and participants span several tables; state transitions must remain valid under concurrent updates.
- **Security:** Player-scoped APIs must enforce JWT subject alignment with `playerId` in paths and bodies.
- **Operations:** Schema evolution on PostgreSQL without Hibernate auto-DDL in production.

## Architecture decisions

- **Modular monolith (Spring Boot):** Clear packages (`auth`, `player`, `matchmaking`, `session`) with shared infrastructure (`common`).
- **JWT resource server:** Stateless API suitable for horizontal scaling on Render.
- **Flyway migrations:** Versioned SQL (`V1` baseline gameplay schema, `V2` accounts, `V3` lockout).
- **Domain events + Micrometer:** Observable flows without requiring a separate message broker for the portfolio scope.
- **Scheduled matchmaking:** Periodic queue processing supplements join-triggered matching.

## Concurrency handling

- Optimistic locking (`version` columns) on queue and session entities where needed.
- Repository constraints (e.g. unique active queue entry per player) enforced in the database.
- Integration tests include concurrent queue scenarios.

## Database consistency

- Hibernate `ddl-auto: validate` in all environments.
- Transactions around match creation and session activation.
- Test cleanup utilities preserve admin accounts while resetting gameplay data.

## Security design

- BCrypt passwords, HS256 JWT, role-based access, login lockout persistence.
- Production profile disables public Swagger and restricts Actuator metrics.
- Secrets only via environment variables.

## Testing strategy

- **99 Surefire tests:** unit, slice, and integration tests with PostgreSQL (test profile).
- **2 Failsafe tests:** Testcontainers + Flyway on ephemeral Postgres.
- **Security matrix:** authorization, JWT, end-to-end matchmaking-to-admin-completion.
- **CI:** GitHub Actions on `main` with service Postgres and Docker for Testcontainers.

## Deployment approach

- **Render** Docker web service (free tier, Singapore region in blueprint).
- **Neon** PostgreSQL (external, SSL, no Render free DB expiry).
- `render.yaml` documents required secrets; see [deployment.md](deployment.md).

## Future improvements

- WebSocket or UDP game data plane (outside current REST scope).
- Rate limiting at the edge for auth endpoints.
- Read replicas or Neon pooler for higher connection counts.
- Dedicated game simulation client and anti-cheat telemetry.

This project demonstrates backend design and verification; it does not simulate millions of concurrent users or production-scale traffic.