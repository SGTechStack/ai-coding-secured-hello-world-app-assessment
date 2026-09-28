# 08 — Decide session management and the CSRF contract

Type: grilling
Status: resolved
Blocked by: 01, 02, 04, 05, 20

## Question

How do sessions behave end to end, and what is the exact CSRF contract between the SPA on one origin
and the API on another?

## Settled going in

The App Standard's `[Enforced Constraint]` settles the part that looked hardest: **Synchronizer
Token Pattern bound to the HTTP session** via `HttpSessionCsrfTokenRepository`, delivered by a
dedicated non-cacheable endpoint. `CookieCsrfTokenRepository` (double submit) is **prohibited**.
Spring Session JDBC is the store. Cookies are `HttpOnly`, `Secure`, `SameSite=Lax`.

Also settled and worth restating because it is counter-intuitive: **logout is not CSRF-exempt.** Its
CSRF token is session-bound, so logout on an expired session correctly returns 401/403, and the SPA
must absorb that rather than the server relaxing protection. The standard says so explicitly.

## Inherited from ticket 06 — constraints, not open questions

[Decide the API error envelope and the enumeration-safe response contract](06-error-envelope-and-enumeration-contract.md)
fixed three things this ticket was going to have to invent:

- **CSRF failure and authorization failure share status 403 but are distinguished by `code`.**
  `CSRF_TOKEN_INVALID` triggers **one** silent re-bootstrap-and-retry, capped at a single attempt so a broken
  token cannot loop; `ACCESS_DENIED` never retries. This matters on the *normal* path, not an edge case:
  ticket 04 found `CsrfAuthenticationStrategy` and `CsrfLogoutHandler` clear the token on login and logout, so
  the token must be re-fetched, and the recipes' "SPA treats 401/403 alike, clear state and redirect to login"
  would log out a legitimately authenticated user.
- **Logout on a dead session returns 403**, resolving the corpus's deliberately unresolved "401/403". The CSRF
  filter runs first and the session-bound token is already gone. Not engineered around — §5 explicitly forbids
  exempting logout from CSRF.
- **Session expiry (idle or absolute) returns 401 `AUTHENTICATION_FAILED`, never a redirect**, overriding
  Failure Path 9's `/login?expired` 302. A 302 to an HTML login page is incoherent for an SPA on its own origin.
- `Clear-Site-Data: "cache","cookies","storage"` stays a logout-only header; it is not attached to 401s.

## What to decide

**Session lifecycle.**

- Idle timeout: standard default 15 minutes. Confirm, or justify a different value.
- Absolute lifetime: standard default 8 hours. Spring Session has no first-class absolute-lifetime
  knob, so decide the mechanism (a creation-time attribute checked by a filter? a custom
  `SessionRepository` wrapper?) using "Pin down the Spring Security 7 config surface".
- Maximum concurrent sessions: standard default 1, meaning a second login kills the first. Confirm.
  Decide which session dies — the standard says the *earlier* one is invalidated, so a new login
  always wins. Note the UX consequence: two browser tabs are fine, two devices are not.
- Session fixation: ID rotation on login. Spring Security does this by default; confirm it survives
  the custom JSON authentication filter.
