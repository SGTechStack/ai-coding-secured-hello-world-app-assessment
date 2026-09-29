# AI Coding — Secured Hello World App Assessment

This repository is an AI-assisted coding assessment. The goal is to build a
secure username/password login application (React frontend + Spring Boot
backend) by working from the provided Product Requirements Document (PRD) and
generating the implementation on your own branch.

## Repository Layout

```
.
├── prd/
│   └── assessment-prd.md   # The Product Requirements Document — your source of truth
└── README.md               # This file
```

The [`prd/assessment-prd.md`](prd/assessment-prd.md) file contains the full
specification: user stories, acceptance criteria, data model, security
requirements, and testing requirements. Read it before you start.

## The Assessment

You are expected to:

1. Read and understand the PRD in `prd/assessment-prd.md`.
2. Use the PRD as the specification and generate the code for the application.
3. Commit your work to **your own branch** (see [Branching](#branching) below).
4. Push your branch to the remote so it can be reviewed.

Everyone works independently. **Every participant must create their own
branch** — do not commit to `main`, and do not commit to someone else's branch.

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

### Workflow

Create your branch from an up-to-date `main`:

```bash
# Make sure main is current
git checkout main
git pull origin main

# Create and switch to your own branch (use your full name, lowercase, letters only)
git checkout -b janedoe
```

Generate the code from the PRD, then check it in to your branch:

```bash
git add .
git commit -m "Implement secured hello world app from PRD"
```

Push your branch to the remote:

```bash
git push -u origin janedoe
```

Continue committing to your branch as you make progress. Keep all of your work
on your own branch.

## Rules Summary

- Read the PRD first — it is the source of truth.
- Create your own branch named `<yourfullname>` (lowercase alphabets only).
- Do not commit directly to `main`.
- Do not commit to anyone else's branch.
- Check your generated code in to your own branch and push it for review.

---

## Jingshun's implementation

All twelve PRD stories are implemented on `jingshun`: registration, secure login,
account lockout and IP throttling, logout, personalized greeting, password reset,
and admin user management. Authentication uses **server-side JDBC sessions**, with
CSRF protection on every mutation. JWT remains a documented alternative only.

### Run locally

Prerequisites: **Java 21**, **Node.js 22.12+** (Node 24 used for verification), and
internet access for the first dependency install. Maven is downloaded by the
checked-in wrapper; no global Maven, Docker or external database is needed.

In a PowerShell terminal, configure your initial administrator and start the API:

```powershell
cd backend
$env:APP_ADMIN_USERNAME = 'jingshun'
$env:APP_ADMIN_EMAIL = 'jingshun@example.com'
$env:APP_ADMIN_PASSWORD = Read-Host 'Choose an admin password (12+ characters)' -MaskInput
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

In a second terminal:

```powershell
cd frontend
npm ci
npm run dev
```

Open **http://localhost:3000**. The API runs on **http://localhost:8080**.
Use the configured administrator account or register a new member. The admin's
“Manage users” page can change other accounts' status/role and delete them.

On macOS/Linux, export the same variables and use `./mvnw`:

```bash
cd backend
export APP_ADMIN_USERNAME=jingshun APP_ADMIN_EMAIL=jingshun@example.com
read -rs -p 'Admin password: ' APP_ADMIN_PASSWORD; export APP_ADMIN_PASSWORD
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Passwords require at least 12 characters and at most 72 UTF-8 bytes. There are no
hardcoded administrator credentials. Bootstrap only runs if no ADMIN exists;
changing environment variables does not overwrite an existing account.

H2 persists to `backend/data/` when run from `backend`. Stop the API before copying
or resetting this directory. The `dev` profile permits HTTP cookies; **do not use
it for a real deployment**. Without `dev`, session cookies are Secure by default.

If a port is already occupied, configure the API with
`"-Dspring-boot.run.arguments=--server.port=18080"` and set
`$env:VITE_API_URL='http://localhost:18080/api'` before starting the frontend.
Keep both browser/API hosts as `localhost` (do not mix `localhost` and `127.0.0.1`).

### Try password reset

Choose “Forgot password?”, submit the registered email, and open the
`reset_link` printed by the backend's **development email stub**. The link lasts
twenty minutes and can be used once. A successful reset ends all existing sessions.
This stub intentionally logs the reset link, not a password; replace it before
deployment and treat development logs as sensitive.

### Verify and build

```powershell
cd backend
.\mvnw.cmd verify
# Runs formatting checks, real HTTP integration tests, and builds the executable JAR.
# Format Java changes with: .\mvnw.cmd spotless:apply

cd ../frontend
npm ci
npm test
npm run lint
npm run format:check
npm run build
```

Tests use an isolated in-memory database and dedicated test credentials, so they
need no environment secrets or running application. `npm run build` includes strict
TypeScript checking and writes `frontend/dist/`; Maven writes
`backend/target/secured-hello-1.0.0.jar`. Run the JAR with your configured environment:

```powershell
cd backend
java -jar target/secured-hello-1.0.0.jar --spring.profiles.active=dev
```

### API

All paths below are prefixed with `/api`. Requests/responses are JSON; successful
logout/delete responses have no body. User responses contain only `id`, `username`,
`email`, `role`, `enabled` and `createdAt`. Errors contain `message` and optional
field `errors`. The client must use `credentials: 'include'`.

| Method | Path | Body / response |
| --- | --- | --- |
| GET | `/auth/csrf` | `{token, headerName}`; send this header on every mutation |
| POST | `/auth/register` | `{username,email,password}` → 201 user |
| POST | `/auth/login` | `{username,password}` → user |
| GET | `/auth/me` | Current user, or 401 |
| POST | `/auth/logout` | 204; session invalidated and cookie cleared |
| GET | `/hello` | JSON string `"Hello, <username>"`, or 401 |
| POST | `/auth/password-reset/request` | `{email}` → generic success message |
| POST | `/auth/password-reset/confirm` | `{token,password}` → success message |
| GET | `/admin/users` | ADMIN-only user array |
| PATCH | `/admin/users/{id}/status` | `{enabled}` → user |
| PATCH | `/admin/users/{id}/role` | `{role: "USER" or "ADMIN"}` → user |
| DELETE | `/admin/users/{id}` | 204 |

Login failures return generic 401; IP throttling returns 429 with `Retry-After`.
All admin self mutations are rejected, and non-admin users receive 403.

### Configuration and design

`backend/.env.example` lists environment variables for reference; Spring Boot does
not automatically load that file. `frontend/.env.example` can be copied to
`frontend/.env.local`. All `VITE_*` configuration is public, so never put secrets there.

- [PRD coverage and tests](docs/requirements.md)
- [Architecture decisions](docs/decisions.md)
- [Security, deployment assumptions, and JWT alternative](docs/security.md)
- [Verification record and handoff](docs/handoff.md)

The application is organized by feature under `backend/src/main/java/com/example/hello`
and `frontend/src`. Dependencies are pinned through the Maven parent and npm lockfile.
The original assessment instructions above remain applicable.
