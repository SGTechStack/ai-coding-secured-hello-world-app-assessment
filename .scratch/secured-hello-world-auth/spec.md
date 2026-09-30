# Spec: Secured Hello World Auth

Status: open
Labels: ready-for-agent

Sources: `prd/assessment-prd.md` (scope), `App-Standards/Appfw-User-Standards/User_Standalone/` and `App-Standards/Appfw-Logging-Standards/` (implementation), `CONTEXT.md` (vocabulary), `docs/adr/0001`–`0012` (decisions and deliberate deviations).

## Problem Statement

We need a reference application that shows how a secure username/password login should be built with a React SPA and a Spring Boot REST API on separate origins. Most "hello world" login demos cut corners: passwords in plaintext or in weak hashes, no lockout, error messages that reveal whether an account exists, sessions that survive logout, CSRF switched off, and logs full of personal data. Anyone copying from such a demo copies those flaws. The application must follow the organisation's App Standards for user access control and structured logging, keep to the PRD's scope, and record every place where the two disagree.

## Solution

A small application with two parts, a React SPA and a Spring Boot API, on the same site but different origins:

- A **Visitor** can register an **Account** and log in.
- A **Regular user** sees a personalised greeting and can log out, which fully ends the session.
- Someone who forgot their password can recover the account with a **Password reset token** sent to their email. Email is a stub that logs instead of sending.
- An **Admin** can list accounts; enable or disable, change the role of, unlock, or delete another account; and can never do any of these to their own account.
- A **Bootstrap admin** is created at first startup from operator configuration.

Security measures throughout:

- Server-side sessions in the database, carried by `HttpOnly`/`Secure`/`SameSite=Lax` cookies.
- CSRF tokens stored in the session (the synchronizer pattern).
- Per-account lockout, plus per-IP and per-username throttling.
- Responses that reveal nothing about whether an account exists.
- Soft-delete **Tombstones**.
- A check against passwords known from data breaches (Have I Been Pwned).
- Security response headers.
- ECS-format structured audit logs containing no personal data.

## User Stories

### Registration

1. As a Visitor, I want to register with a username, email and password, so that I can log in and use the protected app.
2. As a Visitor, I want to learn every password rule when I register (at least 12 characters, at most 72 bytes, not a common password), so that I can pick a valid password first time.
3. As a Visitor, I want a clear error when my username or email is already taken, so that I know to pick another. The error doesn't say which of the two clashed.
4. As a Visitor, I want usernames compared regardless of case, so that nobody can register `Alice` to impersonate `alice`.
5. As a Visitor, I want to be sent to the login page after registering instead of being logged in automatically, so that registration never becomes a second way to authenticate.
6. As a Visitor, I want malformed input (bad email, username outside 3–32 characters of `[a-zA-Z0-9._-]`, oversized request) rejected with a clear validation error, so that I can correct it.
7. As an operator, I want new accounts to start with role `USER`, enabled, not locked, and their password stored only as a BCrypt hash, so that registration never grants privilege or stores a secret in recoverable form.
8. As an operator, I want registration throttled per IP, so that bulk account-existence probing through the registration form is slowed down.
9. As an operator, I want the usernames and emails of Tombstones kept reserved, so that a deleted identity can never be re-registered by someone else.

### Login

10. As a Regular user, I want to log in with my username and password, so that I can reach the protected content.
11. As a Regular user, I want my username matched regardless of case at login, so that `Alice` and `alice` both reach my Account.
12. As a Regular user, I want a new session ID issued every time I log in, so that someone who planted a session ID on me before login (session fixation) can't take over my session.
13. As a Regular user, I want my Failed-login counter reset to zero when I log in successfully, so that old typos don't count toward a lockout.
14. As a Regular user, I want a single generic error for a wrong password, an unknown username, or a Locked or Disabled Account, so that an attacker learns nothing about which accounts exist or what state they're in.
15. As an operator, I want failed logins for unknown usernames to take the same time as for real ones, so that timing reveals nothing either.
16. As a Regular user, I want a new login to end my earlier session, so that I never have more than one session at a time.
17. As a Regular user, I want my session to expire after 15 minutes of inactivity and after 8 hours no matter what, so that an abandoned or stolen session has a bounded lifetime.
18. As a Regular user, I want my session to survive an API restart, so that a routine deployment doesn't log me out.

