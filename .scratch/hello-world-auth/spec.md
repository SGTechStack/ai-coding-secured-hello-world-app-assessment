# Spec: Hello World Auth App (React + Spring Boot)

Status: ready-for-agent

## Problem Statement

A developer needs a copyable reference implementation of secure username/password authentication for a React SPA calling a Spring Boot REST API on a separate origin. Existing demo apps typically shortcut the hard parts (CSRF, session security, lockout, reset flows), so there is no trustworthy baseline to copy. Today only requirements exist — no code.

## Solution

Build the application described in the canonical requirements doc: self-registration, login with account lockout plus independent IP throttling, logout with true server-side invalidation, a protected "hello" endpoint, a single-use password-reset flow with a stubbed email service, an admin user-management module (list, enable/disable, role change, delete) with self-action guards, automatic admin seeding, CSRF-protected cookie-based sessions, CORS with credentials, and structured JSON audit logging — all backed by a broad automated test suite that proves the security-critical behaviors.

## User Stories

1. As a visitor, I want to register with a username, email, and password, so that I can access the protected app.
2. As a visitor, I want a clear validation error when my username or email is already taken, so that I can correct it and try again.
3. As a visitor, I want my password rejected when it is under 12 characters, so that weak credentials can't enter the system.
4. As a registered user, I want to log in with my username and password, so that I get a session and can reach protected content.
5. As a user, I want login failures to return a generic "Invalid username or password." message, so that my account's existence can't be inferred.
6. As a user, I want my failed-login counter reset when I log in successfully, so that intermittent typos don't accumulate against me.
7. As a security-conscious operator, I want an account locked after N failed attempts within a window, so that brute-force guessing is blunted.
8. As a user whose account is locked, I want even correct credentials rejected until the cooldown expires, so that the lockout is meaningful.
9. As a security-conscious operator, I want repeated failures from one IP throttled independently of any account lockout, so that an attacker can't lock out a legitimate user by failing their password.
10. As a logged-in user, I want to log out, so that my session is fully ended and a replayed cookie is rejected.
11. As a logged-in user, I want `GET /api/hello` to greet me by name, so that I can confirm authentication works.
12. As an anonymous caller, I want `GET /api/hello` to return 401, so that protected content stays protected.
13. As a user who forgot my password, I want to request a reset by email, so that I can regain access — and I want the same generic response whether or not my email is registered.
14. As a user with a valid reset link, I want to set a new password, so that I regain access to my account.
15. As a user, I want an expired or already-used reset token rejected, so that tokens can't be replayed.
16. As a user who resets my password, I want all my existing sessions invalidated, so that a stolen session dies with the old password.
17. As the React SPA, I want a CSRF bootstrap endpoint, so that I can obtain a token before my first mutating request.
18. As the React SPA, I want a `GET /api/auth/me` endpoint, so that I can detect an existing session on page load.
19. As an admin, I want to list all users (never password hashes), so that I can review who has access.
20. As an admin, I want to enable or disable another user's account, so that I can suspend access without deleting data.
21. As an admin, I want to change another user's role between USER and ADMIN, so that I can grant or revoke privileges.
22. As an admin, I want to delete another user's account, so that obsolete accounts can be removed.
23. As an admin, I want self-targeting disable/role-change/delete requests rejected, so that I can't lock out the only admin.
24. As a non-admin user, I want `/api/admin/**` to return 403, so that admin functions stay admin-only.
25. As an operator deploying for the first time, I want an initial admin seeded from configuration, so that there's a way into the admin module.
26. As an operator, I want restarts to skip seeding when an admin already exists, so that no duplicate admin appears.
27. As an auditor, I want structured JSON log lines for login success/failure, lockouts, reset requests/completions, and admin actions (actor + target) — never passwords or tokens.
28. As an operator, I want a health endpoint, so that I can check the app is up.
29. As a developer, I want a dev profile with documented seed credentials and in-memory H2, so that the app runs locally with zero setup.

## Implementation Decisions

### Stack

- **Backend:** Java 21, Maven, Spring Boot 4.1.x, Spring Security 7.1.x, Spring Session 4.x (JDBC), Spring Data JPA over H2.
- **Frontend:** Vite + TypeScript + React, shadcn/ui + Tailwind, react-router. Six pages: login, register, forgot-password, reset-password, protected hello, admin user-management panel.
- **Topology:** two origins — SPA on `localhost:3000`, API on `localhost:8080`, CORS with credentials. No same-origin serving.
- **Observability:** Spring Actuator `/actuator/health` only.

