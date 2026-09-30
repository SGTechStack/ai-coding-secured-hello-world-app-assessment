# Independent review: tickets 08, 09, 10 (`2184851..7c075e9`)

Branch `zacharylim`. 19 commits: 08 (audit emitter, ECS stream, trace restart), 09 (JDBC sessions, header-only CSRF,
SPA bootstrap), 10 (JSON sign-in, hello, profile, sign-out, SPA pages, e2e), plus the README dev-values commit and a
ticket-09 status edit. Read-only review: nothing was built or run. Paths are relative to the repo root. Line numbers
are at `7c075e9`. "HEAD" means `18a7654`.

Method: the `code-review` skill (Standards axis + Spec axis). The repo documents no coding standards (no
CODING_STANDARDS, CONTRIBUTING or AGENTS/CLAUDE file), so the Standards axis is the skill's smell baseline only. Both
axes were done inline rather than in sub-agents. The review is weighted towards the security controls named in the
brief.

## Summary

| Severity | Count |
|---|---|
| Critical | 0 |
| High | 0 |
| Medium | 4 |
| Low | 7 |

I found no session-fixation, CSRF-bypass, enumeration or secret-logging defect in the production code. The login
composite rotates the id (`ChangeSessionIdAuthenticationStrategy`) and the CSRF token (`CsrfAuthenticationStrategy`).
CSRF resolves from the header only, and saving the token never creates a session. Every password-axis failure goes
through one writer with one `matches()`. Audit rows carry only keyed hashes, and `emit` has no throwable path. The
Medium findings are an ordering bug in the filter chain, an edge case in concurrent-session handling, a
canary-scan test that never reaches the code it claims to guard, and committed key material that nothing refuses
outside `dev`.

---

## Spec

### M1 (Medium): The absolute-lifetime 401 is written before CORS and the security headers
`backend/src/main/java/sg/securedhello/security/SecurityConfig.java:73-74`,
`backend/src/main/java/sg/securedhello/session/AbsoluteLifetimeFilter.java:64-68`.

The filter is placed with `addFilterAfter(..., SecurityContextHolderFilter.class)`, which gives it order 601. In
Spring Security's `FilterOrderRegistration`, `HeaderWriterFilter` is 800 and `CorsFilter` is 900. When the absolute
lifetime has run out, the filter writes 401 and returns without calling the chain, so neither of those filters runs.
The 401 therefore has:
- **No `Access-Control-Allow-Origin` or `-Credentials`.** The SPA is on another origin (ADR-059), so the browser hides
  the response and `fetch` rejects with a `TypeError`. The client sees "no status", not `AUTHENTICATION_FAILED`, so the
  *authority* rule ("the envelope `code` overrides") never fires. A GET is saved by `shouldRetry`: the retry lands on
  the now-deleted session and gets a proper 401. A mutation is not retried, so the user gets a generic failure
  instead of being sent to sign-in.
- **None of the default security headers** (`Cache-Control: no-store`, `X-Content-Type-Options`, HSTS and so on) on
  this error response.

