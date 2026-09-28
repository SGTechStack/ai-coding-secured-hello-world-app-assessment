# Design decisions — Helloworld Auth

Companion to [`prd/assessment-prd.md`](prd/assessment-prd.md). The PRD says what the application must
do; this says how the parts it left open were settled, and why.

> **The PRD wins.** Where this document and the PRD disagree, the PRD is correct and this one is stale.
> The stories and acceptance criteria are reproduced below so this document reads on its own, but the
> PRD is canonical if they ever drift.

Start with **Resolved Decisions** near the end: those are the ten questions the PRD deliberately left
unanswered, and the code cites them by number. Several behaviours that look like omissions — that
registration does not log you in, that a locked account gets the same error as a wrong password — are
recorded choices with reasons, not gaps.

## Problem Statement

A team that needs cookie-session authentication has two unappealing places to look. Production
codebases have it tangled up with domain logic that obscures the pattern. Demos and tutorials
show the shape but take every shortcut — passwords in configuration, no lockout, no CSRF, no
logout — and so teach the wrong habits by omission. Someone copying a demo gets something that
looks finished and is unsafe.

What's missing is a reference implementation small enough to read end to end, but built to a
production security baseline rather than a demo one: real password hashing, real lockout, real
CSRF, real session invalidation, and a documented reason for each choice — including for the
approach that was considered and *not* taken.

## Solution

A two-origin application: a React frontend and a Spring Boot REST backend, deliberately kept on
separate origins so the cross-origin session-cookie problem is solved properly rather than
side-stepped.

From each role's perspective:

- A **visitor** can register with a username, email and password, then log in. Failed attempts
  count against them, and enough of them locks the account; hammering many accounts from one
  address gets that address throttled instead. Nothing in any response tells them whether a
  username or email exists.
- A **user** logs in and gets a server-side session behind a secure `HttpOnly` cookie, sees a
  greeting proving authentication worked, can log out for real — the cookie is dead afterwards,
  not merely forgotten — and can recover a forgotten password through a single-use, short-lived,
  hashed token emailed by a stubbed service.
- An **admin** can list accounts, disable one without deleting its data, move an account between
  `USER` and `ADMIN`, and delete an account — but never to their own account, in any of the
  three cases. The first admin is seeded at startup from configuration, so there is a way in
  without editing the database by hand.

Authentication is a server-side session via Spring Session. A JWT bearer-token alternative is
documented in the PRD appendix with its trade-offs, and deliberately not built.

## User Stories

Connextra format, reproduced from the PRD with their acceptance criteria.

### Registration

**1.** As a **visitor**, I want to register an account with a username, email, and password, so that I can log in and access the protected app.

- Given a visitor submits a unique username, unique email, and a password meeting the minimum strength policy (length ≥ 12), when they submit registration, then an account is created with role `USER`, `enabled = true`, and the password stored as a BCrypt hash.
- Given a visitor submits a username or email that's already registered, when they submit registration, then the request is rejected with a clear validation error (username/email conflict), and no account is created.
- Given a visitor submits a password that fails the strength policy, when they submit registration, then the request is rejected with a validation error and no account is created.
- Given any registration attempt, when handled, then the plaintext password is never logged or stored.

### Login

**2.** As a **registered user**, I want to log in with my username and password, so that I can access my session and the protected app content.

- Given a registered, enabled, non-locked account with correct credentials, when the user submits login, then a server-side session is created, a secure session cookie is set, and `failed_login_attempts` resets to 0.
- Given incorrect credentials, when the user submits login, then the request is rejected with a generic error message that does not reveal whether the username exists, and `failed_login_attempts` increments.
- Given an account that is currently locked (`locked_until` in the future), when the user submits login with correct credentials, then the request is still rejected until the lockout expires.

**3.** As a **security-conscious operator**, I want repeated failed logins to trigger account lockout and IP-level throttling, so that brute-force credential guessing is blunted.