### Security filter chain (backend)

- Login is a REST controller calling `AuthenticationManager` — not a custom filter. On the Security 6+/7 line the controller must itself (a) invoke a `SessionAuthenticationStrategy` (`ChangeSessionIdAuthenticationStrategy` + `CsrfAuthenticationStrategy`) — the `sessionFixation()` DSL is a no-op without an auth filter — and (b) persist the context via `SecurityContextRepository.saveContext(...)`.
- CSRF: `csrf.spa()` (cookie repo `withHttpOnlyFalse` + BREACH-aware handler). Tokens are deferred, so a `permitAll` `GET /api/auth/csrf` bootstrap endpoint forces emission; the SPA calls it at app start and after login/logout, then sends `X-XSRF-TOKEN` (read from the `XSRF-TOKEN` cookie) on every mutation.
- CORS: `UrlBasedCorsConfigurationSource` bean + `.cors(withDefaults())`; allow-list `http://localhost:3000`, `allowCredentials(true)`, allow the `X-XSRF-TOKEN` header.
- Authorization: `/api/auth/**` permitAll, `/api/admin/**` `hasRole("ADMIN")`, everything else authenticated; `HttpStatusEntryPoint(401)` so anonymous calls get 401 (not 403/redirect); no formLogin/httpBasic.
- Lockout gate: `locked_until` mapped to `UserDetails.isAccountNonLocked()` (pre-auth `LockedException`); counters via `@EventListener`s on `AuthenticationSuccessEvent`/`AuthenticationFailureBadCredentialsEvent` with a `DefaultAuthenticationEventPublisher` on the `ProviderManager`.

### Sessions

- Spring Session **JDBC over H2** (`spring-boot-starter-session-jdbc`); schema auto-created via `spring.session.jdbc.initialize-schema=embedded`. The in-memory repo is disqualified — no `FindByIndexNameSessionRepository`, so it can't enumerate a user's sessions.
- Per-user invalidation (password reset, admin disable/delete): `findByPrincipalName(username)` + `deleteById`.
- Logout: `invalidate()` deletes the row — replayed cookie resolves to anonymous → 401.
- Cookie attributes via `server.servlet.session.cookie.*` (mapped onto `DefaultCookieSerializer`): HttpOnly, `same-site` set explicitly (Boot 4 SameSite regression), `secure=true` in prod profile only.

### Lockout + IP throttling

- Both enforced **service-level inside the login path**, ordered: IP-throttle check → `locked_until` check → credential verification. Throttled attempts must never increment `failed_login_attempts` (the anti-DoS invariant).
- IP throttle: in-memory Caffeine cache keyed by IP, sliding-window counter. Client IP = `getRemoteAddr()` only; never trust `X-Forwarded-For` without a stripping proxy (documented deployment assumption; `server.forward-headers-strategy` when proxied).
- Success resets the account counter but **not** the IP bucket (window decay only — avoids shared-IP laundering).
- Auth events feed audit logging only — never drive state.
- `Clock` bean injected for all lockout/expiry time math.

### Data model

- `users`: id, username (unique), email (unique), password_hash (BCrypt), role (`USER`|`ADMIN`), enabled, failed_login_attempts, **last_failed_at** (nullable — added per ratified deviation so the lockout window is literal), locked_until, created_at.
- `password_reset_tokens`: id, user_id FK, token_hash (SHA-256), expires_at (15–30 min), used_at (nullable, single-use).
- `SPRING_SESSION`/`SPRING_SESSION_ATTRIBUTES`: auto-created by Spring Session JDBC.
- Reset token: 32 bytes `SecureRandom`, base64url in the link; SHA-256 hash stored.
- `EmailService` stub: logs the reset link (`http://localhost:3000/reset-password?token=<token>`); no real SMTP.

### API contract

