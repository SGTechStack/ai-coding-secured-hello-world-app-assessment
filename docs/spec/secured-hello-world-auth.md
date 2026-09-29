# Spec: Secured Hello World Auth (React + Spring Boot)

> Status: draft, ready-for-agent. Not yet published to the issue tracker (tracker setup pending — see Further Notes). Vocabulary follows [CONTEXT.md](../../CONTEXT.md); session-storage rationale in [ADR 0001](../adr/0001-spring-session-jdbc.md).

## Problem Statement

A Visitor needs a way to create an Account and prove who they are before they can see protected content, and an Admin needs a way to see and manage who has access — without anyone being able to guess whether a given username or email exists, brute-force a password, or keep using a Session after logging out. Today none of this exists: the repository contains only the PRD. We need a runnable, demonstrably secure username/password login application that an operator can stand up, log into as an Admin, and hand to Users.

## Solution

A React single-page app (its own origin) talks to a Spring Boot REST API (its own origin) over CORS with credentialed cookies. A Visitor registers with username, email, and a password of at least 12 characters; the password is stored only as a BCrypt hash. Logging in establishes a server-side **Session** referenced by a secure `HttpOnly` cookie (Spring Session JDBC). An authenticated User sees a personalized greeting at `GET /api/hello`. Repeated failures trigger **Account Lockout** (per-Account) and **IP Throttling** (per-source-address) independently, so an attacker cannot lock out a victim from a single source. A User who forgets their password requests a reset by email (delivery stubbed), receives a single-use **Reset Token**, and sets a new password — which invalidates all of that User's Sessions. An Admin can list Accounts, enable/disable them, change roles, and delete them, but never against their own Account. An initial Admin is seeded on first startup. Every security-relevant action emits a structured **Audit Event** that never contains secrets.

## User Stories

