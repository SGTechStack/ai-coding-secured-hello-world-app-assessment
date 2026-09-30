# ADR 0002: In-memory indexed Spring Session store for the reference build

Status: accepted · Date: 2026-09-30

## Context

Stories 4, 7, 9, 10 and 11 need "log this user out everywhere". Spring Session's stock `MapSessionRepository` cannot look sessions up by principal, and adding Spring Session JDBC/Redis brings infrastructure the PRD keeps out of scope.

## Decision

Implement `InMemoryIndexedSessionRepository` (about 70 lines) on `FindByIndexNameSessionRepository`, using Spring Session's own `PrincipalNameIndexResolver` to find sessions by username with a linear scan. All callers depend only on the interface.

## Consequences

- Sessions live in the JVM: a restart logs everyone out and two instances do not share sessions. Acceptable for a demo; not for production.
- Swapping to `spring-session-jdbc` (H2/Postgres) or `spring-session-data-redis` is a one-bean change in `SessionConfig` with no service changes.
- The linear scan is O(sessions) per invalidation; fine below tens of thousands of sessions.
