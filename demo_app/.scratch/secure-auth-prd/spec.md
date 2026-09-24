# Spec: Secure Auth App — Full PRD with OWASP Top 10 Hardening

**Status:** ready-for-agent
**Source:** `../prd/assessment-prd.md` (Hello World Auth App PRD), one level above this repo.
**Builds on:** `.scratch/login/spec.md` (login, session and guards) and `.scratch/logout/spec.md` (logout). Their decisions still hold unless this spec says otherwise.

## Problem Statement

The demo app handles only one seeded account (`johndoe`). A visitor can log in, see "Hello, John!" and log out. The PRD asks for a production-grade security baseline, and the app falls well short of it:

- A visitor cannot register. A user who forgets their password has no way back in. Nobody can manage accounts.
- Nothing slows down credential guessing. An attacker can try passwords against one account, or spray them across many accounts from one IP, for as long as they like.
- Every account has the same privileges. There is no `ADMIN` role, no admin module and no way to create the first admin.
- Nothing records security-relevant events, so an operator cannot see a brute-force attempt, a lockout or an admin changing someone's role.
- The API already sends a strict CSP and other security headers. The SPA sends none, and its `index.html` runs an inline script, so a strict CSP cannot be applied to it as it stands. Once other users' data appears on screen (the admin user list, a user's first name), the SPA becomes the main place a stored XSS could land.
- The app runs as a single origin behind the Vite proxy. The PRD describes two origins with an explicit CORS allow-list, and the CSRF flow depends on the SPA reading a cookie the API sets. That only works while both run on one host.
- Some failures fall through to Spring's defaults: a malformed JSON body, an unknown path, an unexpected exception. The API does not guarantee a generic JSON error for them, so some could leak detail or return a non-JSON body.

## Solution

Build out the rest of the PRD (registration, lockout and IP throttling, password reset, the admin module, admin bootstrap and audit logging). Harden the whole app against the OWASP Top 10 (2025) while doing it, with cross-site scripting (XSS) as a first-class concern.

From the user's side:

- A **visitor** can create an account from a "Create an account" link on the login page. They see clear field errors, including "username already taken", and then land on the login page with a confirmation.
- A **user** who fails to log in five times in a row is locked out for 15 minutes. They see the same generic "Invalid username or password" message throughout, so an attacker learns nothing. Anyone flooding login from one IP is throttled, with a "Too many attempts" message, without the real account owner being locked out.
- A **user who forgot their password** can request a reset link by email. They always see the same "if an account exists…" message. Following the link lets them set a new password, and doing so signs them out everywhere.
- An **admin** sees an "Admin" link in the navbar. The admin page lists every user and lets the admin enable, disable, promote, demote or delete any account except their own.
- An **operator** gets a working admin account on first start from configuration, and structured audit log lines for every security-relevant event.
- Throughout, anything a user types is shown as text and never run as script. The SPA runs under a strict Content-Security-Policy. The API accepts requests only from the configured frontend origin.

## User Stories

### Registration (PRD Story 1)

1. As a visitor, I want a "Create an account" link on the login page, so that I can find registration without knowing its URL.
2. As a visitor, I want to register with a username, email, first name and password, so that I can log in and be greeted by name.
3. As a visitor, I want to be told when my username is already taken, so that I can pick another one.
4. As a visitor, I want to be told when my email is already registered, so that I know to log in or reset my password instead.
5. As a visitor, I want to be told when my password is shorter than 12 characters, so that I can choose a stronger one before submitting again.
6. As a visitor, I want to be told when my password is on a list of common passwords, so that I don't pick one attackers try first.
7. As a visitor, I want to confirm my password in a second field, so that a typo doesn't lock me out of the account I just created.
8. As a visitor, I want field errors next to the field they concern, and announced to screen readers, so that I can fix them quickly.
9. As a visitor, I want to land on the login page with "Account created. Please log in." after registering, so that I know it worked and what to do next.
10. As a visitor, I want my username treated case-insensitively, so that `JohnDoe` and `johndoe` can't be two different accounts that are easy to mix up.
11. As an authenticated user, I want visiting the registration page to send me to the landing page, so that I don't create a second account by accident.
12. As a security-conscious operator, I want every new account to get the `USER` role whatever the request says, so that nobody can register themselves as an admin.
13. As a security-conscious operator, I want passwords stored only as BCrypt hashes and never logged, so that a database or log leak doesn't expose them.
14. As a security-conscious operator, I want registration throttled per IP, so that nobody can script mass account creation or use the endpoint to enumerate usernames at scale.

### Login, lockout and throttling (PRD Stories 2–3)

15. As a registered user, I want to log in with my username and password, so that I can reach my greeting.
16. As a registered user, I want a successful login to reset my failed-attempt count, so that old typos don't bring me closer to a lockout.
17. As a registered user, I want the same "Invalid username or password" message for a wrong password, an unknown username, a locked account and a disabled account, so that an attacker can't tell which one it was.
18. As a security-conscious operator, I want an account locked for 15 minutes after 5 consecutive failed logins, so that online password guessing against one account stops paying off.
19. As a locked-out user, I want to log in normally once the 15 minutes are up, so that a lockout never needs an admin to clear it.
20. As a locked-out user, I want failed attempts during the lockout not to extend it, so that an attacker can't keep me locked out for ever.
21. As a security-conscious operator, I want failed logins from one IP across many usernames throttled, so that password spraying from one source is blunted.
22. As a legitimate user, I want IP throttling kept separate from my account's lockout, so that an attacker failing my password from their own IP only throttles themselves.
23. As a throttled client, I want a `429` with a `Retry-After` header, so that a well-behaved client knows when to try again.
24. As a user on a throttled network, I want the message "Too many attempts. Please try again later.", so that I know it isn't my password.
25. As a security-conscious operator, I want a locked or disabled account's response to take about as long as a wrong password, so that timing doesn't reveal the account's state.