- What invalidates every session for a user, and where that is centralised: password reset, password
  change, admin disable, admin role change (does changing someone's role force re-login? decide),
  admin delete.

**CSRF contract.**

- The token endpoint: path, method, response body shape, cache headers. The standard requires no
  caching.
- Header name for submission — `X-CSRF-TOKEN` per the standard's note.
- **Bootstrap ordering**, the subtle bit: the SPA needs a CSRF token to POST to login, but the token
  is session-bound and there is no session before login. Decide the sequence: does an anonymous
  session get created to carry the pre-login token, and if so, does the session ID rotate on
  successful login while the CSRF token is also rotated? Check
  `Standalone_Session_Login_with_CSRF_Bootstrap.md` — it likely prescribes this exactly.
- Token rotation policy: on login, on logout, per request, or never within a session.
- What the SPA does on a 403 from an expired token: silently re-fetch and retry once, or bounce to
  login? This decision is consumed by "Design the frontend architecture".

~~**Cross-origin specifics.**~~ and ~~**Per-profile cookie config.**~~ **Both closed — do not
re-decide.** Settled by
[Decide the origin topology](20-deployment-origin-topology.md): two origins at
**`http://localhost:5173`** (not `3000` — the PRD's port is hedged "e.g.") and `http://localhost:8080`,
which are cross-*origin* but same-*site*, so the naive "we need `SameSite=None`" reasoning is wrong.
The value is **`SameSite=Strict`**, not the standard's mandated `Lax`, and it fails the standard's own
prescribed test at line 499 by design — assert `Strict` with a comment pointing at the ADR. Cookie name
is `SESSION` in dev and **`__Host-SESSION`** in the non-dev profile, which is what makes the profile
split unforgeable: the prefix is rejected by browsers over plain HTTP, so a production path shipping
with `Secure=false` cannot set a cookie at all. Production is a **documented requirement, not an
environment**, so that profile is asserted by a test and never run. The same-site deployment constraint
is recorded and has moved to
[the operational handover document](25-operational-handover-document.md). *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-SES-002, T-SES-011. Amend the table by ID, not this list.*

## Done when

Timeouts, concurrency, invalidation triggers, the CSRF bootstrap sequence, rotation policy, and the
same-site deployment constraint are all recorded, with the profile split specified.

## Answer

Sessions are **15 minutes idle / 8 hours absolute / 1 concurrent, new login wins**, with the absolute
clock anchored on a single `AUTH_INSTANT` stamping point and no persistent cookie. CSRF is
**session-bound, header-only, rotated on login and logout**, fetched from an explicit
`GET ${api.base-path}/csrf` that creates the pre-login session deliberately rather than by accident.
Three things in the inherited plan were wrong and are corrected here: **Route C silently dropped CSRF
token rotation at login**, the default handler **accepts the token as a query parameter**, and the
`[Enforced Constraint]` pre-login session existed **only as a Jackson side effect**. Two further
defects found in the binding Standard take this map's total to six. *Consolidated into the register (ticket 33): R-STD-015. Amend the table by ID, not this list.*

---

### 1. The numbers, and why none of them move

| Control | Value | Source |
| --- | --- | --- |
| Idle timeout | **15 min** | §3.5:344 default; Questions Q9 recommendation |
| Absolute lifetime | **8 hours** | §3.5:345 default; Questions Q9 offers 12 for 24/7 ops, which we are not |
| Max concurrent sessions | **1** | §3.5:343; new login wins, earlier session dies |
| Session persistence | Spring Session JDBC | §4:409 `[Enforced Constraint]` |

All three are the standard's own defaults, so **no ADR is owed for the values** — only for the
mechanisms and deviations below. Idle timeout is one property, `spring.session.timeout=15m`; ticket 05 *Consolidated into the ADR routing (ticket 34): REJ-012. Amend by ID, not this list.*
established the Spring Session key wins over `server.servlet.session.timeout`, so setting both is how
you get a silent mismatch.

**Pre-login sessions get the same 15 minutes — deliberately no second timer.** A shorter
unauthenticated window was considered and rejected: it adds a second clock to reason about, and the
failure it prevents is already handled, since an expired pre-login token yields 403
`CSRF_TOKEN_INVALID` and ticket 06's single retry recovers it transparently. *Consolidated into the register (ticket 33): R-SES-009. Amend the table by ID, not this list.*

The real control on unauthenticated session growth is **rate limiting, not the cleanup cron**, and the
arithmetic matters because it is easy to assume the cron covers this. `spring.session.jdbc.cleanup-cron`
deletes only rows whose `EXPIRY_TIME` has passed; **live rows are untouched**. Live anonymous rows ≈
request rate × idle window, so at ticket 09's current 10/min figure a single source holds roughly
**150 live rows** that the cron never sees. There is no structural escape — §3.1:238 requires the token
be session-bound, so a pre-login session is unavoidable — which makes the limiter the only lever.
Ticket 09 inherits that number.

### 2. The absolute clock: one stamping point, and the `Max-Age` we do not set

Mechanism is ticket 05's: an `AuthInstantStampingStrategy` writes `AUTH_INSTANT` into the session at
password login, and an `AbsoluteSessionLifetimeFilter` invalidates past it. Not `getCreationTime()`,
because `changeSessionId()` preserves it from before login.

**The invariant this ticket adds: `AuthInstantStampingStrategy` is wired in exactly one place — the
login composite — and nowhere else.** The trap surfaced three separate times during this grill (TOTP
factor grant, TOTP re-verification every 10 minutes, and session-id rotation on password change), and
each time the failure mode is identical and silent: re-stamping resets the 8-hour window, so a busy
admin never absolutely expires. Recorded as an invariant rather than remembered three times.

**`server.servlet.session.cookie.max-age` is deliberately omitted**, deviating from ticket 05's draft
`application-prod.properties` which set it to `8h`. Reasons: it converts a browser-session cookie into
a **persistent** cookie surviving browser close, which is a de facto weak remember-me when Questions Q10
recommends none at all; and it is advisory only, since a hostile client just keeps sending the cookie.
The server-side filter is the control. Recorded because a future reader will otherwise "fix" it back —
it looks like an oversight and is not.

### 3. Concurrency: the cap is trivial, the response is not

Cap 1, `maxSessionsPreventsLogin(false)` (the default), earlier session dies. The standard is unusually
consistent here — §3.5:343, Happy Path:47, Failure Path 8:89, the sequence diagram:157 and Questions Q9
all agree — so this is confirmation, not a decision.

**Lazy eviction accepted.** Ticket 05 found `expireNow()` only sets a marker attribute on the Spring
Session row; the displaced user is logged out when their next request reaches `ConcurrentSessionFilter`.
That satisfies line 89's actual requirement ("can no longer make authenticated calls") without needing
the row deleted synchronously.

**The part the corpus never addresses: two code paths bypass the error envelope.** At eviction the
default `SessionInformationExpiredStrategy` writes plain text, and a stale cookie hitting
`invalidSessionStrategy` gets a redirect or nothing. Both must be wired to ticket 06's
`ProblemDetailWriter` emitting **401 `AUTHENTICATION_FAILED`**. Ticket 06 already found four independent
envelope producers because Spring Security never routes through `@RestControllerAdvice`; these are the
fifth and sixth, and they were not on its list.

**Eviction reuses `AUTHENTICATION_FAILED` rather than earning a 15th code.** This keeps ticket 06's enum
closed. Stated cost, not hidden: the displaced user gets no "you signed in elsewhere" explanation. The
distinction lives in the audit log instead, which is ticket 13's to catalogue — and §3.3 currently has
**no auditable event for concurrent-session eviction** even though §6 tells us to monitor exactly that.

UX consequence to record: two browser tabs are fine, two devices are not. *Consolidated into the register (ticket 33): R-SES-001. Amend the table by ID, not this list.*

### 4. Failed login does not kill a live session — Standard defect four

Failure Path 1 (§2:82) says invalid credentials return 401 "and any existing session is invalidated".
Read literally, **anyone who knows a username can terminate that user's live session at will** by
POSTing a wrong password — an unauthenticated denial of service sitting two lines above line 84's stated
concern about mass-lockout DoS. No other clause supports it: lines 47 and 89 both attach invalidation to
*successful* authentication.

**Deviation: invalidate on successful authentication only.** Narrow by design — the failed-login counter
still increments and lockout still applies, so the attacker gains nothing they did not already have.
This is the fourth defect found in the Standard on this map, after ticket 02's missing lockout
observation window and unreachable per-account rate limit, and ticket 22's non-sliding sliding window.
Needs an ADR because a compliance reviewer reading Failure Path 1 will look for this behaviour and find
it absent. *Consolidated into the register (ticket 33): R-STD-015. Amend the table by ID, not this list.*

### 5. Invalidation is actor-relative — and the recipe's own method name proves it

The standard says "all sessions" for credential changes (§2:71, §5:466, Decision Logic:128). Applied
literally to self-service password change, that logs the user out of the tab they just changed their
password in.

**The recipe contradicts itself on this, which is the strongest available evidence of intent.**
`Standalone_Self-Service_Password_and_History_Management.md:245-256` declares
`revokeOtherSessions(String username)` and then deletes **every** session returned by
`findByPrincipalName`, the caller's included. The method name states an intent the body does not
implement.

**The distinction only bites where the actor and the subject are the same principal**, which narrows the
deviation to two triggers:

| Trigger | Scope | Actor |
| --- | --- | --- |
| Self-service password change | **all others** | same principal |
| Forced-change completion | **all others** | same principal |
| Password reset redemption | all | unauthenticated, no actor session exists |
| Admin reset token issuance | all (of target) | different principal |
| Admin disable | all (of target) | different principal |
| Admin role change | all (of target) | different principal |
| Admin soft-delete | all (of target) | different principal |
| Account lockout | all | no actor |
| Admin TOTP factor reset | all (of target) | different principal |

For every "different principal" row, "all" and "all others" are the same thing, so nothing is decided
there. The standard helpfully forbids the self-acting admin cases that would blur this: Failure Path 12
blocks changing your own roles through the admin flow, Failure Path 13 blocks self-delete, and
self-unlock is blocked too.

**Four triggers the standard omits entirely are added**: admin manual disable, admin role change, admin
soft-delete, and account lockout. Only the *batch* disable path is covered (§2:114), and batch hygiene
jobs are out of scope on this map, so that clause never applies to us and the manual equivalents have no
clause at all. Since authorities are serialised into the session, **a role downgrade otherwise leaves a
live privileged session for up to 15 minutes**, and lockout is cosmetic against an already-authenticated
attacker. Ticket 04 confirmed `ImmutableSecurityHandler` guards role *definitions* only, so Story 10's
role-change endpoint is permitted and this is reachable.

**The surviving session's id rotates on password change** (OWASP renewal on credential change), using
the minimal strategy from §7 — and not `AuthInstantStampingStrategy`, per §2's invariant.

**One seam owns every call.** A single `SessionTerminationService` wrapping
`SpringSessionBackedSessionRegistry`, mirroring ticket 07's "one `PasswordService` owns `encode()`"
discipline for the same reason: the corpus's demonstrated failure mode is a shared helper that simply
never gets called. Ordering rule kept from the recipe — **persist the state change first, then
invalidate**, so a failed invalidation never leaves a user unable to log in.

**Silent-failure warning inherited from ticket 05, restated because it voids this entire section:** the
`PRINCIPAL_NAME` index must be populated or `findByPrincipalName` returns nothing and *every* trigger
above becomes a no-op that still looks correct in code review. Requires an integration test, not
inspection. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-SES-009. Amend the table by ID, not this list.*

### 6. The `/csrf` contract, and the query-parameter path nobody noticed

`GET ${api.base-path}/csrf` → 200. `api.base-path` is adopted as `/api/v1`, consistent with the recipes'
own examples; no other ticket owns it, so this is where it is recorded.

**Our own response DTO: `{"headerName": "X-CSRF-TOKEN", "token": "..."}`.** The recipe
(`Common_Security_Headers_and_SPA_CSRF_Configuration.md:109-119`) returns Spring's `CsrfToken` interface
directly, so the wire format is an accident of Jackson reflecting over a framework type and no recipe
ever prints the resulting JSON. `parameterName` is dropped deliberately — see below.

**`Cache-Control: no-store` set explicitly on the method**, not inherited from `HeaderWriterFilter`.
§3.1:238 makes non-caching part of an `[Enforced Constraint]` and §5:443 tests it; any `headers()`
customisation can switch the default off, and cheap insurance beats a silent regression on an enforced
clause.

**Header-only token resolution.** The default `CsrfTokenRequestAttributeHandler` resolves the header and
falls back to the `_csrf` **request parameter**. §3.4 forbids logging CSRF tokens, and a token in a query
string lands in access logs and `Referer` — so the framework default actively creates the leak the
logging contract prohibits. Nothing in the corpus notices this. There is no setter to disable the
fallback, so we wrap `XorCsrfTokenRequestAttributeHandler` (keeping BREACH protection) in a handler that
reads the header only. Dropping `parameterName` from the DTO removes the discovery path as well.

Two facts to record rather than decide: the XOR handler returns a **different masked value on every
call**, so nothing may cache the response or assert token equality across calls; and every
`${api.base-path}` in the recipes sits inside a **Java string literal where it is never resolved**, so
the printed whitelist matches a literal `${api.base-path}/csrf` while `@RequestMapping` resolves properly
— as printed, the bootstrap endpoint is mapped at the real path and whitelisted at a fake one, and the
unauthenticated GET is rejected. That is the single most damaging defect in the recipe for this flow.

### 7. Bootstrap: an Enforced Constraint resting on a serialisation side effect

The SPA needs a token to POST to login, the token is session-bound, and there is no session before
login. The corpus's entire treatment of this is the phrase "resolves 'Deferred Token' issues".

**What actually happens today is undocumented and fragile**: serialising the returned `CsrfToken` calls
`getToken()`, which resolves the `DeferredCsrfToken`, which calls `HttpSessionCsrfTokenRepository.saveToken`
→ `request.getSession()`. The pre-login session — required by an `[Enforced Constraint]` — exists as a
**side effect of Jackson calling a getter**. Change the controller's return type and it stops working
silently.

**Decision: create it deliberately**, in the controller, with a comment naming §3.1:238 as the reason.

Sequence recorded for ticket 14:

1. `GET /api/v1/csrf` with `credentials: 'include'` → anonymous session + token
2. `POST /api/v1/login` with JSON body + `X-CSRF-TOKEN` — CSRF **does** apply to login (Failure Path
   6:87 is explicit; this is login-CSRF protection)
3. On success: session id rotates **and** token rotates (§8), so the SPA re-fetches

Neither `credentials: 'include'` nor the CORS preflight on step 2 (JSON content type plus custom header)
is mentioned anywhere in the corpus, and omitting the former means the session cookie never arrives.

### 8. Rotation: Route C silently dropped `CsrfAuthenticationStrategy`

**This is the most consequential finding in the ticket.** Tickets 04 and 06 both assume
`CsrfAuthenticationStrategy` clears the token at login — true when form login is wired through the DSL,
because `CsrfConfigurer` registers it into the composite. Ticket 05 chose **Route C** (custom JSON filter
with a hand-built `CompositeSessionAuthenticationStrategy`), and the composite it drafted contains
`ConcurrentSessionControl`, `ChangeSessionId`, `RegisterSession` and `AuthInstantStamping` — and **no
`CsrfAuthenticationStrategy`**.

Two consequences, both bad and neither visible in a passing test suite: a pre-login token an attacker
planted survives authentication, and ticket 06's entire re-bootstrap-and-retry design loses its trigger
and becomes dead code.

**Decided: add `CsrfAuthenticationStrategy` to the composite explicitly**, with a comment saying why it
is listed when the DSL provides it free — the surprise is exactly that Route C makes it not free.
Separately **verify** that `CsrfLogoutHandler` still arrives via `CsrfConfigurer` now that logout uses
the DSL while login does not. Assume nothing there.

**Rotate on login and logout only.** Per-request rotation is rejected: `HttpSessionCsrfTokenRepository`
does not do it, and it would break TanStack Query's parallel mutations.

**The SPA re-fetches proactively** on login success, logout success and TOTP verify success, keeping
ticket 06's single silent retry as the **backstop rather than the primary path**. Reason: leaning on the
retry manufactures a 403 on the first mutation after every single login, and §6 asks us to monitor CSRF
403 and endpoint-access spikes. A design that guarantees a 403 per session poisons its own detection
signal.

### 9. Granting the TOTP factor into a live session

Ticket 19 handed this ticket two explicit questions. Both now answered.

**Serialisation — verified, no problem.** `FactorGrantedAuthority` lives in
`org.springframework.security.core.authority`, implements `Serializable` and `GrantedAuthority`, and
carries a `String` plus an `Instant` from `getIssuedAt()`. Both fields are serialisable, so it survives
JDK serialisation into `SPRING_SESSION_ATTRIBUTES` — which is the storage ticket 05 chose, so the Jackson
3 / `SecurityJackson2Modules` mismatch it flagged never arises here.

**That `getIssuedAt()` instant *is* the clock `validDuration(10 min)` reads.** So re-verification is not
a flag flip: it means constructing a new `Authentication` with a fresh authority set and writing it back
through the `SecurityContextRepository`. Worth stating because "grant the factor" sounds like a mutation
and is actually a replacement.

**Session id rotates on factor grant**, using a **purpose-built minimal strategy, not the login
composite**: `ChangeSessionIdAuthenticationStrategy` plus an explicit `saveContext`, plus
`CsrfAuthenticationStrategy` so the token rotates with the id and the SPA re-fetches. Deliberately
**excluding `AuthInstantStampingStrategy`** (§2's invariant) — otherwise each 10-minute re-verification
resets the 8-hour absolute window and a working admin never absolutely expires. Rationale for rotating at
all: OWASP asks for id renewal on privilege elevation, and this is a real elevation for a cost of
approximately nothing.

**Handover to ticket 23 — corrected mid-grill, and smaller than first stated.** An earlier draft of this
answer claimed Spring cannot distinguish a missing-factor denial from an ordinary role denial. That was
wrong. The [Spring Security 7 MFA announcement](https://spring.io/blog/2025/10/21/multi-factor-authentication-in-spring-security-7)
states that when a factor authority is absent, Spring Security redirects the user to the endpoint where
that factor can be obtained, giving `FACTOR_PASSWORD` → `/login` as its example. The framework tracks
which factor is missing and routes on it.

So ticket 23 inherits a **conversion job, not an invention job**: replace the redirect with ticket 06's
JSON **412 `MISSING_FACTOR`**. This is the same shape as a decision ticket 06 already made — a 302 to an
HTML page is incoherent for an SPA, so 06 replaced the session-expiry redirect with 401 — which makes it
a second instance of an established pattern rather than a novel problem. The one genuinely open piece:
`FACTOR_TOTP` is a **custom** factor with no framework-known endpoint, so the factor-to-endpoint mapping
must be declared before there is a redirect to convert. Content rephrased from the source for licensing
compliance.

### 10. Logout: what `Clear-Site-Data` actually reaches

§3.5:391 mandates `Clear-Site-Data: "cache","cookies","storage"`. Under ticket 20's two-origin topology
its reach is uneven, and per [MDN's description of the directives](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Clear-Site-Data)
the split is:

- **`cookies`** reaches the registered domain, so it does clear the session cookie at `localhost`
  regardless of port. This half works.
- **`cache` and `storage`** are scoped to the responding origin, `:8080`, whose storage is empty. The
  SPA's `localStorage` and query cache at `:5173` are **never touched**.

An earlier draft of this answer also claimed the header is ignored over plain HTTP and therefore
untestable locally. **That was wrong** — `http://localhost` is a potentially trustworthy origin, so the
header fires in dev and every claim above is **observable in a browser test rather than assumed**. The
correction matters beyond the fact: the bad fact was being used to justify not testing, and it wrongly
grouped this header with CSP as "asserted but never exercised". It is not in that category.

**Decided: send the header exactly as prescribed, assert it, and treat the SPA clearing its own state as
the actual control** — unconditional, on every logout and every 401. Ticket 16 gets a real browser
assertion for the scoping; ticket 25's handover document gets the origin-scoping note. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-HDR-005. Amend the table by ID, not this list.*

**`.deleteCookies("JSESSIONID", "SESSION")` is dropped.** It is wrong three ways for this stack: the name
is profile-dependent (`__Host-SESSION` outside dev), `deleteCookies` emits a bare `Set-Cookie` with no
`Secure`/`Path`/`SameSite`, and a `__Host-` cookie will not match it. Spring Session's
`CookieHttpSessionIdResolver` expires the cookie through the same `DefaultCookieSerializer` that wrote
it, so name and attributes stay correct across profiles by construction. Implementation note rather than
an ADR — the recipes have already been established as non-compiling and non-binding by tickets 04 and 22. *Consolidated into the ADR routing (ticket 34): REJ-011. Amend by ID, not this list.*

Unchanged from ticket 06: **logout on a dead session returns 403**, and `Clear-Site-Data` stays
logout-only and is never attached to 401s.

### 11. Two prescribed tests that cannot pass as written — Standard defects five and six

**§5:446 — "Previously redeemed CSRF tokens are rejected on subsequent attempts."**
`HttpSessionCsrfTokenRepository` issues one token per session with no redemption concept at all. Taken
literally this requires bespoke one-time-token logic that would break parallel mutations and contradict
the repository mandated four sections earlier by §3.1:238. **Reinterpreted** as "a token from a
superseded session is rejected", and tested as such: a pre-login token fails after login, a pre-logout
token fails after logout. That is a real security property, it is satisfiable, and §8's rotation is
precisely what delivers it — so the reinterpretation costs no coverage. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CSRF-007. Amend the table by ID, not this list.* *Consolidated into the register (ticket 33): R-STD-016. Amend the table by ID, not this list.*

**§5:499 and §3.5:346-350 — "Session *and CSRF* cookies must have `HttpOnly`, `Secure`,
`SameSite=Lax`."** Under the mandated Synchronizer Token Pattern **there is no CSRF cookie at all**, and
`HttpOnly` on a double-submit CSRF cookie would break double-submit by construction — so the clause
cancels §3.1:238 twice over. Ticket 20 already settled the `SameSite` half (assert `Strict`, comment
pointing at the ADR). The CSRF-cookie half is **dead text**, and rather than silently skipping it we
convert it into a **negative assertion: no CSRF cookie is ever set on any response**. The recipes' own
manual verification already asks for this ("verify no `XSRF-TOKEN` cookie is present"); we make it an
automated check. Turning dead prose into an enforced negative is strictly better than a skipped line in
a compliance review. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CSRF-001. Amend the table by ID, not this list.* *Consolidated into the register (ticket 33): R-STD-017. Amend the table by ID, not this list.*

### 12. Filter ordering is load-bearing, and `sendError` is prohibited

**Ordering.** The absolute-lifetime filter registers after `SecurityContextHolderFilter`, which places
it **before `CsrfFilter`** — and that ordering is what produces the right answer. An absolutely-expired
session doing a POST gets **401 `AUTHENTICATION_FAILED`** (ticket 06's decision). Move the filter later
and `CsrfFilter` runs first against a session whose token has just been destroyed, yielding a misleading
403 `CSRF_TOKEN_INVALID`, a pointless re-bootstrap-and-retry, and *then* the 401. Recorded as an
invariant because the ordering looks arbitrary and is not. Anonymous sessions are unaffected: no
`AUTH_INSTANT` attribute means the filter skips them.

**`sendError` is prohibited by ticket 06, and two inherited code samples violate it.** The recipe's
`AbsoluteSessionTimeoutFilter` (`Standalone_Session_Login_with_CSRF_Bootstrap.md:149-167`) calls
`response.sendError(SC_UNAUTHORIZED, "Session expired")`, and ticket 05's own draft filter carries the
same call with a `// emit the app's standard 401 envelope here` placeholder. Both must route through
ticket 06's `ProblemDetailWriter`. (The recipe's version additionally does not compile — `doFilterInternal`
is declared `public` against a `protected` superclass method and omits the checked exceptions its body
throws.)

### 13. What this hands to other tickets

- **09 (lockout and rate limiting):** `/csrf` is unauthenticated and writes a session row per call;
  **the limiter, not the cleanup cron, is the sizing control** — roughly 150 live rows per source at
  10/min with a 15-minute window. Also: lockout must invalidate the target's sessions.
- **10 (credential flows):** self-service change and forced-change completion terminate **all other**
  sessions and rotate the surviving session id. Also a recipe contradiction to resolve — the
  `PasswordChangeFilter` whitelist in `Standalone_Privileged_User_Administration_and_Password_Reset.md:532-537`
  permits `GET /csrf` but **not** `/logout`, while `Standalone_Self-Service_Password_and_History_Management.md:162`
  permits `/logout` but **not** `/csrf`. Under our contract a forced-change user needs **both**: no token,
  no change POST and no logout POST.
- **12 (data model):** `SPRING_SESSION` + `SPRING_SESSION_ATTRIBUTES` DDL owned in Flyway,
  `initialize-schema=never`, `PRINCIPAL_NAME` index load-bearing.
- **13 (audit catalogue):** §3.3 has **no auditable event** for session expiry or concurrent-session
  eviction, though §6 tells us to monitor both. Idle-vs-absolute-vs-eviction must be distinguishable in
  the log since it is deliberately not distinguishable on the wire.
- **14 (frontend):** the bootstrap sequence in §7; `credentials: 'include'` mandatory; proactive
  re-fetch on login/logout/TOTP-verify with the single retry as backstop; clear local state on every
  logout and every 401 regardless of `Clear-Site-Data`.
- **16 (test plan):** the reinterpreted §5:446, the negative no-CSRF-cookie assertion, a browser test
  for `Clear-Site-Data` scoping, a two-logins test for `maximumSessions(1)`, and a short-lifetime
  integration test for the absolute filter.
- **23 (TOTP flows):** declare where `FACTOR_TOTP` is obtained, then convert Spring's missing-factor
  redirect into 412 `MISSING_FACTOR`.
- **25 (handover):** `Clear-Site-Data` reaches the API origin's storage only. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-SES-010, T-SES-006, T-HDR-005, T-CSRF-007, T-CSRF-001. Amend the table by ID, not this list.*

### 14. ADRs owed (eight) and glossary terms (three)

1. Failed login does **not** invalidate an existing session — deviation from Failure Path 1 on DoS
   grounds. *Consolidated into the ADR routing (ticket 34): ADR-034. Amend by ID, not this list.*
2. Self-actor triggers terminate **all other** sessions, not all — deviation from §3.5/§5:466, citing
   ASVS and the recipe's own method name. *Consolidated into the ADR routing (ticket 34): ADR-035. Amend by ID, not this list.*
3. **No cookie `Max-Age`**; the server-side filter is the sole absolute-lifetime control — deviation from
   ticket 05's draft, rejecting a persistent cookie as implicit remember-me. *Consolidated into the ADR routing (ticket 34): REJ-008. Amend by ID, not this list.*
4. **Header-only CSRF resolution** — overriding the framework's `_csrf` parameter fallback because §3.4
   forbids logging tokens. *Consolidated into the ADR routing (ticket 34): ADR-036. Amend by ID, not this list.*
5. **Two prescribed tests reinterpreted** (§5:446 as superseded-session rejection; §5:499's CSRF-cookie
   clauses as dead text replaced by a negative assertion). *Consolidated into the ADR routing (ticket 34): REJ-009. Amend by ID, not this list.*
6. **Session invalidation extended to four triggers the Standard omits** — admin disable, role change,
   soft-delete, lockout. *Consolidated into the ADR routing (ticket 34): ADR-037. Amend by ID, not this list.*
7. **Session id rotation on factor grant and on credential change**, with the single-stamping-point
   `AUTH_INSTANT` invariant and the filter-ordering invariant. *Consolidated into the ADR routing (ticket 34): ADR-038. Amend by ID, not this list.*
8. **`Clear-Site-Data` retained for compliance** while SPA-side state clearing is the actual control,
   given per-directive origin scoping. *Consolidated into the ADR routing (ticket 34): REJ-010. Amend by ID, not this list.*

Glossary terms owed to ticket 17: **auth instant** (the absolute-lifetime anchor, distinct from session
creation time), **superseded session** (displaced by a newer login or a credential change, distinct from
expired), **factor freshness** (the `getIssuedAt()`-based window `validDuration` reads).

---

## Amendment from ticket 09 (lockout and dual rate limiting)

**`/csrf` is 30 requests per minute per source IP, not 10, so your live-row arithmetic changes.** 10/min
breaks an ordinary developer reloading the SPA, because every load with no usable token fetches one. At
30/min against the 15-minute idle window a single source holds roughly **450 live anonymous session rows**,
not ~150. Your underlying finding is unaffected and is the reason the number had to be decided rather than
defaulted: `spring.session.jdbc.cleanup-cron` deletes only rows whose `EXPIRY_TIME` has passed, so the
limiter and not the cron is the sizing control. Flagged onward to ticket 12 for the session-table sizing note.

**The safe-method oddity is recorded as unfixable, not deferred.** An unauthenticated `GET /csrf` with a
persistent side effect is at odds with HTTP safe-method semantics, and the obvious remedy — do not persist a
session for anonymous callers — is unavailable for exactly the reason you already established: §3.1:238
requires the token be session-bound, so a pre-login session is structural. Recorded as a known oddity so it
is not re-litigated as a sizing problem. *Consolidated into the register (ticket 33): R-CSRF-001. Amend the table by ID, not this list.*

**Confirmed, not changed:** lockout invalidates the target's sessions, as your four added invalidation
triggers specify. Ticket 09 adds the mechanism detail that the lock transition is detected inside a
pessimistic row lock, so the session kill fires exactly once per transition rather than once per failed
attempt past the threshold.

**New constraint on your absolute-lifetime filter's neighbourhood.** Ticket 09 places the per-IP rate-limit
filter **first inside the chain** (`addFilterBefore(..., DisableEncodeUrlFilter.class)`), which is earlier
than your filter and therefore earlier than `CsrfFilter`. No conflict with your ordering invariant — the
absolute filter still precedes `CsrfFilter` — but the invariant's wording should say "after
`SecurityContextHolderFilter` and before `CsrfFilter`" rather than "early", since there is now something
earlier. *Consolidated into the ADR routing (ticket 34): ADR-038 (attached amendment). Amend by ID, not this list.*

**Lock ordering.** Three tickets now take row locks (11 on admin user rows, 09 on a user row, 08's lockout
trigger reaching session rows). Pinned convention: **user rows before session rows, always.**

## Amendment from ticket 10 (credential flows)

- **`api.base-path` is `/api`, not `/api/v1`.** This ticket is the stale outlier: tickets 09, 10 and 11 all use
  `/api`, and ticket 11 explicitly dropped `/v1`. Every path recorded here reads one segment long.
- **CSRF on anonymous token-bearing endpoints was a gap this ticket left, now closed: no exemptions.**
  `POST /api/register`, `POST /api/register/activate`, `POST /api/password-reset/request` and
  `POST /api/password-reset/confirm` all require the header token, so each needs a `GET /api/csrf` round trip
  first and each mints an anonymous session row. Ticket 09's 30/min budget already sized for this.
- **The deliberate pre-login session is vindicated by name.** The OWASP CSRF Prevention Cheat Sheet names
  pre-sessions plus a token as the login-CSRF mitigation, and warns that a pre-session must be destroyed and
  replaced on authentication to avoid session fixation — which the rotate-on-login decision here already does.
- **The invalidation table gains two vacuous rows and keeps one name.** Activation redemption and invite
  redemption invalidate nothing, because the account has no sessions yet; recorded so the absence is not read as
  an oversight. The "admin reset token issuance" row name is correct — ticket 10 chose the token model. *Consolidated into the ADR routing (ticket 34): ADR-037 (attached amendment). Amend by ID, not this list.*
- **Redemption never mints a session.** The Forgot Password cheat sheet is explicit that the user should log in
  through the usual mechanism afterwards. Redemption sets the credential, terminates all sessions, and returns
  none. Terminating automatically rather than asking the user is the deliberate choice of the two the cheat sheet
  offers.
- The password-change session-id rotation recorded here applies to forced-change completion too, and still must
  not re-stamp `AUTH_INSTANT`.

---

## Amendment from ticket 23 (TOTP enrolment, step-up, and factor-reset flows)

**Your `AuthInstantStampingStrategy` invariant is confirmed as the framework default, not a hypothesis — plus a
fifth anonymous-session source and four settings the factor-grant filter needs explicitly.**

**1. The single-stamping-point invariant is load-bearing and now verified.**
`AbstractAuthenticationProcessingFilter.doFilter` calls `sessionStrategy.onAuthentication(...)` on **every**
successful attempt including a second factor, and `AbstractAuthenticationFilterConfigurer.configure` injects the
*shared* strategy — which for us is your login composite. So wiring the TOTP filters through a configurer resets
`AUTH_INSTANT` on every 10-minute re-verification and a busy admin never absolutely expires. That is exactly the
silent failure you predicted; it is the framework's default path, and avoiding it is the reason ticket 23 registers
the filters with `addFilterBefore` and sets the strategy by hand rather than using a configurer.

**2. Four settings on each factor-grant filter, two of which fail silently if omitted.** Beyond your minimal
strategy, from 7.1.x source:

- `setMfaEnabled(true)` — default `false`, normally set by `EnableMfaFiltersConfiguration`'s `BeanPostProcessor`,
  which ticket 23 does not get because `@EnableMultiFactorAuthentication` is deliberately unused. Without it the
  framework's authority merge is skipped, `FACTOR_PASSWORD` is dropped, and the verified admin 412s forever.
- `setSecurityContextRepository(...)` — the filter has its **own** repository field defaulting to
  `RequestAttributeSecurityContextRepository`, and its setter javadoc says "The default action is not to save the
  SecurityContext". Your §9 note about an explicit `saveContext` gestures at this but the filter's field is separate:
  without it the grant lives for exactly one request.
- non-redirecting success and failure handlers — the defaults are `SavedRequestAwareAuthenticationSuccessHandler`
  and `SimpleUrlAuthenticationFailureHandler`.

**3. Your `FactorGrantedAuthority` findings held, and one gained teeth.** `getIssuedAt()` *is* the `validDuration`
clock, so re-verification is a replacement. Source adds the reason that is now a correctness requirement rather than
a preference: `AllRequiredFactorsAuthorizationManager.requiredFactorError` uses `findFirst()` on the matching
authority and does **not** try later ones, so a stale `FACTOR_TOTP` beside a fresh one decides the outcome.
Appending is forbidden.

**4. A fifth anonymous-session source, uncounted in your ~450 figure.**
`ExceptionTranslationFilter.sendStartAuthentication` calls `requestCache.saveRequest(request, response)`, and
`HttpSessionRequestCache.saveRequest` creates a session. So **every anonymous request to `/api/admin/**` mints a
session row**, and nothing in our design ever replays a saved request. Fix: `NullRequestCache` on the chain — not
`setCreateSessionAllowed(false)` — which also makes `RequestCacheAwareFilter` inert; worth one line so its presence
is not later mistaken for a live dependency. Note `DelegatingMissingAuthorityAccessDeniedHandler` already defaults
to `NullRequestCache`, so only the anonymous path has this.

No re-check of the CSRF repository is needed: ticket 05 prohibited `CookieCsrfTokenRepository` and `csrf.spa()`, you
already recorded that session-bound `GET /api/csrf` mints a session by design and cannot be fixed under §3.1:238,
and you already sized ~450 rows per source from its 30/min budget. It remains the dominant contributor and it is
already counted.

**5. Your handover discharged.** `FACTOR_TOTP` is obtained at `POST /api/mfa/totp/verification` and at
`POST /api/mfa/totp/enrolment/confirmation`; the missing-factor redirect is converted to 412 `MISSING_FACTOR` via
`defaultDeniedHandlerForMissingAuthority("FACTOR_TOTP")`, branching on `WebAttributes.REQUIRED_FACTOR_ERRORS`. Your
contract that the SPA re-fetches CSRF on TOTP verify success is honoured, and extended to confirmation success,
since confirmation also grants the factor.

**6. Your `api.base-path` outlier is confirmed stale**: `/api`, not `/api/v1`.

---

## Amendment from ticket 13 (audit event catalogue)

[Build the audit event catalogue](13-audit-event-catalogue.md) resolves the handover this ticket left —
"§3.3 has no auditable event for session expiry or concurrent-session eviction, though §6 tells us to
monitor both" — and finds one ordering constraint in this ticket's composite that silently breaks the
correlation chain if missed.

**The correlation chain needs a row at authentication, and no new field.** This ticket rotates the
session id at login, at factor grant and at password change, so `session.hash` changes at each. Without
a row at authentication, nothing links the pre-auth failure rows to the successful login — `trace.id` is
per-request, so there is no join key. Resolution: rotation happens **inside** the login request, so a
`session-start` row carrying the **pre-rotation** hash and the login-success row carrying the
post-rotation hash **share one `trace.id`**, and the pre-rotation hash is the one the failure rows
already carry. Reason values `LOGIN | FACTOR_GRANT | PASSWORD_CHANGE`. *(Amended by
[ticket 27](27-inbound-trace-context.md): this join rests on `trace.id` being server-generated, which holds
because every inbound trace is restarted at the boundary. Under Boot's default continuation, a caller could pin it
across requests.)*

**Ordering inside the composite is load-bearing.** The composite is `ConcurrentSessionControl` →
`ChangeSessionId` → `RegisterSession` → `AuthInstantStamping`. The audit strategy must sit **after
`ConcurrentSessionControl`** (so displacement is already decided and observable) and **before
`ChangeSessionId`** (so the row still sees the pre-rotation id). Placed last, both rows carry the
post-rotation hash and the join evaporates — with no test failing and the code reading correctly, which
is the same failure shape as this ticket's `PRINCIPAL_NAME` index finding. *Consolidated into the ADR routing (ticket 34): ADR-038 (attached amendment). Amend by ID, not this list.*

**Eviction is emitted at displacement, and needs a named mechanism.** This ticket accepted **lazy**
eviction — `expireNow()` marks the row and the displaced user is logged out at their next request — so a
row emitted on *detection* may never be written at all. `SessionInformation.expireNow()` returns
nothing, so either decorate `ConcurrentSessionControlAuthenticationStrategy` or have the audit strategy
call `SessionRegistry.getAllSessions(principal, true)` and emit one row per session now marked expired.
The second is preferred: no framework subclass. This is the row that carries the explanation the
displaced user never receives on the wire. *Consolidated into the register (ticket 33): R-SES-001. Amend the table by ID, not this list.*

**Idle expiry cannot be produced at expiry, and the reason is structural.** Verified against the Spring
Session reference: the **JDBC module documents no session event publication**, and expiry is a **cron
clean-up job that bulk-DELETEs expired rows** — no per-row identity, no hook. The same reference
documents that the job can be disabled and replaced, so a custom reaper could select-then-delete and
emit one row per expired session — meaning idle expiry is **producible in principle and declined on
scope**, because scheduled jobs are out of scope on this map and a reaper imports the ShedLock questions
the hygiene-jobs deferral avoided. So idle expiry is observed **lazily at the next request**, keyed by
the hash of the presented cookie, and **cannot be distinguished from a deleted or forged session id**.

Net: §3.3's three session endings become **two producible rows plus one ambiguous one** —
`ABSOLUTE_TIMEOUT` (from `AbsoluteSessionLifetimeFilter`), `CONCURRENT_EVICTION` (at displacement), and
`UNKNOWN_OR_EXPIRED` (lazy, identity-less). Stated as the honest limit rather than three discriminated
rows. *Consolidated into the register (ticket 33): R-AUD-003. Amend the table by ID, not this list.*

**A CSRF row this ticket's contract needed and nobody had.** §3.3 requires logging "security control
bypass attempts, including validation, business logic, and anti-automation". This ticket specified
session-bound header-only CSRF, rotation at login and logout, and six envelope producers — but no audit
row for a CSRF rejection, so the control standing between a cross-origin attacker and every
state-changing endpoint was silent. Added: `access-control` / `["denied"]` / WARN / medium, reason
`CSRF_MISSING | CSRF_INVALID`, with `user.id` where the session is authenticated.

**One constraint inherited in the other direction.** This ticket's rule that the absolute-lifetime
filter must precede `CsrfFilter` also fixes the audit ordering: an expired session produces the
`ABSOLUTE_TIMEOUT` row and a 401, not a CSRF row and a misleading 403.

---

## Amendment from ticket 09 (§R, the ticket 21 reopening) — you now own a dispatch rule for all five triggers

**Your four added invalidation triggers plus ticket 09's new fifth share a property nobody had checked: the
session kill can never be atomic with the state change that triggers it.**

Spring Session **4.1.x**'s `JdbcHttpSessionConfiguration` builds its `TransactionTemplate` with
`PROPAGATION_REQUIRES_NEW`, and `JdbcIndexedSessionRepository` routes `deleteById` and
`findByIndexNameAndIndexValue` through it, so **every session write suspends the caller's transaction and commits
independently**. Verified on the pinned line, not only `main` — see
[§19 of the verification asset](../research/boot-4.1-actuator-observability-and-nist-throttling-verification.md).
So lockout, admin disable, role change and soft-delete all already have split-commit behaviour, and none of them
can be rolled back with the state change beside them.

**A row lock cannot be released early to work around it.** Locks release at commit or rollback; savepoints do not
drop them. So the only two options are *before commit, inside the lock* and *after commit, outside it*. An earlier
draft of ticket 09 §R specified "lock, write, release, then kill sessions", which has no implementation.

**Decision, and it is yours to own rather than ticket 09's: after commit, plus an idempotent startup
reconciliation sweep.** Inline gives the fail-safe ordering, but it demands a **second pooled connection while a
row lock is held**, so under ticket 09 §R.2's mass primitive the connection pool joins the lock graph and threads
block on the pool's timeout — which H2's pinned 1-second `LOCK_TIMEOUT` does **not** bound. That is thread-pool
exhaustion, the exact failure mode ticket 09 §11 used to decline sleep-based backoff, arriving through a door
nobody was watching; and ticket 09 §3's fail-open-on-counting would swallow it, so the visible symptom is lost
counter increments under load. After-commit's residual is a crash between commit and dispatch, which transactions
cannot fix and a sweep can.

**The rule, stated so it can be followed without knowing what `REQUIRES_NEW` is:**

> **User rows, then TOTP rows, inside the transaction. Session rows only after commit, never inside the lock.**

This replaces ticket 09 §3's ordering sentence (`user rows → TOTP rows → session rows`), which reads as an
ordering *within* one transaction, and it keeps tickets 13 and 10's after-commit dispatch rule instead of
inverting it for one trigger.

**The sweep.** For every user with `password_disabled_at` set, delete their sessions. Idempotent, indexed
(ticket 12 owes the index), and **your existing Spring Session JDBC cleanup job is the host** — no new scheduler,
which keeps the hygiene-jobs deferral intact. Generalise it to whichever of the five triggers leave durable state;
the transient ones (role change) have nothing to reconcile against.

**Why this lands here and not in ticket 09.** You own the trigger list, so you own the dispatch rule and the
sweep for all five at once. Ticket 09 deciding it for the fifth would leave four inheriting by accident, and the
unsafe half — a disabled authenticator coexisting with the live session it minted — is exactly what ASVS
**7.4.2 (L1)** exists to prevent. Note 7.4.2 rather than 7.4.3: 7.4.3 (L2) requires *offering the user the option*
to terminate *other* sessions after a factor change, which is not an automated kill of the subject's own sessions.

**One test you own that ticket 09 cannot write:** the sweep is idempotent, and it reconciles a state written
without its session kill. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-SES-022. Amend the table by ID, not this list.* *Consolidated into the ADR routing (ticket 34): ADR-037 (attached amendment). Amend by ID, not this list.*

---

## Amendment from ticket 25 — the `Clear-Site-Data` handover item, taken from the body rather than the bullet

**This ticket's handover bullet is lossier than the analysis it summarises, and the bullet is what ticket 25 would
have inherited.** §13's line reads "**25 (handover):** `Clear-Site-Data` reaches the API origin's storage only."
§12's body is correct and the bullet is not: cookies reach the **registered domain**, so the item must be written
from the body.

Verified against the W3C Clear Site Data specification, directive by directive, for a logout response from the API
origin under ticket 20's topology:

- **`cookies` — takes effect on the SPA.** The algorithm resolves the registered domain of the response origin and
  clears cookies across it, with the spec's own rationale that "we remove all the cookies for an entire registered
  domain, as cookies ignore the same-origin policy". HTTP authentication entries and bound tokens clear on the same
  basis. This is the only directive with cross-subdomain reach.
- **`storage` — does not.** Origin-scoped: localStorage, sessionStorage, IndexedDB and service worker
  registrations belong to the API origin alone.
- **`cache` — does not.** Host-keyed for the network cache and origin-keyed beyond it, so it never reaches a
  different host either way. *Consolidated into the register (ticket 33): R-HDR-008. Amend the table by ID, not this list.*

So the handover item is: **a logout response from the API origin clears the SPA's cookies but never its storage** —
which is the opposite emphasis from the bullet, and it matters operationally because an operator diagnosing a stale
session will look in the wrong place if told cookies do not reach the SPA. *Consolidated into the register (ticket 33): R-HDR-008. Amend the table by ID, not this list.*

Two conditions worth carrying into ticket 16's test rather than discovering later. The header is only acted on
**when the fetch's credentials flag is set**, and it is **ignored on service-worker-served responses** ("It is
imperative that the Clear-Site-Data header is only respected on responses fetched over network"). And on this
ticket's claim that the header is exercisable on `http://localhost`: true in practice, but the spec route is
conditional — Clear Site Data requires an "a priori authenticated URL", Mixed Content equates that with
potentially-trustworthy, and Secure Contexts makes the **name** `localhost` conditional on the UA following the
localhost resolution rules (a MAY) while `127.0.0.1` is unconditional. Chrome and Firefox both qualify, so the test
stands — but if it must be UA-independent, the loopback literal is the safer target. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-HDR-005. Amend the table by ID, not this list.* *Consolidated into the ADR routing (ticket 34): REJ-010 (attached amendment). Amend by ID, not this list.*

---

## Amendment from ticket 29 (anonymous session-row growth)

**`08:118-121` is deviated from, and your reasoning is answered rather than overridden.** Anonymous sessions keep
the same 15 minutes, but measured from `CREATION_TIME` instead of the last access. It is a second anchor, not a
second duration. Your recovery argument is untouched: the token expires, the user gets 403, and ticket 06's single
retry recovers it. What it did not cover is **pings keeping the row alive**: any request carrying a live cookie *Consolidated into the register (ticket 33): R-SES-009. Amend the table by ID, not this list.*
extends an anonymous row by a full window, so the ~150 and ~450 figures were never ceilings (ticket 29 §2;
[asset](../research/anonymous-session-growth-and-h2-file-verification.md) §F.3).

Four things change here:

- **§12's `AbsoluteSessionLifetimeFilter` gains its second branch.** For sessions with no `AUTH_INSTANT`, the same
  test you use to skip them at `08:420`, it pins the interval to `CREATION_TIME + W − now`. When the remaining time
  is ≤ 0 it calls **`invalidate()`, never the setter**. A negative interval that gets saved is immortal and can't
  be deleted (§F.4). The ordering invariant is unchanged.
- **The login composite resets `maxInactiveInterval` to `W`.** This sits beside `AuthInstantStampingStrategy`,
  not inside it. It is idempotent, so running it twice is harmless. *Consolidated into the ADR routing (ticket 34): ADR-038 (attached amendment). Amend by ID, not this list.*
- **§7's "create it deliberately" is now load-bearing.** Ticket 29's CSRF repository wrapper skips non-null saves
  when no session exists. `/api/csrf` keeps creating a session only because the controller calls
  `getSession(true)` before resolving the token.
- **Your ~150 / ~450 figures become 480 unexpired / 510 present per source.** That is a correction, not a
  reopening. The aggregate is now bounded by ticket 29's `N_max`. *Consolidated into the register (ticket 33): R-CSRF-001. Amend the table by ID, not this list.*

---

## Amendment from ticket 16 (test plan)

- **The `Clear-Site-Data` browser assertion (08:372–380) now has an owner and a level.** It is ticket 16, level E (Playwright, Chromium and Firefox, against `vite preview` plus the backend). The test asserts that `cookies` reaches the domain and that `cache`/`storage` do not reach the SPA origin.
- **Replay tests are enumerated from the trigger table (08:205–215), not from a count.** That gives:
  - nine rows;
  - ticket 09 §R's cap disable (08:649–651's "fifth");
  - the two rows that invalidate nothing, activation redemption and invite redemption (08:522).

  Each captures the raw cookie, fires the trigger, replays the cookie against the real JDBC store, and asserts 401 plus the row being gone. Self-service change and forced-change completion assert "all others". The two rows that invalidate nothing get **negative** tests (an existing session survives). Each of those carries its reason inline, citing 08:522, so a future tidy-up reads the failure as an overturned decision.
- **Ticket 16 owns the reconciliation-sweep test (09 §R test 7) outright.** "Whichever of you writes ticket 08's suite" is retired.
- **Idle expiry is tested by aging `SPRING_SESSION` rows.** Spring Session stamps `lastAccessedTime` with its own `Instant.now()`. The cleanup cron is set to `-` in shared test contexts so it cannot delete an aged row before the request arrives. One dedicated test runs with the cron enabled. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-SES-012, T-SES-013, T-SES-014, T-SES-015, T-SES-003, T-SES-004, T-SES-016, T-SES-017, T-SES-018, T-SES-021, T-SES-019, T-SES-020, T-SES-001, T-SES-023, T-HDR-005, T-SES-022. Amend the table by ID, not this list.*
