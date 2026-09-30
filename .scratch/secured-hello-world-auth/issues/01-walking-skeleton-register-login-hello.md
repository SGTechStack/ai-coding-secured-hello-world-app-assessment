# 01: Walking skeleton: a Visitor registers, logs in and sees "Hello, username"

**What to build:** A Visitor opens the SPA, registers an Account, is sent to the login page, logs in and sees "Hello, <username>" on the home page. This is the first vertical path through both apps. It sets up the backend (Spring Boot 4.1.1, Java 21, Maven wrapper) and the frontend (React 19.3, TypeScript, Vite 8, React Router 8) as sibling apps at the repo root, and puts the core security plumbing in place: server-side sessions stored in the database, synchronizer-pattern CSRF, the CORS allow-list and Problem Details errors. Later tickets harden each piece. See the spec's API contract and "Session, cookie, CORS and header settings" sections, and ADR-0004.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Both apps build and start locally. The backend uses the Boot 4 modular starters (web MVC, security, data JPA, validation, session JDBC, actuator) with H2, and its schema uses only types portable to Postgres/MySQL.
- [ ] The `users` table matches the spec's schema (UUID id, username with a case-insensitive lookup key, lowercased email, `password_hash` with encoder prefix, role, enabled, `failed_login_attempts`, `locked_until`, `created_at`, `deleted_at`), including uniqueness constraints that also cover Tombstones.
- [ ] `GET /csrf` works anonymously and returns the token, header name (`X-CSRF-TOKEN`) and parameter name. Tokens are held in the session only, never in a cookie, and `csrf.spa()` is not used.
- [ ] `POST /register` (JSON, CSRF) creates an Account with role `USER`, enabled, not locked, and the password stored only as a BCrypt (cost 12) hash through `DelegatingPasswordEncoder`. It returns 201 and does not log the Visitor in.
- [ ] `POST /login` (form-encoded, CSRF) uses Spring Security form login with JSON handlers. It returns 200 on success, rotates the session ID, and returns 401 `invalid credentials` on failure.
- [ ] `GET /hello` returns "Hello, <username>" with a valid session and 401 `unauthenticated` without one.
- [ ] `GET /me` returns only the caller's id, username, email and role.
- [ ] The base path can be set through `app.api.base-path` (default `/api`).
- [ ] Sessions are stored through Spring Session JDBC. The session cookie is `HttpOnly`, `SameSite=Lax` and `Secure` by default; only the dev profile may turn `Secure` off, through a property.
- [ ] CORS allows only the configured frontend origins (no wildcard), with credentials, the spec's methods and headers, and `maxAge` 3600.
- [ ] Every error is an RFC 9457 `ProblemDetail` with a stable `code`. Validation errors list each failing field, and no response contains a stack trace.
- [ ] State-changing requests without a valid CSRF token get 403, and GET requests need no token.
- [ ] The SPA's API client is the only module that calls the backend. It uses `credentials: 'include'`, fetches the CSRF token on startup and again after login, keeps it in memory only, attaches `X-CSRF-TOKEN` to state-changing requests, and parses Problem Details.
- [ ] The SPA holds auth state from `/me` in memory only and has register, login and home (greeting) routes. Nothing auth-related goes into browser storage.
- [ ] Backend tests run through the full-context HTTP seam (`@SpringBootTest` + `MockMvcTester`) with a controllable `Clock` bean. Frontend API-client tests use Vitest with `fetch` mocked.
