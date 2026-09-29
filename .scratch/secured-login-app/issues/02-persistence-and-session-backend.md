# 02 — Persistence and session backend

Type: grilling
Status: open
Blocked by: —
Map: [Secured Login App](../map.md)

## Question

What relational database and what session-persistence backend does this application use, and how is the datasource configured?

Answer `Q6` (relational database) and `Q7` (session persistence backend and datasource configuration) of the standard's question set.

Constraints already on the table:

- PRD: Spring Data JPA over **H2 in the dev profile**, with a schema "portable to Postgres/MySQL later" (`prd/assessment-prd.md:11`). Portability is a stated requirement, so column types and DDL generation strategy matter.
- PRD: primary auth is a **server-side session via Spring Session** and a secure HttpOnly cookie (`prd/assessment-prd.md:13`).
- Sessions must be invalidated on logout (Story 4) and, for *all* of a user's sessions, on password reset (Story 7). Whether the session store supports find-by-principal is therefore not a detail — it decides whether Story 7's acceptance criterion is implementable at all.

Decide: H2-only or H2-dev-plus-a-real-profile; Spring Session JDBC versus in-memory versus another backend; schema management (JPA `ddl-auto` versus Flyway/Liquibase migrations) given the portability requirement; and whether the session store shares the application datasource.

This ticket gates lockout persistence (07), session policy (13), the test plan (14) and the tech baseline (15).

**Amended by [01 — Context, topology and API surface](01-context-topology-and-api-surface.md).** Half of `Q7` is already decided: topology is **single instance**, but sessions are persisted to a **JDBC** store anyway, because the standard's Required Runtime Configuration mandates a session-state persistence backend regardless of topology (`Standalone_User_Access_Control_Application_Standard.md:604`) and because it is what makes the PRD's "reset invalidates all sessions" criterion (`prd/assessment-prd.md:79`) assertable. Record that rather than re-arguing it; what remains open here is `Q6` (which relational database) and the concrete datasource/Spring Session JDBC configuration.
