# AI Coding: Secured Hello World App Assessment

This repository is an AI-assisted coding assessment. The goal is to build a
secure username/password login application (React frontend + Spring Boot
backend) by working from the provided Product Requirements Document (PRD) and
generating the implementation on your own branch.

Session-based authentication app with a Spring Boot backend and a React SPA frontend. Covers registration, login/logout, password reset, session-based auth guards, and role-based admin user management.

## Repository Layout

```
.
├── prd/
│   └── assessment-prd.md   # The Product Requirements Document — your source of truth
├── backend/                 # Spring Boot 4 (Java 21) REST API
├── frontend/                # React + TypeScript SPA (Vite)
└── README.md                 # This file
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

## Structure

- `backend/` — Spring Boot 4 (Java 21) REST API, session + CSRF cookie auth via Spring Security, sessions stored with Spring Session (JDBC), H2 in-memory database.
- `frontend/` — React + TypeScript SPA (Vite), calls the backend as a separate origin via `fetch(..., { credentials: 'include' })`.

## Features

- Registration and login (`/api/auth/register`, `/api/auth/login`, `/api/auth/me`, `/api/auth/logout`)
- Password reset request/confirm flow (`/api/password-reset/request`, `/api/password-reset/confirm`)
- Account lockout after repeated failed logins, and IP-based login throttling
- Role-based access control (`USER` / `ADMIN`); admin user management (`/api/admin/users`)
- CSRF protection (cookie-based token) and cross-origin CORS between frontend and backend
- H2 web console for local inspection (dev profile only, `/h2-console`)

## Prerequisites

- Java 21 (JDK)
- Maven (`mvn` on your PATH — the project has no `mvnw` wrapper)
- Node.js + npm

## Running locally

### 1. Backend (Spring Boot, port 8080)

The backend defaults to the **`prod`** profile (`application.properties`'s `spring.profiles.active=prod`) — running it with no override requires the env vars listed under [Production profile](#production-profile), and fails fast without them. For local dev, override the profile yourself:

```
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

On first startup under the `dev` profile it seeds a throwaway admin account plus a couple of extra local accounts (`app.local.users[n].*` in `application-dev.properties`):

The H2 console is available at `http://localhost:8080/h2-console` (dev only).

Note: `mvn test`/`mvn verify` are unaffected by the app's own default — every `@SpringBootTest` class is pinned to `@ActiveProfiles("dev")` directly, so the test suite always runs against dev-shaped config regardless of `SPRING_PROFILES_ACTIVE`.

### 2. Frontend (Vite dev server, port 3000)

```
cd frontend
npm install   # first time only
npm run dev
```

Open `http://localhost:3000`. The frontend reads the backend URL from `frontend/.env.development` (`VITE_API_BASE_URL=http://localhost:8080`).

Note: the dev server is pinned to port 3000 (`vite.config.ts`) because the backend's dev-profile CORS allow-list only permits `http://localhost:3000` (`app.cors.allowed-origins` in `application-dev.properties`). Vite's own default port (5173) would get CORS-blocked against that origin.

## Running tests

```
cd backend && mvn test        # JUnit + JaCoCo coverage report
cd frontend && npm run test   # Vitest
```

## Production profile

`application-prod.properties` requires these to be supplied as environment variables (no defaults, so startup fails fast if missing):

- `CORS_ALLOWED_ORIGINS`
- `FRONTEND_BASE_URL`
- `CSRF_COOKIE_DOMAIN` — parent domain for the `XSRF-TOKEN` cookie (e.g. `.example.com`); set it empty only for a single-origin deployment
- `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `ADMIN_EMAIL`

Prod also switches the session and CSRF cookies to `SameSite=Strict; Secure` and disables the H2 console. This assumes frontend and backend are deployed on sibling subdomains of one site over HTTPS (e.g. `app.example.com` + `api.example.com`): still separate origins, so CORS applies as in dev, but same-site, so `Strict` cookies are still sent on the SPA's API calls. Hosting them on unrelated domains (or on subdomains of a public suffix such as `*.vercel.app`) would make every API call cross-site and the cookies would be dropped.