### Lockout and throttling

19. As an operator, I want an Account to become Locked for 20 minutes after 5 consecutive failed logins, so that brute-force guessing against one account is blunted.
20. As a Regular user, I want a lock to expire by itself, so that someone who deliberately locks my Account only locks me out for a limited time.
21. As a Regular user, I want to log in normally with the correct password once the lock has expired, with my Failed-login counter reset, so that I recover without admin help.
22. As an operator, I want lock state stored on the Account, so that restarting the API doesn't clear locks or counters.
23. As an operator, I want failed logins from one IP throttled across all usernames, so that password spraying from one source is slowed down independently of any Account's lock state.
24. As an operator, I want login attempts throttled per submitted username (10 per minute), keyed on the string as typed rather than a looked-up Account, so that a 429 reveals nothing about whether the Account exists.
25. As a client developer, I want every throttled response to be a 429 with a `Retry-After` header, so that the SPA can tell the user when to try again.
26. As an operator, I want the client IP taken only from the connection's remote address unless trusted proxies are configured, so that attackers can't escape the IP throttle by forging `X-Forwarded-For`.

### Logout and sessions

27. As a Regular user, I want to log out, so that my session ends on the server and the session cookie is cleared.
28. As a Regular user, I want a session cookie captured before logout to be rejected afterwards, so that a stolen cookie is useless once I've logged out.
29. As a Regular user, I want the response to logout over HTTPS to carry `Clear-Site-Data`, so that my browser drops cached data for the API.
30. As a Regular user, I want the SPA to clear its own state on logout, so that nothing is left in the frontend's origin, which `Clear-Site-Data` from the API can't reach.
31. As a Regular user, I want a logout attempt on an already-expired session to quietly send me back to login, so that I never see an error page for logging out.

### CSRF

32. As a client developer, I want a dedicated endpoint that returns the current CSRF token and is never cached, so that the SPA can include it in state-changing requests.
33. As an operator, I want every state-changing request (register, login, logout, password reset, admin changes) to fail with 403 without a valid CSRF token, so that a cross-site forged request can't act on a Regular user's behalf.
34. As a client developer, I want the CSRF token to change when the session changes at login and logout, and I want the SPA to fetch it again then, so that tokens from an earlier session are rejected.
35. As an operator, I want CSRF tokens held only in the server session and never in a cookie, so that we follow the standard's ban on double-submit cookies.

### Protected content and self-read

36. As a Regular user, I want `GET /api/hello` to return "Hello, <username>", so that I can see my login actually worked.
37. As a Visitor, I want `GET /api/hello` without a valid session to return 401, so that protected content is never served anonymously.
38. As a Regular user, I want a "who am I" endpoint returning only my own Account's details (id, username, email, role), so that the SPA can adapt its UI without reading anyone else's data.
39. As a Regular user, I want the SPA to hide admin pages because my role isn't ADMIN, knowing the server enforces access anyway, so that the UI matches what I'm allowed to do.

### Password reset

40. As a Regular user who forgot my password, I want to request a reset by entering my email, so that I can regain access without contacting an admin.
41. As an operator, I want the reset-request response to be identical, in body and timing, whether or not the email is registered, so that account existence can't be inferred.
42. As an operator, I want a reset token created only for a registered, non-Tombstone Account: 32 or more random bytes, stored only as a SHA-256 hash, valid for 30 minutes, so that a database leak doesn't expose usable tokens.
43. As a Regular user, I want the reset link delivered through the email service (a stub that logs the link), so that only whoever controls my inbox can reset my password.
44. As an operator, I want issuing a new Password reset token to invalidate any pending one for that Account, so that only the most recent link works.
45. As a Regular user with a valid token, I want to set a new password that meets the password policy, so that I regain access.
46. As a Regular user, I want a successful reset to end all my existing sessions, so that anyone who had my old password or session is kicked out.
47. As an operator, I want an expired, already-used or unknown token rejected with one stable error and the password left unchanged, so that tokens are strictly single-use and time-limited.
48. As an operator, I want a successful reset to leave any lock and the Failed-login counter untouched, so that the reset link can't be used to get around a lockout.
49. As an operator, I want both reset endpoints rate-limited, so that tokens can't be brute-forced and reset emails can't be used to flood an inbox.
50. As a Regular user, I want to be notified when my password has been reset, so that I notice a reset I didn't ask for.