### Logout and sessions (PRD Story 4, NFR session security)

26. As a logged-in user, I want logout to keep working as it does today, so that nothing regresses.
27. As a user who reset my password, I want every one of my existing sessions ended, so that whoever knew my old password is signed out too.
28. As a user whose account an admin disabled, I want my existing sessions ended straight away, so that the suspension takes effect at once.
29. As a user whose role an admin changed, I want my existing sessions ended, so that the new role applies from my next login and no session keeps stale privileges.
30. As a user whose session was ended by the server, I want my next request to get a `401` and send me to the login page, so that I'm never shown a half-working app.

### Protected content (PRD Story 5)

31. As a logged-in user, I want `GET /api/v1/hello` to return `Hello, <username>` as JSON, so that the PRD's protected-content check exists.
32. As an anonymous visitor, I want `GET /api/v1/hello` to answer `401`, so that protected content is never served without a session.
33. As a logged-in user, I want the landing page to keep greeting me by first name, so that the existing experience is unchanged.

### Password reset (PRD Stories 6–7)

34. As a user who forgot my password, I want a "Forgot password?" link on the login page, so that I can find the reset flow.
35. As a user who forgot my password, I want to request a reset by email, so that I can get back in without an admin.
36. As a user who forgot my password, I want the same "If an account exists for that email, we've sent a reset link." message whether or not my email is registered, so that nobody can use the form to find out who has an account.
37. As a user with a reset link, I want to set a new password that meets the same policy as registration, so that a reset never weakens my account.
38. As a user with a reset link, I want the link to work only once, so that a copy of it from my inbox or history can't be reused.
39. As a user with a reset link, I want the link to expire after 30 minutes, so that an old email can't be used against me.
40. As a user who asked for more than one reset, I want only the newest link to work, so that earlier links in my inbox are harmless.
41. As a user with an expired, used or unknown link, I want "This reset link is invalid or has expired." and a way to request a new one, so that I'm not stuck.
42. As a user who reset my password, I want any lockout on my account cleared, so that I can log in with the new password straight away.
43. As a user who reset my password, I want to land on the login page with "Password updated. Please log in.", so that I know it worked.
44. As a security-conscious operator, I want only a hash of each reset token stored, so that a database leak doesn't hand out working reset links.
45. As a security-conscious operator, I want the reset token removed from the address bar and never sent in a `Referer` header, so that it doesn't leak through history, analytics or third-party requests.
46. As a security-conscious operator, I want reset requests throttled per IP, so that nobody can flood a user's inbox or use the endpoint to enumerate at scale.

### Admin module (PRD Stories 8–11)

47. As an admin, I want an "Admin" link in the navbar, so that I can reach user management.
48. As a non-admin user, I want no "Admin" link, so that the UI doesn't offer something I can't use.
49. As an admin, I want a table of all users with username, email, role, status and created date, so that I can see who has access.
50. As a security-conscious operator, I want the admin user list never to include password hashes, lockout counters or reset tokens, so that the admin API doesn't leak credentials.
51. As an admin, I want to disable and re-enable another user's account, so that I can suspend access without deleting data.
52. As an admin, I want to change another user's role between `USER` and `ADMIN`, so that I can grant or revoke admin rights.
53. As an admin, I want to delete another user's account after confirming, so that I can remove accounts that should no longer exist and don't delete one by mistake.
54. As an admin, I want the actions on my own row disabled, and rejected by the server if I try anyway, so that I can't lock myself out or leave the system with no admin.
55. As an admin, I want a clear error if an action fails (the user was already deleted, the server is unreachable), so that I know the change didn't happen.
56. As a non-admin user, I want every `/api/v1/admin/**` endpoint to answer `403`, so that admin functions are enforced on the server and not only hidden in the UI.
57. As a non-admin user who types `/admin/users` into the address bar, I want to be sent to the landing page, so that I never see a broken admin screen.
58. As an anonymous visitor, I want every admin endpoint to answer `401`, so that admin functions always need a session.

### Admin bootstrap (PRD Story 12)

59. As an operator deploying for the first time, I want an admin account created on startup from configuration, so that I can reach the admin module without editing the database.
60. As an operator, I want no duplicate admin created on restart, so that the seed is idempotent.
61. As an operator, I want startup to fail with a clear message when no admin exists and no bootstrap credentials are configured, so that I never deploy an app nobody can administer.
62. As an operator, I want the bootstrap password held to the same policy and hashed the same way as any other, so that the first admin isn't the weakest account.
63. As an operator, I want no default admin credentials outside the `dev` and `test` profiles, so that a known password never reaches production.

### Cross-origin, CSRF and headers (NFRs)

64. As an operator, I want the API to accept cross-origin requests only from configured frontend origins, with credentials, so that no other site can read API responses as a signed-in user.
65. As an operator, I want a preflight from a disallowed origin rejected, so that a misconfigured or malicious site gets a clear refusal.
66. As a security-conscious operator, I want CSRF protection kept on every state-changing endpoint (register, login, logout, reset request and confirm, admin mutations), so that cookie-based auth can't be abused cross-site.
67. As a developer, I want the SPA to get its CSRF token from the API's response body, so that CSRF keeps working when the SPA and API are on different hosts and the SPA can't read the API's cookies.
68. As a security-conscious operator, I want the SPA served with a strict Content-Security-Policy, so that injected markup can't run script even if an escaping bug slips through.
69. As a security-conscious operator, I want the SPA's `index.html` to have no inline script, so that the CSP needs no `'unsafe-inline'`.
70. As a security-conscious operator, I want HSTS on HTTPS responses in production, so that browsers never fall back to plain HTTP.

### XSS, injection and error handling

