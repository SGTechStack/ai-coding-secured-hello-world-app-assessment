# Design and Security Notes

How the implementation meets [the PRD](../prd/assessment-prd.md), where the PRD left
choices open, and what a real deployment still needs.

## Architecture

```
Browser ── http://localhost:3000 ──▶ React SPA (Vite)
   │
   │  fetch(..., { credentials: 'include' })   HttpOnly session cookie + X-CSRF-TOKEN header
   ▼
http://localhost:8080 ──▶ Spring Boot API
   CORS allow-list → Spring Session (JDBC) → Spring Security (CSRF, authorization) → controllers
                                                                             │
                                                   H2 (dev/test) or PostgreSQL (prod), via Flyway
```

- **Backend** (`backend/`): Spring Boot 4.1, Spring Security 7, Spring Session JDBC, Spring Data
  JPA, Flyway. Java 21. Code is packaged by feature: `auth`, `passwordreset`, `admin`, `hello`,
  with shared `user`, `security`, `audit`, `throttle`, `web` and `config` packages.
- **Frontend** (`frontend/`): React 19, React Router, TypeScript, Vite. All HTTP calls go through
  `src/api/client.ts`.
- **Sessions** are stored in the database, so every session of a user can be found and deleted,
  and sessions survive a restart.

## API

Errors are always `application/problem+json` (RFC 9457) with a machine-readable `code`, and
`errors` (field → message) on validation failures. Every state-changing request needs the
`X-CSRF-TOKEN` header.

| Method | Path | Who | Success | Notable errors |
| --- | --- | --- | --- | --- |
| GET | `/api/auth/csrf` | anyone | 200 `{headerName, token}` | |
| POST | `/api/auth/register` | anyone | 201 `{id, username, role}` | 400 `VALIDATION_FAILED`, 409 `REGISTRATION_CONFLICT` |
| POST | `/api/auth/login` | anyone | 200 `{id, username, role}` and a new session cookie | 401 `INVALID_CREDENTIALS`, 429 `TOO_MANY_REQUESTS` + `Retry-After` |
| POST | `/api/auth/logout` | anyone | 204, cookie expired | |
| POST | `/api/auth/password-reset/request` | anyone | 202 generic message | 429 |
| POST | `/api/auth/password-reset/confirm` | anyone | 204 | 400 `INVALID_RESET_TOKEN`, 400 `VALIDATION_FAILED` |
| GET | `/api/me` | signed in | 200 `{id, username, role}` | 401 |
| GET | `/api/hello` | signed in | 200 `text/plain` `Hello, <username>` | 401 |
| GET | `/api/admin/users?page=0&size=20` | ADMIN | 200 `{items, page, size, totalItems, totalPages}` | 401, 403 |
| PATCH | `/api/admin/users/{id}/status` | ADMIN | 200 user, body `{"enabled": false}` | 403, 404, 409 `SELF_ACTION_NOT_ALLOWED` |
| PATCH | `/api/admin/users/{id}/role` | ADMIN | 200 user, body `{"role": "ADMIN"}` | 403, 404, 409 |
| DELETE | `/api/admin/users/{id}` | ADMIN | 204 | 403, 404, 409 |
| GET | `/actuator/health` | anyone | 200 `{"status":"UP"}` | |
| GET | `/.well-known/security.txt` | anyone | 200 `text/plain` (RFC 9116 disclosure contact) | |

Any other path is denied.

## Security requirements

| PRD requirement | Implementation |
| --- | --- |
| BCrypt, no custom hashing | `BCryptPasswordEncoder`, cost 12 (4 in tests). |
| HttpOnly, Secure, SameSite cookie | Cookie defined in `SessionCookieConfig`: always HttpOnly and `SameSite=Strict`; Secure everywhere except the dev profile; named `__Host-SESSION` in prod. |
| Session fixation protection | Session ID changes at login (`SessionLogin`). |
| Session timeout | Server sessions expire after 15 minutes idle. The SPA signs out after 15 minutes without input in any tab (`useIdleTimeout`). |
| Sessions invalidated on logout and password reset | Logout invalidates the session. A password reset deletes every session of the user from the session store. |
| CSRF on all state-changing endpoints | Spring Security synchronizer token held in the session. The SPA fetches it from `/api/auth/csrf`, and it is rotated at login. |
| CORS allow-list with credentials | Exact origins from `app.cors.allowed-origins` (wildcards rejected at startup), `Allow-Credentials: true`. |
| Enumeration resistance | Login: one 401 body for unknown user, wrong password, locked and disabled accounts, with one BCrypt comparison on every path. Reset request: the same 202 for any email, and the lookup runs asynchronously so response time does not reveal it either. |
| Audit logging | `AUDIT` logger (`AuditLog`). Events: registration, login success/failure (with reason), lockout, throttling, logout, reset requested/completed/rejected, enable/disable/role change/delete (actor and target), rejected self-actions, admin views of the user list, every 403 (role or CSRF failure), admin bootstrap. |
| Never log passwords | No audit field can hold a password. Request DTOs redact `toString()`. Validation errors never echo rejected values. A test captures all output of real flows and asserts no password appears. |
| Least privilege, server-side | URL rules in `SecurityConfig` plus `@PreAuthorize` on the admin controller. Roles come from the server-side session only. |

