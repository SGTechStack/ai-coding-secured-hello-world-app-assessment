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

## Running the Implementation

```
backend/    Spring Boot 3.4 (Java 21), Spring Security, Spring Session JDBC, JPA + H2
frontend/   React 18 + Vite
```

**Backend** (http://localhost:8080). The initial admin is seeded only if a password
is supplied (min 12 chars); no default password is shipped:

```bash
cd backend
APP_ADMIN_PASSWORD='choose-a-long-password' ./mvnw spring-boot:run
./mvnw test        # integration tests for the security-critical paths
```

Optional env vars: `APP_ADMIN_USERNAME`, `APP_ADMIN_EMAIL`, and `--spring.profiles.active=prod`
(Secure cookies, Postgres, `ddl-auto=validate`; requires `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
`APP_ALLOWED_ORIGINS`). The `prod` schema must be created beforehand (e.g. by migrations); the Postgres
path has not been run against a real database.

**Frontend** (http://localhost:3000):

```bash
cd frontend
npm install
npm run dev
```

Password-reset emails are stubbed: the reset link is written to the backend log.

### Design notes

- **Auth:** server-side session in an HttpOnly, SameSite=Lax cookie (`SESSION`); session id rotated on login.
- **CSRF:** enabled everywhere. The SPA fetches a token from `GET /api/csrf` and sends it as `X-XSRF-TOKEN`.
- **Lockout / throttling:** 5 failures locks the account for 15 min. Separately, 10 failures per client IP
  in 15 min returns 429; a throttled IP is rejected *before* any account counter is touched. The IP counter
  is in-memory (single node) and keyed on the socket address; behind a proxy, configure
  `server.forward-headers-strategy` deliberately rather than trusting `X-Forwarded-For` blindly.
- **Sessions are revoked** on password reset, and when an admin disables, deletes, or changes the role of
  the target user (roles are cached in the session, so a demoted admin would otherwise keep access).
- **Password limit:** 12 to 72 bytes (BCrypt ignores anything past 72).
- **Known gaps (per PRD):** HTTP-only local dev, no MFA, stubbed email. Reset-request timing differs slightly
  between registered and unregistered emails; send mail asynchronously in a real deployment.

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
