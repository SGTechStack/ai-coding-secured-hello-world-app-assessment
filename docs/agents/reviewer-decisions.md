# Reviewer Decisions

Human decisions on reviewer `Human Decision Needed` findings. Apply a matching entry instead of asking again.

## Request body size limit for chunked / length-less bodies

- **Area:** Backend request-body limit filter (`RequestBodyLimitFilter`), issue 01 walking-skeleton.
- **KB finding:** None (KB unavailable). Reviewer finding: bodies without `Content-Length` bypassed the up-front size rejection; oversized chunked bodies reached auth/controllers, and `getReader()` was not wrapped.
- **Chosen action:** When `Content-Length` is absent, buffer up to limit+1 bytes in the filter and reject with 400 `request_too_large` before CSRF, auth, or controllers. Enforce the limit on both `getInputStream()` and `getReader()`. Cover the chunked path with a test. (Rejected: refusing all length-less bodies; accepting read-time-only enforcement.)
- **When to reuse:** Any request-size / body-limit enforcement question — enforce pre-controller regardless of transfer encoding, with `request_too_large` as the error code.

## Non-blocking reviewer notes to fold into correction passes

- **Area:** Issue 01 minor review notes.
- **KB finding:** None (KB unavailable).
- **Chosen action:** Fix issues that prevent near-term bugs (catch-all `@ExceptionHandler(Exception.class)` must re-throw Spring Security `AccessDeniedException`/`AuthenticationException`; `VITE_API_ORIGIN` must be a bare origin with no path/query/fragment). Skip cosmetic/low-risk ones (plain-text CORS 403 body; `aria-live` on placeholder loading text).
- **When to reuse:** Fold cheap, future-bug-preventing minor notes into the correction pass; leave cosmetic notes on placeholder UI or browser-unreadable responses.

## Code-reviewer gate: controls owned by a later issue

- **Area:** Code-reviewer validation gate (IM8 and similar checks), issue 01.
- **KB finding:** None (KB unavailable). IM8 flagged lm-15, lm-16, as-4, as-5, as-6, as-11, ac-6 and pm-6 as Critical/High, but each is explicitly owned by a later issue file (02, 14, 06/07, 03, 03, 05, 10/13, 16).
- **Chosen action:** Treat them as tracked deferrals, not blockers, for the current issue's gate. They count as blockers again once the gate runs for the owning issue.
- **When to reuse:** Any gate finding for a control that a later issue file assigns to itself (cite the issue file and line). A finding with no owning issue is still a blocker.

## Code-reviewer gate: ADR-accepted deviations awaiting risk-owner sign-off

- **Area:** IM8 ac-2 (MFA), ac-3 (inactive accounts), ac-4 (access review).
- **KB finding:** None (KB unavailable). Accepted deviations in `docs/adr/0001-app-standards-override-prd.md`; IM8 still wants risk-owner sign-off.
- **Chosen action:** The ADR is enough for the code gate; not a blocker. Risk-owner sign-off is a governance item tracked outside the code commits.
- **When to reuse:** Any gate finding for a control that an accepted ADR records as a deviation.

## OpenAPI spec gap (IM8 pm-6)

- **Area:** System documentation.
- **KB finding:** None (KB unavailable). No issue planned an OpenAPI spec; issue 16 covers only the README.
- **Chosen action:** Tracked as a new issue, `.scratch/secured-hello-world/issues/17-openapi-spec.md` (needs-triage). Not a blocker for earlier issues.
- **When to reuse:** Gate findings about missing API documentation before issue 17 is done.

## Chunked-body wrapper replays bytes only (no form decoding)

- **Area:** `RequestBodyLimitFilter`, issue 01 (thermo-nuclear High, raised twice).
- **KB finding:** None (KB unavailable). Decoding form fields in the filter duplicates servlet parsing that no endpoint needs.
- **Chosen action:** The buffered chunked-body wrapper only replays the body bytes (`getInputStream()` / `getReader()`). A chunked `application/x-www-form-urlencoded` post therefore loses its body form fields and fails CSRF with 403 (fail closed). The SPA sends CSRF as a header. All API endpoints, including issue 04's login, must accept JSON bodies, not form posts. Supersedes the earlier "malformed chunked form body → 400 validation" behaviour; the size limit (400 `request_too_large`) still applies.
- **When to reuse:** Any request-body wrapper or endpoint body-format question: keep wrappers byte-replay only and use JSON request bodies.