1. As a Visitor, I want to register with a username, email, and password, so that I can obtain an Account and log in.
2. As a Visitor, I want registration to reject a password shorter than 12 characters, so that my Account starts with a policy-compliant credential.
3. As a Visitor, I want registration rejected with a clear conflict error when my username is already taken, so that I know to choose another.
4. As a Visitor, I want registration rejected with a clear conflict error when my email is already registered, so that I know the email is in use.
5. As a Visitor, I want my new Account created with role `USER`, `enabled = true`, and a BCrypt password hash, so that I have a standard, usable Account.
6. As a Visitor, I want my plaintext password never logged or stored, so that a leak of logs or database rows cannot expose it.
7. As a registered User, I want to log in with my username and password, so that I get a Session and can reach protected content.
8. As a User, I want a successful login to reset my failed-attempt counter to zero, so that past failures don't count against me once I prove myself.
9. As a User, I want login failures to return a single generic error regardless of whether the username exists, the password is wrong, or my Account is locked, so that no one can infer account existence or state (Enumeration Resistance).
10. As a User whose Account is locked, I want login rejected even with the correct password until the lockout expires, so that lockout actually protects me.
11. As a security-conscious operator, I want an Account locked for a cooldown after N consecutive failed logins, so that per-Account brute force is blunted.
12. As a User, I want a successful login after the cooldown elapses to succeed and reset my counter, so that lockout is temporary, not permanent.
13. As a security-conscious operator, I want failed logins from one IP across many usernames to trigger IP Throttling independent of any single Account's lockout, so that an attacker cannot lock out a legitimate User from one source.
14. As a logged-in User, I want to log out, so that my current Session is invalidated and its cookie cleared.
15. As a security-conscious operator, I want a Session cookie captured before logout to be rejected after logout, so that a stolen cookie is worthless once its owner logs out.
16. As a logged-in User, I want `GET /api/hello` to return `Hello, <username>`, so that I can confirm my authentication worked.
17. As a Visitor or User with no valid Session, I want `GET /api/hello` to return 401, so that protected content is never served unauthenticated.
18. As a User who forgot my password, I want to request a reset by email and always get a generic success response, so that account existence cannot be inferred from the reset flow (Enumeration Resistance).
19. As a User whose email is registered, I want a single-use Reset Token generated, its hash stored with a short expiry, and the reset email "sent" (stub logs the link), so that I can regain access.
20. As a User with a valid, unexpired, unused Reset Token, I want to set a new policy-compliant password, so that I regain access.
21. As a User who resets my password, I want the Reset Token marked used and all my existing Sessions invalidated, so that the reset both consumes the token and logs out every device.
22. As a security-conscious operator, I want an expired Reset Token rejected without changing the password, so that stale tokens are useless.
23. As a security-conscious operator, I want an already-used Reset Token rejected on a second attempt, so that single-use is enforced.
24. As a User who just reset my password, I want to not be auto-authenticated, so that I log in fresh after all Sessions were invalidated.
25. As an Admin, I want to list all Accounts with username, email, role, enabled status, and created-at — never password hashes — so that I can review who has access.
26. As a non-admin User, I want `GET /api/admin/users` (and every `/api/admin/**` endpoint) to return 403, so that least privilege is enforced server-side.
27. As a Visitor with no Session, I want `/api/admin/**` to return 401, so that admin surfaces are never exposed to anonymous callers.
28. As an Admin, I want to enable or disable another Account, so that I can suspend access without deleting data.
29. As a security-conscious operator, I want a Disabled Account unable to log in, so that disabling actually revokes access.
30. As an Admin, I want to be blocked (409) from disabling my own Account, so that I cannot lock myself out.
31. As an Admin, I want to change another Account's role between `USER` and `ADMIN`, so that I can grant or revoke admin privileges.
32. As an Admin, I want to be blocked (409) from changing my own role, so that I cannot accidentally demote myself.
33. As an Admin, I want to delete another Account, so that I can remove Accounts that should no longer exist.
34. As an Admin, I want to be blocked (409) from deleting my own Account, so that I cannot remove myself.
35. As an operator deploying for the first time, I want an initial Admin seeded from configuration when no Admin exists, so that I can get into the admin module without editing the database.
36. As an operator restarting the app, I want no duplicate Admin seeded when one already exists, so that seeding is idempotent.
37. As a React SPA, I want to fetch the CSRF token/cookie before any state-changing request, so that the double-submit CSRF flow works on a cold page load.
38. As a React SPA, I want a `GET /api/auth/me` endpoint, so that I can restore the authenticated User's state after a refresh without re-login.
39. As a security-conscious operator, I want every state-changing endpoint protected by CSRF, so that cookie-based auth is not abusable cross-site.
40. As a security-conscious operator, I want structured Audit Events for login success/failure, lockout, IP throttle, reset requested/completed, and admin mutations (actor + target), with no secrets, so that security actions are traceable.
41. As a User, I want a functional UI for register, login, hello, forgot-password, reset-password, so that the whole flow is demoable end-to-end.
42. As an Admin, I want a functional UI listing Accounts with controls to enable/disable, change role, and delete, so that I can manage access without calling the API by hand.

### Security hardening (approved additions, beyond the PRD baseline)

43. As a security-conscious operator, I want disabling an Account to immediately terminate that user's existing Sessions, so that a Disabled Account cannot keep using a live Session — not just be blocked at next login.
44. As a security-conscious operator, I want changing an Account's role to immediately terminate that user's existing Sessions, so that a downgraded Admin cannot retain `ADMIN` authority through a stale Session.
45. As a security-conscious operator, I want failed-login counting and Account Lockout to be race-safe under concurrent attempts, so that parallel guesses against one Account cannot bypass the lockout threshold via lost updates.
46. As a security-conscious operator, I want Reset Tokens generated from a cryptographically secure random source with at least 256 bits of entropy, so that a token cannot be guessed or brute-forced.
47. As a security-conscious operator, I want password-reset requests rate-limited per source IP, so that reset-request flooding is prevented — without revealing whether any email is registered (Enumeration Resistance preserved).
48. As a security-conscious operator, I want no authentication secret (password, password hash, Reset Token, token hash, session identifier) to ever appear in any API response, `ProblemDetail` body, application log, or Audit Event, so that secrets cannot leak through any output channel.
49. As a security-conscious operator, I want admin endpoints to authorize strictly from the authenticated principal — never trusting a client-supplied id or frontend state for privilege or self-identity — so that IDOR/BOLA and authorization-bypass attacks fail.
50. As a security-conscious operator, I want login to take constant work whether or not the username exists, so that response timing cannot be used to enumerate accounts even though the 401 bodies are already identical.
51. As a security-conscious operator, I want the app to ship with no usable default admin password and to fail startup in a non-dev profile when the admin password is missing or a known placeholder, so that a deployment cannot be taken over via source-visible default credentials.
52. As a security-conscious operator, I want password-reset links built from a configured frontend base URL rather than the request `Host` header, so that Host-header injection cannot poison the reset link and exfiltrate a token.
53. As a security-conscious operator, I want baseline security response headers, an explicit session idle timeout, and error responses that never leak stack traces, so that clickjacking, cache leakage of sensitive responses, and internal-detail disclosure are prevented.

