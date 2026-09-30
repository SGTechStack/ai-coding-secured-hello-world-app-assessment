# demo-app

A full-stack web application with a Spring Boot backend and a React frontend.

---

## What is this project?

Demo App is a secure, cloud-ready business application consisting of:

- A **Spring Boot REST API** (`backend/`) — modular monolith with DDD-inspired package structure, Spring Security session-based auth, JPA persistence, and structured logging.
- A **React SPA** (`frontend/`) — file-based routing, server-state management via TanStack Query, and a headless UI component library.

In production the backend serves the pre-built frontend as static files. Locally the two run as separate dev servers.

---

## Tech stack

### Backend

| Concern      | Technology                                                                          |
| ------------ | ----------------------------------------------------------------------------------- |
| Language     | Java 21 (virtual threads enabled)                                                   |
| Framework    | Spring Boot 4.0                                                                     |
| Persistence  | Spring Data JPA — H2 (local/test), MS SQL Server (cloud)                            |
| Security     | Spring Security — session-based auth                                                |
| Sessions     | Spring Session JDBC (all profiles)                                                  |
| Config       | Application properties + AWS Secrets Manager (dev/qa/prod)                          |
| API docs     | springdoc-openapi / Swagger UI (non-prod only)                                      |
| Logging      | ECS-format structured JSON (`logs/log.json`)                                        |
| TLS          | Self-signed cert at startup; ALB terminates TLS in cloud                            |
| Build        | Maven (wrapper included)                                                            |
| Code quality | Spotless (Google Java Format), Checkstyle, SpotBugs, JaCoCo, OWASP Dependency Check |

### Frontend

| Concern       | Technology                                      |
| ------------- | ----------------------------------------------- |
| Framework     | React 19 (with React Compiler)                  |
| Build         | Vite 8                                          |
| Language      | TypeScript 6 (strict)                           |
| Routing       | TanStack Router v1 — file-based                 |
| Data fetching | TanStack Query v5                               |
| UI primitives | Base UI (`@base-ui/react`) — headless, unstyled |
| Styling       | Tailwind CSS v4                                 |
| Icons         | lucide-react                                    |
| HTTP          | Native Fetch API                                |
| Testing       | Vitest + Testing Library                        |
| Formatting    | Prettier + ESLint                               |

---

## Project structure

```
demo-app/
├── backend/                  # Spring Boot application
│   ├── src/main/java/org/eds/demo/
│   │   ├── config/           # Security, web, OpenAPI, TLS config
│   │   ├── common/           # Shared utilities
│   │   └── user/             # User domain (api / application / domain)
│   ├── src/main/resources/
│   │   ├── application.properties              # Base config (defaults to prod)
│   │   ├── application-local.properties        # Local dev overrides
│   │   ├── application-feat-https.properties   # TLS feature flag
│   │   ├── application-feat-aws-secrets.properties
│   │   ├── application-unsafe-openapi.properties
│   │   └── application-unsafe-debug.properties
│   └── pom.xml
└── frontend/                 # React SPA
    └── src/
        ├── components/
        │   ├── ui/           # shadcn-style primitive components
        │   └── [feature]/    # Feature-scoped components
        ├── lib/
        │   ├── utils.ts          # cn() helper
        │   └── query-client.ts   # Shared QueryClient singleton
        └── routes/
            ├── __root.tsx    # Root layout
            └── index.tsx     # / (home)
```

---

## Setup

### Prerequisites

- Java 21+
- Node.js 20+ and npm

### Backend

```bash
cd backend

# Install git hooks (first time)
./scripts/setup-hooks.sh        # macOS/Linux
.\scripts\setup-hooks.ps1       # Windows

# Run locally (H2 database, Swagger UI enabled)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Run tests
./mvnw test

# Package
./mvnw package

# Wipe the local H2 database (see below) for a fresh schema
./mvnw clean
```

The API starts on **http://localhost:8080** (local profile).  
Swagger UI is available at `/swagger-ui.html` when the `unsafe-openapi` profile is active.

