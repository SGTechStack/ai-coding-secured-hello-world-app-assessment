# API contract details

Type: grilling
Status: resolved
Blocked by: —

## Question

Freeze the wire contract so backend and frontend build against the same shapes:

- **Endpoint list:** ratify the paths named in `assessment-wayfinder.md` (`/api/auth/register|login|logout`, `/api/auth/password-reset/request|confirm`, `/api/hello`, `/api/admin/users` + `PATCH …/status`, `PATCH …/role`, `DELETE …/{id}`) against canonical `assessment-prd.md`. Ticket 04 decided the auth-state probe: `GET /api/auth/me` returning the current principal or 401 — include it in the ratified list.
- **Error envelope:** RFC 7807 `application/problem+json` vs a simple `{ "error": "..." }` shape; validation-error format (field-level?).
- **Generic-message wording:** exact strings for login failure and reset-request success (enumeration resistance is an AC — the wording is a security control).
- **Password policy:** length ≥ 12 is specified; decide whether any complexity rules are added (PRD leaves it to the implementer).
- **Request/response shapes:** register/login bodies, `PATCH` bodies for status/role, user-list DTO fields.
- **Schema deviation to ratify:** ticket 03's research found "N failures *within a window*" isn't implementable without a failure timestamp — ratify or reject adding a nullable `last_failed_at` column to `users`.

## Answer

Decided:

- **Endpoint list (ratified):** `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout`, `POST /api/auth/password-reset/request`, `POST /api/auth/password-reset/confirm`, `GET /api/auth/csrf` (CSRF bootstrap, ticket 01), `GET /api/auth/me` (session probe, ticket 04), `GET /api/hello`, `GET /api/admin/users`, `PATCH /api/admin/users/{id}/status`, `PATCH /api/admin/users/{id}/role`, `DELETE /api/admin/users/{id}`.
- **Error envelope:** RFC 7807 `application/problem+json` via Spring's built-in `ProblemDetail`.
- **Generic messages:** login failure → `"Invalid username or password."`; reset-request → `"If an account with that email exists, we've sent a reset link."`
- **Password policy:** length ≥ 12 only — no complexity rules, no blocklist.
- **Schema deviation ratified:** add nullable `last_failed_at` to `users` so the lockout failure window is literal.
- **Request/response shapes (defaults):** register `{username, email, password}`; login `{username, password}`; `me` → `{username, role}`; `PATCH …/status` `{enabled: bool}`; `PATCH …/role` `{role: "USER"|"ADMIN"}`; user-list DTO `{id, username, email, role, enabled, createdAt}`; hello → `"Hello, <username>"`.
- **Observability:** Spring Actuator `/actuator/health` only — no metrics.
- **Reset-token mechanics:** 32 bytes from `SecureRandom`, base64url into the link; SHA-256 hash in `token_hash` (high-entropy token — no BCrypt needed).
- **EmailService stub default:** logs `http://localhost:3000/reset-password?token=<token>` (page exists per ticket 04).
