# Secured Hello World App (React + Spring Boot)

A reference application demonstrating a **secure username/password login flow**,
built as a production-oriented security baseline. It implements registration,
session-based login/logout, a protected greeting endpoint, password reset,
account lockout / IP throttling, and an admin user-management module.

The full requirements live in [`prd/assessment-prd.md`](prd/assessment-prd.md);
the derived spec is in [`docs/spec/secured-hello-world-auth.md`](docs/spec/secured-hello-world-auth.md).
Domain vocabulary is defined in [`CONTEXT.md`](CONTEXT.md); architectural
decisions in [`docs/adr/`](docs/adr/).

## Architecture

- **Backend** — Spring Boot 3.5 (Java 21, Maven), REST API on `http://localhost:8080`.
  Server-side sessions via **Spring Session JDBC** over **H2** (dev), portable to
  Postgres/MySQL. Layered controller → service → repository (Spring Data JPA).
- **Frontend** — React 19 + TypeScript + Vite SPA on `http://localhost:3000`,
  talking to the backend over CORS with credentialed cookies.
- **Auth** — cookie-based session (`HttpOnly`, `SameSite=Lax`, `Secure` in prod).
  No JWT (a JWT design is documented in the PRD appendix but deliberately not built).

```
.
├── backend/    Spring Boot API (see backend/src/main/java/com/example/auth)
├── frontend/   React + Vite SPA (see frontend/src)
├── docs/       spec, ADRs, agent config, security traceability
└── prd/        the original product requirements
```

## Prerequisites

- **Java 21** and **Maven 3.9+**
- **Node 20+** and **npm**

## Running the backend

```bash
cd backend
mvn spring-boot:run
```

