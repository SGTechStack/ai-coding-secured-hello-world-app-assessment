# AI Coding: Secured Hello World App Assessment

This repository is an AI-assisted coding assessment. The goal is to build a
secure username/password login application (React frontend + Spring Boot
backend) by working from the provided Product Requirements Document (PRD) and
generating the implementation on your own branch.

This branch contains a session-based authentication app with a Spring Boot backend and a React SPA
frontend. It covers registration, login/logout, password reset, session-based auth guards, and
role-based admin user management.

## Repository Layout

```
.
├── prd/
│   └── assessment-prd.md   # The Product Requirements Document (source of truth)
├── backend/                # Spring Boot 4 (Java 21) REST API
├── frontend/               # React + TypeScript SPA (Vite)
├── docs/
│   ├── adr/                # Architecture decision records
│   └── jwt-alternative.md  # Why server-side sessions rather than JWT
└── README.md               # This file
```

The [`prd/assessment-prd.md`](prd/assessment-prd.md) file contains the full
specification: user stories, acceptance criteria, data model, security
requirements, and testing requirements.

## Features

- Registration and login (`/api/auth/register`, `/api/auth/login`, `/api/auth/me`, `/api/auth/logout`) and a protected `GET /api/hello`
- Password reset request/confirm flow (`/api/password-reset/request`, `/api/password-reset/confirm`)
- Account lockout (5 failures → 20 min), plus layered rate limits on login, registration and password reset (`429` + `Retry-After`). See ADR-0005.
- Role-based access control (`USER` / `ADMIN`) and admin user management (`/api/admin/users`)
- Sessions via Spring Session JDBC (`SESSION` cookie):
  - one concurrent session per user
  - revoked server-side on logout, password reset, and admin disable, role change or delete
  - see ADR-0009
- CSRF protection: the token is stored in the server-side session, fetched from `GET /api/auth/csrf`, and sent as `X-CSRF-TOKEN` (ADR-0003)
- Cross-origin CORS between frontend and backend, with an explicit allow-list (ADR-0004)
- Security headers (HSTS, CSP, Referrer-Policy, Permissions-Policy, frame DENY), plus `Clear-Site-Data` on logout
- Structured ECS JSON logs with a typed audit trail (`AUDIT` logger), correlated by `trace.id` and `X-Correlation-ID` (ADR-0011)
- Uniform error bodies: `{"code": "<STABLE_CODE>", "message": "..."}`
- H2 web console for local inspection (dev profile only, `/h2-console`)

## Local setup, step by step

These steps assume a fresh machine (Linux, macOS, or Windows with WSL / Git Bash). Commands are run
from the repository root unless a step says otherwise.

### Step 1: Install the prerequisites

| Tool | Version | Check with |
|---|---|---|
| Git | any recent | `git --version` |
| Java JDK | **21** (e.g. Temurin 21) | `java -version` |
| Maven | **3.8+** (there is no `mvnw` wrapper, so `mvn` must be on your `PATH`) | `mvn -v` |
| Node.js | **20.19+ or 22.12+** (tested with 24) | `node -v` |
| npm | comes with Node.js | `npm -v` |

`mvn -v` must report Java 21. If it shows another JDK, set `JAVA_HOME` to your JDK 21 installation.

The app itself needs no database, mail server, or Docker: the dev profile uses in-memory H2 and
prints password-reset links to the console.

### Step 2: Clone this branch

```bash
git clone --branch limchuansiangdarren https://github.com/SGTechStack/ai-coding-secured-hello-world-app-assessment.git
cd ai-coding-secured-hello-world-app-assessment
```

### Step 3: Start the backend (port 8080)

Open a terminal:

```bash
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

- The first run downloads dependencies, which takes a few minutes.
- The backend is ready when the log shows `Started AuthApplication`. Logs are JSON lines.
- **The `dev` profile is required.** Without it the app starts in `prod` mode and exits, because it
  needs production environment variables (see [Production profile](#production-profile)).

Alternatively, build a jar and run that:

```bash
cd backend
mvn clean package -DskipTests
java -jar target/auth-app-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Optional quick check from another terminal: `curl http://localhost:8080/api/auth/csrf` should
return JSON with a `token`.

### Step 4: Start the frontend (port 3000)

Open a **second** terminal:

```bash
cd frontend
npm ci        # first time only (installs the exact versions from package-lock.json)
npm run dev
```

- The dev server must run on port **3000**. The backend's dev CORS allow-list only permits
  `http://localhost:3000`, and the port is pinned in `vite.config.ts`.
- The frontend reads the backend URL from `frontend/.env.development`
  (`VITE_API_BASE_URL=http://localhost:8080`). No `.env` changes are needed for local dev.

### Step 5: Log in

Open **http://localhost:3000** and sign in with one of the dev accounts the backend seeds on
startup (`application-dev.properties`):

| Username | Password | Role |
|---|---|---|
| `admin` | `DevAdminPass123!` | ADMIN |
| `alice` | `DevAlicePass123!` | ADMIN |
| `bob` | `DevBobPass123!` | USER |

After logging in you'll see the Hello page. Admin accounts also get the **Admin** page, where you
can list users, enable or disable them, change their role, or delete them.

These are throwaway local credentials (ADR-0007). The database is in-memory, so every backend
restart resets all data back to these seeded accounts.

### Step 6: Try the other flows (optional)

- **Register:** use the Register page. Passwords must be at least 12 characters long and at most
  72 bytes.