Other hardening: `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'` on API
responses, `X-Frame-Options: DENY`, `nosniff`, `Cache-Control: no-store`, `Referrer-Policy:
no-referrer`, and HSTS on HTTPS. The frontend build ships a strict CSP.

The frontend handles every "not authenticated" response in one place (`onUnauthenticated`),
shows an access-denied page for pages that need a role the user lacks, and wraps the app in an
error boundary that never renders error details. Every page links to the vulnerability
disclosure channel, which `/.well-known/security.txt` also publishes.

## Decisions where the PRD was open

1. **Account lockout** is 5 consecutive failures within 15 minutes, which locks the account for
   15 minutes. The "within a window" rule needs one column the PRD data model lacks:
   `users.last_failed_login_at`. Each attempt is counted under a row lock *before* the password
   is checked, so parallel guesses cannot exceed the limit. A test fires 20 concurrent guesses
   and exactly 5 are evaluated.
2. **IP throttling** allows 20 failed logins per client IP per 15-minute window, across all
   usernames. After that the API answers 429 with `Retry-After`. Throttled requests never reach
   an account, so they cannot add failures to it. Successful logins do not count, so many users
   behind one office NAT are not blocked.
   *Interpretation:* Story 3 says an attacker should not be able to lock out a user "merely by
   failing that user's password from one source". With an account limit of 5 below an IP limit
   of 20, one source can still trigger one account's temporary lock, which Story 3's first
   criterion requires. The lock expires after 15 minutes, is cleared by a password reset, and a
   throttled source cannot extend it. An IP limit below 5 would prevent single-source lockouts,
   but would also throttle whole offices sharing one address.
3. **Locked and disabled accounts** get the same generic 401 as a wrong password. The real
   reason goes only to the audit log.
4. **Passwords** must be at least 12 characters and at most 72 bytes. BCrypt ignores everything
   past 72 bytes, and in this Spring Security version `matches()` silently truncates, so login
   also rejects longer input. Without that, `<72-byte password>` + anything would log in.
5. **Sessions are also revoked** when an admin disables a user, changes a role, or deletes a
   user. Otherwise a demoted admin would keep admin rights until the session timed out. Idle
   timeout is 15 minutes, the IM8 threshold. The PRD does not specify one.
6. **Password reset tokens** are 256 random bits, and only their SHA-256 hash is stored. They
   expire after 30 minutes and are single-use (enforced with a row lock). Requesting a new link
   invalidates the old one. The token travels in the URL fragment (`#token=...`), so it is never
   sent to a server or leaked in a Referer header. The page removes it from the address bar
   after reading it. Reset requests are limited to 5 per IP per 15 minutes, against email
   flooding. A successful reset also clears any lockout. Expired tokens are purged hourly.
7. **Usernames and emails are case-insensitive** (stored lowercase), so `Alice` and `alice` cannot
   be two accounts. Usernames are 3-32 characters from `A-Z a-z 0-9 . _ -`.
8. **Admin self-actions** return 409 `SELF_ACTION_NOT_ALLOWED`, which keeps them distinct from
   the 403 a non-admin gets.
9. **Registration conflicts** return 409 with per-field errors, as the PRD asks for a clear
   conflict error. This necessarily reveals that a username or email is taken. Login and
   password reset do not leak account existence.