### Admin: listing

51. As an Admin, I want to list all Accounts with id, username, email, role, enabled status, locked status and created date, so that I can review who has access.
52. As an Admin, I want the list to exclude Tombstones and never include password hashes, so that it shows only live accounts and no secrets.
53. As an Admin, I want the list paginated, so that it stays usable as the number of Accounts grows.
54. As an operator, I want a Regular user calling any admin endpoint to get 403, so that role enforcement happens on the server and never relies on the client.

### Admin: account changes

55. As an Admin, I want to disable or re-enable another Account, so that I can suspend access without deleting data.
56. As an Admin, I want disabling an Account to end its active sessions immediately, so that a Disabled Account loses access at once, not at its next login.
57. As an Admin, I want re-enabling an Account to leave any lock in place, so that Disabled and Locked stay independent states.
58. As an Admin, I want to change another Account's role between `USER` and `ADMIN`, so that I can grant or revoke admin rights.
59. As an Admin, I want a role change to end the target's sessions, so that a demoted Admin can't keep their old rights in a session that's still open.
60. As an Admin, I want to unlock a Locked Account, so that a Regular user locked out by an attacker can get back in before the lock expires.
61. As an Admin, I want to delete another Account, so that it disappears from the app. Underneath, it becomes a Tombstone, its sessions end and its pending reset tokens are removed.
62. As an Admin, I want any attempt to disable, demote or delete my own Account rejected with 403, so that I can't lock myself out and at least one Admin always remains.
63. As an Admin, I want an unknown or Tombstone target to return 404, so that I get a clear answer for a stale link.

### Bootstrap admin

64. As an operator deploying for the first time, I want a Bootstrap admin created from configuration when no active Admin exists, so that there's a way into the admin area without editing the database.
65. As an operator, I want no duplicate Bootstrap admin created on restart, so that restarts are safe.
66. As an operator, I want startup to fail outside the dev profile if the Bootstrap admin settings are missing, so that there are never default admin credentials in a real environment.
67. As an operator, I want the Bootstrap admin's password to pass the same password policy and hashing as any other Account, so that the most powerful account isn't the weakest.

### Security headers and CORS

68. As an operator, I want every response to carry HSTS, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, a restrictive Content-Security-Policy and a Permissions-Policy, so that clickjacking, MIME sniffing and cross-origin leaks are blocked.
69. As an operator, I want CORS to allow only the frontend origins on an explicit list, with credentials enabled and no wildcard, so that only our SPA can make credentialed cross-origin calls.
70. As an operator, I want the H2 console reachable only in the dev profile, so that it never exists in a real deployment.

### Errors

71. As a client developer, I want every error returned as an RFC 9457 Problem Details body with a stable `code` field, so that the SPA can handle errors reliably.
72. As a client developer, I want validation errors to list each failing field and rule, so that the UI can show the problem next to the right input.
73. As an operator, I want no stack traces or internal details in any error response in any environment, so that errors don't leak information about the system.

### Audit and logging

74. As an operator, I want these events audited: login success and failure, logout, lockout, unlock, password reset requested and completed, registration, and admin enable/disable/role-change/delete. Each records who acted, on what, when and the outcome, so that security incidents can be investigated.
75. As an operator, I want audit events written as ECS JSON to their own destination, separate from the application log, so that they can be kept and shipped on their own.
76. As an operator, I want logs to identify Accounts only by UUID, never by username or email, with failed-login events carrying no account identity, so that logs hold no personal data.
77. As an operator, I want client IPs logged only as a keyed hash, so that events from the same source can be linked without storing raw IPs.
78. As an operator, I want passwords, reset tokens, CSRF tokens and raw session IDs never logged, so that logs can never leak credentials.
79. As an operator, I want each log line to carry a trace ID and a correlation ID (taken from `X-Correlation-ID` or generated), so that one request can be followed across log lines.
80. As an operator, I want lockouts logged at WARN, successes at INFO and system failures at ERROR, so that log levels are useful for alerting.
81. As an operator, I want user input stripped of line breaks before it reaches any log field, so that nobody can inject fake log lines.