- Given N consecutive failed login attempts against one account within a window (e.g. 5 attempts), when the Nth failure occurs, then the account is locked for a cooldown period (e.g. 15 minutes) by setting `locked_until`.
- Given a locked account, when the cooldown period elapses and the correct password is submitted, then login succeeds and `failed_login_attempts` resets.
- Given repeated failed login attempts from one IP address across multiple usernames, when a threshold is exceeded, then further attempts from that IP are throttled independently of any single account's lockout state — so an attacker cannot lock out a legitimate user merely by failing that user's password from one source.

### Logout

**4.** As a **logged-in user**, I want to log out, so that my session is fully ended and cannot be reused.

- Given an active session, when the user calls logout, then the server-side session is invalidated and the session cookie is cleared.
- Given a session cookie captured before logout, when it is replayed after logout, then the server rejects it as unauthenticated.

### Protected content

**5.** As a **logged-in user**, I want to see a personalized greeting, so that I can confirm my authentication actually worked.

- Given an authenticated session, when the user requests `GET /api/hello`, then the response is `"Hello, <username>"`.
- Given no session (or an invalid/expired one), when a request is made to `GET /api/hello`, then the response is unauthorized (401).

### Password reset

**6.** As a **user who forgot their password**, I want to request a password reset via my registered email, so that I can regain access without contacting an admin.

- Given a request with an email address, when submitted to the password-reset-request endpoint, then the response is a generic success message regardless of whether the email is registered — so account existence cannot be inferred.
- Given the email matches a registered user, when the request is processed, then a single-use reset token is generated, its hash (not the plaintext token) is stored with a short expiry (15–30 min), and `EmailService.sendPasswordResetEmail(...)` is called (stub implementation logs the link instead of sending mail).

**7.** As a **user with a valid reset token**, I want to set a new password, so that I can regain access to my account.

- Given a valid, unexpired, unused reset token and a new password meeting the strength policy, when submitted to the password-reset-confirm endpoint, then the password is updated, the token is marked used, and all existing sessions for that user are invalidated.
- Given an expired token, when submitted, then the request is rejected and the password is not changed.
- Given a token that has already been used once, when submitted again, then the request is rejected (single-use enforcement).

### Admin: user management

**8.** As an **admin**, I want to see a list of all registered users, so that I can review who has access to the system.

- Given an authenticated admin, when they call `GET /api/admin/users`, then the response lists each user's username, email, role, enabled status, and created-at date — never password hashes.
- Given an authenticated non-admin user, when they call `GET /api/admin/users`, then the response is forbidden (403).

**9.** As an **admin**, I want to enable or disable another user's account, so that I can suspend access without deleting their data.

- Given an admin targets another user's account, when they call the status-toggle endpoint, then the account's `enabled` flag is updated accordingly, and a disabled user can no longer log in.
- Given an admin targets their own account via the status-toggle endpoint, when the request is made, then it is rejected — an admin cannot disable themselves.

**10.** As an **admin**, I want to change another user's role between USER and ADMIN, so that I can grant or revoke admin privileges.

- Given an admin targets another user's account, when they call the role-change endpoint with a valid role, then the account's role is updated.
- Given an admin targets their own account via the role-change endpoint, when the request is made, then it is rejected — an admin cannot demote themselves.

**11.** As an **admin**, I want to delete another user's account, so that I can remove accounts that should no longer exist.

- Given an admin targets another user's account, when they call the delete endpoint, then the account is removed.
- Given an admin targets their own account via the delete endpoint, when the request is made, then it is rejected — an admin cannot delete themselves.

### Admin bootstrap

**12.** As an **operator deploying the app for the first time**, I want an initial admin account to be created automatically, so that there's a way into the admin module without manual database edits.

- Given no `ADMIN` user exists in the database, when the application starts, then one is seeded using credentials supplied via configuration (e.g. `app.admin.username`, `app.admin.password`), with the password hashed identically to any other account.
- Given an `ADMIN` user already exists, when the application restarts, then no duplicate seed account is created.

## Non-Functional & Security Requirements

Cross-cutting, binding on every story that touches them. Reproduced from the PRD.

