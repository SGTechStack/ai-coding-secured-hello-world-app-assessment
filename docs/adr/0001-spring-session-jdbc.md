# Server-side sessions via Spring Session JDBC

The PRD mandates cookie-based server-side sessions and requires that a password reset invalidate *all* of a user's existing sessions (across devices) and that logout reliably end a session. The default Tomcat in-memory `HttpSession` cannot enumerate "all sessions for principal X," and Redis is explicitly out of scope. We therefore persist sessions with **Spring Session JDBC** over the same H2/JPA datasource (portable to Postgres later).

## Considered Options

- **Tomcat in-memory `HttpSession`** — simplest, but no per-principal lookup, so Story 7's mass-invalidation would need a bespoke session registry; also lost on restart.
- **Spring Session Redis** — supports `findByPrincipalName`, but adds infrastructure the PRD rules out of scope.
- **Spring Session JDBC** (chosen) — gives `FindByIndexNameSessionRepository.findByPrincipalName` for clean mass-invalidation, survives restarts, and reuses the existing datasource.

## Consequences

- Adds the `SPRING_SESSION` / `SPRING_SESSION_ATTRIBUTES` schema to the datasource.
- Single-instance assumption for IP throttling still holds (that counter is in-memory, see the throttling design); sessions themselves are now instance-independent.
- Swapping to Postgres later requires no session-code changes, only the dialect/DDL.