### Notifications

82. As a Regular user, I want to be notified when my Account becomes Locked, so that I learn someone may be guessing my password.

### Frontend experience

83. As a Visitor, I want pages to register, log in, request a password reset, and set a new password from a reset link, so that I can do every account flow in the browser.
84. As a Regular user, I want a home page showing my greeting and a logout button, so that I can confirm I'm logged in and end my session.
85. As an Admin, I want an admin page listing Accounts with enable/disable, role, unlock and delete actions, so that I can manage Accounts without API tools.
86. As a Regular user, I want the SPA to keep no auth state or tokens in browser storage, so that a script injected into the page can't steal them from there.
87. As a Regular user, I want any 401 to clear the SPA's state and send me to login without an error screen, so that an expired session is handled smoothly.
88. As a Visitor, I want 429 responses shown as "try again in N seconds", so that I understand why I'm being blocked.

## Implementation Decisions

### Precedence (ADR-0001)
- The PRD sets scope and the App Standards set implementation.
- Each genuine conflict was decided separately and is recorded in ADR-0002 to ADR-0010.
- The PRD is never edited.

### Repository shape and stack
- There are two sibling apps at the repo root:
  - **Backend:** Spring Boot 4.1.1, Java 21, Maven with the Maven wrapper.
  - **Frontend:** React 19.3, TypeScript, Vite 8, React Router 8.
- The Boot 4 modular starters are used: web MVC, security, data JPA, validation, session JDBC and actuator. The H2 console comes as its own module, dev profile only.
- Tracing uses the Micrometer tracing bridge with no exporter; it exists only to put trace IDs in logs.
- The version check confirmed the managed versions: Spring Security 7.1.1, Spring Session 4.1.1, Jackson 3 and Hibernate 7.4.
- Node 22.22 or later is required (React Router 8).

### Backend modules (deep modules with small interfaces)
- **Account module.** Owns the Account entity and its repository, the registration rules, and the Tombstone semantics.
  - Interface: register, find an active account by username (case-insensitive), find an active account by email, and the admin operations (list, set enabled, set role, unlock, soft-delete).
  - "Active" means not a Tombstone.
- **Password policy.** One place to validate a candidate password:
  - length of at least 12 characters and at most 72 bytes of UTF-8
  - not known from data breaches, checked with Spring Security's `HaveIBeenPwnedRestApiPasswordChecker` (ADR-0010). If the check can't be reached within its timeout, setting a password gets 503 `service unavailable`. The same checker also refuses a breached password at login, but lets the login through during an outage.
  - returns a list of the rules that failed.
  - It is used for registration, reset confirm and the Bootstrap admin.
- **Password hashing.** `DelegatingPasswordEncoder` with BCrypt (cost 12) as the default, so a later move to Argon2id needs only config.
- **Login protection.** Owns lockout and throttling:
  - It records failures and successes against an Account: the Failed-login counter and `locked_until`, persisted on the Account.
  - It answers "is this attempt allowed?" for IP-keyed and username-keyed buckets, using Bucket4j `bucket4j_jdk17-core` in memory. This is correct only for a single instance (ADR-0003).
  - It plugs into Spring Security's form-login success and failure handling and its authentication checks.
  - The lockout threshold and duration, every throttle limit and the trusted-proxy list are configuration properties.
- **Password reset module.**
  - Request: always responds the same way. The token is created and the email sent asynchronously.
  - Confirm: validates the token hash, expiry and unused state; applies the password policy; updates the credential; marks the token used; ends all sessions.
  - Owns the reset-token table.
  - Depends on the `EmailService` port and the `Clock`.
- **Session control.** Wraps the Spring Session JDBC indexed repository, which supports looking up sessions by principal, behind one operation: "end all sessions for Account X".
  - Used by reset confirm, disable, role change and delete.
  - It also enforces the 8-hour absolute lifetime, rejecting an older session as 401, alongside the 15-minute idle timeout.
  - At most one session per user is enforced through `maximumSessions(1)` with the Spring-Session-backed session registry; a new login ends the older session.