## Implementation Decisions

### Repository & stack
- Monorepo: `backend/` (Spring Boot 3.5.x, Maven, Java 21) and `frontend/` (React 19 + TypeScript, Vite, Node 20+).
- Backend layered as controller → service → repository (Spring Data JPA) over H2 in the dev profile; schema kept portable to Postgres/MySQL.
- Session persistence via **Spring Session JDBC** on the same datasource (see ADR 0001). Adds the `SPRING_SESSION*` schema.

### Data model
- `users`: `id` (UUID, PK), `username` (unique), `email` (unique), `password_hash` (BCrypt), `role` (enum `USER`/`ADMIN`), `enabled` (boolean), `failed_login_attempts` (int), `locked_until` (timestamp, nullable), `created_at` (timestamp).
- `password_reset_tokens`: `id` (UUID, PK), `user_id` (FK → users), `token_hash` (string — hash only, never plaintext), `expires_at` (timestamp), `used_at` (timestamp, nullable).

### Authentication & session
- Custom `POST /api/auth/login` controller delegates to `AuthenticationManager.authenticate()`; the controller/service owns failed-attempt increment/reset, Account Lockout check, IP Throttling check, generic-error shaping, Audit Events, and explicit session-fixation protection (`changeSessionId`). `UserDetails.isAccountNonLocked()` derives from `locked_until`; `isEnabled()` derives from `enabled`.
- Passwords hashed with `BCryptPasswordEncoder`. Password policy: length ≥ 12, max 72 (BCrypt byte cap); no complexity classes.
- Logout invalidates the current Session and clears the cookie. Password reset invalidates **all** of the User's Sessions via `FindByIndexNameSessionRepository.findByPrincipalName(username)` → delete each.

### Abuse controls
- Account Lockout: after 5 consecutive failed logins, set `locked_until` to now + 15 min. Configurable under `app.security.*`.
- IP Throttling: in-memory Caffeine cache keyed by client IP (`request.getRemoteAddr()`; `X-Forwarded-For` deliberately not trusted), threshold ~20 failures / 15-min rolling window, independent of Account Lockout. Configurable under `app.security.*`. Single-instance limitation documented.

### API contract
All JSON is camelCase. All error responses use RFC 9457 `ProblemDetail` (`application/problem+json`).

Shared DTOs:
- `UserResponse` = `{ id, username, email, role, enabled, createdAt }` — never `passwordHash`. Reused by register, admin list, and `/me`.
- `AuthResponse` = `{ username, role }`.

