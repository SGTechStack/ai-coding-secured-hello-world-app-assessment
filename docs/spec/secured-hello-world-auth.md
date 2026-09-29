# Spec: Secured Hello World Auth

Status: implemented on branch `junwen`. Behavior comes from `prd/assessment-prd.md`. Session storage is ADR 0001. Reset-token hashing is ADR 0002. The password rule is ADR 0003.

## Problem

A visitor needs an account and a way to sign in before they can see a protected greeting. An admin needs to see who has access and to suspend, re-role, or remove someone else. The app must not reveal whether a username or email exists, must slow repeated guessing, and must refuse a session after logout or a password reset.

## What ships

A React app on port 3000 calls a Spring Boot API on port 8080 with `credentials: 'include'`. Registration stores a BCrypt hash and creates a `USER` account. Login opens a server session and sets the `HELLOSESSION` cookie. `GET /api/hello` returns `Hello, <username>` for that session and 401 otherwise.

Five bad passwords in a row set `locked_until` for 15 minutes. A locked account is rejected even with the right password, and the client still sees the same message as a wrong password. A separate in-memory counter throttles one IP after 20 failures in 15 minutes, including failures for usernames that do not exist, so spraying names cannot be used to lock a single account without also hitting the IP limit.

Forgot-password always returns the same success sentence. If the email is registered, the API stores a SHA-256 digest of a random token for 20 minutes and logs the link. Confirming it changes the password, marks the token used, and deletes that account's sessions. The caller is not signed in by the reset.

An admin can list accounts and can disable, change role, or delete another account. Those three actions return 409 when the target is the caller. The first startup seeds one admin from `app.admin.username` and `app.admin.password` when no admin exists.

## Stories the build covers

1. A visitor registers with a unique username, unique email, and a password of at least 12 characters. The account is `USER`, enabled, and the password is stored as BCrypt.
2. A duplicate username or email is a 409 and creates nothing. A short password is a 400 and creates nothing. The plaintext password is not logged or stored.
3. A correct login for an enabled, unlocked account sets the session cookie and clears `failed_login_attempts`.
4. A wrong password, an unknown username, a disabled account, and a locked account all return 401 with `Invalid username or password`.
5. The fifth consecutive failure sets `locked_until`. After that time has passed, the right password works and the counter returns to zero.
6. Twenty failures from one IP produce 429 on the next attempt even when the target account has never failed. Another IP can still log in.
7. Logout invalidates the session. Replaying the old cookie gets 401.
8. Reset request returns one generic message whether or not the email exists. A match stores only the token hash, with a 20-minute expiry, and the stub logs the link.
9. A valid token sets a new password, marks the token used, and kills existing sessions. An expired or reused token does not change the password.
10. An admin list includes username, email, role, enabled, and created-at, and never the password hash. A `USER` calling any `/api/admin/**` route gets 403.
11. An admin can disable, re-role, or delete another account, and cannot do any of those to themselves. A disabled account cannot log in. Disable and role change also drop that account's sessions.
12. Startup creates the configured admin only when the database has no admin yet.

CSRF stays on for register, login, logout, both reset calls, and the admin writes. CORS allows `http://localhost:3000` with credentials. Audit lines cover login success and failure, lockout, IP throttle, reset requested and completed, and admin changes. They name the actor and the target. They do not include passwords or token values except the reset link, which the stub is required to log.

## API

Errors are JSON `{ "message": "..." }`. Success bodies are camelCase.

| Method | Path | Who | Success | Failure |
| --- | --- | --- | --- | --- |
| GET | `/api/auth/csrf` | Public | 200 `{ token, headerName }` and the CSRF cookie | |
| POST | `/api/auth/register` | Public + CSRF | 201 account, no hash | 400 weak password, 409 taken |
| POST | `/api/auth/login` | Public + CSRF | 200 account and `HELLOSESSION` | 401 generic, 429 throttled |
| POST | `/api/auth/logout` | Signed in + CSRF | 204 | 401 |
| GET | `/api/auth/me` | Signed in | 200 account | 401 |
| POST | `/api/auth/password-reset/request` | Public + CSRF | 200 generic | 400 bad email |
| POST | `/api/auth/password-reset/confirm` | Public + CSRF | 200, sessions deleted | 400 bad token or weak password |
| GET | `/api/hello` | Signed in | 200 `{ message: "Hello, <username>" }` | 401 |
| GET | `/api/admin/users` | Admin | 200 account list | 401 anonymous, 403 user |
| PATCH | `/api/admin/users/{id}/enabled` | Admin + CSRF | 200 account | 409 self, 403 user |
| PATCH | `/api/admin/users/{id}/role` | Admin + CSRF | 200 account | 409 self, 403 user |
| DELETE | `/api/admin/users/{id}` | Admin + CSRF | 204 | 409 self, 403 user |

The account body is `{ id, username, email, role, enabled, createdAt }`.

The SPA cannot read a cookie set by port 8080, so the CSRF cookie is `HttpOnly` and `GET /api/auth/csrf` returns the token. The browser stores it in memory and sends `X-XSRF-TOKEN`. State-changing calls still require that header to match the cookie.

Login of an unknown username still runs a BCrypt compare against a dummy hash so the failure path does similar work to a wrong password.

## Data

`users`: id (UUID), username, email, password_hash, role (`USER` or `ADMIN`), enabled, failed_login_attempts, locked_until, created_at.

`password_reset_tokens`: id, user_id, token_hash, expires_at, used_at.

Emails are stored lowercased. Usernames are matched as entered.

## Tests

`SecurityRequirementTests` drives MockMvc through the security filter and Spring Session. It covers the login messages, lockout and cooldown, IP throttle without locking the victim, cookie replay after logout, reset single-use, expiry, and session kill, the admin self-action 409s, and 403 for a normal user on admin routes.

## Out of scope

JWT, MFA, real email, containers, CI, local HTTPS, and authorization finer than `USER` / `ADMIN`. IP throttling is in-memory and applies to one API process. The dev profile ships a config admin password so the directory can be opened; a production profile must set `app.security.secure-cookies` and supply its own admin password.