71. As a user, I want my first name shown exactly as I typed it, even if it contains `<`, `>` or quotes, so that my name is never mangled or run as code.
72. As an admin, I want usernames, emails and first names that look like HTML shown as plain text in the user list, so that another user can't attack me through their profile fields.
73. As a security-conscious operator, I want all database access to use parameterised queries, so that user input can't change a query's meaning.
74. As a security-conscious operator, I want every request body validated against an explicit shape and limits, so that oversized or malformed input is rejected before it reaches business logic.
75. As a security-conscious operator, I want every API error, including unknown paths, wrong methods, malformed JSON and unexpected exceptions, to answer with the standard JSON error body and no stack trace or internal detail, so that errors can't be used for reconnaissance.

### Audit logging (NFR)

76. As an operator, I want a structured audit log line for each login success and failure, lockout, throttle, registration, reset request and completion, admin action and admin bootstrap, so that I can investigate incidents.
77. As an operator, I want each audit line to name the event, the actor, the target (for admin actions), the client IP and the outcome, so that it answers "who did what to whom, from where".
78. As a security-conscious operator, I want passwords, reset tokens, CSRF tokens and session IDs never to appear in any log, so that logs aren't a source of secrets.
79. As a security-conscious operator, I want user-supplied values in logs neutralised (no raw CR/LF), so that nobody can forge log lines.

### Supply chain and quality gates

80. As a developer, I want a documented dependency vulnerability scan for the backend and frontend, so that known-vulnerable libraries are caught before release.
81. As a developer, I want the existing quality gates (JaCoCo ≥80%, Vitest coverage thresholds, `tsc`, Prettier, Vite build) to stay green, so that the hardening doesn't lower the bar.
82. As a developer, I want the e2e suite to run against a production build with the production CSP, so that a CSP violation fails the build and doesn't wait for production to find it.

## Implementation Decisions

### OWASP Top 10 (2025) control map

Every control below is binding. The rest of this section describes how each one is realised.

