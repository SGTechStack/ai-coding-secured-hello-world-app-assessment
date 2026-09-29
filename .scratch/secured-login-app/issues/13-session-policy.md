# 13 — Session policy

Type: grilling
Status: resolved
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

## Answer

Resolved by grilling, one round, four questions. Decisions delegated by the user to the orchestrating session after the evidence was presented.

**Idle 15m, absolute 8h, one concurrent session. `SpringSessionBackedSessionRegistry`, not `SessionRegistryImpl`. No `InvalidSessionStrategy` at all. Logout returns `200` with `Clear-Site-Data`. Session revocation by direct repository deletion, not `SessionRegistry`.**

### `Q9` and `Q11` answered

- **`Q9`** — idle **15 minutes** (`Qs:214` "recommended", `Std:344`, `BOOT:39`); absolute **8 hours** (`Qs:220`, `Std:345`, `BOOT:47`); concurrent sessions **1** (`Qs:225` "high-security, recommended", `Std:343`, `BOOT:46`); session-expiry redirect **N/A — see `Q17` below**.
- **`Q10`** — remember-me **declined**, not skipped. `Qs:254` marks "No Remember Me" as "recommended for high-security applications" and the map already ruled it out of scope for want of a PRD story. Recorded so the question is answered rather than blank.
- **`Q11`** — **N/A for a cross-origin SPA.** All three login options (`Qs:265-267`) and all three logout options (`Qs:273-275`) are server-rendered redirect targets. Login returns bare **`200` with no body** (`BOOT:136` `response.setStatus(HttpServletResponse.SC_OK)`, `Std:158`), matching 01's ruling; the SPA routes itself and learns its role from `GET /currentUser`. Logout returns **`200` + `Clear-Site-Data`** (`Std:223`). Answered as "not applicable, and why", not left blank.

### The standard contradicts itself on expiry, and `:90` loses (`Q17`)

Two clauses in the same file:

- `Std:90` — "When the session expires, the caller is redirected to the configured invalid-session path, or the framework default if none is set", with `:91` glossing it: "*the default redirect target is the login page (e.g., `/login?expired`) **when using form login***."
- `Std:438` — "Logout on an already-expired or invalid session must be handled gracefully by the client… the SPA must catch this response (typically via a global interceptor), clear local state, and redirect to the login page **without displaying an error**" — and the response it describes is a `401`/`403`, not a redirect.

**No `InvalidSessionStrategy`, no `invalidSessionUrl`.** `:90` is written for the server-rendered case and says so in its own Spring Boot note; 01 removed `formLogin`, so its premise is gone. The `SecurityFilterChain` in the binding Standalone recipe (`BOOT:61-82`) sets **neither** — only the SSO recipe does (`SSO…:104`), and that is a different product shape.

**Setting one would also spring 08's trap.** `CsrfConfigurer.createAccessDeniedHandler` inserts an `InvalidSessionAccessDeniedHandler` *ahead of* the shared handler in a `DelegatingAccessDeniedHandler` — verified in `spring-security-config-7.0.6`. A CSRF failure on an expired session would then bypass 08's JSON handler and emit whatever the strategy produces, silently breaking 21's envelope on exactly the path a user hits most often. Declining the strategy is what keeps one error contract.

**Citation fix:** this ticket cites `:437` for the global-interceptor clause. It is **`:438`**; `:437` is the plain "logout invalidates server-side session state and clears session-related cookies" line. Both of the ticket's `:438` references map to the single clause at `:438`.

### `SessionRegistryImpl` would violate an enforced constraint (`Q18`)

`Std:409` is an `[Enforced Constraint]`: "Session state is persisted to enforce concurrent session limits **across requests and restarts**."

`BOOT:77-79` configures `.maximumSessions(1).sessionRegistry(sessionRegistry())`, and the only `sessionRegistry()` body shown anywhere returns `new SessionRegistryImpl()` (`SSO…:142`) — which is in-memory, so **the concurrent-session limit evaporates on restart** and `:409` is broken. `SSO…:135-139` says as much in a comment, and `Qs:168` states the fix outright: "When using Spring Session JDBC, use `SpringSessionBackedSessionRegistry` instead of `SessionRegistryImpl` for cross-instance session invalidation."

**`SpringSessionBackedSessionRegistry` over 02's JDBC store.** The cost is zero — the store and its `PRINCIPAL_NAME` index already exist because 02 owns Spring Session's DDL through Liquibase specifically to guarantee that index survives. Taking the in-memory option would mean recording a `:409` violation to buy nothing. Single-instance topology does not change this: the binding word in `:409` is *restarts*, not *instances*.

**On exceeding the limit the earlier session is expired, not the new login rejected.** `Qs:228` — "On exceeding limit, invalidate oldest session"; `Std:47` — a new session is created "(invalidating any previous session for that user)"; `Std:89` — "A second login by the same user invalidates the earlier session." Unanimous, and it is also the behaviour that cannot lock a user out of their own account from a stale tab.

**Session fixation:** `changeSessionId()`, which is both the Spring Security 7 default (`BOOT:31`, `SSO…:105`) and what `Std:338` requires. Not configured explicitly — configuring it adds a line that can only ever restate the default. It does not break the CSRF token: the token is session-bound (09), `changeSessionId` rotates the id **in place without losing session attributes** (`SSO…:105`), so the token survives the rotation it is supposed to survive. This is also the mechanism 09 leans on for its `Std:446` reading.

### The absolute-timeout filter is the third `sendError` site (`Q19`)