- **Password storage:** BCrypt (`BCryptPasswordEncoder`); no custom hashing.
- **Session security:** `HttpOnly`, `Secure` (prod), `SameSite` cookie attributes; session-fixation protection; sessions invalidated on logout and password reset.
- **CSRF:** Enabled for all state-changing endpoints (register, login, logout, password reset, admin mutations), since auth is cookie-based.
- **CORS:** Explicit allow-list of the frontend origin(s); `Access-Control-Allow-Credentials: true` for the session cookie to travel cross-origin.
- **Enumeration resistance:** Login and password-reset-request responses never reveal whether a username/email exists.
- **Transport:** Any real deployment must sit behind HTTPS (required for `Secure` cookies and HSTS); local dev over HTTP is a documented, accepted gap.
- **Audit logging:** Structured log lines (no dedicated table required) for login success/failure, lockout triggered, password reset requested/completed, and role change/enable/disable/delete (actor + target). Never log passwords.
- **Least privilege:** Role checks enforced server-side via Spring Security; never trusted from client-supplied state.

## Data Model

**`users`** — `id` (UUID/long, PK), `username` (string, unique, the login identifier), `email`
(string, unique, used only for password reset), `password_hash` (string, BCrypt), `role` (enum
`USER` | `ADMIN`), `enabled` (boolean, lets an admin suspend without deleting),
`failed_login_attempts` (int, lockout tracking), `locked_until` (timestamp, nullable),
`created_at` (timestamp).

**`password_reset_tokens`** — `id` (UUID/long, PK), `user_id` (FK → `users`), `token_hash`
(string, stored hashed, never plaintext), `expires_at` (timestamp, 15–30 min),
`used_at` (timestamp, nullable, single-use enforcement).

Field-level detail is tabulated in the PRD. The schema is written for H2 in the dev profile but
must stay portable to Postgres and MySQL, which rules out H2-specific types and functions.

## Implementation Decisions

**Spring Boot backend, React frontend, two separate origins.** Backend on its own origin (e.g.
`localhost:8080`), frontend on its own (e.g. `localhost:3000`). This is a deliberate choice, not
an accident of tooling: keeping them on one origin via a dev proxy would hide the cross-origin
credential problem that any real deployment has, and the PRD wants that problem solved rather
than avoided.

**CORS with credentials, not a dev-server proxy.** An explicit allow-list of frontend origins
with `Access-Control-Allow-Credentials: true`. Wildcard origins are incompatible with credentialed
requests and are therefore forbidden here as well as unwise.

**Spring Security for authentication and authorization; Spring Session for session storage.**
Role checks are enforced server-side through Spring Security and never inferred from anything the
client sends. Session-fixation protection comes from the framework and must not be disabled.

**Spring Data JPA over H2 in the dev profile.** Schema portable to Postgres/MySQL later.

**BCrypt via `BCryptPasswordEncoder`.** No custom hashing, no alternative algorithm.

**CSRF enabled for every state-changing endpoint.** Cookie-based auth means an ambient credential,
which means CSRF is live. This covers register, login, logout, both password-reset endpoints, and
all admin mutations. The React client needs a token-retrieval path; the mechanism is an open
question below.

**Lockout is per-account state on the `users` row; IP throttling is a separate mechanism.** The
two must be independent, and the reason is in the PRD: if throttling were keyed only to the
account, an attacker could lock a victim out of their own account by failing that victim's
password from one address. Account lockout uses `failed_login_attempts` and `locked_until`
(defaults: 5 attempts, 15-minute cooldown, auto-lifting). A successful login resets the counter.

**Reset tokens are hashed at rest, short-lived, and single-use.** The plaintext token goes only to
`EmailService`; only its hash is stored. Expiry 15–30 minutes. Redemption marks `used_at` and
invalidates every existing session for that user, so a password reset ends sessions an attacker
may already hold.

**`EmailService` is a stub that logs the reset link.** Real SMTP is out of scope. The interface
should be the one a real implementation would satisfy, so swapping it in needs no caller changes.