| # | Method | Path | Auth | Request | Success | Failure |
|---|--------|------|------|---------|---------|---------|
| — | GET | `/api/auth/csrf` | Public | — | `200` `{token}` + `XSRF-TOKEN` cookie | — |
| 1 | POST | `/api/auth/register` | Public + CSRF | `{username, email, password}` | `201` `UserResponse` | `400` policy; `409` username/email taken |
| 2 | POST | `/api/auth/login` | Public + CSRF | `{username, password}` | `200` `AuthResponse` + session cookie | `401` generic (unknown / wrong / locked identical) |
| 4 | POST | `/api/auth/logout` | Authenticated + CSRF | — | `204` (session invalidated, cookie cleared) | `401` |
| — | GET | `/api/auth/me` | Authenticated | — | `200` `UserResponse` | `401` |
| 6 | POST | `/api/auth/password-reset/request` | Public + CSRF | `{email}` | `200` generic (always) | — |
| 7 | POST | `/api/auth/password-reset/confirm` | Public + CSRF | `{token, newPassword}` | `200` (all sessions invalidated; no auto-login) | `400` invalid/expired/used token or policy |
| 5 | GET | `/api/hello` | Authenticated | — | `200` `{message: "Hello, <username>"}` | `401` |
| 8 | GET | `/api/admin/users` | ADMIN | — | `200` `UserResponse[]` | `401` anon / `403` non-admin |
| 9 | PATCH | `/api/admin/users/{id}/status` | ADMIN + CSRF | `{enabled}` | `200` `UserResponse` | `403` non-admin; `409` self-target |
| 10 | PATCH | `/api/admin/users/{id}/role` | ADMIN + CSRF | `{role}` | `200` `UserResponse` | `403` non-admin; `409` self-target |
| 11 | DELETE | `/api/admin/users/{id}` | ADMIN + CSRF | — | `204` | `403` non-admin; `409` self-target |

### CSRF / CORS / cookies
- CSRF via `CookieCsrfTokenRepository.withHttpOnlyFalse()`; React reads `XSRF-TOKEN` cookie and echoes `X-XSRF-TOKEN` header on state-changing requests.
- CORS explicit allow-list of the frontend origin with `allowCredentials: true`.
- Session cookie `HttpOnly`, `SameSite=Lax` for local same-site dev (`localhost:3000` ↔ `localhost:8080`), `Secure` in prod. Real cross-site deployments use `SameSite=None; Secure` over HTTPS.

### Authorization
- Role checks enforced server-side via Spring Security (`/api/admin/**` requires `ROLE_ADMIN`); never trusted from client state.
- Self-action guard on all three admin mutations returns `409 Conflict` with a `ProblemDetail` ("An admin cannot modify their own account."), kept distinct from the authz `403`.

### Admin bootstrap
- On startup, if no `ADMIN` Account exists, seed one from config (`app.admin.username`, `app.admin.password`), hashed identically to any other Account. Idempotent — no duplicate on restart.

### Audit
- `AuditService` emits logfmt-style `key=value` lines at INFO: `login_success`, `login_failure`, `account_locked`, `ip_throttled`, `password_reset_requested`, `password_reset_completed`, `admin_user_action` (with `actor`, `target`, `action`). Single choke point guaranteeing no password/token/hash is ever logged.

### Time
- A `java.time.Clock` bean is the single source of "now" for Account Lockout cooldown and Reset Token expiry, so time is controllable in tests.

### Frontend
- Full functional UI, minimally styled (semantic HTML + light CSS): register, login, hello, forgot-password, reset-password, and an admin Account table with enable/disable, role-change, and delete controls. Uses `/api/auth/me` to rehydrate and `/api/auth/csrf` to bootstrap the token.

### Security hardening mechanisms (approved additions)