`Std:398` makes both timeouts an `[Enforced Constraint]`, but **absolute timeout has no configuration key** — Spring's `server.servlet.session.timeout` is idle-only. `BOOT:148-167` implements it as an `AbsoluteSessionTimeoutFilter` storing a `START_TIME` session attribute, and at `:161` calls `response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session expired")`.

**That is exactly the defect 08 verified twice already**: `AuthorizationFilter.filterErrorDispatch` defaults `true`, so `sendError` triggers an `ERROR` dispatch which is itself authorized; and `sendError` cannot carry a `code`.

**Build the filter, drop the `sendError`.** It writes its response directly. The `401` case keeps an **empty body** per 08 and `Std:247`, so here the filter writes only a status — but it must do so via `response.setStatus` + `flushBuffer`, never `sendError`. **Recorded as a chain-wide rule** rather than a third per-filter fix: *no filter under `com.assessment.auth` calls `response.sendError`.* That is now three sites (tier-0 forced-change, IP throttle, absolute timeout) and it is a good candidate for a fifth ArchUnit row in 15.

Filter position: after authentication, before the tier-0 forced-change filter — position 7 in 09's chain order. Timestamps use 02's injectable `Clock` so 14 tests expiry without sleeping 8 hours.

**Interaction with the framework sweep, recorded per this ticket's brief:** Spring Session JDBC's `spring.session.jdbc.cleanup-cron` sweep (default every minute, kept enabled by 02) **reclaims expired rows; it does not enforce expiry.** Expiry is a fact of `SPRING_SESSION.EXPIRY_TIME` checked on read. Absolute timeout is enforced only by the filter above, and the sweep will not clean up a session that is past its *absolute* limit but within its *idle* limit — that row lives until idle expiry catches it. Harmless, but 14 should not assert row deletion as a proxy for absolute expiry.

**Under 02's file-based `dev` H2, sessions survive an application restart.** A pre-restart cookie is still valid afterwards unless a timeout has passed. In `test` (in-memory) it is not. Stated explicitly because it looks like a bug in local development and is not.

### Revocation by repository deletion, and `Clear-Site-Data` (`Q20`)

Two mechanisms coexist in the standards for "invalidate all sessions for a user":

- `Std:66` and `:72` name `SessionRegistry` / `SpringSessionBackedSessionRegistry`.
- **All three** Standalone recipes instead do `sessionRepository.findByPrincipalName(username).keySet().forEach(sessionRepository::deleteById)` — `Standalone_Self-Service_Password_and_History_Management.md:252-256`, `Standalone_Privileged_User_Administration_and_Password_Reset.md:447-449`, `Standalone_Scheduled_Account_Hygiene_Jobs.md:225-235`.

**Direct `FindByIndexNameSessionRepository` deletion.** Three recipes against one parenthetical note, and the mechanisms are not equivalent: `deleteById` **removes the row**, while `SessionRegistry.expireNow()` only marks the session expired and leaves it for the sweep. For a credential reset, removal is the semantics `Std:128` asks for ("invalidate all existing sessions… forcing the user to re-authenticate").

Note this coexists with `Q18`'s answer without conflict: the registry is what *enforces the concurrent-session cap*; the repository is what *revokes on demand*. Both are backed by the same JDBC store, so they cannot disagree about what sessions exist.

Call sites, all already required elsewhere: password reset confirm (PRD Story 7, `Std:128`), self-service change (04, `Std:466`), admin disable and admin role change (10), admin delete (10).

**Logout** — `invalidateHttpSession(true)`, `deleteCookies("JSESSIONID", "SESSION")` (`BOOT:76`, `SSO…:113-114`), returns `200`, and emits **`Clear-Site-Data: "cache","cookies","storage"`**. That header is mandated four times (`Std:391`, `:73`, `:223`, `:635`) and is **absent from `BOOT:74-76`** — another case of a recipe undershooting its standard. `Std:74` names the fix: a custom `LogoutSuccessHandler`, since Spring's default logout handler cannot add it. Handed here by 09.

"Cleared" for a cross-origin cookie means the `Set-Cookie` deletion must carry **the same `Path`, `SameSite` and `Secure` attributes** as the original or the browser ignores it — the one detail `deleteCookies` gets right only if the cookie was created with default attributes, which under 09's explicit `same-site: lax` / `secure: true` it is not. **14 must assert the replayed pre-logout cookie is rejected** (PRD Story 4, `prd:61`), which catches this whether or not the attributes match.

**Logout on an expired session returns `401`** under 08's `HttpStatusEntryPoint` — `Std:438`'s predicted behaviour, absorbed by the SPA interceptor, and explicitly *not* to be "fixed" by exempting logout from CSRF (09 confirmed no exemption exists).

### Amends

- **09** — `Clear-Site-Data` lands here, as flagged; no chain-order change beyond inserting `AbsoluteSessionTimeoutFilter` at position 7.
- **15** — a fifth ArchUnit row candidate: no `response.sendError` under `com.assessment.auth`. `SpringSessionBackedSessionRegistry` needs no new dependency.
- **21** — the absolute-timeout filter is a writer site, but writes an empty `401`, so it uses the status-only path rather than the `ProblemDetail` path.
- **10, 11** — both call the revocation helper settled here; 10 additionally on disable and role change (`Priv:262-267` fires on either), 11 on reset confirm.
- **04** — its "change kills all sessions including the caller's" ruling uses this mechanism; no change to the decision.
- **14** — tests for: idle expiry on an injected `Clock`; absolute expiry at 8h likewise; a second login expiring the first session (`Std:433`); the concurrent limit surviving a restart (`Std:434`, the `:409` property); replayed post-logout cookie rejected; `Clear-Site-Data` present on logout; logout on an expired session returning `401`; and dev-H2 sessions surviving restart.
