# Secured Hello World — Implementation

A secure username/password login app built from [`prd/assessment-prd.md`](prd/assessment-prd.md).

- **Backend:** Java 17 + Spring Boot 3.3.4, Spring Security (session cookie), Spring Data JPA, H2 (in-memory).
- **Frontend:** React 18 + Vite + React Router. 🎪 Circus theme.

## What's implemented (mapped to the PRD)

| Story | Feature | Where |
| --- | --- | --- |
| 1 | Registration (unique username/email, ≥12-char password, BCrypt) | `AuthService.register`, `AuthController` |
| 2 | Login → session cookie; generic error; lockout respected | `AuthService.authenticate`, `SessionAuthService` |
| 3 | Account lockout after 5 failures; IP throttling | `LoginAttemptService`, `IpThrottleService` |
| 4 | Logout invalidates session; replay rejected | `SessionAuthService.endSession` |
| 5 | `GET /api/hello` → `"Hello, <username>"`, else 401 | `HelloController` |
| 6–7 | Password reset: hashed single-use token, expiry, session invalidation | `AuthService`, `SessionValidityFilter` |
| 8 | `GET /api/admin/users` (ADMIN only, no hashes); 403 for USER | `AdminController` |
| 9–11 | Enable/disable, role change, delete — with self-action guards | `AdminService` |
| 12 | Auto-seed initial ADMIN on first start | `AdminBootstrap` |

Cross-cutting: BCrypt, HttpOnly/SameSite cookie + session-fixation protection, CSRF
(cookie token), CORS allow-list with credentials, enumeration resistance, audit logging.

## Prerequisites

- **JDK 17+** (tested on JDK 26)
- **Maven 3.9+**
- **Node.js 18+** and npm (for the frontend)

## Run the backend

```bash
cd backend
mvn spring-boot:run
```

Backend starts on **http://localhost:8080**. On first start it seeds an admin:

- username: `admin`
- password: `ChangeMeAdmin123!`

(configurable in `backend/src/main/resources/application.yml` under `app.admin`)

## Run the frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend starts on **http://localhost:3000** and talks to the backend at `:8080`
with credentials. CORS is already configured for that origin.

## Run the tests

```bash
cd backend
mvn test
```

15 integration tests cover the security-critical paths (login, lockout, logout,
password reset single-use/expiry/session-invalidation, admin self-action guards,
role enforcement).

> Note on JDK 26: Byte Buddy (via Mockito) needs `-Dnet.bytebuddy.experimental=true`,
> which is already set for the Surefire test JVM in `pom.xml`.

## Password reset in dev

`EmailService` is a stub — it **logs** the reset link to the backend console instead
of sending email. Copy the token from the log into the "Set a New Password" page
(or open the logged link directly).

## Security notes / documented gaps (per PRD "Out of Scope")

- Local dev runs over **HTTP**; `Secure` cookies + HSTS require HTTPS in real deploys.
- "Invalidate all sessions on password reset" is implemented via a per-user
  `sessionsValidFrom` epoch checked in `SessionValidityFilter` (no distributed
  session store needed for this single-node demo).
- JWT is documented in the PRD appendix only — not built.
