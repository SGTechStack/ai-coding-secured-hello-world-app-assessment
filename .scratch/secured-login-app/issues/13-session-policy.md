# 13 — Session policy

Type: grilling
Status: open
Blocked by: 02
Map: [Secured Login App](../map.md)

## Question

What are the session timeout policies and the login/logout behaviours?

Answer `Q9` (session timeout policies, `Questions.md:211`) and `Q11` (login and logout redirect behaviours, `Questions.md:261`) of the standard's question set.

- **Timeouts**: idle timeout, absolute maximum session lifetime, concurrent-session policy (may one user hold several sessions at once — which interacts with Story 7's "invalidate all sessions" and with 02's find-by-principal requirement), and what the client sees when a session expires mid-use.
- **Login/logout behaviour**: `Q11` is framed for server-rendered redirects, but this is a cross-origin SPA — so the answer is status codes and response bodies, not `Location` headers. Settle what login success returns (200 with a body? 204? what does the SPA learn about its own role, which ties to 10's self-read endpoint), and what logout returns.
- Session-fixation protection: the PRD requires it (`prd/assessment-prd.md:117`) — confirm the strategy (new session id on authentication) and that it does not break the CSRF token established in 09.
- Logout must invalidate server-side **and** clear the cookie, and a replayed pre-logout cookie must be rejected (Story 4). Decide explicitly what "cleared" means for a cross-origin cookie.

`Q10` (remember-me) is already **out of scope** — record that it was consciously declined rather than leaving `Q10` blank.

Blocked on 02 (the session store decides what timeout and concurrency controls are available).

**Amended by [02 — Persistence and session backend](02-persistence-and-session-backend.md); this ticket is now unblocked.** The store is **Spring Session JDBC on the shared application datasource**, so the controls this ticket's `Q9` chooses between are all available:

- **Find-by-principal works**, which is what makes a concurrent-session cap and Story 7's "invalidate all sessions" enforceable at all — `FindByIndexNameSessionRepository` is backed by the `PRINCIPAL_NAME` index carried in Spring Session's packaged DDL. 02 owns that DDL through Liquibase specifically to guarantee the index is not lost; if this ticket sets a concurrent-session maximum, that index is the mechanism.
- **Idle and absolute timeouts** are `server.servlet.session.timeout` plus Spring Session's own settings; the `SPRING_SESSION` row carries `EXPIRY_TIME`, so expiry is server-side fact, not cookie arithmetic.
- **A framework-internal expired-session sweep already runs** (`spring.session.jdbc.cleanup-cron`, default every minute). This ticket should note its interaction with whatever absolute timeout it sets — the sweep is what reclaims rows, not what enforces expiry — and that 02 deliberately left it enabled.
- **Under 02's file-based `dev` H2, sessions now survive an application restart.** That is a behaviour this ticket should state explicitly: a pre-restart cookie is still valid afterwards unless the timeout has passed. In `test` (in-memory) it is not.
- Timestamp comparisons follow 02: UTC `Instant`, with an injectable `Clock` so 14 can test expiry without sleeping.

**Amended by [08 — Authorization matrix](08-authorization-matrix.md).** One interaction, found while verifying the CSRF path, that this ticket alone can trip.

- **If this ticket configures an `InvalidSessionStrategy`** (via `sessionManagement().invalidSessionUrl(...)` or an explicit strategy), `CsrfConfigurer.createAccessDeniedHandler` inserts an `InvalidSessionAccessDeniedHandler` *ahead of* the shared handler in a `DelegatingAccessDeniedHandler` — verified in `spring-security-config-7.0.6`. A CSRF failure on an expired session then bypasses 08's JSON `accessDeniedHandler` entirely and produces whatever that strategy emits. Decide it deliberately: it is a reasonable behaviour, but it must not be acquired by accident, because it is the one path on which the error body silently stops matching 21's contract.
- **Logout on an expired session returns `401`** under 08's explicit entry point, which is what `Standalone_User_Access_Control_Application_Standard.md:438` predicts and what the SPA's global interceptor is built to absorb. Any invalid-session strategy this ticket picks must not convert that into a redirect, which is the other half of what `:438` forbids.
