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

## Running the App

```powershell
# backend/ — http://localhost:8080
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"

# frontend/ — http://localhost:3000
npm run dev
```

### Dev admin credentials

The username is `admin`. There is no fixed password: if `APP_ADMIN_PASSWORD`
is unset, the backend generates one on each start and prints it at `WARN`:

```
No APP_ADMIN_PASSWORD set, so a random one was generated for this run only:

    username: admin
    password: <generated>
```

Copy it from the backend console. To pin it across restarts, set the variable:

```powershell
$env:APP_ADMIN_PASSWORD = "your-dev-password-at-least-12-chars"
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Outside the `dev` profile there is no default at all — `APP_ADMIN_USERNAME` and
`APP_ADMIN_PASSWORD` are required and the application fails to start without
them, so a deployment cannot boot with a guessable admin account.

### Walking the password reset flow locally

Email delivery is a stub, and the reset link is **not** logged by default: it
contains a live single-use token, so logging it would mean anyone who can read
the logs can take over any account that has requested a reset.

To surface the link for local testing, opt in explicitly:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=dev" "-Dspring-boot.run.arguments=--app.mail.log-reset-link=true"
```

The link is then printed at `WARN`. Never enable this flag anywhere real
accounts exist.

## Rules Summary

- Read the PRD first — it is the source of truth.
- Create your own branch named `<yourfullname>` (lowercase alphabets only).
- Do not commit directly to `main`.
- Do not commit to anyone else's branch.
- Check your generated code in to your own branch and push it for review.