- **Session termination on admin change (stories 43–44):** the same Spring Session JDBC mechanism used for password reset — a small `SessionInvalidator` wrapping `FindByIndexNameSessionRepository.findByPrincipalName(username)` → delete each — is reused by admin disable and role-change. Introduced in Slice 5 (password reset) and reused in Slice 6. After a disable or role-change commits, every Session for the target user is deleted, so the next request on a stale cookie is unauthenticated. Consequence: a role change or disable logs the target out of all devices (accepted, more secure than live-mutating authorities). Consistent with [ADR 0001](../adr/0001-spring-session-jdbc.md).
- **Race-safe Account Lockout (story 45):** failed-login handling runs in one `@Transactional` method that loads the `User` under a pessimistic write lock (`@Lock(PESSIMISTIC_WRITE)` finder, `SELECT … FOR UPDATE`), increments `failed_login_attempts`, and sets `locked_until` when the threshold is reached. Concurrent failures for one Account serialize on the row, so no lost update lets attempts exceed the threshold without locking. H2 and Postgres both support `FOR UPDATE`.
- **Reset Token entropy (story 46):** 32 bytes (256 bits) from `SecureRandom`, Base64URL-encoded without padding, emitted once via the `EmailService`. Stored as a **SHA-256 hex digest** (deterministic, so the confirm endpoint can look up by hash), not BCrypt — deterministic lookup requires a non-salted hash, and the token's high entropy makes a fast hash safe (unlike a low-entropy password). This is why Reset Tokens hash differently from passwords. Warrants a short ADR (0002) when Slice 5 lands.
- **Reset-request rate limit (story 47):** reuses the per-IP Caffeine limiter from Slice 4 (`app.security.reset-request.*`). Over the limit → `429 ProblemDetail`; within the limit → always the generic `200` regardless of email existence. The limit is keyed on source IP, not email, so it leaks no account-existence signal. Shares the single-instance limitation already documented for IP Throttling.
- **Sensitive-data protection (story 48):** `ProblemDetail` bodies carry only generic messages and never echo submitted credential values; validation messages are static strings. `AuditService` remains the sole logging path for security events and accepts no secret arguments (no password/hash/token/session-id parameters exist on it).
- **Admin authorization / IDOR-BOLA (story 49):** `/api/admin/**` requires `ROLE_ADMIN` via Spring Security; the acting Admin's identity for the self-action guard is read from the `SecurityContext` principal, never from a request body or param. The `{id}` path variable selects only the *target*; it can never grant privilege or designate "self". Inbound admin mutations use narrow DTOs (`{enabled}`, `{role}`) so no request field binds to the `User` entity (mass-assignment safe).
- **Login timing resistance (story 50):** when the username does not exist, the authentication path still performs a BCrypt comparison against a fixed dummy hash before returning the generic 401, so an existing vs non-existing account cannot be distinguished by response latency.
- **No usable default admin credentials (story 51):** `app.admin.password` has no working default. On startup the seeder validates the configured password against the policy and a placeholder blocklist; in any non-`dev` profile a missing/placeholder value fails startup (fail-fast) rather than seeding a guessable admin.
- **Reset-link Host safety (story 52):** the reset URL is composed from a configured `app.frontend.base-url`; the request `Host`/`X-Forwarded-*` headers are never used to build it.
- **Baseline hardening (story 53):** Spring Security's default headers are kept (X-Content-Type-Options nosniff, X-Frame-Options DENY, HSTS over HTTPS) and extended with `Referrer-Policy: no-referrer` and `Cache-Control: no-store` on auth responses; `server.servlet.session.timeout` is set explicitly; `server.error.include-stacktrace=never` and `include-message=never` are pinned. Actuator remains off the classpath and the H2 console stays disabled.
- **Case-sensitive identifiers (accepted limitation):** usernames and emails are stored and matched verbatim (case-sensitive) as implemented in registration. Not normalized to lowercase; documented as an accepted limitation for this assessment rather than reopening the committed registration slice.

## Testing Decisions