- **Audit logger.** A single typed component with one method per auditable event, writing to a dedicated `audit` logger through the SLF4J fluent API with ECS key-value fields.
  - Fields used: `event.action`, `event.outcome`, `event.category`, `user.id`, target UUID, `source.ip_hash` (HMAC-SHA256 with a server secret from config), `error_code`.
  - No other code logs security events directly.
- **Email service port.**
  - `sendPasswordResetEmail`, `sendLockoutNotification` and `sendPasswordChangedNotification`.
  - The stub implementation logs that a message was sent, and in the dev profile only, the reset link. The link is never written to the audit log.
- **Bootstrap admin initializer.**
  - Runs in every profile and creates the Bootstrap admin only if no active Admin exists (ADR-0008).
  - Outside dev it fails startup when `app.admin.username`, `app.admin.password` or `app.admin.email` is missing.
  - Development-only seed accounts may exist only in the dev profile.
- **Security configuration.** One `SecurityFilterChain` for the API. URL rules use `PathPatternRequestMatcher` only, and `@EnableMethodSecurity` adds role checks on admin handlers on top of the URL rules.
  - A separate dev-only chain for the H2 console.
  - `csrf.spa()` must never be used; it switches to cookie-based CSRF, which the standard prohibits.

### Schema
- `users`:
  - `id` UUID
  - `username` stored as typed, with a unique case-insensitive lookup key
  - `email` lowercased and unique
  - `password_hash` with its encoder prefix
  - `role` enum `USER`/`ADMIN`
  - `enabled`
  - `failed_login_attempts`
  - `locked_until` (nullable)
  - `created_at`
  - `deleted_at` (nullable; set means Tombstone)
- The uniqueness constraints on the username and email keys cover Tombstones too, so their identities stay reserved.
- `password_reset_tokens`:
  - `id` UUID
  - `user_id` FK
  - `token_hash` SHA-256 hex, unique
  - `expires_at`
  - `used_at` (nullable)
  - `created_at`
- Spring Session JDBC tables are created by the framework's schema setup.
- The schema must stay portable to Postgres/MySQL: no H2-only types.

### API contract (base path configurable as `app.api.base-path`, default `/api`)
- `GET /csrf` (anonymous, never cached): returns the CSRF token, header name and parameter name. The header is `X-CSRF-TOKEN`.
- `POST /register` (anonymous, CSRF): JSON body with `username`, `email`, `password`.
  - 201 on success.
  - 400 `user exist` for a username or email clash, without saying which (ADR-0005).
  - 400 `validation failed` with a per-field list.
- `POST /login` (anonymous, CSRF): form-encoded `username` and `password`, handled by Spring Security form login with JSON-returning handlers.
  - 200 on success.
  - 401 `invalid credentials` for every failure.
  - 429 when throttled.
- `POST /logout` (CSRF): 200 and clears the session cookie. `Clear-Site-Data: "cache","cookies","storage"` is sent on HTTPS only; Spring's writer is used as-is.
- `GET /me`: the caller's own id, username, email and role.
- `GET /hello`: `"Hello, <username>"`, or 401.
- `POST /password-reset/request` (anonymous, CSRF): JSON `email`. Always 202 with a generic message.
- `POST /password-reset/confirm` (anonymous, CSRF): JSON `token` and `newPassword`.
  - 200 on success.
  - 400 `password reset token expired or invalid`.
  - 400 `validation failed`.
- `GET /admin/users?page=&size=` (ADMIN): paginated, default size 50. Tombstones are excluded.
- `PATCH /admin/users/{id}/status` with `{enabled}`, `PATCH /admin/users/{id}/role` with `{role}`, `POST /admin/users/{id}/unlock` and `DELETE /admin/users/{id}`, all ADMIN and CSRF.
  - 403 `self action not allowed` when the target is the caller.
  - 404 for an unknown id or a Tombstone.
  - Disable, role change and delete end the target's sessions.