**Admin bootstrap runs at startup and is idempotent.** If no `ADMIN` exists, seed one from
configuration (`app.admin.username`, `app.admin.password`), hashed the same way as any other
account. If one exists, do nothing — restarts must not accumulate seed accounts.

**API surface.** `GET /api/hello` and `GET /api/admin/users` are fixed by the PRD. The remaining
endpoints — register, login, logout, password-reset-request, password-reset-confirm, and the admin
status-toggle, role-change and delete operations — are described by function in the PRD without
paths, and are listed as an open question below rather than invented here.

**Enumeration resistance is about response content.** Login must return a generic error that does
not distinguish "no such username" from "wrong password", and password-reset-request must return
the same generic success whether or not the email is registered. Note what the PRD does *not*
require: identical response *timing*. Constant-time behaviour is not a stated requirement, so it
is not assumed here.

**Audit logging is structured log lines, not a table.** Required events: login success and failure,
lockout triggered, password reset requested and completed, and each admin mutation with both actor
and target. Passwords never appear.

## Testing Decisions

**What the PRD mandates.** Automated integration tests for the security-critical paths, with
general CRUD and UI coverage left to implementer discretion. The required set, mapped to stories:

| Story | Required coverage |
|---|---|
| 2 — Login | success; wrong password; unknown username producing an identical generic error; account locked |
| 3 — Lockout | N failures trigger lockout; login after cooldown succeeds and resets the counter; IP throttling engages independently of account lockout |
| 4 — Logout | a session cookie replayed after logout is rejected |
| 6–7 — Password reset | token single-use; token expiry; reset invalidates existing sessions |
| 9–11 — Admin self-action guard | admin cannot disable, delete or demote their own account |
| 8 — Role enforcement | a `USER` calling any `/api/admin/**` endpoint receives 403 |

**The seam is the backend's HTTP boundary.** Spring integration tests — `@SpringBootTest` with
`MockMvc` or a test rest client, against a real H2 schema — because every required behaviour above
is observable there and most are *only* observable there. Lockout counters, session replay after
logout, token single-use and expiry, and the admin self-action guards are all server-side state
transitions that a browser cannot inspect and should not be asked to.

**This reverses the previous single-browser-seam decision.** The superseded spec chose one
browser seam on the grounds that every acceptance criterion was written from the user's vantage
point. Under the PRD that is no longer true: the criteria are written in terms of
`failed_login_attempts`, `locked_until`, `used_at`, HTTP status codes and role checks. The seam
has to move to where the behaviour lives. Browser tests are not forbidden, but they are no longer
the required seam and no longer sufficient.

**What makes a good test here.** It asserts an externally observable outcome of an HTTP request —
status code, response body, resulting database state, whether a replayed cookie is accepted — and
not which class computed it. Every required test traces to a story. The negative cases are the
point: an "unknown username" test that merely asserts a 401 is worthless unless it also asserts
the response is *indistinguishable* from the wrong-password case.

**Prior art.** None survived. The previous tests were 9 Playwright browser specs written against a
TypeScript Express service the PRD replaces, testing behaviours the PRD does not require at a seam
it does not mandate. They were deleted rather than adapted.

**What was built (2026-09-24).** 72 tests in `api/src/test`, at the HTTP boundary as decided above,
covering every mandated row of the table plus the enumeration-resistance and CSRF requirements that
belong to no single story. The test client (`support/ApiClient`) is hand-rolled rather than
`TestRestTemplate` for one reason: several requirements are about cookies specifically — that a
captured cookie is worthless after logout, that login replaces rather than reuses a session — and
those need a client whose cookie jar can be inspected and forged. A client that manages cookies
invisibly cannot express them.

**A second, smaller browser seam exists after all** — 9 Playwright specs in `web/e2e`. This does not
contradict the decision above: they are not the required seam and nothing mandated is tested only
there. They cover the one thing HTTP-level tests structurally cannot, which is whether the React
application works at all. A backend can pass all 72 of its tests while the frontend fails to render,
omits the CSRF header, or drops the credentials flag — and each of those looks like a working
application right until someone opens it.