- Endpoints: `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout`, `POST /api/auth/password-reset/request`, `POST /api/auth/password-reset/confirm`, `GET /api/auth/csrf`, `GET /api/auth/me`, `GET /api/hello`, `GET /api/admin/users`, `PATCH /api/admin/users/{id}/status`, `PATCH /api/admin/users/{id}/role`, `DELETE /api/admin/users/{id}`, `GET /actuator/health`.
- Errors: RFC 7807 `application/problem+json` via Spring's built-in `ProblemDetail`.
- Enumeration-resistant wording: login failure → `"Invalid username or password."`; reset-request → `"If an account with that email exists, we've sent a reset link."`
- Password policy: length ≥ 12 only.
- Shapes: register `{username,email,password}`; login `{username,password}`; `me` → `{username,role}`; `PATCH …/status` `{enabled:bool}`; `PATCH …/role` `{role:"USER"|"ADMIN"}`; user-list DTO `{id,username,email,role,enabled,createdAt}`; hello → `"Hello, <username>"`.

### Configuration

- Profiles `dev` + `prod`. dev: in-memory H2 + console, `Secure=false`, localhost CORS, documented default admin creds. prod: `Secure=true`, H2 console off, env-driven CORS, fail-fast if admin creds absent.
- All security tunables externalized as `app.*` properties: lockout threshold/window/cooldown, IP-throttle values, password min length, session timeout, `app.admin.username`/`app.admin.password`.
- Audit logging: JSON via logstash-logback encoder; events = login success/failure, lockout triggered, reset requested/completed, admin actor+target actions. Passwords/tokens never logged.

### Frontend

- Auth-state: `GET /api/auth/me` on load; route guards for protected + admin routes.
- Fetch wrapper: `credentials:'include'`, base URL `localhost:8080`, reads `XSRF-TOKEN` cookie → `X-XSRF-TOKEN` header on mutations; bootstrap call on start/login/logout.
- Styling: invoke the `design-taste-frontend` skill — design-taste polish, not bare defaults.

## Testing Decisions

- **What makes a good test:** exercises external behavior only — HTTP requests, responses, status codes, cookies — never internal implementation details. A refactor that preserves observable behavior must not break tests.
- **Primary seam: the HTTP API boundary** via `MockMvc` + `@SpringBootTest` on H2 (same engine as dev, incl. Spring Session JDBC tables). All required coverage flows through it:
  - Login: success, wrong password, unknown username (identical generic error), locked account.
  - Lockout: N failures triggers lockout; post-cooldown success resets the counter.
  - IP throttle: engages independently of account lockout — `.remoteAddress(...)` varies source IP (Spring 6.0.10+).
  - Logout: replayed cookie rejected after logout.
  - Reset: token single-use, expiry, session invalidation — `Clock` bean controls time; tokens seeded via repositories.
  - Admin: self-action guard (disable/delete/demote → rejected); `USER` → 403 on `/api/admin/**`.
- **Secondary (narrower) seam: service-level unit tests** for the lockout/throttle logic — ordering invariant (throttled attempts never increment the counter), window/cooldown math against the injected `Clock`, counter reset semantics. Chosen because the ordering invariant is easier to pin precisely below HTTP.
- **CSRF on in tests:** real path — tests fetch a token via `GET /api/auth/csrf` and send `X-XSRF-TOKEN` like the SPA.
- **Scope:** broad suite — the PRD-required list plus near-full endpoint coverage incl. edge cases.
- **Fixtures:** users/tokens seeded via repositories in per-test setup; admin seed tested via the config-driven seeder.
- **Prior art:** none — greenfield; these tests establish the convention.

## Out of Scope

- JWT implementation (appendix design in the PRD only), MFA/2FA, real SMTP/email delivery.
- Containerization, CI/CD, hosting infra; local HTTPS (documented deployment assumption only).
- Granular per-resource authorization beyond the USER/ADMIN role check.
- Same-origin production serving of the SPA.
- Appfw standards conformance (the workspace's Appfw-*-Standards dirs are context only).
- Metrics/observability beyond `/actuator/health` and the required audit logs.

## Further Notes

- Canonical requirements: `assessment-prd.md` (Connextra stories + ACs; `last_failed_at` added to the users table per the ratified deviation). `assessment-wayfinder.md` is supporting context.
- All decisions above trace to the wayfinder map: `.scratch/hello-world-auth/map.md`, with tickets in `issues/` and per-claim cited research in `research/`. The two traps the research surfaced are worth repeating: `sessionManagement().sessionFixation(...)` is a no-op without an auth filter, and `csrf.spa()` still needs a bootstrap endpoint because tokens are deferred.
- Domain glossary: `CONTEXT.md` (roles; the disabled-vs-locked distinction matters — `enabled` is an admin action, `locked_until` is automatic).
- Frontend build should invoke the `design-taste-frontend` skill for the SPA's look.
