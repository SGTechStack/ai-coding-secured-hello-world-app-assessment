# Hello World Auth App — System Documentation

A reference implementation of a secure username/password login flow: a React SPA
frontend and a Spring Boot REST backend using server-side sessions over a secure
cookie. Built from `prd/assessment-prd.md` with IM8 controls applied (see
[Compliance](#im8-compliance--waivers)).

## Architecture

```
frontend/   React + TypeScript + Vite SPA (own origin, e.g. :3000)
backend/    Spring Boot 3.4 REST API (own origin, e.g. :8080)
```

- **Auth model:** server-side session established on login; the session id travels
  in a hardened cookie (`JSESSIONID`, HttpOnly, SameSite=Lax, Secure under prod).
- **Persistence:** Spring Data JPA over H2 in dev (`ddl-auto: update`); schema is
  portable to Postgres/MySQL (prod profile uses `validate` + externalised datasource).
- **Authorization:** default-deny; URL rules + method-level `@PreAuthorize` for admin.
- **Sessions:** Spring Security `SessionRegistry` + `ConcurrentSessionFilter` enable
  per-user session invalidation (used by admin disable/delete/role-change and password reset).

### Backend package layout (`com.example.helloauth`)

| Package | Responsibility |
| --- | --- |
| `domain` | JPA entities (`User`, `PasswordResetToken`) and `Role` enum |
| `repo` | Spring Data repositories |
| `config` | `SecurityConfig` (CORS/CSRF/CSP/HSTS/session), `AppProperties` |
| `security` | `AppUserDetails(Service)`, `AuditLogger`, `IpThrottleService` |
| `service` | `AuthService`, `LoginAttemptService`, `UserService`, `PasswordResetService`, `SessionRegistryService`, `EmailService`, `PasswordPolicy` |
| `web` | REST controllers + DTOs + global exception handler |
| `bootstrap` | `AdminBootstrap` initial-admin seeder |

## API Specification

Base path `/api`. All state-changing requests require the CSRF token
(`X-XSRF-TOKEN` header, read from the `XSRF-TOKEN` cookie) and send the session cookie.

| Method | Path | Auth | Body | Success | Notes |
| --- | --- | --- | --- | --- | --- |
| GET | `/csrf` | public | — | 200 | Primes the CSRF cookie |
| POST | `/register` | public | `{username,email,password}` | 201 | password ≥ 12; unique username/email |
| POST | `/login` | public | `{username,password}` | 200 `{username,role,mustChangePassword}` | generic 401 on failure |
| POST | `/logout` | session | — | 200 | invalidates session, clears cookie |
| GET | `/hello` | session | — | 200 `{message:"Hello, <username>"}` | 401 if unauthenticated |
| POST | `/account/change-password` | session | `{currentPassword,newPassword}` | 200 | clears `must_change_password` |
| POST | `/password-reset/request` | public | `{email}` | 200 (generic) | enumeration-resistant |
| POST | `/password-reset/confirm` | public | `{token,password}` | 200 | single-use, expiry-checked |
| GET | `/admin/users` | ADMIN | — | 200 `[AdminUserView]` | never returns password hashes |
| PATCH | `/admin/users/{id}/status` | ADMIN | `{enabled}` | 200 | cannot disable self |
| PATCH | `/admin/users/{id}/role` | ADMIN | `{role}` | 200 | cannot demote self; role ∈ {USER,ADMIN} |
| DELETE | `/admin/users/{id}` | ADMIN | — | 200 | cannot delete self |

Error responses use `{ "message": "..." }`. Login/reset messages are generic to
resist account enumeration. A `USER` calling `/admin/**` receives 403.

## Data Model

**users:** `id (UUID PK)`, `username (unique)`, `email (unique)`, `password_hash (BCrypt)`,
`role (USER|ADMIN)`, `enabled`, `must_change_password`, `failed_login_attempts`,
`locked_until (nullable)`, `last_login_at (nullable)`, `created_at`.

**password_reset_tokens:** `id (UUID PK)`, `user_id (FK)`, `token_hash (SHA-256, never plaintext)`,
`expires_at`, `used_at (nullable, single-use)`.

## Data Flow — login

1. SPA calls `GET /api/csrf` to obtain the `XSRF-TOKEN` cookie.
2. SPA `POST /api/login` with credentials + `X-XSRF-TOKEN` over HTTPS (prod).
3. Backend: IP-throttle check → user lookup (parameterised) → lockout/enabled checks
   → BCrypt password verify.
4. On success: fresh session (fixation protection), security context saved, session
   registered in `SessionRegistry`, counters reset, `last_login_at` set, audit log emitted.
5. On failure: `failed_login_attempts` incremented in an independent transaction
   (`LoginAttemptService` `REQUIRES_NEW`), IP failure recorded, lockout applied at
   threshold, generic 401 returned, audit log emitted.

## Network Topology

```
[Browser]
   | HTTPS (prod) / HTTP (local dev, accepted gap)
   v
[Reverse proxy: TLS termination + HSTS]      (prod)
   |                         |
   | React static origin     | Spring Boot API origin
   | (:3000)                 | (:8080)
   +-------------------------+
                             |
                      [Spring Boot app] --(JPA)--> [H2 dev | Postgres/MySQL prod]
                             |
                      [Secret store: env / Secrets Manager / Vault]
```

In local dev, the Vite dev server proxies `/api` to `:8080` so the SPA and API share
an origin and the session/CSRF cookies work without cross-site complications.

## Security Controls

- **Passwords:** BCrypt (`BCryptPasswordEncoder`), min length 12; never logged/stored in plaintext.
- **Sessions:** server-side; cookie HttpOnly + SameSite=Lax (+ Secure in prod); fixation
  protection on login; idle timeout 30m; invalidated on logout and password reset.
- **CSRF:** enabled for all state-changing endpoints (cookie-based auth).
- **CORS:** explicit origin allow-list with credentials enabled.
- **Enumeration resistance:** identical generic responses for login and reset-request.
- **Brute-force:** per-account lockout (5 failures / 15 min cooldown) + independent
  per-IP throttling (15 failures / 15 min window).
- **Headers:** CSP (`default-src 'self'`), HSTS (1 year, prod/HTTPS).
- **Audit logging:** structured key=value lines for login success/failure, lockout,
  reset requested/completed, admin actions (actor+target), with CR/LF/quote neutralisation.
- **Secrets:** admin seed + datasource + secrets sourced from env/secret store, never committed.
- **Errors:** generic client messages; no stack traces or internal detail exposed.

## IM8 Compliance & Waivers

Vetted against IM8 (36) + ARC (88). Full report:
`artifacts/spec-compliance/spec-compliance-hello-world-auth-app-react-spring-boot-2026-09-28-1611.html`.
All in-scope controls PASS. ARC is entirely N/A (not an agentic AI system).

**Waived (out of scope) with compensating controls:**

| Control | Waiver rationale | Compensating control |
| --- | --- | --- |
| ac-2 MFA | MFA out of scope for all users incl. ADMIN | lockout, IP throttling, forced first-login change, secure sessions, audit logging |
| ac-3 inactivity disable | lifecycle automation out of scope | admin manual disable; `last_login_at` recorded |
| ac-4 access review | org/ops process out of scope | admin user list for ad-hoc review |
| lm-16 RED/USE metrics | monitoring infra out of scope | structured audit logging retained |

**Implemented hardening beyond the base stories:** ac-6 (forced first-login password
change via `must_change_password`), as-8 (secrets externalised), as-9 (CSP), as-11
(session timeout), as-15 (self-service/forced password change path).

## Running Locally

Prerequisites: JDK 21+ and Node 20+.

**Backend** (from `backend/`):
```
# Provide admin seed credentials via environment (never commit these):
$env:APP_ADMIN_USERNAME="admin"; $env:APP_ADMIN_EMAIL="admin@example.com"; $env:APP_ADMIN_PASSWORD="ChangeMeNow123"
mvn spring-boot:run
```
The seeded admin is created with `must_change_password=true` and must change its
password on first login before any admin action.

**Frontend** (from `frontend/`):
```
npm install
npm run dev      # serves on http://localhost:3000, proxies /api to :8080
```

## Testing

Integration tests cover all security-critical paths (login success/wrong/unknown/locked,
lockout + cooldown reset, IP throttling independence, logout replay rejection, reset
token single-use/expiry/session-invalidation, admin self-action guards, USER→admin 403,
forced first-login change).

```
cd backend
mvn test          # 24 tests
```