ADR-038 only requires "after `SecurityContextHolderFilter`, before `CsrfFilter`", and placing the filter after
`CorsFilter` meets both. At HEAD the same order is now *documented* in the SecurityConfig javadoc ("AbsoluteLifetimeFilter,
then CorsFilter"). Ticket 11's `SourceRateLimitFilter` got a `CorsPolicy.allowOrigin` callback for exactly this
problem, but this filter did not. No test sends an `Origin` header to an absolutely expired session
(`CorsTest` covers preflight and refused origins only). **Still applies at HEAD** (`SecurityConfig.java:90`).
Fix: `addFilterAfter(..., CorsFilter.class)` (still before `RequestBodyCapFilter`/`CsrfFilter`). Add a test for the
`Origin`, ACAO and `Cache-Control` headers on the expiry 401.

### M2 (Medium): Re-signing-in from a displaced session keeps its "expired" flag, which ends both sessions
`backend/src/main/java/sg/securedhello/security/login/SignIn.java:121-146`.

`ConcurrentSessionControlAuthenticationStrategy` expires the older session through `SpringSessionBackedSessionRegistry`,
which stores the attribute `SpringSessionBackedSessionInformation.EXPIRED` in that session. `ConcurrentSessionFilter`
sits *after* the login filter in the chain. So a `POST /api/login` made on a displaced session (it still holds a valid
CSRF token, for example after the SPA's back-navigation to `/sign-in` with the token cached) goes through the whole
composite:
1. The registry's `getAllSessions(principal, false)` skips the current, flagged session, so the *other* live session
   (the one that displaced it) is expired.
2. `changeSessionId()` copies every attribute, including the `EXPIRED` flag, to the new id.

The login answers 200. The very next request on the new session is refused by `ConcurrentSessionFilter` with 401. The
user ends up with no live session on either device. In the SPA, `signIn()` then fails on its `refreshCsrfToken()` call
and shows "Sign-in failed".

This is reasoned from the framework source, not run. It needs a test: log in on A, log in on B, then POST
`/api/login` on A with A's token, and check that A's next GET is 200 and what happens to B. A fix could be a first
composite step that clears the flag, or rejecting a login on a session the registry reports as expired. **Still
applies at HEAD** (composite unchanged).

### M3 (Medium): The T-AUD-013 canary test's login body never reaches the login code
`backend/src/test/java/sg/securedhello/audit/CanarySecretScanTest.java` (`@Proves("T-AUD-013")`).

The test posts `{"username":"canary-admin","password":"<ADMIN_PASSWORD>"}` to `/api/login` with **no session and no
CSRF token**, and accepts any 4xx. `CsrfFilter` refuses it (403) before `JsonCredentialsConverter`, the provider, the
`AbstractAuthenticationFailureEvent` or `LoginFailureAudit` ever run. So the one path that handles a submitted
password (parse, authenticate, failure event, WARN audit row) is never exercised with a canary. The suite-wide
`LogOutputGuard` doesn't close the gap either. Its canaries are `TestSecrets.CANARIES` plus per-test registered
values, and the login fixtures' passwords (`Accounts.PASSWORD`, the wrong-password literals) are never registered.
Today the test would stay green if, for example, a failure handler logged `failure.getAuthentication()`. Fix:
bootstrap a `CsrfSession` first, so the canary reaches the provider, and add a success-path login with a registered
password. **Still applies at HEAD** (file unchanged). HEAD's admin seed may exercise `ADMIN_PASSWORD` elsewhere, but
nothing in this test proves it.

### M4 (Medium): Real-format key material and an admin password are committed, and nothing refuses them outside `dev`
`backend/README.md` (commit `7c075e9`, "Shared dev demo values").

The commit adds working base64 values for `APP_MFA_TOTP_ENCRYPTION_KEY`, `APP_SECURITY_HMAC_TOMBSTONE_KEY`,
`APP_SECURITY_HMAC_LOG_KEY` and `APP_ADMIN_PASSWORD=dev-demo-admin-passphrase`. The warning text is good, but there is
no technical control behind it. `ProhibitedConfigurationValidator` (HEAD) checks the reset-link logger and the
datasource URL only, and `grep` finds these values nowhere but the README. If they are pasted into a shared or
production environment:
- the log key makes every `source.ip_hash` and `session.hash` a public, reversible lookup, which undoes ADR-054;
- the TOTP key decrypts every stored factor secret;
- the admin password is known to anyone who can read the repo.

The canonical guard is cheap: refuse to start outside `dev` when a key's fingerprint, or the admin password, matches
a published demo value. The startup row already computes key fingerprints. **Still applies at HEAD.**

### Low

- **L1: Ticket 10's idle-timeout item is ticked on a test that doesn't match its wording.** The item says "moving the
  `Clock` past 15 minutes idle … gives 401". `SignInTest.anIdleSessionPastTheWindowGets401NotARedirect` (T-SES-001)
  sets `LAST_ACCESS_TIME = 0, EXPIRY_TIME = 0` instead. That matches the T-SES-001 row, and Spring Session's idle
  expiry runs on wall-clock time, not the injected `Clock`. The test is fine; the ticket's wording overstates it. It
  also doesn't probe the 15-minute boundary, only "at most 15 minutes configured" plus "far past it".
- **L2: The item "logout on a dead session *without* a CSRF token gets 403" isn't what the test sends.**
  `logoutOnADeadSessionWithAStaleTokenIsRefusedForCsrf` (T-CSRF-005) sends a *stale* token, as the T-ID row says. The
  no-token variant on a dead session isn't asserted. It is covered indirectly by
  `anAbsolutelyExpiredSessionPostingAMutation…`.
- **L3: T-SES-026 doesn't cover the filter-served routes.** `AnonymousSessionCreationTest` builds its route list from
  `RequestMappingHandlerMapping`. `/api/login` and `/api/logout` are filters, not handler methods, so the "creates no
  session" sweep never hits them. They are CSRF-refused first today, so this is not a live defect. But the test
  wouldn't catch a later change there.
- **L4: The login body was unbounded in this range.** `JsonCredentialsConverter.convert` reads the whole stream
  through Jackson. **Fixed at HEAD** by `RequestBodyCapFilter` (ticket 11).
- **L5: The SPA sent every sign-in to `/hello`,** so an ADMIN (whose matrix row has no `/api/hello`) landed on a 403
  "greeting could not be loaded". That contradicts "routing is driven by the self-read". **Fixed at HEAD**
  (`landingFor(profile)`).
- **L6: `signIn()` can report a failure after the server has signed the user in.**
  `frontend/src/lib/auth/session.ts:28-35`: if `refreshCsrfToken()` fails after a 200 login, the page shows "Sign-in
  failed" and clears the password, although the server session is live. The next attempt then displaces it (see M2).
  Treat a token-refresh failure as non-fatal, since the retry backstop re-bootstraps anyway.
- **L7: `pinAnonymous` rewrites the session on every anonymous request.** `AbsoluteLifetimeFilter.java:77-85` changes
  `maxInactiveInterval` each time, so Spring Session JDBC issues an `UPDATE` per request on anonymous sessions. It is
  correct, but the write cost is proportional to anonymous traffic, which is the axis ticket 26 sheds on.

### Checked and found correct (no finding)

- **Session fixation:** the id is rotated in the login composite, the pre-login row is deleted (T-SES-005 asserts
  this), and the CSRF token is rotated (T-CSRF-007).
- **CSRF:** header-only resolution. The `_csrf` parameter and form body are refused (T-CSRF-009). A token is never
  saved into a non-existent session. `/api/logout` stays behind `CsrfFilter`. `NullRequestCache` is set.
- **Enumeration:** one writer for every failure; `UsernameNotFound` is hidden. A never-activated account is mapped to
  not-found so it pays the dummy `matches()`. `alwaysPerformAdditionalChecksOnUser` is verified reflectively and by
  call count (`PasswordMatchCountTest`). `LoginFailureAudit` looks the account up only *after* failure, on every
  failure path alike, so the extra query is uniform.
- **Log redaction:** audit rows never carry `error.*`, MDC is allowlisted to trace and span ids, `session.hash` and
  `source.ip_hash` are HMACs under the log key with domain prefixes, and neither the username nor the raw id is
  written (T-AUD-008, T-AUD-020). Inbound `traceparent`, B3 and baggage are stripped before the observation filter.
- **Sessions:** the cookie serializer is explicit (REJ-083), only the first cookie is honoured, and attributes
  deserialise through an allowlist that ends in `!*`.

## Standards

The repo has no documented coding standards, so these are smell-baseline judgement calls only.

- **Possible Divergent Change: `SignIn`** (`security/login/SignIn.java`) owns the login filter, the six-step login
  composite, the concurrent-session filter, the logout handlers and the success body. Login-policy changes (tickets
  11, 12, 13 and 16 all touched it) and logout changes land in one class. Consider splitting sign-out into its own
  small configurer.
- **Possible Primitive Obsession: role as a `String`.** `SignedInUser.role`, `Profile.role` and the
  `"ADMIN".equals(user.role())` check in `Profile.of` stand in for the `Role` concept that ADR-042 defines. A typo
  fails silently.
- **Possible Duplicated Code: the "signed-in user id" lookup.** It is repeated in `ProblemAccessDeniedHandler.signedInUserId`,
  `SignIn.auditLogout` and `SignIn.userOf`, each with a slightly different null or instanceof handling. One helper
  would keep the unsafe cast in `userOf` out of the composite lambda.
- **Comment quality is high.** Load-bearing lines (`getSession(true)` in `CsrfController`, the hand-listed
  `CsrfAuthenticationStrategy`, `loadDeferredToken` deliberately not overridden) are explained where a maintainer
  would otherwise "fix" them. No finding.

---

**Spec:** 11 findings (0 Critical, 0 High, 4 Medium, 7 Low). The worst is M1, the absolute-expiry 401 missing CORS and
security headers, which still applies at HEAD.
**Standards:** 3 judgement-call smells and no documented-standard breaches. The worst is the Divergent Change in `SignIn`.
