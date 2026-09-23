Status: ready-for-agent

# Spec: Secured Hello World App (React + Spring Boot)

Source: [prd/assessment-prd.md](../../prd/assessment-prd.md)

## Problem Statement

A team needs a reference implementation of a secure username/password login
flow that behaves like a production-grade security baseline, not a
shortcut-everything demo — so they can see what "done right" looks like for
registration, session-based login, lockout/throttling, logout, protected
content, password reset, and admin user management, all built with a React
frontend and a Spring Boot backend talking cross-origin over cookies.

## Solution

Build the app described in the PRD end to end:

- **Frontend:** React app on its own origin (e.g. `localhost:3000`).
- **Backend:** Spring Boot REST API on its own origin (e.g. `localhost:8080`).
- **Cross-origin:** CORS allow-lists the frontend origin with
  `Access-Control-Allow-Credentials: true` so the session cookie travels.
- **Persistence:** Spring Data JPA over H2 (dev profile only for this build;
  the PRD's "portable to Postgres/MySQL later" note is explicitly deferred —
  no other profile is wired up in this effort).
- **Auth:** Server-side session via a secure HttpOnly cookie (Spring
  Session), per the PRD's primary mechanism. The JWT alternative in the PRD
  appendix is documentation only — not built.

## User Stories

Numbered exactly as in the PRD, since the PRD is already the source of
truth; extending here only where the PRD's Given/When/Then implies a
sub-behaviour worth calling out on its own line.

1. As a visitor, I want to register an account with a username, email, and
   password, so that I can log in and access the protected app.
2. As a visitor submitting registration, I want a unique-username/email
   check, so that duplicate accounts are rejected with a clear validation
   error and no account is created.
3. As a visitor submitting registration, I want a password-strength check
   (length ≥ 12), so that weak passwords are rejected before an account is
   created.
4. As the system handling any registration attempt, I want the plaintext
   password to never be logged or stored, so that credential material never
   leaks into logs or the database in cleartext.
5. As a registered user, I want to log in with my username and password, so
   that I can access my session and the protected app content.
6. As a registered user logging in with correct credentials, I want a
   server-side session and secure session cookie to be created and my
   `failed_login_attempts` reset to 0, so that I regain clean access.
7. As a visitor submitting incorrect credentials, I want a generic error
   that does not reveal whether the username exists, so that account
   enumeration is not possible via the login form.
8. As a user whose account is locked (`locked_until` in the future), I want
   login to be rejected even with correct credentials until the lockout
   expires, so that a stolen/guessed password can't be used mid-lockout.
9. As a security-conscious operator, I want N consecutive failed logins
   against one account within a window (default 5) to lock that account for
   a cooldown period (default 15 minutes), so that brute-force credential
   guessing is blunted.
10. As a user whose lockout cooldown has elapsed, I want a correct password
    to succeed and `failed_login_attempts` to reset, so that lockout is
    temporary, not permanent.
11. As a security-conscious operator, I want repeated failed logins from one
    IP across multiple usernames to trigger IP-level throttling independent
    of any single account's lockout state, so that an attacker can't lock
    out a legitimate user merely by failing their password from one source.
12. As a logged-in user, I want to log out, so that my session is fully
    ended and cannot be reused.
13. As a logged-in user who has logged out, I want a replayed session
    cookie captured before logout to be rejected as unauthenticated, so
    that a captured cookie is useless after logout.
14. As a logged-in user, I want to see a personalized greeting at
    `GET /api/hello`, so that I can confirm my authentication actually
    worked.
15. As a visitor (no session, or an invalid/expired one), I want
    `GET /api/hello` to respond 401, so that protected content never leaks
    to unauthenticated requests.
16. As a user who forgot their password, I want to request a password reset
    via my registered email, so that I can regain access without contacting
    an admin.
17. As a visitor requesting a password reset, I want a generic success
    response regardless of whether the email is registered, so that account
    existence cannot be inferred from the reset-request endpoint.