## Frontend error and performance reporting (IM8 lm-16, SPA side)

- **Area:** SPA observability.
- **KB finding:** None (KB unavailable). The SPA reports no client-side errors or performance data; no issue owned it.
- **Chosen action:** Added to issue 14's scope (`.scratch/secured-hello-world/issues/14-operations-metrics-and-dependency-scan.md`). Tracked deferral for earlier issues.
- **When to reuse:** Gate findings about SPA-side telemetry before issue 14 is done.

## Password length/byte caps return `password_policy`

- **Area:** Credential policy (`CredentialPolicy`), issue 03 registration; reused by Password Change, reset and Bootstrap Admin.
- **KB finding:** None (KB unavailable). Issue 03 said the 64-character / 72-byte caps return 400 `validation`; `spec.md` (story 2, Testing Decisions, Further Notes) and ADR 0001 say `password_policy`.
- **Chosen action:** Keep 400 `password_policy` with violations `max_length` / `max_bytes`, enforced only in the Credential policy. Issue text corrected to match the spec.
- **When to reuse:** Any password-rule error-code question: password rules (including length and byte caps) belong to the Credential policy and return `password_policy`; `validation` is for non-password field format errors.

## Non-H2 migrations: fix obvious dialect issues now, defer real-database runs

- **Area:** Flyway migrations for Postgres/MySQL, issue 03 (V2 accounts).
- **KB finding:** None (KB unavailable). Only H2 is tested; MySQL `TIMESTAMP` overflows in 2038.
- **Chosen action:** Use `DATETIME(6)` for MySQL time columns. Don't add Testcontainers/Docker in feature issues; running migrations against real Postgres/MySQL is deferred to the issue that owns deployment (or noted in the current issue if none does).
- **When to reuse:** Any later migration: fix clear dialect bugs in place; don't block a feature issue on real-database migration runs.

## Session-end audit for Sessions that expire unused

- **Area:** `SessionControl` session-end auditing, issue 05 session lifetime.
- **KB finding:** None (KB unavailable). Spring Session's JDBC cleanup silently deletes an abandoned expired Session, so no `session-end` event is written. When a later login or `endAll` removes an already-expired Session, it is recorded as `new_login` (or the caller's reason).
- **Chosen action:** When `endOldestBeyondLimit` / `endAll` remove a Session that has already passed its idle or absolute limit, record the real reason (`idle_timeout` / `absolute_timeout`). Accept that abandoned Sessions nothing touches again get no event, and note this gap in the issue. No scheduled sweep (option B rejected; could be a separate follow-up issue).
- **When to reuse:** Any audit-completeness question about Sessions or other records that a store expires in the background: record the true reason wherever the app touches them, and document the untouched-expiry gap instead of adding a background job.

## Wrong current password counts toward lockout

- **Area:** Password Change (`PATCH /api/me/password`), issue 08.
- **KB finding:** None (KB unavailable). A wrong current password was only audited, with no lockout or rate limit, so a hijacked Session could guess the password without limit.
- **Chosen action:** Count a wrong current password as a failed login attempt: same threshold, lock, and Account-locked email as login. No dedicated endpoint rate limit (options B/D rejected). Also fixed the minor notes: redirect to login even if the CSRF refresh fails after success, correct the misleading form-clearing comment, send the "Password changed" email after commit. Left the hard-coded "last 3" SPA text.
- **When to reuse:** Any endpoint that re-verifies a password inside an authenticated Session (password change, re-auth, step-up): a wrong password feeds the same lockout counter as login.

## Public SPA routes are Visitor-only, and admin-screen affordances stay out of scope