- Every error is an RFC 9457 `ProblemDetail` with a stable `code`. The codes are `invalid credentials`, `user exist`, `validation failed`, `too many requests` (with `Retry-After`), `password reset token expired or invalid`, `forbidden`, `self action not allowed`, `unauthenticated` and `not found`.
  - Any other error status takes its HTTP reason phrase in lowercase as its code, for example `method not allowed` (405), `unsupported media type` (415) and `internal server error` (500). A status with no standard reason phrase gets `error`.
  - These are never mapped onto a listed code, because that would tell the client something false (a 405 is not `validation failed`).
  - In the SPA, a response that carries no `code` (for example a proxy's error page) is reported as `error`.

### Session, cookie, CORS and header settings (ADR-0004)
- Session cookie:
  - `HttpOnly`
  - `SameSite=Lax`
  - `Secure=true` by default; only the dev profile may turn it off, through a property.
- Idle timeout 15 minutes, absolute 8 hours, at most 1 concurrent session, session state stored in the database.
- CORS:
  - an explicit list of origins from config, with no wildcard
  - credentials allowed
  - methods GET/POST/PUT/PATCH/DELETE/OPTIONS
  - headers `Content-Type`, `X-CSRF-TOKEN` and `X-Correlation-ID`
  - `maxAge` 3600.
- Headers: HSTS (1 year, includeSubDomains), `nosniff`, `X-Frame-Options: DENY`, `Content-Security-Policy: default-src 'self'; object-src 'none'`, and a Permissions-Policy denying geolocation, microphone and camera.
- In production the frontend and API must share a registrable domain.

### Logging
- ECS structured JSON on the console for the application log.
- A separate rolling ECS JSON file for the `audit` logger, which does not also write to the application log.
- `service.name/version/environment` are set.
- `traceId` is renamed to `trace.id`. The rename's exact output (nested or flat key) is to be confirmed during the build.
- A servlet filter puts a correlation ID into MDC (propagated or generated) and `user.id` after authentication, and clears both afterwards.
- Levels:
  - INFO for successes.
  - WARN for failed logins, lockouts (`event.action=ATTEMPTS_EXCEEDED`, `error_code 423`), throttling, 403s and failed admin actions (ADR-0009).
  - ERROR for system failures.

### Frontend modules
- **API client.** The single module that talks to the backend. It:
  - uses `credentials: 'include'`
  - fetches the CSRF token on startup and again after login and logout, holding it in memory only
  - attaches `X-CSRF-TOKEN` to state-changing requests
  - parses Problem Details
  - exposes a global 401 handler: clear state, redirect to login, no error screen.
- **Auth state.** Whoever is logged in, as reported by `/me`. Held in memory only.
- **Routes:**
  - login
  - register
  - forgot password
  - reset password, which reads the token from the query string
  - home, with the greeting and logout
  - admin users, shown only when `/me` reports ADMIN.

## Testing Decisions

### What makes a good test here
- Test externally visible behaviour through the highest seam: HTTP status, body, headers, cookies and emitted audit events. Don't test private methods, repository calls or class interactions.
- A test should keep passing if the internals are rewritten.
- Time-dependent rules use an injected, controllable `Clock`, never `sleep`.
- Test data uses made-up usernames and emails (for example `testuser1@test.example.com`).

### Seams (agreed)
1. **Backend HTTP API, full Spring context.** This is the primary seam. `@SpringBootTest` with `@AutoConfigureMockMvc`, using `MockMvcTester`, against H2 with the real security filter chain. Supporting substitutes:
   - a controllable `Clock` bean
   - a recording `EmailService`, so tests can read the plaintext reset token
   - an offline `CompromisedPasswordChecker`, and for the Have I Been Pwned wiring itself, a local stub of its range API, so no test calls the network
   - a Logback `ListAppender` attached to the `audit` logger, for checking events and that no personal data leaks
   - configuration overrides to lower throttle and lockout limits where that helps.

   Use `@MockitoBean`, not the removed `@MockBean`.
2. **Frontend API client.** Vitest with Testing Library, with `fetch` mocked. Covers the CSRF lifecycle (fetch on start, again after login and logout, header attached) and the global 401 handler.
3. **End-to-end.** One Playwright test against the real frontend and API on their separate origins: register → log in → hello → log out → a replayed session cookie is rejected. This is the only test that exercises real CORS, cookies and the same-site setup.

### Required backend coverage (PRD minimum plus agreed behaviour)
- Login: success; wrong password; unknown username with an identical response; Locked; Disabled; username case-insensitivity; session ID changes on login; a second login ends the first session.
- Lockout: 5 failures lock; still locked at 19 minutes; login succeeds after 20 minutes and resets the counter; unlock clears the lock; lock survives a context-level reload of state (persisted on the Account).
- Throttling: IP throttle engages independently of Account lockout; username throttle returns an identical 429 for unknown and real usernames; `Retry-After` present; a forged `X-Forwarded-For` is ignored without a trusted-proxy setting.
- Logout: a replayed session cookie gets 401 after logout; `Clear-Site-Data` present on a secure request.
- Sessions: 401 after the idle timeout; 401 after the absolute lifetime even with activity (using the controllable clock).
- CSRF: every state-changing endpoint rejects a missing or invalid token with 403; a token from before login is rejected after login; the `/csrf` response isn't cacheable; GET needs no token.
- Registration: success creates a `USER`, enabled, BCrypt-hashed Account; a clash on username (any case) or email returns the same `user exist`; each failing password rule reported, including a compromised password; a Tombstone username stays reserved.
- Password reset: same response for registered and unregistered emails; single use; expiry at 30 minutes; a new token invalidates the old one; reset ends existing sessions; reset leaves a lock in place; the plaintext token appears in no log.
- Admin: a `USER` gets 403 on every `/admin/**` endpoint; an Admin can't disable, demote or delete themselves (403); disable, role change and delete end the target's sessions; a demoted Admin's old session loses admin access; delete makes a Tombstone that is hidden from the list, can't log in, and returns 404 afterwards; the list never contains password hashes.
- Bootstrap admin: created when no active Admin exists; not duplicated on restart; startup fails without the settings outside dev.
- Headers and CORS: the security headers are on every response; the allowed origin passes preflight with credentials; any other origin is refused.
- Audit: each audited event is emitted with the right `event.action` and outcome; no event contains a password, token, username, email or raw IP.

### Prior art
- The repo has no code yet, so there are no existing tests to copy.
- Follow the patterns in the App Standards:
  - the session-login-with-CSRF-bootstrap recipe for security tests
  - the typed audit-logging recipe for `ListAppender` checks
  - the standard's "Test & Validation" section as the checklist.

## Out of Scope

- JWT authentication. The design is documented only in the PRD appendix.
- MFA. The MFA standards don't require it here.
- Real SMTP or any real email delivery.
- Containers, CI/CD, hosting, and local HTTPS. `Clear-Site-Data` and `Secure` cookies are therefore only fully exercised on HTTPS.
- Standard-only features left out on purpose (ADR-0002, ADR-0006):
  - forced password change on first login or re-enable, and its grace period
  - password history
  - self-service password change
  - scheduled account-cleanup jobs (inactivity disable, role revocation) and ShedLock
  - roles and permissions defined in config files
  - batch password reset
  - admin-issued password reset
- Email verification at registration. Its absence leaves the registration leak accepted in ADR-0005.
- Rate limiting across multiple instances. The in-memory buckets are correct only for a single instance.
- Access control beyond the USER/ADMIN role check.
- Keeping audit logs for 90 days. This is an operational requirement on whatever ships the audit log file, not application code.

## Further Notes

- Known deviations from the standards, each in an ADR:
  - self-service reset only (0006)
  - combined registration conflict error (0005)
  - the IP throttle's real purpose, which corrects PRD Story 3's rationale (0003)
  - Bootstrap admin in every profile (0008)
  - hashed IP and WARN-level lockout (0009)
  - BCrypt rather than Argon2id (0011)
  - `/me` returns the Account id (0012).
  
  Reviewers checking against the standard should read these before flagging them.
- The compromised-password check (ADR-0010) needs outbound HTTPS to `api.pwnedpasswords.com` in every deployed environment. It is not a deviation: the standard's recipes suggest this API.
- The PRD's "e.g. 15 min" lockout is replaced by the standard's 20 minutes, and "15–30 min" token expiry by 30 minutes, following ADR-0001.
- The frontend and API must share a registrable domain in production, or `SameSite=Lax` session cookies won't be sent (ADR-0004).
- Use the glossary terms in `CONTEXT.md` in code, tests and ticket names: Account, Visitor, Regular user, Admin, Bootstrap admin, Tombstone, Disabled, Locked, Throttled, Failed-login counter, Password reset token.