Good tests here assert **external behavior at the HTTP boundary** — status codes, response bodies, cookie presence/absence, and observable state changes (a subsequent request's behavior, the recorded reset token, an Audit Event) — not internal method calls or private fields.

- **Single primary seam:** `@SpringBootTest(webEnvironment = RANDOM_PORT)` with a cookie-aware HTTP client (`TestRestTemplate`/`WebTestClient`), exercising the real Spring Security filter chain, Spring Session JDBC, H2, controllers, services, and repositories. Security internals are not mocked for the required tests. Live port (not `MockMvc`) because the required tests depend on real cookie/session round-tripping.
- **Clock seam:** the injected `Clock` bean is overridden in tests to advance time deterministically for lockout-cooldown and token-expiry cases — no `Thread.sleep`.
- **EmailService seam:** the PRD-defined stub is replaced by a test double that records the last Reset Token, so reset-confirm tests obtain the plaintext while production stores only the hash.

Required coverage (mapped to stories):
- **Session termination on admin change (43–44, Slice 6):** an authenticated user whose Account is disabled can no longer reach `GET /api/hello` or any protected endpoint with the pre-existing Session (401); an authenticated Admin downgraded to `USER` loses `ADMIN` access on `/api/admin/**` with the pre-existing Session.
- **Race-safe lockout (45, Slice 4):** many concurrent wrong-password attempts against one Account end with the Account locked and a consistent counter — no lost-update path lets attempts exceed the threshold without locking.
- **Reset-token hardening (46, Slice 5):** tokens captured via the `EmailService` double are unique across requests and of the expected high-entropy length/charset; the persisted `token_hash` never equals the plaintext token.
- **Reset-request rate limit (47, Slice 5):** repeated requests from one IP eventually return `429`; within the limit the response is always the generic `200` regardless of whether the email is registered.
- **Sensitive-data protection (48, Slices 3/5/6 + final review 9):** `ProblemDetail` bodies for validation failures do not echo the submitted password/token; a log/audit capture asserts no secret appears in emitted log lines.
- **Admin authorization / IDOR-BOLA (49, Slice 6):** a `USER` calling any admin endpoint gets 403; an anonymous caller gets 401; the self-action guard resolves "self" from the authenticated principal (not a request field), so an Admin cannot evade or trigger it by supplying another id.
- **Login timing resistance (50, Slice 3):** unknown-username and wrong-password logins return identical generic 401s; a behavioral test confirms the encoder is invoked even for a non-existent user (constant-work path), rather than asserting wall-clock timing.
- **Default admin fail-fast (51, Slice 7):** under a non-`dev` profile with a missing or placeholder admin password, application startup fails; under `dev` with a policy-compliant configured password, exactly one Admin is seeded (idempotent).
- **Reset-link Host safety (52, Slice 5):** the reset link captured via the `EmailService` double uses the configured base URL even when the request `Host` header is attacker-controlled.
- **Baseline hardening (53, Slice 3 / verify 9):** auth responses carry the expected security headers (`X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Cache-Control: no-store`); the session-timeout property is set; an error response body contains no stack trace.
- Login (7–10): success; wrong password; unknown username (byte-identical generic 401 to wrong password); locked Account rejected with correct password.
- Lockout & throttle (11–13): 5 failures locks the Account; login after cooldown succeeds and resets the counter; failures from one IP across many usernames trigger IP Throttling independent of any single Account's lockout.
- Logout (14–15): a Session cookie captured pre-logout is rejected after logout.
- Password reset (18–24): token single-use; token expiry; reset invalidates all existing Sessions; no auto-login after reset.
- Admin self-action guards (30, 32, 34): Admin cannot disable/demote/delete own Account (409).
- Role enforcement (26): a `USER` calling any `/api/admin/**` endpoint gets 403.

No prior art exists in this greenfield repo; these integration tests establish the pattern. Frontend tests are optional (a light Vitest/React Testing Library smoke of the login flow is welcome but not required).

## Out of Scope

- JWT implementation (design documented in the PRD appendix only).
- Multi-factor authentication.
- Real SMTP/email delivery (stubbed `EmailService` logs the link).
- Containerization, CI/CD, hosting infra.
- Local HTTPS setup (documented deployment assumption; local dev over HTTP).
- Granular per-resource authorization beyond the `USER`/`ADMIN` check on admin endpoints.
- Multi-instance IP Throttling and reset-request rate limiting (both in-memory Caffeine, single-instance; documented limitation — a horizontally scaled deployment would need a shared store).
- Required frontend E2E tests.
- Case-insensitive username/email (identifiers are case-sensitive as implemented; accepted limitation — see hardening mechanisms).
- Request-body-size / JSON-bomb DoS protection (a reverse-proxy concern, out of scope with hosting infra).
- Dependency vulnerability scanning in CI (CI/CD excluded).

## Further Notes

- Publishing to the issue tracker is pending: `/setup-matt-pocock-skills` has not been run (no `CLAUDE.md`/`AGENTS.md`, no triage-label vocabulary), and `gh auth status` is failing (keyring timeout on account `sngeiting`). Resolve both before `/to-tickets`, then this spec should be published with the `ready-for-agent` label.
- Every participant works on their own branch named after themselves (README rule); current branch is `sngeiting`. Do not commit to `main`.
- Transport: any real deployment must sit behind HTTPS (required for `Secure` cookies and HSTS); local HTTP is an accepted, documented gap.