- **Area:** SPA route guards (`frontend/src/App.tsx`) and the admin Account list, issue 13 required password change.
- **KB finding:** None (KB unavailable). Reviewer finding: `/register`, `/forgot-password` and `/reset-password` were unguarded, so a holder with `password_change_required` set could open three screens beyond Password Change and logout, against criterion 6. Every request they made there was refused 403 `password_change_required`, so the effect was a dead end, not a security hole.
- **Chosen action:** Wrap `/register`, `/forgot-password` and `/reset-password` in the existing `VisitorOnly`, matching `/login`. Those endpoints are outside what any authenticated Session can usefully call, so the same guard fixes the flagged case and the pre-existing looseness at once; a logged-out holder still reaches the reset flow, which is how a reset clears the flag. Rejected: a second flag-specific guard (duplicates what `VisitorOnly` already says), and deferring to a general route-guard issue. Also rejected, as outside this issue: adding `passwordChangeRequired` to `AdminAccountView` to hide or relabel the per-row "Require password change" button, and a confirmation dialog on the acting Admin's own row — an Admin requiring the change of themselves and being bounced to login is the intended consequence of having no self-action guard (`spec.md:198`), and the first confirmation dialog should be designed once for all destructive admin actions. Kept the filter as a bean with a disabled `FilterRegistrationBean` rather than extracting a public `RequiredPasswordChangeCheck` port: the workaround is two explained lines, the port is more surface.
- **When to reuse:** Any screen whose API endpoints an authenticated Session cannot usefully call belongs in `VisitorOnly`, not a bespoke guard. Admin-screen affordances (state badges, confirmations) are their own issue unless a criterion names them; don't widen an admin API contract for a cosmetic gain.

## CSRF exemption where CSRF itself would create the Session

