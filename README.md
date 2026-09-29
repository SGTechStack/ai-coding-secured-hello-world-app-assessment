# AI Coding — Secured Hello World App Assessment

This repository is an AI-assisted coding assessment. The goal is to build a
secure username/password login application (React frontend + Spring Boot
backend) by working from the provided Product Requirements Document (PRD) and
generating the implementation on your own branch.

## Repository Layout

```
.
├── backend/                # Spring Boot API (Java 21, Maven)
├── frontend/               # React SPA (TypeScript, Vite)
├── docs/
│   └── design.md           # Design decisions, API, security controls, PRD test mapping
├── prd/
│   └── assessment-prd.md   # The Product Requirements Document — your source of truth
└── README.md               # This file
```

The [`prd/assessment-prd.md`](prd/assessment-prd.md) file contains the full
specification: user stories, acceptance criteria, data model, security
requirements, and testing requirements. Read it before you start.

## Running the app

Requirements: Java 21+ and Node.js 22.22+. No database or other services are
needed; the dev profile uses a file-based H2 database in `backend/data/`.

**Backend** on http://localhost:8080. The first start creates the `admin`
account with the password you supply (at least 12 characters):

```bash
cd backend
APP_ADMIN_PASSWORD='choose-a-long-passphrase' ./mvnw spring-boot:run
```

**Frontend** on http://localhost:3000. Open it as `localhost`, not `127.0.0.1`:
the API only accepts the exact origin `http://localhost:3000`.

```bash
cd frontend
npm ci
npm run dev
```

Password reset emails are not sent. The reset link is written to the backend
log, on a line starting with `[EMAIL STUB - not sent]`.

**Checks**

```bash
cd backend && ./mvnw test                                       # unit + integration tests
cd frontend && npm test && npm run lint && npm run build        # tests, lint, type-check + build
```

See [`docs/design.md`](docs/design.md) for the API, the security design, the
decisions taken where the PRD was open, and which test covers each PRD
testing requirement.

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
