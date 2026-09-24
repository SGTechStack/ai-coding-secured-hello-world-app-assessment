# Session store and per-user invalidation

Type: research
Status: resolved
Blocked by: —

## Question

Which Spring Session store should this app use, and how does **"invalidate all existing sessions for user X"** (required on password-reset-confirm, and implied for admin disable/delete) get implemented?

Sub-questions:

- Options: in-memory `MapSessionRepository` (does it support `FindByIndexNameSessionRepository` / principal-name index on the current version?), Spring Session JDBC over H2, or a custom userId→sessionIds registry plus Spring Security's `SessionRegistry`. Compare against the spec's needs — it's a single-instance demo, but the per-user invalidation must actually work.
- Logout semantics: confirm that session invalidation makes a **replayed cookie rejected** (Story 4 AC) under the chosen store.
- Session-fixation strategy under a REST (non-form) login — which `SessionFixationProtectionStrategy` applies when the controller drives authentication.
- Cookie attributes wiring: `HttpOnly`, `SameSite`, `Secure` (prod profile) — where they're set for the chosen store.

Investigate against primary sources (Spring Session reference docs). Deliver a findings file plus a recommendation; record it as the answer.

## Answer

**Use Spring Session JDBC over H2** (`spring-boot-starter-session-jdbc`; Spring Boot 4.1.x / Spring Session 4.1.x / Spring Security 7.x on Java 21). Findings: `MapSessionRepository` on the current line implements only `SessionRepository` — no `FindByIndexNameSessionRepository`, so it cannot enumerate a user's sessions; `JdbcIndexedSessionRepository` does implement it, so "invalidate all sessions for user X" is `findByPrincipalName(username)` + `deleteById` per id (principal index is auto-populated by the Spring Security integration). Schema machinery is trivial: `spring.session.jdbc.initialize-schema=embedded` (default) auto-creates `SPRING_SESSION*` on the dev H2. `SessionRegistry` works but is in-memory/per-JVM and needs explicit `SessionAuthenticationStrategy` wiring under a controller-driven login — a weaker fit. Logout via `invalidate()` deletes the row server-side, so a replayed cookie resolves to no session → anonymous → 401. Session fixation: `SessionManagementFilter` is off by default since Spring Security 6, so `sessionFixation()` DSL does nothing for a REST login — the controller must call `request.changeSessionId()` then persist the context via `SecurityContextRepository.saveContext(...)`. Cookies: Boot maps `server.servlet.session.cookie.*` onto Spring Session's `DefaultCookieSerializer` (HttpOnly=true, SameSite=Lax, Secure=request.isSecure() by default; set `secure=true` in prod profile) — a custom `CookieSerializer` bean makes that mapping back off.

Full details, sources, and config sketch: [research/02-session-store-findings.md](../research/02-session-store-findings.md)
