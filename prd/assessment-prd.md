# PRD: Hello World Auth App (React + Spring Boot)

User stories follow the Connextra template: **As a `<role>`, I want `<goal>`, so that `<benefit>`.** Each story carries acceptance criteria in Given/When/Then form. Non-functional/security requirements that don't map to a single user action are called out separately at the end, since Connextra stories describe user-facing behavior, not cross-cutting constraints.

## Overview

A reference/demo application demonstrating a secure username/password login flow, built as a production-grade security baseline rather than a shortcut-everything demo.

- **Frontend:** React, its own origin (e.g. `localhost:3000`).
- **Backend:** Spring Boot, its own origin (e.g. `localhost:8080`), REST API.
- **Cross-origin:** CORS configured on the backend to allow the frontend origin, with credentials (cookies) enabled.
- **Persistence:** Spring Data JPA over H2 to start (dev profile), schema portable to Postgres/MySQL later.
- **Primary auth mechanism:** Server-side session via a secure HttpOnly cookie (Spring Session).
- **Alternative auth mechanism (documented, not built):** JWT bearer token — see [Appendix: JWT Alternative](#appendix-jwt-alternative).

## Out of Scope

- JWT implementation (design documented in the appendix only).
- Multi-factor authentication (MFA/2FA) — for all users, including `ADMIN`. See the Compliance Waivers section for the IM8 ac-2 waiver and compensating controls.
- Real SMTP / email delivery (password reset uses a stubbed `EmailService` that logs instead of sending).
- Containerization / CI/CD / hosting infra.
- Local HTTPS setup (documented as a deployment assumption; local dev runs over HTTP).
- Granular per-resource authorization beyond the USER/ADMIN role check on admin endpoints.

## Roles

- **Visitor** — unauthenticated user, can register or log in.
- **User** — authenticated account holder with role `USER`.
- **Admin** — authenticated account holder with role `ADMIN`, can manage other accounts.

## User Stories

### Registration

**Story 1** — As a **visitor**, I want to register an account with a username, email, and password, so that I can log in and access the protected app.

- Given a visitor submits a unique username, unique email, and a password meeting the minimum strength policy (length ≥ 12), when they submit registration, then an account is created with role `USER`, `enabled = true`, and the password stored as a BCrypt hash.
- Given a visitor submits a username or email that's already registered, when they submit registration, then the request is rejected with a clear validation error (username/email conflict), and no account is created.
- Given a visitor submits a password that fails the strength policy, when they submit registration, then the request is rejected with a validation error and no account is created.
- Given any registration attempt, when handled, then the plaintext password is never logged or stored.

### Login

**Story 2** — As a **registered user**, I want to log in with my username and password, so that I can access my session and the protected app content.

- Given a registered, enabled, non-locked account with correct credentials, when the user submits login, then a server-side session is created, a secure session cookie is set, and `failed_login_attempts` resets to 0.
- Given incorrect credentials, when the user submits login, then the request is rejected with a generic error message that does not reveal whether the username exists, and `failed_login_attempts` increments.
- Given an account that is currently locked (`locked_until` in the future), when the user submits login with correct credentials, then the request is still rejected until the lockout expires.

**Story 3** — As a **security-conscious operator**, I want repeated failed logins to trigger account lockout and IP-level throttling, so that brute-force credential guessing is blunted.

- Given N consecutive failed login attempts against one account within a window (e.g. 5 attempts), when the Nth failure occurs, then the account is locked for a cooldown period (e.g. 15 minutes) by setting `locked_until`.
- Given a locked account, when the cooldown period elapses and the correct password is submitted, then login succeeds and `failed_login_attempts` resets.
- Given repeated failed login attempts from one IP address across multiple usernames, when a threshold is exceeded, then further attempts from that IP are throttled independently of any single account's lockout state — so an attacker cannot lock out a legitimate user merely by failing that user's password from one source.

### Logout

**Story 4** — As a **logged-in user**, I want to log out, so that my session is fully ended and cannot be reused.

- Given an active session, when the user calls logout, then the server-side session is invalidated and the session cookie is cleared.
- Given a session cookie captured before logout, when it is replayed after logout, then the server rejects it as unauthenticated.

### Protected content

**Story 5** — As a **logged-in user**, I want to see a personalized greeting, so that I can confirm my authentication actually worked.

- Given an authenticated session, when the user requests `GET /api/hello`, then the response is `"Hello, <username>"`.
- Given no session (or an invalid/expired one), when a request is made to `GET /api/hello`, then the response is unauthorized (401).

### Password reset

**Story 6** — As a **user who forgot their password**, I want to request a password reset via my registered email, so that I can regain access without contacting an admin.

- Given a request with an email address, when submitted to the password-reset-request endpoint, then the response is a generic success message regardless of whether the email is registered — so account existence cannot be inferred.
- Given the email matches a registered user, when the request is processed, then a single-use reset token is generated, its hash (not the plaintext token) is stored with a short expiry (15–30 min), and `EmailService.sendPasswordResetEmail(...)` is called (stub implementation logs the link instead of sending mail).

**Story 7** — As a **user with a valid reset token**, I want to set a new password, so that I can regain access to my account.

- Given a valid, unexpired, unused reset token and a new password meeting the strength policy, when submitted to the password-reset-confirm endpoint, then the password is updated, the token is marked used, and all existing sessions for that user are invalidated.
- Given an expired token, when submitted, then the request is rejected and the password is not changed.
- Given a token that has already been used once, when submitted again, then the request is rejected (single-use enforcement).

### Admin: user management

**Story 8** — As an **admin**, I want to see a list of all registered users, so that I can review who has access to the system.

- Given an authenticated admin, when they call `GET /api/admin/users`, then the response lists each user's username, email, role, enabled status, and created-at date — never password hashes.
- Given an authenticated non-admin user, when they call `GET /api/admin/users`, then the response is forbidden (403).

**Story 9** — As an **admin**, I want to enable or disable another user's account, so that I can suspend access without deleting their data.

- Given an admin targets another user's account, when they call the status-toggle endpoint, then the account's `enabled` flag is updated accordingly, and a disabled user can no longer log in.
- Given an admin targets their own account via the status-toggle endpoint, when the request is made, then it is rejected — an admin cannot disable themselves.

**Story 10** — As an **admin**, I want to change another user's role between USER and ADMIN, so that I can grant or revoke admin privileges.

- Given an admin targets another user's account, when they call the role-change endpoint with a valid role, then the account's role is updated.
- Given an admin targets their own account via the role-change endpoint, when the request is made, then it is rejected — an admin cannot demote themselves.

**Story 11** — As an **admin**, I want to delete another user's account, so that I can remove accounts that should no longer exist.

- Given an admin targets another user's account, when they call the delete endpoint, then the account is removed.
- Given an admin targets their own account via the delete endpoint, when the request is made, then it is rejected — an admin cannot delete themselves.

### Admin bootstrap

**Story 12** — As an **operator deploying the app for the first time**, I want an initial admin account to be created automatically, so that there's a way into the admin module without manual database edits.

- Given no `ADMIN` user exists in the database, when the application starts, then one is seeded using credentials sourced from a managed secret store (e.g. environment-injected secrets, AWS Secrets Manager, or HashiCorp Vault — never plaintext committed to configuration files), with the password hashed identically to any other account. (IM8 as-8)
- Given an `ADMIN` user already exists, when the application restarts, then no duplicate seed account is created.
- Given the seeded admin (or any admin-issued/temporary credential) has never changed its password, when the admin logs in, then a mandatory password change is enforced before any admin action is permitted; the account carries a `must_change_password` flag that is cleared only after a successful change. (IM8 ac-6)

## Non-Functional & Security Requirements

These are cross-cutting constraints rather than individual user actions, so they sit outside the Connextra story format above but are binding on every story that touches them.

- **Password storage:** BCrypt (`BCryptPasswordEncoder`); no custom hashing. (IM8 as-6)
- **Secrets management:** All secrets (admin seed credentials, database credentials, session/reset-token signing material) are sourced from a managed secret store or environment-injected secrets, never committed to source or plaintext config files. (IM8 as-8)
- **Forced credential change:** Admin-issued or temporary credentials (including the seeded admin) require a mandatory password change on first login before any privileged action; a forced password reset can also be triggered by an operator on suspected account compromise. (IM8 ac-6, as-15)
- **Session security:** `HttpOnly`, `Secure` (prod), `SameSite` cookie attributes; session-fixation protection; sessions invalidated on logout and password reset. An absolute maximum session lifetime and idle timeout are enforced, after which re-authentication is required. (IM8 as-11)
- **CSRF:** Enabled for all state-changing endpoints (register, login, logout, password reset, admin mutations), since auth is cookie-based.
- **CORS:** Explicit allow-list of the frontend origin(s); `Access-Control-Allow-Credentials: true` for the session cookie to travel cross-origin.
- **Output encoding / CSP:** All user-controlled output (e.g. the greeting `"Hello, <username>"`, validation and error messages) is contextually encoded; the React client escapes by default and API responses use non-HTML content types. A minimally-permissive Content-Security-Policy header (`default-src 'self'`, no inline/`unsafe-eval`) is set on frontend responses. (IM8 as-3, as-9)
- **Enumeration resistance:** Login and password-reset-request responses never reveal whether a username/email exists.
- **Transport:** Any real deployment must sit behind HTTPS (required for `Secure` cookies and HSTS, with `max-age` ≥ 1 year); local dev over HTTP is a documented, accepted gap. (IM8 as-10, dp-3)
- **Error handling:** Client-facing errors are generic; internal details, stack traces, and debug information are never disclosed to end users. (IM8 as-13)
- **Audit logging:** Structured (JSON/ECS) log lines (no dedicated table required) for login success/failure, lockout triggered, password reset requested/completed, and role change/enable/disable/delete (actor + target). Passwords and reset tokens are never logged; sensitive fields are masked. (IM8 lm-4, lm-15, lm-19)
- **Least privilege:** Default-deny authorization; role checks enforced server-side via Spring Security URL + method-level security; never trusted from client-supplied state. (IM8 ac-1, as-7)

See the Compliance Waivers section for controls (IM8 ac-2, ac-3, ac-4, lm-16) that are waived as out of scope with compensating controls.

## Data Model

### `users`

| Field | Type | Notes |
| --- | --- | --- |
| id | UUID/long | PK |
| username | string, unique | login identifier |
| email | string, unique | used only for password reset |
| password_hash | string | BCrypt |
| role | enum: `USER`, `ADMIN` | |
| enabled | boolean | admin can disable an account without deleting it |
| must_change_password | boolean | forces password change on next login for seeded/temporary credentials (IM8 ac-6) |
| last_login_at | timestamp, nullable | last successful login (audit / operational visibility) |
| failed_login_attempts | int | for lockout tracking |
| locked_until | timestamp, nullable | for lockout |
| created_at | timestamp | |

### `password_reset_tokens`

| Field | Type | Notes |
| --- | --- | --- |
| id | UUID/long | PK |
| user_id | FK -> users | |
| token_hash | string | store hashed, never plaintext |
| expires_at | timestamp | short-lived (e.g. 15–30 min) |
| used_at | timestamp, nullable | single-use enforcement |

## System Documentation

Maintained to satisfy IM8 pm-6 (architecture, API specification, network topology, data-flow).

### API surface

| Method | Path | Auth | State-changing (CSRF) |
| --- | --- | --- | --- |
| POST | `/api/register` | public | yes |
| POST | `/api/login` | public | yes |
| POST | `/api/logout` | session | yes |
| GET | `/api/hello` | session | no |
| POST | `/api/password-reset/request` | public | yes |
| POST | `/api/password-reset/confirm` | public | yes |
| GET | `/api/admin/users` | ADMIN | no |
| PATCH | `/api/admin/users/{id}/status` | ADMIN | yes |
| PATCH | `/api/admin/users/{id}/role` | ADMIN | yes |
| DELETE | `/api/admin/users/{id}` | ADMIN | yes |

### Network topology (deployment)

```
[Browser] --HTTPS--> [Reverse proxy / TLS termination + HSTS]
     |                         |
     |  React static origin    |  Spring Boot API origin
     |  (e.g. :3000)           |  (e.g. :8080)
     +-------------------------+
                               |
                        [Spring Boot app]
                               |
                        [Spring Session store]
                               |
                        [JPA -> H2 (dev) / Postgres|MySQL (prod)]
                               |
                        [Secret store: env / Secrets Manager / Vault]
```

Local dev runs both origins over HTTP (accepted gap); every non-dev deployment sits behind HTTPS.

### Data-flow (login example)

1. Browser submits credentials + CSRF token over HTTPS to `/api/login`.
2. Backend validates input, looks up the user via parameterised JPA query, checks lockout/enabled state.
3. On success: session id regenerated (fixation protection), secure `HttpOnly`/`SameSite` cookie set, `failed_login_attempts` reset; structured audit log emitted (no secrets).
4. On failure: `failed_login_attempts` incremented, generic error returned, lockout/IP-throttle evaluated, audit log emitted.

### Residual controls register

| Control | Status | Rationale / compensating control |
| --- | --- | --- |
| IM8 ac-2 privileged MFA | waived (out of scope) | See Compliance Waivers; compensated by lockout, IP throttling, forced first-login change, audit logging, secure sessions. |
| IM8 ac-3 inactivity disable | waived (out of scope) | Lifecycle automation out of scope; admin can manually disable accounts (Story 9). |
| IM8 ac-4 access review | waived (out of scope) | Periodic review is an org/ops process, not app scope; admin user list (Story 8) supports ad-hoc review. |
| IM8 lm-16 key-signal metrics | waived (out of scope) | Monitoring/observability infra out of scope (see Out of Scope: hosting infra). |

## Compliance Waivers

The following IM8 controls are **waived** because they fall outside the declared scope of this reference/demo application. Each waiver records the reason and the compensating controls that remain in force.

- **IM8 ac-2 — MFA for privileged accounts.** *Waived:* MFA/2FA is out of scope for all users, including `ADMIN` (see Out of Scope). *Compensating controls:* per-account lockout and independent IP throttling (Story 3), mandatory first-login password change for the seeded admin (Story 12, ac-6), BCrypt password storage, secure session cookies with fixation protection, and full audit logging of privileged actions. *Residual risk accepted:* privileged accounts rely on a single (password) factor; acceptable for a non-production demo behind HTTPS. Revisit before any production/public deployment.
- **IM8 ac-3 — Inactive/expired account disable.** *Waived:* automated inactivity-based lifecycle management is out of scope. *Compensating control:* admins can manually disable accounts (Story 9); `last_login_at` is recorded for future automation.
- **IM8 ac-4 — Periodic access review.** *Waived:* a scheduled privilege-review process is an operational/governance activity outside app scope. *Compensating control:* the admin user list (Story 8) exposes role/enabled/created-at for ad-hoc review.
- **IM8 lm-16 — Key-signal (RED/USE) monitoring.** *Waived:* runtime observability infrastructure is out of scope (aligned with the Out of Scope exclusion of hosting/CI-CD infra). *Compensating control:* structured audit logging (lm-4/lm-15) provides security-event visibility.

## Testing Requirements

Automated integration tests are required for the security-critical paths; general CRUD/UI test coverage is left to implementer discretion. Minimum required coverage, mapped to the stories above:

- Login (Story 2): success, wrong password, unknown username (identical generic error either way), account locked.
- Lockout (Story 3): N failed attempts triggers lockout; successful login after cooldown resets the counter; IP throttling engages independently of account lockout.
- Logout (Story 4): a reused session cookie is rejected after logout.
- Password reset (Stories 6–7): token single-use, token expiry, reset invalidates existing sessions.
- Admin self-action guard (Stories 9–11): admin cannot disable/delete/demote their own account.
- Role enforcement (Story 8): a `USER` calling any `/api/admin/**` endpoint receives 403.
- Privileged-account hardening (Story 12): seeded admin is forced to change its password on first login before any admin action succeeds. (IM8 ac-6)

## Appendix: JWT Alternative

Documented for future extension; not part of this build.

- Access token issued on login, signed (e.g. HS256/RS256), short expiry (e.g. 15 min), sent via `Authorization: Bearer` header — **not** localStorage, to reduce XSS exposure; if stored client-side at all, prefer an in-memory variable over persistent storage.
- Refresh token with longer expiry, itself stored in an HttpOnly cookie or rotated on use.
- **Server-side logout requires a blacklist**: since JWTs are stateless and normally valid until expiry regardless of "logout," true logout needs a token-id (`jti`) blacklist/revocation store (e.g. a table or cache keyed by `jti` with a TTL matching the token's remaining life) checked on every authenticated request.
- CSRF is less of a concern for header-based bearer tokens (no ambient credential), but CORS and XSS-driven token theft become the primary risks instead.
- Trade-off vs. the session-cookie approach chosen as primary: JWT avoids server-side session storage but reintroduces statefulness anyway via the blacklist, while adding token-storage risk on the client — this is why session-cookie was chosen as primary for this spec.