10. **Admin bootstrap** uses `app.admin.username` (default `admin`), `app.admin.email`, and
    `app.admin.password`. The password comes from the `APP_ADMIN_PASSWORD` env var and has no
    default. Bootstrap runs whenever no *enabled* ADMIN exists: a disabled admin cannot sign in,
    so it does not count. If the password is missing or fails the policy, startup fails. Bootstrap
    never takes over an existing account with the configured username or email, even a disabled
    admin. Instead startup fails and asks for unused values.
11. **Why the session cookie is configured in code:** Spring Boot applies
    `server.servlet.session.cookie.*` only when it runs its own embedded server. In a WAR
    deployment it takes the servlet container's settings instead, which are not HttpOnly. A test
    caught this, so the cookie is now defined explicitly.
12. **Boot's default in-memory user** (a generated password printed at startup) is switched off.

## PRD test requirements

Run with `cd backend && ./mvnw test`. The integration tests boot the full application and go
through the real filter chain (Spring Session, CORS, CSRF, authorization) with an H2 database.

| PRD requirement | Test |
| --- | --- |
| Login: success, wrong password, unknown username (same error), locked | `LoginIntegrationTests`: `successfulLoginCreatesHardenedSessionAndResetsFailedAttempts`, `wrongPasswordAndUnknownUsernameAreIndistinguishable`, `lockedAccountRejectsCorrectPasswordWithTheSameGenericError` |
| Lockout after N failures, reset after cooldown | `LockoutIntegrationTests`: `fifthConsecutiveFailureLocksTheAccount`, `correctPasswordAfterCooldownSucceedsAndResetsTheCounter`, `parallelGuessesCannotExceedTheThreshold` |
| IP throttling independent of account lockout | `IpThrottleIntegrationTests`: `ipIsThrottledAcrossUsernamesWithoutLockingAnyAccount`, `throttledIpCannotAddFailuresToAVictimAccount` |
| Reused cookie rejected after logout | `LogoutIntegrationTests.logoutInvalidatesTheSessionAndClearsTheCookie` |
| Reset token single use, expiry, sessions invalidated | `PasswordResetIntegrationTests`: `validTokenSetsNewPasswordAndCanOnlyBeUsedOnce`, `expiredTokenIsRejectedAndPasswordIsUnchanged`, `resetInvalidatesEveryExistingSessionOfTheUser` |
| Admin cannot disable, delete or demote self | `AdminIntegrationTests.adminCannotDisableDemoteOrDeleteThemselves` |
| USER gets 403 on `/api/admin/**` | `AdminIntegrationTests.userRoleGets403OnEveryAdminEndpoint` |

Also covered: registration (`RegistrationIntegrationTests`), admin bootstrap
(`AdminBootstrapIntegrationTests`), audit events and the absence of passwords in logs
(`AuditLoggingIntegrationTests`), CORS, headers and default-deny (`WebSecurityIntegrationTests`),
and session fixation (`LoginIntegrationTests.loginChangesTheSessionIdToPreventFixation`).
Frontend tests (`cd frontend && npm test`) cover:
- the API client's CSRF handling and ended-session detection
- sign-in and the reset-link flow
- the admin page and the access-denied page
- the inactivity sign-out and the error boundary

The full backend suite was also run once against PostgreSQL 18.6 (embedded, via
`io.zonky.test:embedded-postgres`), and all 71 tests passed. That harness is not committed.

Compliance and threat analysis: the IM8 review is in `artifacts/`, and the threat model is in
[`docs/threat-model/`](threat-model/threat-model.md).

## Before a real deployment

- Run with `SPRING_PROFILES_ACTIVE=prod` behind a TLS-terminating proxy, and set the environment
  variables listed in `application-prod.yml`.
- **Provide a real `EmailService`.** The logging stub is disabled in prod, and the app will not
  start without a real implementation.
- **IP throttle state is in memory, per instance.** Account lockout is in the database and is
  shared. With several instances, move the throttle to a shared store such as Redis.
- **Serve the frontend's `dist/` with the headers** shown in `vite.config.ts` under `preview`,
  including CSP with `frame-ancestors`.
- **Known race between admins:** two admins who disable or demote each other at the same moment
  can both succeed and leave no enabled admin. Recovery needs no database edit: restart with
  unused `APP_ADMIN_USERNAME` and `APP_ADMIN_EMAIL`, and bootstrap seeds a new admin.
- **Accepted gaps (outside the PRD's scope):**
  - Registration has no bot protection.
  - There is no MFA.
  - MySQL would need its own migrations for the `UUID` and `TIMESTAMP WITH TIME ZONE` columns.