Starts on `http://localhost:8080` with an in-memory H2 database (data is lost on
restart). On first start an initial admin is seeded (see
[Initial admin](#initial-admin)).

## Running the frontend

```bash
cd frontend
npm install
npm run dev
```

Starts on `http://localhost:3000`. The Vite dev server proxies `/api/*` to the
backend on `:8080`, so cookies are same-site in development.

## Running the tests

```bash
# Backend — JUnit + Spring integration tests (real filter chain, H2, Spring Session)
cd backend && mvn -B test

# Frontend — Vitest + React Testing Library, type-check, and production build
cd frontend && npm test && npx tsc --noEmit && npm run build
```

## How authentication / session / CSRF works

1. **CSRF bootstrap** — the SPA calls `GET /api/auth/csrf` on load. Spring Security
   sets a non-`HttpOnly` `XSRF-TOKEN` cookie (double-submit pattern).
2. **State-changing requests** — the SPA echoes the cookie value in the
   `X-XSRF-TOKEN` header on every `POST`/`PATCH`/`DELETE`. All requests use
   `credentials: 'include'` so the session cookie travels.
3. **Login** — `POST /api/auth/login` authenticates via Spring Security's
   `AuthenticationManager`, applies session-fixation protection (`changeSessionId`),
   and persists the security context into the Spring Session JDBC store.
4. **Session rehydration** — on load the SPA calls `GET /api/auth/me`; a `200`
   means an active session, `401` means unauthenticated.
5. **Logout** — `POST /api/auth/logout` invalidates the server-side session (the
   cookie is cleared by Spring Session); a replayed cookie is rejected.
6. **Authorization** is always enforced server-side. The frontend uses the `/me`
   role only to decide **what UI to show** — it never makes authorization
   decisions. `/api/admin/**` requires `ROLE_ADMIN` regardless of the client.

## Initial admin

An initial `ADMIN` is seeded on startup from configuration, only if no admin
exists yet (idempotent). Configure via `backend/src/main/resources/application.yml`
or environment variables:

| Property | Env var | Default |
| --- | --- | --- |
| `app.admin.username` | `APP_ADMIN_USERNAME` | `admin` |
| `app.admin.email` | `APP_ADMIN_EMAIL` | `admin@example.com` |
| `app.admin.password` | `APP_ADMIN_PASSWORD` | *(placeholder — dev only)* |

The shipped password is a **known placeholder** accepted only under a dev/local
profile. Under any non-dev profile the app **fails to start** unless a strong
`app.admin.password` is supplied (see below).

## Configuration for a non-development deployment

This project targets local development over HTTP. A real deployment must:

- Run behind **HTTPS** and set `server.servlet.session.cookie.secure=true`
  (required for `Secure` cookies and HSTS; for genuinely cross-site frontends,
  switch the session cookie to `SameSite=None`).
- Run with a non-`dev` Spring profile and supply a strong **`APP_ADMIN_PASSWORD`**
  (a placeholder value causes fail-fast startup).
- Set the CORS allow-list (`app.cors.allowed-origins`) to the real frontend origin.
- Set `app.frontend.base-url` to the real frontend origin (used to build
  password-reset links; the request `Host` header is never trusted).
- Point the datasource at a persistent database (Postgres/MySQL); the schema is
  portable. Session storage moves with it via Spring Session JDBC.
- For multiple instances, replace the in-memory throttling/rate-limiting with a
  shared store (see limitations).

`VITE_API_BASE_URL` (frontend) is the only build-time frontend variable and is
**public** — never put secrets in `VITE_*` variables.

## Security controls (requirement → implementation → test)

| Requirement | Implementation | Test |
| --- | --- | --- |
| Password hashing (BCrypt) | `BCryptPasswordEncoder` bean; `RegistrationService` | `RegistrationIntegrationTest` |
| Password policy (12–72) | `@Size` on `RegistrationRequest` / `PasswordResetConfirmRequest` | `RegistrationIntegrationTest` |
| Session cookie hardening | `application.yml` (`HttpOnly`, `SameSite=Lax`, `Secure` prod) | `LoginIntegrationTest` |
| Session fixation | `changeSessionId()` in `SecurityConfig` + login flow | `LoginIntegrationTest` |
| Logout invalidation | `AuthController.logout` invalidates Spring Session | `LoginIntegrationTest.reusingSessionCookieAfterLogout` |
| Enumeration resistance (login) | Generic `401` for all causes | `LoginIntegrationTest`, `LockoutIntegrationTest` |
| **Login timing resistance (50)** | `DaoAuthenticationProvider` dummy-hash for unknown users | `LoginIntegrationTest.loginUnknownUsername*` |
| Account lockout (5→15m) | `LockoutService` (configurable) | `LockoutIntegrationTest` |
| **Race-safe lockout (45)** | `PESSIMISTIC_WRITE` via `findByUsernameWithLock` | `LockoutIntegrationTest.concurrentFailedLogins*` |
| IP throttling | `IpThrottleService` (Caffeine, `getRemoteAddr`) | `IpThrottleIntegrationTest` |
| **High-entropy reset token (46)** | 256-bit `SecureRandom`, Base64URL; SHA-256 stored (ADR-0002) | `PasswordResetIntegrationTest` |
| Reset single-use / expiry | `PasswordResetService` + `Clock` | `PasswordResetIntegrationTest` |
| Reset kills all sessions (7) | `SessionInvalidator` on confirm | `PasswordResetIntegrationTest.confirmInvalidatesAllExistingSessions` |
| Reset enumeration resistance | Generic `200` regardless of email | `PasswordResetIntegrationTest` |
| **Reset-request rate limit (47)** | `ResetRequestRateLimiter` (per-IP) → `429` | `ResetRequestRateLimitTest` |
| **Reset-link Host safety (52)** | Link from `app.frontend.base-url` | `PasswordResetIntegrationTest.resetLink_usesConfiguredBaseUrl*` |
| Admin role enforcement | `hasRole('ADMIN')` on `/api/admin/**` | `AdminUserIntegrationTest` (401/403) |
| **Session kill on disable/role change (43,44)** | `SessionInvalidator` in `AdminUserService` | `AdminUserIntegrationTest.*invalidates*Session*` |
| Self-action guard (409) | Identity from principal, not request | `AdminUserIntegrationTest.*cannot*Self*` |
| **IDOR/BOLA (49)** | Server-side authz; `{id}` selects target only | `AdminUserIntegrationTest.user_craftedAdminMutation*` |
| Mass-assignment safe | Explicit DTO records; no entity binding | (design) |
| **Secure admin bootstrap (51)** | Fail-fast on placeholder in non-dev | `AdminBootstrapRunnerTest` |
| **Security headers (53)** | `SecurityConfig` (`Referrer-Policy` + defaults) | `LoginIntegrationTest.securityHeaders*` |
| Error hardening (53) | `server.error.include-*=never` | `LoginIntegrationTest.errorResponse*` |
| No secrets in responses | `UserResponse` omits hash | `AdminUserIntegrationTest`, `LoginIntegrationTest` |
| No secrets / no forging in logs | `AuditService` (sanitises CR/LF, no secret args) | `AuditServiceTest` |

## Security considerations

- Authorization is enforced **server-side** on every request; the frontend role
  is a UX hint only. Directly calling `/api/admin/**` as a non-admin returns 403.
- No credentials or session identifiers are stored in `localStorage`/`sessionStorage`;
  the session lives in an `HttpOnly` cookie.
- `ProblemDetail` responses carry generic messages; stack traces and exception
  messages are never returned (`server.error.include-*=never`).
- Audit events are structured logfmt lines and never contain passwords, hashes,
  or reset tokens; user-supplied fields are sanitised against log injection.
- Spring Boot Actuator is not on the classpath; the H2 console is disabled.

## Known limitations / out of scope

- **Local HTTP** — dev runs over HTTP; `Secure` cookies/HSTS require HTTPS in prod.
- **In-memory throttling** — account/IP throttling and reset-request rate limiting
  are single-instance (Caffeine); a multi-instance deployment needs a shared store.
- **H2** in dev; migrations (Flyway/Liquibase) are out of scope.
- **Case-sensitive** usernames/emails (documented accepted limitation).
- **No MFA**, no real SMTP (reset email is a logging stub), no JWT, no CI/CD,
  no containerization — all explicitly out of scope per the PRD.

---

## Assessment / branching

This repository is an AI-assisted coding assessment. Each participant works on
their **own branch named after themselves** (lowercase letters only) — do not
commit to `main` or to someone else's branch. See the branch table and workflow
in [`prd/assessment-prd.md`](prd/assessment-prd.md) and the project docs above.