| OWASP category | Controls in this spec |
| --- | --- |
| A01 Broken Access Control | Deny by default (every route authenticated unless listed). `/api/v1/admin/**` requires `ROLE_ADMIN` in the filter chain, and the admin service also enforces it at method level. Admin self-action guard. Sessions expired on disable, role change and password reset. Request DTOs never bind entities, so `role`, `enabled` and the lockout fields can't be mass-assigned. Strict CORS allow-list. CSRF on every state-changing request. Frontend route guards are UX only. |
| A02 Security Misconfiguration | Existing API headers kept, plus HSTS in prod. A strict CSP and the other headers on the SPA. No stack traces or messages from Spring's default error handling. Dev seeds and dev admin credentials only in the `dev` and `test` profiles. No H2 console, no actuator. No CORS wildcard alongside credentials. |
| A03 Software Supply Chain Failures | Lockfiles committed. A documented scan (`npm audit --audit-level=high` for the frontend, the repo's dependency-vuln-scan gate for Maven) runs before merge. No runtime CDN scripts: everything is bundled. |
| A04 Cryptographic Failures | BCrypt for passwords, cost raised to 12 for new hashes (existing cost-10 hashes still verify). Reset tokens are 256 bits from `SecureRandom`, stored as SHA-256. `Secure` cookies and HSTS in any real deployment. |
| A05 Injection (including XSS) | Spring Data JPA derived or parameterised queries only. No string-built JPQL or SQL. Bean Validation on every request DTO. React's escaping, with no `dangerouslySetInnerHTML`, `innerHTML`, `eval`, `new Function` or user-controlled `href`/`src`. A strict SPA CSP without `'unsafe-inline'`, plus Trusted Types. API responses are always `application/json` with `nosniff`, so they can never be sniffed as HTML. Log-injection neutralisation. |
| A06 Insecure Design | Enumeration resistance on login and reset request. Throttling on login, registration and reset request. Lockout that can't be extended by an attacker. Single-use, short-lived, newest-only reset tokens. |
| A07 Authentication Failures | Password policy (12–64 chars, ≤72 UTF-8 bytes, not on a common-password list). Lockout. IP throttle. Session fixation protection (existing). 30-minute idle timeout (existing). Session invalidation on logout, reset, disable and role change. Generic errors. No default production credentials. |
| A08 Software or Data Integrity Failures | Jackson default typing stays off. Unknown JSON properties are ignored and never bound. No client-side state is trusted for authorisation: the role always comes from the server-side session principal. |
| A09 Logging & Alerting Failures | Audit events via a dedicated logger with a fixed event vocabulary. No secrets in logs. CR/LF neutralised. |
| A10 Mishandling of Exceptional Conditions | A catch-all JSON error handler (generic `500`). JSON `400`/`404`/`405`. Throttle and lockout fail closed. Admin bootstrap fails fast. SPA guards treat any failure as "no session" (existing). |

### Topology: cross-origin, as the PRD says

- **Origins.** The SPA runs on its own origin (dev: `http://localhost:3000`) and the API on its own (dev: `http://localhost:8080`). The Vite `/api` proxy is removed. The SPA reads the API base URL from build-time config (`VITE_API_BASE_URL`, default `http://localhost:8080`), and every request uses `credentials: "include"`.
- **CORS** is enabled in the security filter chain from one `CorsConfigurationSource`:
  - Allowed origins come from config (`app.cors.allowed-origins`, a list). They are exact origins: no wildcards, no `null`, no patterns. Dev and test default to `http://localhost:3000`. Prod has no default, and startup fails if the list is empty.
  - `allowCredentials: true`. Methods `GET, POST, PATCH, DELETE`. Allowed request headers `Content-Type, Accept, X-XSRF-TOKEN`. Preflight cache 1 hour.
  - A preflight or request from any other origin gets no `Access-Control-Allow-Origin`, and a preflight from one answers `403`.
- **Session cookie.** It stays `HttpOnly`, `SameSite=Strict`, `Secure` outside `dev`, host-only (no `Domain`). `localhost:3000` and `localhost:8080` are the same *site*, so `SameSite=Strict` still sends the cookie. The documented deployment rule is that the SPA and API must share a registrable domain (e.g. `app.example.com` and `api.example.com`). A deployment across different sites is unsupported.
- **CSRF across origins.** The SPA can no longer rely on reading the `XSRF-TOKEN` cookie, because in a real split-host deployment that cookie belongs to the API host.
  - `GET /api/v1/auth/csrf` now answers `200` with body `{"headerName": "X-XSRF-TOKEN", "token": "<raw token>"}`, and still sets the `XSRF-TOKEN` cookie. The cookie repository and the plain (unmasked) request handler stay as they are. The double-submit check still compares header against cookie.
  - The SPA keeps the token **in memory only**, never in `localStorage` or `sessionStorage`. It fetches one lazily before the first state-changing request. It drops the in-memory token after a successful login (the token rotates), after logout, and after a successful password-reset confirm, and it fetches a fresh one on the next state-changing request.
  - The CORS allow-list is what stops a hostile origin reading this response. Record that dependency in the security configuration's documentation.
- **Frontend dev server** moves to port `3000`, matching the PRD. `FRONTEND_PORT` and `BACKEND_PORT` overrides keep working for Playwright and dev.

### SPA hardening (XSS)

- **CSP for the SPA**, set as a response header by whatever serves the built SPA:
  `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self' <API origin>; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'; require-trusted-types-for 'script'`.
  - Define the policy once, in the frontend build config. Build `connect-src` from the same API base URL. Apply it through Vite's `preview` server headers, and document it in the README as the header a production static host must send.
  - Send it alongside `Referrer-Policy: no-referrer`, `X-Content-Type-Options: nosniff`, `Permissions-Policy: camera=(), geolocation=(), microphone=()` and `X-Frame-Options: DENY`.
  - If a bundled dependency turns out to break `style-src 'self'` (inline style attributes) or Trusted Types, relax only that directive (`style-src 'self' 'unsafe-inline'`, or drop `require-trusted-types-for`), and write down why in the README and in this spec's Comments. Never relax `script-src`.
  - The Vite **dev** server sends no CSP, because React Refresh injects an inline script. That is why the e2e suite runs against `vite preview` (see Testing).
- **No inline script in `index.html`.** The pre-paint theme script moves to a static same-origin file, loaded as a classic blocking `<script src>` in `<head>` so dark mode still never flashes. Add `<meta name="referrer" content="no-referrer">` as a belt-and-braces measure for the reset-token URL.
- **Rendering rules**, enforced in review and by the Semgrep gate:
  - All user-controlled data renders as JSX text.
  - No `dangerouslySetInnerHTML`, no direct `innerHTML`/`outerHTML`/`insertAdjacentHTML`, no `eval`/`new Function`/string `setTimeout`.
  - No `href` or `src` built from user data.
  - Navigation uses the router's typed routes only.
- **Storage.** No auth data (profile, role, CSRF token, reset token) goes into web storage. `localStorage` holds only the theme choice, as today.

### Data model

A new Flyway migration evolves `user_account`. The table name stays as it is and the PRD's `users` columns map onto it:

| Column | Type | Notes |
| --- | --- | --- |
| id | bigint identity | existing PK |
| username | varchar(50), unique | existing. Stored lowercase from now on. The migration lowercases existing rows. |
| email | varchar(254), unique | new, stored trimmed and lowercased. The migration backfills the dev seed. |
| first_name | varchar(100) | existing, kept |
| password_hash | varchar(100) | existing, BCrypt |
| role | varchar(10), `USER`/`ADMIN`, default `USER` | new |
| enabled | boolean, default true | new |
| failed_login_attempts | int, default 0 | new |
| locked_until | timestamp with time zone, nullable | new |
| created_at | timestamp with time zone | new. The migration backfills existing rows with the migration time. |

New table `password_reset_token`: `id` (bigint identity PK), `user_id` (FK to `user_account`, `ON DELETE CASCADE`), `token_hash` (char(64), unique, SHA-256 hex), `expires_at`, `used_at` (nullable), `created_at`.

- The dev seed (`johndoe`) gains `email = johndoe@example.com` and role `USER`.
- The schema stays portable (no H2-only syntax in versioned migrations). The existing seed's `MERGE … KEY` is dev-only and stays as is.

### Backend modules

- **Clock seam.** A single `java.time.Clock` bean (system UTC), injected wherever "now" matters: lockout, throttling, reset-token expiry, `created_at`. Tests replace it with a controllable clock. This is the only new seam.
- **Account module (user package).**
  - `UserAccount` gains the new fields and domain methods for the lockout transitions: register a failure, which may lock; register a success, which resets; check whether the account is locked at an instant.
  - `AccountUserDetails` carries `ROLE_USER` or `ROLE_ADMIN` as its authority and the `enabled` flag. It stays `Serializable`.
  - The repository gains case-insensitive lookups by username and email (on normalised values) and a lookup by role.
- **Password policy.** One component shared by registration, reset confirm and admin bootstrap:
  - 12–64 characters and ≤72 UTF-8 bytes (BCrypt's limit).
  - Not on a bundled common-password list. Use a top-10k list as a classpath resource, compared case-insensitively.
  - No composition rules (NIST 800-63B).
  - It reports a field error and never echoes the password back.
- **Login and lockout.**
  - `POST /api/v1/auth/login` keeps its contract. A lookup is now case-insensitive.
  - Lockout: 5 consecutive failures (`app.security.lockout.max-failures`) set `locked_until = now + 15m` (`app.security.lockout.duration`).
  - A locked or disabled account is rejected with the existing `401 INVALID_CREDENTIALS` body.
  - The password comparison always runs, even for locked or disabled accounts, and so does the dummy-hash comparison for unknown users (already done by `DaoAuthenticationProvider`). This keeps timing uniform: the lock and enabled checks run **after** the password check.
  - Failures while locked neither increment the counter nor extend the lock.
  - Success resets `failed_login_attempts` to 0 and clears `locked_until`.
  - The failure counter update runs in its own transaction, so it survives the rejected login.
- **IP throttle.** An in-memory, single-instance limiter keyed by client IP, with a bounded size (e.g. at most 100k keys, evicting the oldest) so that it can't be used for memory exhaustion.
  - **Login:** at most 20 failed logins per IP per 15 minutes. It counts failures only, so a NAT full of legitimate users isn't punished for successes.
  - **Registration and reset request:** at most 10 requests per IP per 15 minutes each.
  - While throttled, the endpoint answers `429` with `code: "TOO_MANY_REQUESTS"`, message `"Too many attempts. Please try again later."` and a `Retry-After` header in seconds. For login this happens **before** credentials are checked, even when they are correct. Account state is not touched.
  - Client IP is `request.getRemoteAddr()`. In prod, the existing `forward-headers-strategy: native` resolves `X-Forwarded-For` only through Tomcat's trusted-proxy handling. The app never parses `X-Forwarded-For` itself.
  - The limits are configuration properties under `app.security.throttle.*`.
- **Registration.** `POST /api/v1/auth/register`, anonymous, CSRF-protected.
  - Body: `{username, email, firstName, password}`.
    - `username`: 3–50 chars, `[A-Za-z0-9._-]`, stored lowercase.
    - `email`: valid format, ≤254, stored trimmed and lowercased.
    - `firstName`: 1–100 chars after trimming, no control characters. Otherwise any Unicode, stored verbatim; encoding happens at output.
    - `password`: the policy above.
  - The request DTO has no `role` or `enabled`, and unknown JSON properties are ignored, so a `"role": "ADMIN"` in the body has no effect.
  - Success: `201`, body is the new `UserProfile`. **No session is created.**
  - Validation failure: `400 VALIDATION_FAILED` with `fieldErrors`.
  - A taken username or email: `409 ACCOUNT_CONFLICT` with `fieldErrors` naming the conflicting field(s), as the PRD requires. The enumeration this allows is accepted and mitigated by the registration throttle.
- **Protected content.** `GET /api/v1/hello` answers `200 {"message": "Hello, <username>"}` (JSON, never `text/plain` or `text/html`) or `401`. `/me` now also returns `role`.
- **Password reset.**
  - `POST /api/v1/auth/password-reset/request` takes body `{email}` and always answers `202` with an empty body, whatever the input (a malformed email included, once it passes the size limit).
    - If the email matches an **enabled** account, mark any unused tokens for that user as used, create a new token, and call `EmailService.sendPasswordResetEmail(email, link)`.
    - The link is `<app.frontend-url>/reset-password#token=<token>`. The token goes in the URL **fragment**, which browsers never send to servers or in `Referer`.
    - The stub `EmailService` logs the link on its own non-audit logger at `INFO`. It is the only place a token is ever logged, and it exists only because real email is out of scope.
  - `POST /api/v1/auth/password-reset/confirm` takes body `{token, newPassword}`.
    - Valid, unexpired and unused: set the new BCrypt hash, mark the token used, reset the lockout fields, expire every session of that user, and answer `204`.
    - Unknown, expired or used tokens all get the same `400 INVALID_RESET_TOKEN`, message `"This reset link is invalid or has expired."`.
    - A policy failure gets `400 VALIDATION_FAILED` and does **not** consume the token.
  - Token expiry: 30 minutes (`app.security.password-reset.token-ttl`).
- **Session registry.**
  - Register Spring Security's in-memory `SessionRegistry` together with the HTTP session event publisher, and apply it through `sessionManagement` (unlimited concurrent sessions).
  - Password reset, disable, role change and delete call one "expire all sessions for user X" operation.
  - An expired session's next request gets the standard JSON `401 UNAUTHORIZED`, never a redirect.
  - This deliberately does **not** adopt Spring Session (see Further Notes).
- **Admin module** (new `admin` package, all under `/api/v1/admin`; `ROLE_ADMIN` required in the filter chain and on the service):
  - `GET /users` → `200`, a list of `{id, username, email, firstName, role, enabled, createdAt}`, sorted by `createdAt`. It never includes the hash, the lockout fields or tokens.
  - `PATCH /users/{id}/status`, body `{enabled: boolean}` → `200` with the updated user row. Disabling expires that user's sessions.
  - `PATCH /users/{id}/role`, body `{role: "USER" | "ADMIN"}` → `200` with the updated row. An unknown role gets `400 VALIDATION_FAILED`. A change expires that user's sessions.
  - `DELETE /users/{id}` → `204`. It expires that user's sessions, and their reset tokens cascade.
  - A target that is the caller's own account gets `409 SELF_ACTION_NOT_ALLOWED` on status, role and delete. Because an admin can never demote, disable or delete themselves, at least one admin always remains, so no separate last-admin rule is needed.
  - An unknown id gets `404 USER_NOT_FOUND`. A non-admin gets `403 FORBIDDEN`. Anonymous gets `401`. All are CSRF-protected.
- **Admin bootstrap.** An application runner that starts after Flyway.
  - If no `ADMIN` exists, it creates one from `app.admin.username`, `app.admin.email` and `app.admin.password`, run through the same normalisation, password policy and encoder as registration.
  - If any property is missing, or the password fails the policy, while no admin exists, startup fails with a clear message that never contains the password.
  - If an admin already exists, it does nothing, even when the properties are set.
  - The `dev` and `test` profiles supply documented, dev-only values (username `admin`, a ≥12-character password that is not on the common list, listed in the README next to `johndoe`). The base and `prod` configs supply none and read them from the environment.
- **Audit log.** An `AuditLog` component logs to a dedicated `AUDIT` logger through the SLF4J key-value API.
  - Fixed event names: `LOGIN_SUCCESS`, `LOGIN_FAILURE`, `ACCOUNT_LOCKED`, `LOGIN_THROTTLED`, `REQUEST_THROTTLED`, `LOGOUT`, `USER_REGISTERED`, `PASSWORD_RESET_REQUESTED`, `PASSWORD_RESET_COMPLETED`, `PASSWORD_RESET_REJECTED`, `USER_ENABLED`, `USER_DISABLED`, `USER_ROLE_CHANGED`, `USER_DELETED`, `ADMIN_SELF_ACTION_REJECTED`, `ADMIN_BOOTSTRAPPED`.
  - Fields: `event`, `actor` (username or `anonymous`), `target` (for admin actions), `ip`, `outcome`, plus event-specific fields (e.g. old and new role).
  - For `PASSWORD_RESET_REQUESTED` with an unknown email, the target is logged as `unknown`. The submitted email is not logged, which avoids building an address list in the logs.
  - Every user-supplied value has CR/LF and other control characters replaced before logging.
  - Outside `dev`, Spring Boot's structured console logging (ECS JSON) is on, so every line, audit or not, is machine-parseable and escaped.
- **Error handling** (`ApiExceptionHandler` and the security JSON handler).
  - `ApiError` gains an optional `fieldErrors: [{field, message}]`, left out when empty. The existing shape is otherwise unchanged.
  - New mappings:
    - Malformed or unreadable JSON: `400 MALFORMED_REQUEST`.
    - Unsupported media type: `415`.
    - Unknown path: `404 NOT_FOUND`.
    - Wrong method: `405 METHOD_NOT_ALLOWED`.
    - Any other exception: `500 INTERNAL_ERROR`, `"Something went wrong"`, with the exception logged server-side only.
  - `server.error.include-message`, `include-stacktrace` and `include-binding-errors` are set to `never`, and the whitelabel page is off.
  - Request bodies are capped at 16 KB.
- **Headers.** The existing API headers stay unchanged. HSTS (`max-age=31536000; includeSubDomains`) is written on secure requests. That is Spring Security's default, now asserted in the prod profile test.

### Frontend modules

- **API client.**
  - Uses the configured base URL, `credentials: "include"` and an in-memory CSRF token (above).
  - `RequestOptions.method` gains `PATCH` and `DELETE`.
  - `ApiError` gains `code` and `fieldErrors` from the response body.
  - The kinds become `unauthorized` (401), `throttled` (429), `rejected` (other 4xx) and `unavailable` (5xx or network).
  - `withCsrfRetry` now retries **only on `403`**. Today it retries on any `rejected`, which would wrongly resend a `400`, `409` or `429`.
- **Auth API module.** Adds `register`, `requestPasswordReset`, `confirmPasswordReset` and `fetchHello` (unused by the UI). `UserProfile` gains `role`.
- **Admin API module.** Adds `listUsers`, `setUserEnabled`, `setUserRole` and `deleteUser`, plus TanStack Query options for the user list. Each mutation invalidates the list on success.
- **Routes** (TanStack Router, guards reuse the existing `currentSession` helper):
  - `/register`, `/forgot-password` and `/reset-password` are anonymous-only: an existing session redirects to `/`.
  - `/admin/users` needs a session with `role === "ADMIN"`. No session redirects to `/login`, and a non-admin is redirected to `/`.
  - `/` and `/login` are unchanged, except that `/login` can show a one-shot success notice ("Account created. Please log in." or "Password updated. Please log in."). The notice is passed as a typed search param from a fixed enum, never as free text.
- **Pages.**
  - Login page gains "Create an account" and "Forgot password?" links, and the `throttled` message.
  - Registration page: username, email, first name, password and confirm password. Validation on submit, reusing `FormField`. Server `fieldErrors` are mapped onto fields.
  - Forgot-password page: email field, then the generic confirmation.
  - Reset-password page: reads the token from `location.hash` on mount, then immediately removes the hash from the URL (`history.replaceState`) and keeps the token in component state. Fields for the new password and its confirmation. An invalid token shows the error with a link to `/forgot-password`.
  - Admin users page: a semantic `<table>` with caption and column headers. Per row: an enable/disable toggle button, a role select or button, and a delete button behind a confirmation dialog (shadcn/Radix `AlertDialog`). Controls on the caller's own row are disabled, with an explanation. Failures show the shared `ErrorAlert`.
- **Navbar.** The existing shell shows an "Admin" link next to Log out only when the cached profile's role is `ADMIN`.

### Seams (test boundaries)

Reuse the existing seams. The only new one is the `Clock` bean.

1. **Backend HTTP seam.** `@SpringBootTest` with MockMvc over the real filter chain and H2, driven through `SpaAuthFlow`, which is extended to read the CSRF token from the `/csrf` body and to register and log in arbitrary users. Real HTTP (`RestClient`, as in the session cookie test) only where MockMvc can't observe it: cookie attributes, CORS preflight, and session expiry through the container.
2. **Clock seam.** A test configuration supplies a controllable `Clock`, so tests can move time past lockout, throttle windows and token expiry without sleeping.
3. **Frontend app seam.** `renderApp` with MSW through the real router. Tests act as a user and assert on the URL, visible text, ARIA state and outbound requests.
4. **E2E seam.** Playwright against the real backend (`dev` profile) and the **production build served by `vite preview`** with the production CSP headers.

## Testing Decisions

- **What makes a good test.** Assert only on externally observable behaviour.
  - At the API seam: status, JSON body, headers, `Set-Cookie`, audit log output (captured with Spring Boot's output capture), and follow-up requests that prove the effect (e.g. an old session's `/me` is `401`).
  - At the UI seam: visible text, roles, ARIA state, the URL and the requests sent.
  - Nothing asserts on repositories, bean wiring, hooks or component internals. Where a test needs database state (e.g. "the password is stored as BCrypt"), it proves it through the API: log in with the new password, and the old one fails.
- **Backend test classes** (prior art: `LoginApiTest`, `LogoutApiTest`, `SessionCookieTest`, `SecurityHeadersTest`, `ProdProfileTest`, `SpaAuthFlow`):
  - Registration:
    - `201` with no session.
    - Can log in afterwards.
    - Role is `USER` even when the body says `ADMIN`.
    - Duplicate username or email, case-insensitive: `409` with the field named.
    - A short, common or >72-byte password: `400` with the field named.
    - An HTML/script payload in `firstName` is stored and returned verbatim, as JSON.
    - The plaintext password never appears in captured logs.
    - The 11th request from one IP inside the window gets `429`.
  - Login and lockout (the PRD's required login and lockout coverage):
    - Success.
    - A wrong password and an unknown username get identical bodies.
    - The 5th failure locks the account. The correct password while locked gets the identical `401`.
    - Failures while locked don't extend the lock.
    - After the clock moves past 15 minutes, the correct password succeeds and the counter is reset: 4 more failures don't lock.
    - A disabled account gets the identical `401`.
  - IP throttle:
    - 20 failures across many usernames from one IP lead to `429` with `Retry-After`, even with correct credentials, while the target account is **not** locked.
    - Another IP is unaffected.
    - The window resets when the clock moves past it.
  - Logout: the existing tests stay green, plus a new one: a replayed cookie after logout gets `401` (already covered, keep).
  - Protected content: `/hello` returns `200` with the username, and `401` anonymously.
  - Password reset (the PRD's required reset coverage):
    - The request gets an identical `202` for known, unknown and disabled emails. The stub is called only for the known, enabled one (a test `EmailService` captures the link).
    - Confirm with the captured token: `204`, the new password works, the old one fails.
    - The same token again: `400 INVALID_RESET_TOKEN`.
    - A token past 30 minutes on the clock: `400`, and the password is unchanged.
    - An older token after a newer request: `400`.
    - A policy failure doesn't consume the token.
    - An existing session gets `401` from `/me` after the reset.
    - The lockout is cleared by a reset.
  - Admin (the PRD's required self-guard and role-enforcement coverage):
    - A `USER` gets `403` on every `/api/v1/admin/**` endpoint and method. Anonymous gets `401`.
    - The list has no `password`, `passwordHash` or lockout fields.
    - Disable: that user's live session gets `401`, and they can't log in. Re-enable: they can.
    - Role change: takes effect after the target re-logs in, and their old session gets `401`.
    - Delete: `204`, then that user can't log in.
    - Self disable, self demote and self delete each get `409`, with state unchanged.
    - An unknown id gets `404`.
    - Every mutation without CSRF gets `403`.
  - Admin bootstrap:
    - With no admin and the properties set, one admin exists and can log in.
    - A restart (a second context with the same DB) doesn't duplicate it.
    - Missing properties with no admin fail the context start.
    - A weak bootstrap password fails the context start.
  - CORS:
    - A preflight from the allowed origin gets the allow headers, including credentials.
    - A preflight from another origin gets `403` and no allow-origin.
    - An actual request from another origin has no allow-origin header.
  - CSRF: `/csrf` returns the token in the body and the cookie, and the body token works as the header.
  - Errors:
    - Malformed JSON gets `400 MALFORMED_REQUEST`.
    - An unknown path gets `404` JSON.
    - A wrong method gets `405` JSON.
    - A forced unexpected exception gets `500 INTERNAL_ERROR` with no stack trace or exception class in the body.
    - An over-limit body is rejected.
  - Audit:
    - Each event in the vocabulary appears with the expected fields for its trigger.
    - A username containing `\r\n` is logged on one line.
    - Passwords and tokens never appear.
  - Headers: the prod profile emits HSTS on an HTTPS request.
  - JaCoCo stays ≥80%.
- **Frontend tests** (prior art: `LoginPage.test.tsx`, `LoginPage.validation.test.tsx`, `LandingPage.test.tsx`, `router.test.tsx`, `AppShell.test.tsx`, `client.test.ts`, MSW `handlers.ts`):
  - API client:
    - Credentials are included.
    - The CSRF token is fetched once and sent as the header, and dropped after login and logout.
    - Retry happens only on `403`, not on `400`, `409` or `429`.
    - `429` maps to `throttled`.
    - `fieldErrors` and `code` are parsed.
  - Registration page:
    - Happy path ends on `/login` with the notice.
    - A `409` maps to the right field.
    - Mismatched confirmation is caught on submit.
    - An authenticated user is redirected to `/`.
  - Forgot and reset pages:
    - The generic message is shown for any response.
    - The token is read from the hash and the hash is removed from the URL.
    - An invalid token shows the message and a link.
    - Success ends on `/login` with the notice.
  - Login page: a `429` shows the throttle message.
  - Admin page:
    - The admin sees the table.
    - Own-row controls are disabled.
    - Each action sends the right request and refreshes the list.
    - Delete needs confirmation.
    - A failure shows `ErrorAlert`.
    - A non-admin navigating to `/admin/users` ends on `/`.
    - The navbar shows "Admin" only for admins.
  - XSS rendering: MSW returns a `firstName` of `<img src=x onerror=alert(1)>`. The landing page and the admin table show that literal text, and no `img` element is rendered.
  - Coverage thresholds, `tsc`, Prettier and the Vite build stay green.
- **E2E** (prior art: the Story 1–3 specs and `e2e/support.ts`). New spec files, one per PRD area, with one test per acceptance scenario in Further Notes. Playwright's `webServer` for the frontend changes to build and then `vite preview` on `FRONTEND_PORT` (default `3000`).
  - A shared fixture fails any test on a `securitypolicyviolation` event or a CSP console error.
  - The reset link is read from the backend's stub log output, which Playwright captures from the backend `webServer`, or from a dev-only test hook if that turns out to be unreliable. Record which one in the ticket.
  - Throttle and lockout e2e scenarios use a fresh, uniquely named registered user per test, so the tests stay independent under `fullyParallel`. The IP throttle is exercised at the API seam only, because every e2e test shares one IP.

## Out of Scope

- Everything the PRD excludes: JWT (the design stays in the PRD appendix), MFA, real SMTP (only the logging `EmailService` stub), containerisation/CI/CD/hosting, local HTTPS, and per-resource authorisation beyond `USER`/`ADMIN`.
- Spring Session and any shared store for sessions or throttle counters. Both are in-memory, single instance.
- CAPTCHA, device fingerprinting and breached-password lookups against an external API (e.g. HIBP). The bundled common-password list is the only check.
- Email verification on registration and email-change flows.
- Self-service profile editing and "change password while signed in".
- Admin-created users, admin password resets for other users, editing another user's email or name, and pagination, search or sorting in the admin list.
- Manual admin unlock of a locked account. The lockout expires on its own, and a password reset clears it.
- Cross-tab session sync and idle-timeout warnings.
- A WAF, bot management or IP allow-lists.
- Frontend CSP in the Vite dev server.

## Further Notes

### Deviations from the PRD (recorded on purpose)

- **Paths are versioned.** `/api/hello` becomes `/api/v1/hello` and `/api/admin/users` becomes `/api/v1/admin/users`, to match the existing `/api/v1/auth` contract.
- **`first_name` is kept** and registration collects it, so the existing "Hello, John!" landing page and e2e contract keep working. `/api/v1/hello` still returns the PRD's `Hello, <username>`.
- **No Spring Session.** The container `HttpSession` plus Spring Security's in-memory `SessionRegistry` gives "invalidate all sessions for a user" without changing the cookie name (`JSESSIONID`) and the logout and cookie tests already built around it. Revisit when the app runs on more than one instance.
- **Usernames are case-insensitive** (stored lowercase). The PRD is silent on case. This prevents look-alike accounts.
- **The registration conflict error** reveals whether a username or email exists, as the PRD requires. This is the one deliberate enumeration surface. It is mitigated by per-IP throttling and listed as residual risk.
- **The reset link carries the token in the URL fragment** rather than the query string, so it never reaches server logs or `Referer`.

### Acceptance scenarios (for the e2e suite and review)

These continue the story numbering of the existing e2e suite: Stories 1–3 are login, session and guards, and logout.

**Story 4: Registration**
1. A visitor registers with valid details, lands on `/login` with "Account created. Please log in.", logs in and sees "Hello, <first name>!".
2. A visitor registers with the taken username `johndoe` and sees a username error, and no account is created.
3. A visitor submits an 8-character password and sees the password policy error.
4. A visitor whose first name is `<img src=x onerror=alert(1)>` registers and logs in. The greeting shows that literal text, no dialog opens, and no CSP violation occurs.

**Story 5: Lockout**
1. A registered user fails login 5 times. A 6th attempt with the correct password shows "Invalid username or password".
2. (API seam only, because of time travel) After the cooldown, the correct password succeeds.

**Story 6: Password reset**
1. A user requests a reset for their email and sees the generic message. They follow the logged link, set a new password, land on `/login` with "Password updated. Please log in.", and log in with the new password.
2. A request for an unregistered email shows the same generic message.
3. Reusing a link that has already been used shows "This reset link is invalid or has expired."
4. A user signed in in one browser context resets their password from another. The first context's next navigation lands on `/login`.

**Story 7: Admin**
1. The bootstrap admin logs in, sees the "Admin" link and the user list including `johndoe`, and sees no password data.
2. The admin disables a freshly registered user. That user can no longer log in. The admin re-enables them and they can.
3. The admin promotes a user to `ADMIN`. After that user logs in again, they see the "Admin" link.
4. The admin deletes a user after confirming, and the row disappears.
5. The admin's own row has its controls disabled.
6. `johndoe` has no "Admin" link, and opening `/admin/users` directly lands on `/`.
7. A user registered with an HTML-looking first name shows up as literal text in the admin table.

**Cross-cutting**
- The document served to the browser carries the production CSP header, and no test in the whole suite triggers a CSP violation.

### Other notes

- The repo has no `CONTEXT.md` or ADRs. This spec uses the PRD's role names (visitor, user, admin, operator) and the earlier specs' terms (session, landing page, banner/alert). Registering "operator", "lockout", "throttle" and "reset token" in a glossary would be worth doing through `/domain-modeling`.
- `backend/` is mid-rename from `simplelogin` to `demo_app` in the working tree. Land that before starting these tickets.
- Suggested ticket slicing for `/to-tickets`. Each is a vertical slice with its own tests:
  1. Clock seam, error handling and headers hardening, and CSRF token in the `/csrf` body.
  2. Cross-origin topology (CORS, the SPA API base URL, port 3000, the in-memory CSRF token, retry only on `403`).
  3. SPA CSP, the external theme script, and e2e on `vite preview` with the CSP-violation fixture.
  4. Schema migration, roles in the principal, `/me` role, and `/hello`.
  5. Audit log.
  6. Registration (API and UI).
  7. Lockout.
  8. IP throttle (API and the login UI message).
  9. Password reset (API and UI).
  10. Session registry, then the admin API.
  11. Admin UI and navbar link.
  12. Admin bootstrap.
  13. The e2e Stories 4–7.