18. As the system processing a valid reset request, I want a single-use
    reset token to be generated, its hash (never the plaintext token)
    stored with a short expiry (default 30 minutes, within the PRD's
    15–30 min range), and `EmailService.sendPasswordResetEmail(...)`
    invoked (a stub that logs instead of sending), so that the user has a
    time-boxed, single-use path to a new password.
19. As a user with a valid, unexpired, unused reset token, I want to submit
    a new password meeting the strength policy and have it accepted, so
    that I regain access to my account.
20. As the system completing a password reset, I want all existing sessions
    for that user invalidated, so that a compromised session cannot survive
    a password change intended to shut it out.
21. As a user submitting an expired reset token, I want the request
    rejected and the password left unchanged, so that stale tokens can't be
    used to take over an account.
22. As a user submitting an already-used reset token, I want the request
    rejected, so that single-use enforcement holds even on retry.
23. As an admin, I want to see a list of all registered users (username,
    email, role, enabled status, created-at — never password hashes), so
    that I can review who has access to the system.
24. As a non-admin user, I want `GET /api/admin/users` to respond 403, so
    that only admins can enumerate the user base.
25. As an admin, I want to enable or disable another user's account, so
    that I can suspend access without deleting their data.
26. As an admin attempting to disable my own account via the status-toggle
    endpoint, I want the request rejected, so that an admin can't lock
    themselves out.
27. As an admin, I want to change another user's role between USER and
    ADMIN, so that I can grant or revoke admin privileges.
28. As an admin attempting to change my own role via the role-change
    endpoint, I want the request rejected, so that an admin can't demote
    themselves.
29. As an admin, I want to delete another user's account, so that I can
    remove accounts that should no longer exist.
30. As an admin attempting to delete my own account via the delete
    endpoint, I want the request rejected, so that an admin can't delete
    themselves.
31. As an operator deploying the app for the first time, I want an initial
    admin account seeded automatically from configuration
    (`app.admin.username`, `app.admin.password`) when no `ADMIN` user
    exists, hashed identically to any other account, so that there's a way
    into the admin module without manual database edits.
32. As an operator restarting the app, I want no duplicate seed admin
    created if an `ADMIN` user already exists, so that restarts are
    idempotent.

## Implementation Decisions

- **Modules:** a Maven-based Spring Boot backend under `backend/`, and a
  React app (Create React App or Vite — implementer's choice, standard
  either way) under `frontend/`, as sibling directories at the repo root.
- **Backend dependencies:** Spring Web, Spring Security, Spring Data JPA,
  H2, Spring Session (session-backed, not JDBC-backed, since H2 in-process
  is sufficient for this build), Validation (Bean Validation / Jakarta
  Validation).
- **Primary keys:** `Long` auto-increment for both `users` and
  `password_reset_tokens` (the PRD leaves `UUID/long` as an explicit
  either/or; `Long` is chosen for simplicity with H2 dev-profile — no
  other consumer of this schema depends on UUIDs).
- **Password hashing:** `BCryptPasswordEncoder`, default strength.
- **Password strength policy:** length ≥ 12, per the PRD; no additional
  complexity rules (uppercase/digit/symbol) unless the PRD is amended.
- **Session security:** `HttpOnly` always; `Secure` gated behind a
  prod/non-dev profile check (local dev over HTTP is a documented,
  accepted gap per the PRD); `SameSite=Lax` as the default attribute;
  session-fixation protection via Spring Security's default session
  strategy (`changeSessionId`).
- **Lockout defaults:** 5 consecutive failed attempts within a rolling
  window locks the account for 15 minutes (`locked_until` set 15 minutes
  out), matching the PRD's example values exactly, treated as authoritative
  for this build (confirmed with the user — PRD's examples are not merely
  illustrative here).
- **IP throttling:** tracked independently of per-account lockout, keyed by
  client IP across usernames; threshold and window are an implementation
  detail not pinned by the PRD beyond "a threshold" — pick a reasonable
  value (e.g. 20 failed attempts across usernames per IP per 15-minute
  window) and document it in code/tests, since the PRD does not give an
  explicit number for this one.