- **Password reset:**
  1. On the login page, choose "Forgot password" and enter a registered email. The seeded users
     are `admin@localhost`, `alice@localhost` and `bob@localhost`.
  2. No email is sent in dev. Find the backend log line `DEV ONLY - password reset link` and open
     the URL in its `labels.reset_link` field (`http://localhost:3000/reset-password/confirm?token=...`).
  3. Choose a new password. All of that user's existing sessions are ended.
- **Lockout:** 5 wrong passwords lock an account for 20 minutes. After 3 failures from the same
  machine, further attempts for that username get `429 Too Many Requests`, so lockout is hard to
  trigger locally. Restart the backend to reset both lockouts and rate limits.
- **H2 console:** open http://localhost:8080/h2-console with JDBC URL `jdbc:h2:mem:authdb`,
  user `sa`, and an empty password.

### Step 7: Stop the app

Press `Ctrl+C` in both terminals.

### Troubleshooting

| Symptom | Fix |
|---|---|
| Backend exits at startup with `PatternSyntaxException: Illegal repetition`, `Cannot load driver class: ${DATABASE_DRIVER_CLASS_NAME}` or another `${...}` error | You started it without the dev profile, so it ran as `prod` with no environment variables. Use `"-Dspring-boot.run.profiles=dev"` (step 3). |
| `Port 8080/3000 already in use` | Stop the other process (`lsof -i :8080` / `netstat -ano \| findstr 8080` on Windows), or stop an earlier run of this app. |
| Frontend shows "Unable to connect" / the browser console shows CORS errors | Make sure the backend is running and that you opened the app at `http://localhost:3000`, not `127.0.0.1:3000` or another port. |
| `invalid target release: 21` / `release version 21 not supported` | Maven is using an older JDK. Point `JAVA_HOME` at JDK 21. |
| Login returns `429` | You hit a rate limit. Wait the number of seconds in `Retry-After`, or restart the backend. |
| Logged out when logging in from a second browser | This is expected. Only one session per user is allowed, and the newer login wins. |

## Running tests and quality gates

```bash
cd backend && mvn verify                      # JUnit + JaCoCo; fails below 80% line coverage
cd frontend && npm run lint && npm run test   # ESLint (zero warnings) + Vitest
```

Backend tests drive the real filter chain the way a browser does. `support/ApiSession` keeps a
cookie jar (`SESSION`), fetches the CSRF token from `/api/auth/csrf`, and can pick a source IP.
Every `@SpringBootTest` class is pinned to `@ActiveProfiles("dev")`, so tests don't need any
environment variables.

Dependency checks:

```bash
cd backend && NVD_API_KEY=<key> mvn -Pcve verify   # OWASP dependency-check, fails on CVSS >= 7
cd frontend && npm run audit                        # fails on high/critical advisories
```

You can request a free NVD API key at https://nvd.nist.gov/developers/request-an-api-key. Without
one, the first vulnerability-database download is very slow.

## Production profile

The backend defaults to the **`prod`** profile (`spring.profiles.active=prod` in
`application.properties`). `application-prod.properties` requires these environment variables.
None has a default, so startup fails fast if any is missing:

- `DATABASE_URL`, `DATABASE_DRIVER_CLASS_NAME`
- `CORS_ALLOWED_ORIGINS`
- `FRONTEND_BASE_URL`
- `TRUSTED_PROXIES`: a Java regex matching the TLS-terminating proxy or load-balancer addresses
  (e.g. `10\.0\.0\.\d+`). `X-Forwarded-For` and `X-Forwarded-Proto` are honoured only from these
  peers, so clients can't spoof their IP past the rate limits.
- `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `ADMIN_EMAIL`

There is no production mail sender yet. The only `EmailService` (`LoggingEmailService`) is
dev-only, so a `prod` start fails with a missing-bean error until a real implementation is added
(ADR-0001, ADR-0012).

Prod does not create the Spring Session tables (`spring.session.jdbc.initialize-schema=never`).
Before first start, apply Spring Session's `schema-<platform>.sql` (shipped in
`spring-session-jdbc`) together with the app schema.

The frontend production build requires an absolute `https://` API URL:

```bash
cd frontend
VITE_API_BASE_URL=https://api.your-domain.example npm run build   # output in frontend/dist
```

Prod sets the `SESSION` cookie to `SameSite=Strict; Secure` and disables the H2 console. This
assumes frontend and backend are deployed over HTTPS on sibling subdomains of one site (e.g.
`app.example.com` + `api.example.com`). They are still separate origins, so CORS applies as in
dev. But they are same-site, so `Strict` cookies are still sent on the SPA's API calls. Hosting
them on unrelated domains, or on subdomains of a public suffix such as `*.vercel.app`, would make
every API call cross-site, and the browser would drop the cookies.

## Security design and deviations

Architecture decisions live in [`docs/adr/`](docs/adr/).
[ADR-0012](docs/adr/0012-policy-deviations-and-compliance-summary.md) maps the implementation to
IM8 and App-Standards controls. It also lists every deliberate deviation, for example BCrypt
instead of Argon2id (per the PRD) and rate limits held in memory per instance.

## Branching

Each person creates a single branch named after themselves.

### Branch naming format

- The branch name is your **full name**.
- **Lowercase alphabets only** (`a`–`z`).
- **No uppercase letters.**
- **No numbers, spaces, hyphens, underscores, or other symbols.**

| Full name        | Branch name    |
| ---------------- | -------------- |
| Jane Doe         | `janedoe`      |
| John Smith       | `johnsmith`    |
| Maria Garcia     | `mariagarcia`  |

## Rules Summary

- Read the PRD first. It is the source of truth.
- Create your own branch named `<yourfullname>` (lowercase alphabets only).
- Do not commit directly to `main`.
- Do not commit to anyone else's branch.
- Check your generated code in to your own branch and push it for review.