**One gap neither suite closes.** The CORS allow-list and the preflight response cannot be checked by a
test running inside the server process, nor by a browser test that only ever uses the allowed origin.
Verifying that a *disallowed* origin is refused needs a request made from outside both — which was done
by hand against a running pair of servers, and is not automated here.

## Out of Scope

From the PRD:

- **JWT implementation.** Designed in the PRD appendix only, with the trade-off reasoning for why
  session-cookie was chosen instead.
- **Multi-factor authentication.**
- **Real SMTP / email delivery.** `EmailService` is a stub that logs.
- **Containerization, CI/CD, hosting infrastructure.**
- **Local HTTPS.** A documented, accepted gap; any real deployment must sit behind HTTPS for
  `Secure` cookies and HSTS.
- **Granular per-resource authorization** beyond the `USER` / `ADMIN` check on admin endpoints.

Worth stating plainly, because an earlier version of this project treated them as optional: password
hashing, CSRF, rate limiting, account lockout, password reset, roles, logout, session invalidation and
a real database are **requirements**, not nice-to-haves. Nothing here may rely on their absence.

## Resolved Decisions

The PRD leaves ten things unsettled. None of them block starting, but every one of them gets
decided by somebody, so they are decided here rather than by accident during implementation. Each
is a *decision*, not a PRD requirement: if the PRD is later extended to cover one of these, the
PRD wins and the entry below is stale.

**1. Endpoint paths.** Only `GET /api/hello` and `GET /api/admin/users` are fixed by the PRD. The
rest:

| Operation | Method and path |
|---|---|
| CSRF bootstrap | `GET /api/auth/csrf` |
| Register | `POST /api/auth/register` |
| Log in | `POST /api/auth/login` |
| Log out | `POST /api/auth/logout` |
| Current session | `GET /api/auth/me` |
| Request password reset | `POST /api/auth/password-reset/request` |
| Confirm password reset | `POST /api/auth/password-reset/confirm` |
| List accounts | `GET /api/admin/users` |
| Enable / disable account | `PATCH /api/admin/users/{id}/status` |
| Change role | `PATCH /api/admin/users/{id}/role` |
| Delete account | `DELETE /api/admin/users/{id}` |

`GET /api/auth/me` is an addition, not in the PRD. The frontend needs to know on page load whether
a session exists, and the alternative — probing `/api/hello` and treating a 401 as "not logged in"
— overloads a story-5 endpoint with routing duties. `PATCH` for the two mutations because both are
partial updates to an existing account; `PUT` would imply sending the whole representation.

