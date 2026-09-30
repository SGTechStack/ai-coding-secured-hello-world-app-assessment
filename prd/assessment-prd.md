# PRD: Hello World Auth App (React + Spring Boot)

User stories follow the Connextra template: **As a `<role>`, I want `<goal>`, so that `<benefit>`.** Each story carries acceptance criteria in Given/When/Then form. Non-functional/security requirements that don't map to a single user action are called out separately at the end.

This PRD builds on the existing template security (CSRF cookie, CSP, Tomcat cookie hardening, `/admin/api/**` filter chain). It adds only what the template lacks and does not weaken what it has.

## Overview

A reference/demo application demonstrating a secure username/password login flow with admin-managed accounts.

- **Frontend:** React SPA. Same-origin with the backend: served by Spring under `/app` in prod, behind the Vite dev proxy locally ([ADR-DEMO-0002](../docs/adr/ADR-DEMO-0002-same-origin-no-cors.md)). No CORS.
- **Backend:** Spring Boot REST API.
- **Persistence:** Spring Data JPA. H2 for `local`/`test`, MSSQL in cloud profiles. Schema managed by Liquibase changelogs, portable across both.
- **Auth mechanism:** Server-side session via a secure HttpOnly cookie, stored with Spring Session JDBC ([ADR-DEMO-BE-0001](../backend/docs/adr/ADR-DEMO-BE-0001-jdbc-sessions.md)).
- **Account creation:** admin-only. There is no self-registration and no email.
- **Alternative auth mechanism (documented, not built):** JWT bearer token, see [Appendix: JWT Alternative](#appendix-jwt-alternative).

## Out of Scope

- Self-registration.
- Email of any kind, including email-based password reset. Recovery is admin-initiated.
- JWT implementation (design documented in the appendix only).
- Multi-factor authentication (MFA/2FA).
- CORS / separate-origin deployment.
- Containerization / CI/CD / hosting infra.
- Local HTTPS setup (documented as a deployment assumption; local dev runs over HTTP).
- Granular per-resource authorization beyond Role checks on admin endpoints.
- Removing the template's SSO/OIDC leftovers (`aasUuid`, `AppOidcUser`, `feat-oidc`); tracked separately.

## Roles

See [CONTEXT.md](../CONTEXT.md) for **Account**, **Role** and **Temporary Password**.

- **Unauthenticated caller**: can only sign in.
- **User**: authenticated Account with Role `USER`.
- **User manager**: template Role `USER_MANAGER`, unchanged by this PRD.
- **Admin**: authenticated Account with Role `ADMIN`, manages other Accounts.

## User Stories

### Login

**Story 1** — As an **account holder**, I want to log in with my username and password, so that I can access my session and the protected app content.

- Given an enabled account with correct credentials and no active backoff, when the holder submits the JSON login request, then a server-side session is created, the session id is regenerated, a secure session cookie is set, and `failed_login_attempts` resets to 0.
- Given incorrect credentials, when the holder submits login, then the request is rejected with a generic error that does not reveal whether the username exists, and `failed_login_attempts` increments.
- Given an account whose `locked_until` is in the future, when the holder submits login with correct credentials, then the request is still rejected until the backoff expires.
- Given a disabled account, when the holder submits login, then the same generic error is returned.
- Given the login request, then CSRF protection is enforced (the template's CSRF exemption for `/login` is removed).

**Story 2** — As a **security-conscious operator**, I want repeated failed logins to trigger progressive backoff and IP-level throttling, so that brute-force guessing is blunted without allowing permanent lockout of a victim.

- Given consecutive failed logins against one account beyond a threshold (default 3), when a further failure occurs, then `locked_until` is set to now plus a delay that doubles per failure from a base delay (default 1 s) up to a cap (default 15 min). Threshold, base delay and cap are properties.
- Given the backoff has elapsed and the correct password is submitted, then login succeeds and `failed_login_attempts` resets.
- Given an admin resets the account's password (Story 6), then `failed_login_attempts` and `locked_until` are cleared.
- Given repeated failed attempts from one IP across multiple usernames beyond a threshold, then further attempts from that IP are throttled independently of any account's state. The counter is in memory. `X-Forwarded-For` is trusted only when `server.forward-headers-strategy` is set for a known proxy.

### Logout

**Story 3** — As a **logged-in account holder**, I want to log out, so that my session is fully ended and cannot be reused.

- Given an active session, when the holder calls logout (CSRF-protected), then the server-side session is invalidated and the session cookie is cleared.
- Given a session cookie captured before logout, when it is replayed, then the server rejects it as unauthenticated.

### Protected content

**Story 4** — As a **logged-in account holder**, I want to see a personalized greeting, so that I can confirm my authentication worked.

- Given an authenticated session, when the holder requests `GET /api/hello`, then the response is `"Hello, <username>"`.
- Given no valid session, when `GET /api/hello` is requested, then the response is 401.

### Forced password change

**Story 5** — As an **account holder given a Temporary Password**, I want to set my own password, so that only I know it.

- Given an Account with `must_change_password = true`, when the holder logs in with the Temporary Password, then the session is created but every endpoint except change-password and logout returns 403 with a "password change required" problem detail.
- Given the holder submits `newPassword` and `confirmPassword` that match and meet the strength policy (length ≥ 12), when submitted to the change-password endpoint, then the password is updated, `must_change_password` and `temp_password_expires_at` are cleared, and all the holder's other sessions are invalidated.
- Given `newPassword` and `confirmPassword` differ, when submitted, then the request is rejected server-side and nothing changes.
- Given a Temporary Password older than `temp_password_expires_at` (default 24 h, a property), when the holder logs in, then login fails with the generic error.
- Given any change-password attempt, then the plaintext password is never logged or stored.

### Admin: account management

Admin endpoints live under `/admin/api/**` (the template's separate chain and OpenAPI spec), never `/api/admin/**`.

**Story 6** — As an **admin**, I want to create an account or reset a forgotten password, so that people can get access without email or database edits.

- Given an authenticated admin, when they `POST /admin/api/users` with a unique username and a Role, then the Account is created with `enabled = true`, a server-generated Temporary Password (returned once in the response, stored only as a hash), `must_change_password = true` and `temp_password_expires_at` set.
- Given a username that already exists, then the request is rejected with a conflict error.
- Given an admin resets another account (`POST /admin/api/users/{id}/reset-password`), then a new Temporary Password is generated and returned once, the account's sessions are invalidated, and backoff state is cleared.
- Given an admin targets their own account for reset, then the request is rejected; they use the change-password endpoint instead.
- Given a non-admin caller, then the response is 403.

**Story 7** — As an **admin**, I want to see all accounts, so that I can review who has access.

- Given an authenticated admin, when they call `GET /admin/api/users`, then each Account's username, Role, enabled status and created-at are listed. Password hashes, Temporary Passwords and lockout fields are never returned.
- Given an authenticated non-admin, then the response is 403.

**Story 8** — As an **admin**, I want to enable or disable another account, so that I can suspend access without deleting data.

- Given an admin targets another account, when they call the status endpoint, then `enabled` is updated, a disabled account can no longer log in, and its sessions are invalidated.
- Given an admin targets their own account, then the request is rejected.

**Story 9** — As an **admin**, I want to change another account's Role, so that I can grant or revoke privileges.

- Given an admin targets another account with a valid Role, then the Role is updated.
- Given an admin targets their own account, then the request is rejected.

**Story 10** — As an **admin**, I want to delete another account, so that I can remove accounts that should no longer exist.

- Given an admin targets another account, then the Account and its sessions are removed.
- Given an admin targets their own account, then the request is rejected.

### Admin bootstrap

**Story 11** — As an **operator deploying for the first time**, I want an initial admin created automatically, so that there's a way into the admin area without manual database edits.

- Given no `ADMIN` Account exists, when the application starts, then one is seeded from `app.admin.username` and `app.admin.password`, hashed like any other password. In cloud profiles the values come from AWS Secrets Manager; locally from a `local`-only property.
- Given a cloud profile with no admin password configured and no `ADMIN` Account, then startup fails. There is no default password anywhere.
- Given an `ADMIN` Account already exists, when the application restarts, then no duplicate is created.

## Non-Functional & Security Requirements

- **Password storage:** the template's `DelegatingPasswordEncoder` (BCrypt by default, `{bcrypt}` prefix), so hashes stay upgradeable. No custom hashing.
- **Template removals:** the `local` `{noop}password` login and the auto-provisioning of any username (`AppUserDetailsService`, `AppUserProvisioningListener`) are replaced by real credential checks.
- **Session security:** template cookie hardening stays (HttpOnly, `SameSite=strict`, cookie-only tracking, `Secure` under `feat-https`); session-fixation protection; sessions invalidated on logout, password change, admin reset, disable and delete.
- **CSRF:** enabled for all state-changing endpoints including login and logout, via the template's `XSRF-TOKEN` cookie and `X-XSRF-TOKEN` header.
- **No CORS:** same-origin only.
- **Enumeration resistance:** login failures use one generic error for unknown username, wrong password, disabled account and expired Temporary Password. There is no self-service path that reveals usernames.
- **Transport:** real deployments sit behind HTTPS; local dev over HTTP is a documented gap.
- **Audit logging:** structured log lines (actor + target) for login success/failure, backoff triggered, account created, password reset, password changed, and role change/enable/disable/delete. Never log passwords or Temporary Passwords.
- **Least privilege:** Role checks enforced server-side by Spring Security; `/admin/api/**` requires `ADMIN`.

## Data Model

Liquibase changelogs (H2 and MSSQL) extend the existing `app_user` table and add session tables.

### `app_user` (additions)

| Field | Type | Notes |
| --- | --- | --- |
| password_hash | string | delegating-encoder hash |
| enabled | boolean | admin can disable without deleting |
| failed_login_attempts | int | backoff tracking |
| locked_until | timestamp, nullable | backoff expiry |
| must_change_password | boolean | true after create/reset |
| temp_password_expires_at | timestamp, nullable | set with Temporary Password |

Existing `username` (unique), Roles (`app_user_role`) and audit columns are unchanged. `email` is unused.

### Spring Session JDBC tables

Created by changelog, not by `initialize-schema`.

## Testing Requirements

Automated integration tests for security-critical paths; other coverage is at the implementer's discretion.

- Login (Story 1): success, wrong password, unknown username (identical generic error), disabled account, CSRF required.
- Backoff (Story 2): threshold triggers delay, delay grows and caps, success after backoff resets, IP throttle independent of account state, admin reset clears backoff.
- Logout (Story 3): replayed session cookie rejected.
- Forced change (Story 5): 403 everywhere else until changed, mismatched confirm rejected, expired Temporary Password rejected, other sessions invalidated.
- Admin (Stories 6–10): non-admin gets 403 on every `/admin/api/**` endpoint; admin cannot reset, disable, demote or delete self; Temporary Password returned once and never listed.
- Bootstrap (Story 11): seeds once, no duplicate on restart, cloud startup fails without a password.

## Appendix: JWT Alternative

Documented for future extension; not part of this build.

- Access token issued on login, signed (e.g. HS256/RS256), short expiry (e.g. 15 min), sent via `Authorization: Bearer` header — **not** localStorage, to reduce XSS exposure; if stored client-side at all, prefer an in-memory variable.
- Refresh token with longer expiry, itself stored in an HttpOnly cookie or rotated on use.
- **Server-side logout requires a blacklist**: JWTs are stateless and valid until expiry, so true logout needs a `jti` revocation store (table or cache with TTL matching the token's remaining life) checked on every request.
- CSRF is less of a concern for header-based bearer tokens, but CORS and XSS-driven token theft become the primary risks.
- Trade-off vs. the chosen session cookie: JWT avoids server-side session storage but reintroduces statefulness via the blacklist, while adding client token-storage risk — this is why the session cookie is primary.
