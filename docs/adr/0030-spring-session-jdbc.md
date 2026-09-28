---
status: accepted
---

# ADR-030: Spring Session JDBC, not Redis or container sessions

Sessions are stored by Spring Session JDBC in the application's own relational database. They are not kept in the
servlet container's memory, and not in Redis. The deciding requirement is that the application must find and end
**all sessions belonging to one account**, on demand and after a restart. A maintainer who sees only "a session
store" could swap it for one that cannot do that, and every invalidation rule would then silently do nothing.

## Context

- Several flows must end every session of an account, or every session but the caller's: password reset redemption
  (PRD Story 7), self-service and forced password change, and the admin and lockout triggers (ADR-035, ADR-037).
  That needs lookup by principal name, not by session id.
- The governing standard requires the same. §4 Data Persistence makes it an `[Enforced Constraint]` that session state
  is persisted so that concurrent-session limits hold across requests and restarts. §2 Happy Path step 13 names
  `SpringSessionBackedSessionRegistry` for invalidation when Spring Session JDBC is used.
- `SpringSessionBackedSessionRegistry` (Spring Session 4.1.1) takes a `FindByIndexNameSessionRepository` in its
  constructor and answers `getAllSessions(principal, …)` through `findByPrincipalName`. The store must implement that
  interface, or the registry cannot be built.
- The stack baseline has one database, H2 in file mode, and no other infrastructure.

## Considered options

- **Container (Tomcat) sessions with Spring Security's in-memory `SessionRegistryImpl`.** Sessions are lost on
  restart, which fails the standard's persistence constraint, and the concurrent-session cap does not survive a
  restart. Only sessions the registry saw being created are known to it.
- **Spring Session Redis.** It can meet the requirement, but only with the *indexed* repository.
  Spring Boot's default Redis repository type is `RedisSessionRepository`, which keeps no index and does not implement
  `FindByIndexNameSessionRepository`; `spring.session.redis.repository-type=indexed` selects
  `RedisIndexedSessionRepository`, which does. It also adds a second datastore to a stack that has none.
- **Spring Session JDBC (chosen).** `JdbcIndexedSessionRepository` implements `FindByIndexNameSessionRepository`
  backed by the indexed `PRINCIPAL_NAME` column, and it runs on the database the application already has.

## Decision

Spring Session JDBC through `spring-boot-starter-session-jdbc`, with `SpringSessionBackedSessionRegistry` as the only
session registry. The session tables are created by the application's own Flyway migrations, and Spring Session's
schema initialisation is turned off (`spring.session.jdbc.initialize-schema=never`). A single service owns every call
that ends sessions (ADR-037).

## Consequences

- **The `PRINCIPAL_NAME` index is load-bearing.** If it is not populated, `findByPrincipalName` returns nothing and
  every invalidation trigger becomes a no-op that still reads correctly. T-SES-009 pins it.
- **Every session write commits in its own transaction.** Spring Session JDBC builds its `TransactionTemplate` with
  `PROPAGATION_REQUIRES_NEW`, so a session deletion can never be atomic with the state change that causes it. That
  shapes ADR-039.
- **Session writes share the database with account data.** Anonymous sessions are a growth vector against that
  database, which is why ADR-040 and ADR-041 exist.
- **Spring Session JDBC publishes no session events**, and it removes expired rows in bulk. Idle expiry can therefore
  only be observed lazily, at the next request. The register records this.
- A swap to any other store must keep a `FindByIndexNameSessionRepository` implementation, and T-SES-009 must still
  pass on it. For Redis that means the indexed repository, not Boot's default.

## Sources

- PRD, Overview ("Server-side session via a secure HttpOnly cookie (Spring Session)"), and Story 7.
- Standalone User Access Control Application Standard §2 Happy Path step 13, §4 Data Persistence.
- Spring Session 4.1.1 source: `SpringSessionBackedSessionRegistry` (constructor and `getAllSessions`),
  `JdbcHttpSessionConfiguration` (`PROPAGATION_REQUIRES_NEW`).
- Spring Session reference, Redis configuration: "Choosing Between RedisSessionRepository and
  RedisIndexedSessionRepository" and "Finding All Sessions of a Specific User".