- **Reset token expiry:** 30 minutes, the upper bound of the PRD's
  15–30 min range, chosen as the single default.
- **Reset token storage:** the token itself is a high-entropy random value
  handed to the user (via the stub `EmailService`, which logs the link);
  only its hash is persisted in `password_reset_tokens.token_hash`.
- **CSRF:** enabled for all state-changing endpoints (register, login,
  logout, password reset request/confirm, all `/api/admin/**` mutations),
  since auth is cookie-based. The frontend must fetch and echo the CSRF
  token (Spring Security's `CookieCsrfTokenRepository` or equivalent) on
  every mutating request.
- **CORS:** explicit allow-list containing only the configured frontend
  origin (e.g. `http://localhost:3000`), `allowCredentials(true)`.
- **Audit logging:** structured log lines (no dedicated table) for login
  success/failure, lockout triggered, password reset requested/completed,
  and role change/enable/disable/delete (actor + target). Passwords and
  plaintext reset tokens are never logged.
- **Admin bootstrap:** an application startup component (e.g.
  `ApplicationRunner`/`CommandLineRunner`) checks for any `ADMIN` user; if
  none exists, seeds one from `app.admin.username` / `app.admin.password`
  configuration, hashed the same way as any other account.
- **API contract shape:** JSON REST under `/api/**`; `/api/admin/**` for
  admin-only endpoints. Exact route names/methods are an implementation
  detail left to the ticket that builds each story, guided by the PRD's
  story text (e.g. "the status-toggle endpoint", "the role-change
  endpoint", "the delete endpoint").

## Testing Decisions

- Automated integration tests are required for the security-critical paths
  named in the PRD's Testing Requirements section; general CRUD/UI coverage
  is left to implementer discretion.
- Prefer Spring Boot's `@SpringBootTest` with `MockMvc` (or
  `TestRestTemplate`/`WebTestClient` if the implementer prefers a running
  server) driving real HTTP-shaped requests against the full security
  filter chain — these are integration tests of behavior, not unit tests of
  isolated methods, since the PRD's acceptance criteria are behavioral
  (given/when/then against the API surface).
- Required minimum coverage (mirrors the PRD verbatim):
  - **Login (Story 2):** success; wrong password; unknown username
    (identical generic error either way); account locked.
  - **Lockout (Story 3):** N failed attempts triggers lockout; successful
    login after cooldown resets the counter; IP throttling engages
    independently of account lockout.
  - **Logout (Story 4):** a reused session cookie is rejected after logout.
  - **Password reset (Stories 6–7):** token single-use; token expiry;
    reset invalidates existing sessions.
  - **Admin self-action guard (Stories 9–11):** admin cannot
    disable/delete/demote their own account.
  - **Role enforcement (Story 8):** a `USER` calling any
    `/api/admin/**` endpoint receives 403.
- No test may assert on or log a plaintext password or plaintext reset
  token; assertions on hashed/stored values check non-equality to the
  plaintext input plus presence of a hash, not the hash's exact value.

## Out of Scope

Verbatim from the PRD:

- JWT implementation (design documented in the PRD appendix only).
- Multi-factor authentication (MFA/2FA).
- Real SMTP / email delivery (password reset uses a stubbed
  `EmailService` that logs instead of sending).
- Containerization / CI/CD / hosting infra.
- Local HTTPS setup (documented as a deployment assumption; local dev runs
  over HTTP).
- Granular per-resource authorization beyond the USER/ADMIN role check on
  admin endpoints.

Additionally, out of scope for this specific effort (confirmed with the
user):

- Wiring a Postgres/MySQL profile. The schema should remain portable (plain
  JPA entities, no H2-specific types), but only the H2 dev profile is
  configured and tested.

## Further Notes

- The PRD's example numeric defaults (5 failed attempts, 15-minute lockout,
  15–30 minute reset token expiry) are treated as authoritative for this
  build per explicit user confirmation, not just illustrative examples.
- `Long`/auto-increment primary keys were chosen by the implementer per
  explicit user delegation ("pick UUID or Long myself").