**2. CSRF token delivery: bootstrap endpoint, not a JS-readable cookie.** `GET /api/auth/csrf`
returns the token as JSON; the client echoes it in an `X-XSRF-TOKEN` header. The double-submit
cookie pattern — the usual SPA shortcut — cannot work here: the frontend is a different origin from
the backend, so `document.cookie` on `localhost:3000` cannot read a cookie set by `localhost:8080`,
regardless of the `HttpOnly` flag. The backing store is still `CookieCsrfTokenRepository`, so the
server compares header against cookie as usual; only the *delivery* of the value to the client
changes. Worth being precise about why the cookie still arrives at all: `localhost:3000` and
`localhost:8080` are cross-*origin* but same-*site* (ports don't affect site), so a `SameSite=Lax`
cookie is sent. A real deployment on genuinely different sites needs `SameSite=None; Secure`, which
is one more reason the PRD's HTTPS requirement is not optional.

**3. Lockout counter: consecutive failures, reset on success or on lockout expiry.** `5`
consecutive failures locks the account for `15` minutes. The PRD's phrase "N consecutive failed
attempts within a window" combines two different rules; the simpler half is chosen. There is no
sliding window on the counter — it is not decremented by the passage of time — so four failures
last week plus one today locks the account. The counter resets to `0` on a successful login, and
also when an expired lockout is observed, so a locked-out user is not permanently one failure away
from re-locking. Lockout auto-lifts: `locked_until` in the past is not a lock, and nothing has to
run on a schedule to clear it.

**4. IP throttling: 10 failures per IP per 15 minutes, in-memory, login and reset-request both.**
Keyed on client IP, counting failures across all usernames, returning `429` once exceeded. This is
the mechanism that has to stay independent of account lockout, per the PRD's reasoning. It also
covers `POST /api/auth/password-reset/request`, where the counter increments on *every* call rather
than only on failures — that endpoint deliberately cannot distinguish success from failure, and
leaving it unthrottled would hand an attacker an unmetered oracle and a free mail-send amplifier.
Storage is an in-memory sliding window: correct for a single instance, and wrong the moment a
second instance exists, since each would keep its own count. That is an accepted limitation of a
reference app, not a design claim; a real deployment needs a shared store such as Redis, or
throttling moved to the ingress.

**5. Session timeout: 30 minutes idle, no absolute cap.** Story 5 mentions an expired session, so
expiry exists; no policy is given. Idle timeout only, via
`server.servlet.session.timeout`. An absolute session lifetime is a real control and is
deliberately *not* implemented, because Spring Session has no built-in absolute expiry and hand-
rolling one is more machinery than a reference app should carry undocumented. Recorded as a
known gap rather than silently omitted.

**6. Registration does not log the visitor in.** `POST /api/auth/register` returns `201` and the
client sends them to the login page with a success notice. Two reasons. Story 1's criteria say an
account is created and say nothing about a session, and story 2 owns session creation — auto-login
would put session-creation logic in two places. It also keeps registration free of the
authentication concerns that go with issuing a session, including session fixation.

**7. `GET /api/hello` returns `text/plain`.** The body is exactly `Hello, <username>` with no
quotes, braces or trailing newline. The PRD writes the response *as* a bare string; a JSON envelope
like `{"message": "..."}` would be a different response shape than the one written down. The test
asserts the exact bytes and the `Content-Type`.

**8. The admin stories get a UI.** Stories 8 through 11 are written in Connextra form from an
admin's point of view, which means a person doing this through a screen. The mandated tests are all
API-level, so an API-only admin module would pass every required test and still not deliver the
stories — the tests are a floor, not the definition of done. Scope is one admin page: the account
table from story 8, plus the three mutations, each behind a confirmation for the destructive or
privilege-changing ones.

**9. Password policy: length only, 12 to 72 characters.** Length ≥ 12 is the PRD's entire stated
policy and no complexity rule, breach-list check or rotation requirement is invented on top of it —
current guidance favours length over composition rules anyway. The upper bound of 72 is not
arbitrary and is not a policy preference: BCrypt silently truncates input beyond 72 bytes, so
without it, two different long passwords could authenticate the same account. Rejecting at the
boundary is honest; truncating quietly is not.

**10. A failed login against an already-locked account does nothing.** It neither increments
`failed_login_attempts` nor extends `locked_until`. The request is rejected before credentials are
checked. The alternative — extending the lockout on every attempt — lets an attacker hold a
legitimate user out indefinitely with a trickle of junk attempts, which is the same attack the PRD
explicitly wants IP throttling to prevent. IP throttling still applies to these attempts, so they
are not free.

## Known Gaps

Accepted, deliberate, and traceable — not oversights:

- **No absolute session lifetime** (decision 5). Idle timeout only.
- **IP throttle state is per-instance** (decision 4). Horizontal scaling silently weakens it.
- **Local dev runs over HTTP**, so the `Secure` cookie attribute is off in the dev profile. From
  the PRD's out-of-scope list; the prod profile sets it.
- **`EmailService` logs instead of sending.** From the PRD's out-of-scope list. The reset link,
  including the plaintext token, appears in the application log in dev — which is exactly why it
  is a dev-profile-only behaviour and why the token is stored hashed.
- **No constant-time response behaviour.** Enumeration resistance is about response *content*, per
  the PRD; equalising timing is not a stated requirement and is not attempted.