To sign in locally, seed the initial admin: set `app.admin.password` in the git-ignored
`backend/config/application-local.properties` (or export `APP_ADMIN_PASSWORD`) before the first
start. The username defaults to `admin`. There are no other built-in local users.

#### Calling the API locally with curl

The `local` profile enforces the same CSRF and session sign-in rules as every other profile, so
there is no HTTP Basic. Sign in through `POST /login` with the CSRF header and a cookie jar, then
reuse the session cookie:

```bash
JAR=$(mktemp)
BASE=http://localhost:8080

# 1. Any request through the app chain sets the XSRF-TOKEN cookie.
curl -s -c "$JAR" -o /dev/null "$BASE/welcome"
XSRF=$(awk '$6 == "XSRF-TOKEN" { print $7 }' "$JAR")

# 2. Sign in; the session cookie is stored in the jar.
curl -s -b "$JAR" -c "$JAR" -H "X-XSRF-TOKEN: $XSRF" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<your app.admin.password>"}' "$BASE/login"

# 3. Reuse the session. GETs need only the cookie; POST/PUT/PATCH/DELETE also need the header.
curl -s -b "$JAR" "$BASE/api/hello"
curl -s -b "$JAR" "$BASE/admin/api/users"
curl -s -b "$JAR" -X POST -H "X-XSRF-TOKEN: $XSRF" "$BASE/logout"
```

The local DB is file-based (`backend/data/testdb.mv.db`) so it persists across restarts, including
devtools restarts on recompile — `./mvnw clean` wipes it for a fresh schema.

#### Browsing the local DB with H2 console

1. With the backend running under the `local` profile, open http://localhost:8080/h2-console.
2. The login page defaults to a stale `jdbc:h2:mem:testdb` preset from H2's own saved settings
   (unrelated to this app's config) — replace the **JDBC URL** field with:
   ```
   jdbc:h2:file:./data/testdb;AUTO_SERVER=TRUE
   ```
   (`AUTO_SERVER=TRUE` lets the console connect to the same file while the app is still running,
   instead of being locked out — it matches `spring.datasource.url` in
   `application-local.properties`.)
3. **User Name** and **Password**: leave both blank (no credentials are configured for local H2).
4. Click **Connect**.

To connect from an external SQL client (DBeaver, IntelliJ's Database tool, etc.) instead, use the
same driver (`org.h2.Driver`) and an **absolute** path with **no `.mv.db` extension** — H2 appends
that itself, so including it (e.g. `.../data/testdb.mv.db`) silently creates and opens a second,
empty, wrongly-named database rather than erroring:

```
jdbc:h2:file:/absolute/path/to/demo-app/backend/data/testdb;AUTO_SERVER=TRUE
```

### Frontend

```bash
cd frontend

npm install
npm run dev
```

The dev server starts on **http://localhost:3000** and proxies API requests to the backend.

### Runtime profiles

| Profile | Database                   | Sessions  | Config source       | Notes                         |
| ------- | -------------------------- | --------- | ------------------- | ----------------------------- |
| `local` | H2 (file, `backend/data/`) | JDBC      | Property files      | Default for local development |
| `test`  | H2 (in-memory)             | JDBC      | Property files      | Automated tests               |
| `dev`   | MS SQL Server              | JDBC      | AWS Secrets Manager | AWS dev environment           |
| `qa`    | MS SQL Server              | JDBC      | AWS Secrets Manager | AWS QA environment            |
| `prod`  | MS SQL Server              | JDBC      | AWS Secrets Manager | Default active profile        |

---

## Code quality

```bash
# Auto-fix Java formatting
./mvnw spotless:apply

# Full verify (Checkstyle + JaCoCo + tests)
./mvnw verify

# Dependency vulnerability scan (OWASP)
./mvnw dependency-check:check

# Frontend lint + format
npm run lint
npm run format

# Frontend tests with coverage
npm run test:coverage
```