- **Area:** `POST /api/client-events` and ADR 0002's blanket CSRF rule, issue 14 operations and metrics.
- **KB finding:** None (KB unavailable). Reviewer finding: the endpoint is `permitAll` but CSRF stayed on, so `startClientReporting()` at module scope made every page load call `ensureCsrfToken()` → `GET /api/csrf`, and `HttpSessionCsrfTokenRepository.saveToken` calls `request.getSession()`. Every Visitor and crawler got a persisted Spring Session JDBC row and a `SESSION` cookie before any interaction, with no cap on anonymous Sessions (issue 05's cap is per Account) and no rate limit on `GET /api/csrf`.
- **Chosen action:** `csrf.ignoringRequestMatchers` for `POST /api/client-events`, recorded as a third paragraph amending `docs/adr/0002-session-bound-csrf-bootstrap.md` rather than a new ADR — the exemption carves out the exact rule 0002 states, so it belongs where a reader of that rule sees it. The SPA side must also bypass `apiRequest`'s CSRF bootstrap, or the Session is still created and the exemption buys nothing; a test asserts a client-event POST creates no Session and sets no `SESSION` cookie. (Rejected: keeping CSRF and documenting Session-per-page-load; reporting only when a token is already cached, which loses the never-logs-in Visitor and weakens criterion 4; deferring to issue 16.)
- **When to reuse:** Exempt an endpoint from CSRF only when CSRF is itself the cause of a resource-exhaustion or pre-interaction-cookie problem, and the endpoint is public, needs no Session, changes no user state, and is already rate-limited with bounded metric cardinality. ADR 0002's logout reasoning does not transfer to such an endpoint: logout acts *on* a Session. Any exemption is amended into ADR 0002 and guarded by a no-Session test, never left to a code comment.

## Dependency scanning covers every shipped dependency tree

- **Area:** Dependency scanning, issue 14 criterion 3 (`mvn -P security verify`).
- **KB finding:** None (KB unavailable). Reviewer finding: the `security` Maven profile scans only the Maven tree; nothing scanned `frontend/`, whose npm dependencies ship to browsers, so a critical CVE there was as shippable as a backend one. The criterion as written named only the Maven command.
- **Chosen action:** Add `npm audit --audit-level=critical` as a `security` script in `frontend/package.json`, mirroring the Maven profile by name, and amend criterion 3 to name both commands so the widening is visible rather than silent. It was free to add: the audit reported 0 vulnerabilities at every severity. No root orchestration (Makefile/justfile/root `package.json`) was added to run both under one command — that is issue 16's. (Rejected: a follow-up issue only; accepting backend-only as an ADR 0001 deviation.)
- **When to reuse:** A "dependency scan" criterion covers every dependency tree the product ships, not just the one the named command reaches. Add the missing scan when it is cheap and currently clean, and amend the criterion text rather than silently exceeding it. Keep per-stack commands separate until an issue owns build orchestration.

## Security scans that need an external credential are verified by configuration

- **Area:** OWASP Dependency-Check in the `security` Maven profile, issue 14 criterion 3.
- **KB finding:** None (KB unavailable). `mvn -P security verify` aborts at NVD download without a key (`NvdApiException: Invalid API Key, length of 0`) — correct fail-closed behaviour, but it means the scan cannot be proven by execution in an environment without one. `NVD_API_KEY` is set nowhere in this environment or any shell profile, and obtaining a key is a human-only step.
- **Chosen action:** Accept configuration-only verification for the commit (`help:active-profiles` and `help:effective-pom` prove the profile activates and binds `dependency-check:check` to `verify` with `failBuildOnCVSS=9`), and record in the issue's verification note as an explicit open item that a human runs `NVD_API_KEY=… ./mvnw -P security verify` before handoff, as the spec requires. The key is read via `nvdApiKeyEnvironmentVariable` so it never becomes a Maven property that debug logging could print (GHSA-qqhq-8r2c-c3f5). A commented `suppressionFile` was added so the first false positive has a documented escape hatch instead of blocking the build.
- **When to reuse:** Any gate that needs a credential the environment cannot supply: verify the configuration, do not weaken the fail-closed behaviour to make it pass, and record the human run as a named open item against the handoff rather than as done. Read such credentials from an environment variable, never a build property.

## A permitAll path may join the required-password-change allowlist

- **Area:** `RequiredPasswordChangeFilter` allowlist, issue 14 (`POST /client-events`).
- **KB finding:** None (KB unavailable). The implementer left the path out, citing issue 13's decision that the allowlist is settled. The reviewer showed that reason does not hold: the path is already `permitAll` in `SecurityConfig`, so adding it to the filter's `allowed` matcher widens no authorization. The audit-noise argument the decision was originally made on (a flagged holder writing two WARN `password-change-enforcement` events per page load) was eliminated in the same correction pass by the CSRF-exemption decision above, which made the SPA send no cookie at all — see the correction below.
- **Chosen action:** Add `POST /client-events` to the `allowed` matcher, with a test that posts a session cookie explicitly (verified non-vacuous: removing the entry fails it with 403 plus an enforcement event). **Corrected after the fact:** the CSRF exemption made `telemetry.ts` send `credentials: 'omit'`, so the SPA's reports are anonymous and `doFilterInternal`'s first branch short-circuits before the allowlist is ever consulted. The entry is therefore unreachable from the SPA and is kept purely as defence in depth for a cookie-bearing caller and as a guard against a future change that re-adds credentials — not, as originally argued, to stop per-page-load audit noise.
- **When to reuse:** Adding an already-`permitAll` path to the required-password-change allowlist is not widening authorization, so issue 13's "the allowlist is settled" decision does not block it. That decision governs paths an authenticated Session could otherwise not reach. Weigh the audit-signal dilution of leaving a path out, but check first whether the caller actually sends a session cookie: an anonymous request never reaches the allowlist branch, so the allowlist matters only for a cookie-bearing caller. Two decisions taken in one pass can invalidate each other's premise — re-read the earlier entries after the pass lands.

## API contracts whose payload constraint is the security control get a CONTEXT.md term

- **Area:** `CONTEXT.md` glossary, issue 14 (`POST /api/client-events`).
- **KB finding:** None (KB unavailable). The criterion-4 mechanism was recorded only in javadoc/TSDoc. `docs/agents/domain.md` treats a coined concept missing from the glossary as a signal.
- **Chosen action:** One `Client Event` term under a new "Operations" heading in `CONTEXT.md`, whose definition carries the constraint: a Client Event cannot carry user data, a message, a stack trace or an identifier — only a fixed `kind`, a bounded `path`, and an optional bounded `durationMs`. No management-port term (configuration, not vocabulary). No new ADR for the contract itself; the only ADR change was the CSRF amendment above.
- **When to reuse:** When a new contract's security property *is* a payload restriction, put the restriction in the `CONTEXT.md` term, not only in code comments — the term is what stops a later change "improving" the payload with a message or identifier field. `IP Throttle` is the precedent that a named mechanism earns a glossary entry. Reserve ADRs for decisions that overturn or carve out an existing recorded rule.

## Unrated audit writes before the rate limiter: fix all instances in one issue

- **Area:** `ClientEventController`, `/api/register`, `/api/password-reset/*`, issue 14.
- **KB finding:** None (KB unavailable). Reviewer finding: `@Valid` binding and `GlobalExceptionHandler.auditInputFailure` run before the handler body calls `rateLimiters.<limiter>().acquire()`, so a malformed body writes an `ACCESS_CONTROL` `validation` audit event with no rate limit. `IpThrottle` is login-only. Issue 14's CSRF exemption removed the token bootstrap that previously made this cost something on `/api/client-events`, so a bare malformed POST from any host became free — and that endpoint is the only one of the three a browser calls unprompted on every page load.
- **Chosen action:** Tracked as `.scratch/secured-hello-world/issues/18-rate-limit-before-validation.md` (needs-triage) covering all three endpoints under one shared mechanism, and recorded as a known gap in issue 14's verification comment. No code change in issue 14. (Rejected: fixing only `/client-events`, which would leave the same primitive open on its two siblings and make one endpoint structurally inconsistent with them; and noting it without a tracked owner, which under-reacts given the CSRF exemption was itself accepted on a resource-exhaustion argument.)
- **When to reuse:** When a decision in the current issue makes a pre-existing weakness materially cheaper to exploit, that is a reason to give it a tracked owner, not a reason to fix the one instance the current issue happens to touch. Fix an ordering flaw shared by sibling endpoints once, uniformly, in an issue that owns all of them. The same resource-exhaustion argument that justifies a control must be applied when it points the other way.

## A Session must not outlive its Account: check existence per request, not only at the deleting action

- **Area:** `RequiredPasswordChangeFilter` (account package, `addFilterAfter(AuthorizationFilter.class)`), `AccountAdministrationController.delete`, issue 15 (admin delete with tombstone).
- **KB finding:** None (KB unavailable). Reviewer finding: criterion 2's Session ending is one post-commit sweep over `FindByIndexNameSessionRepository.findByPrincipalName`, and Spring Session JDBC indexes a Session under its principal only when the row is written at response commit (the reasoning `SessionControl.endOldestBeyondLimit` already documents). A target mid-login when the delete runs is not in that result set and keeps a live Session carrying the authorities it was issued, including `ROLE_ADMIN`, which satisfies both the `/api/admin/**` URL rule and `@PreAuthorize("hasRole('ADMIN')")`. The same index timing already affects `ACCOUNT_DISABLED` and `ROLE_CHANGED`; delete is different only in that the Session is then *unrevocable* — the id no longer resolves, so a repeat `DELETE` returns 404.
- **Chosen action:** Follow the reviewer recommendation (option A). Hoist an Account-existence check into `RequiredPasswordChangeFilter` **above** its `allowed` matcher — the matcher is evaluated before the `findById`, so anything added below it would skip `/me`, `/me/password`, `/logout`, `/csrf` and `/client-events`. On a missing Account the filter *ends* the Session (invalidate, clear cookie, audit `session-end` with the existing `AccountAdministrationController.ACCOUNT_DELETED` reason) and refuses with the 401 the entry point already writes, rather than merely refusing and leaving the row alive until its absolute timeout. One reason constant, because the cause is the same event. The filter's javadoc line "A Session whose Account no longer exists is left to the endpoint" is now false and was corrected; the existing `orElseThrow(new InsufficientAuthenticationException("Account no longer exists"))` in `OwnAccountController` and `PasswordChangeService` stay, since those sites need the Account object anyway. (Rejected: documenting the window in the verification note, per the issue-05 expiry-gap precedent — that precedent is about *audit completeness*, a missing event, not about live authority no operator can revoke; a `hello`-only existence re-check, which closes the leaked greeting and leaves admin authority intact; a new sibling filter duplicating the same per-request `findById`; and moving the check into `SessionControl.enforceLifetime` before authorization, which is the better chain position but inverts the account → security package direction and needs a port this repo has already rejected once as "more surface".)
- **When to reuse:** Treat "the Account still exists" as the degenerate case of the rule `RequiredPasswordChangeFilter` already enforces — read Account state fresh per request so no Session outlives it — and revoke rather than re-refuse. When a filter has a bypass matcher, decide deliberately whether a new check belongs above or below it; a security check that must hold for every request goes above. Distinguish an *accepted gap* (an event that never gets written) from *live authority that no operator can revoke*: the second earns machinery even when the acceptance criteria do not ask for it, and the issue-05 precedent does not license accepting it.

## A hand-written API document is acceptable when a drift test pins it to the handler mapping

- **Area:** `docs/api/openapi.yaml` and `OpenApiDocumentTest`, issue 17 (IM8 pm-6 system documentation). Triage decision on the issue's own criterion 4, not a reviewer finding.
- **KB finding:** None (KB skipped by request). The issue left generated-vs-hand-written and served-vs-not open for triage.
- **Chosen action:** Hand-written, committed, **not served at runtime**. A test compares the document's `method + path` set against Spring's `RequestMappingHandlerMapping` and fails in *both* directions, which is the guarantee generation would otherwise buy; it needs no new dependency, because snakeyaml is already on the compile classpath. Rejected `springdoc-openapi`: it adds a dependency to the shipped tree that issue 14's `security` profile must then scan, plus endpoints that `anyRequest().denyAll()` must carve out, and it would still need hand-written `@ApiResponse` content for the `code` values that are the substance of the criterion. Rejected serving it publicly (it publishes a map of `/api/admin/**` to unauthenticated callers and adds a `permitAll` hole to a deny-by-default chain) and on the management port (couples the issue to issue 16's port work for no gain over a checkout).
- **When to reuse:** Any "document the API/config/schema" criterion: prefer a hand-written artifact plus a test that derives the truth from the running application, over a generator that ships code to production so a document can be produced. Derive the expected set from the application at test time, never from a second hand-maintained list in the test. Documentation existing satisfies a documentation control; publishing it is a separate decision that needs its own justification.

## Rate limiters run before body binding, via one interceptor

- **Area:** `RateLimitBeforeBindingInterceptor`, `@RateLimitedByClientAddress`, issue 18 (`/api/client-events`, `/api/register`, `/api/password-reset/*`). Triage decision on the issue's own criteria 2 and 5, not a reviewer finding.
- **KB finding:** None (KB skipped by request).
- **Chosen action:** A `HandlerInterceptor` acquires the per-client-address limiter named by an annotation on the handler method, before argument resolution, so malformed bodies (field-invalid or unparseable) spend quota and `validation` audit events are bounded by the limiter. Refusals go through the existing `GlobalExceptionHandler` (same 429, `Retry-After`, `rate_limited` event). Limiters keyed by a body field (per-email reset request) stay in the handler after validation. The `validation` audit event is kept, since it is now rated. (Rejected: a servlet filter, which needs a second path list and its own 429/audit writing; manual validation after `acquire()`, three bespoke copies that still miss unparseable bodies; demoting `validation` to a metric.)
- **When to reuse:** Any new public endpoint with a per-address limiter uses `@RateLimitedByClientAddress` rather than calling `acquire()` in the handler body. A limiter acquired inside a handler that takes `@Valid @RequestBody` does not see malformed requests.
