# Running the Hello World Auth App

A secure username/password login demo: **React** frontend (`:3000`) + **Spring Boot** backend (`:8080`), session-cookie auth with CSRF, H2 in dev.

## Prerequisites

| Tool | Version used | Check |
|------|--------------|-------|
| JDK | 21 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Node.js | 22 | `node --version` |
| npm | 9+ | `npm --version` |

The two apps run as **separate processes on separate origins**. Start the backend first, then the frontend.

## Backend (`backend/`, port 8080)

```bash
cd backend

# Run tests
mvn test

# Run in dev (H2 in-memory, hot classes)
mvn spring-boot:run

# — or build a jar and run it —
mvn -DskipTests package
java -jar target/hello-world-auth-0.0.1-SNAPSHOT.jar
```

On startup an **admin account is auto-seeded** (only if no ADMIN exists) from `application.yml`:

- username `admin`
- password `change-me-please-12+`  ← **dev default — override in any real environment**

Override via env/args, e.g.:

```bash
java -jar target/hello-world-auth-0.0.1-SNAPSHOT.jar \
  --app.admin.username=admin --app.admin.password='your-strong-password-12+'
```

Health check: `curl http://localhost:8080/api/ping` → `{"status":"ok"}`.

### Profiles

- **dev (default):** H2 in-memory, session cookie `Secure=false` (HTTP allowed locally).
- **prod:** activate with `--spring.profiles.active=prod` — sets the session cookie `Secure` (requires HTTPS in front). See `application-prod.yml`.

## Frontend (`frontend/`, port 3000)

```bash
cd frontend

# Install dependencies (first run only)
npm install

# Run tests
npm test

# Dev server (proxies to the backend at http://localhost:8080)
npm run dev
```

The API base defaults to `http://localhost:8080`; override with the `VITE_API_BASE` env var if the backend runs elsewhere.

## How auth works (so requests succeed)

Auth is **cookie + CSRF** based. The flow the SPA follows:

1. A GET/first request receives an `XSRF-TOKEN` cookie (readable by JS — `HttpOnly=false`).
2. Every state-changing request (`POST`/`PUT`/`PATCH`/`DELETE`) must echo that token in the **`X-XSRF-TOKEN`** header and send credentials (`fetch(..., { credentials: "include" })`).
3. On login the server sets an `HttpOnly` session cookie; subsequent requests carry it automatically.

CORS on the backend allow-lists the frontend origin with `Access-Control-Allow-Credentials: true`, so the cookies travel cross-origin between `:3000` and `:8080`.

## Running the tests

Both suites run offline and require no running server (they spin up their own context / jsdom).

### Backend (JUnit + Spring Boot Test, `backend/`)

```bash
cd backend
mvn test                                   # full suite (51 tests)
mvn -Dtest=LoginControllerTest test         # a single test class
mvn -Dtest=LoginControllerTest#loginSucceedsAndResetsAttempts test   # one method
```

Covers the security-critical paths from the PRD: registration validation, login
(success / wrong password / unknown user identical error / locked account),
lockout + IP throttling, logout + session-replay rejection, password-reset
single-use / expiry / session-invalidation, admin role enforcement (403 for
USER), admin self-action guards, and admin-bootstrap idempotency.

### Frontend (Vitest + Testing Library, `frontend/`)

```bash
cd frontend
npm install        # first run only
npm test           # full suite (21 tests, runs once and exits)
npx vitest         # watch mode while developing
npx vitest run src/test/LoginForm.test.jsx   # a single test file
```

Covers the React components (register, login, greeting, logout, password-reset
forms, admin table + controls) against a mocked API client.

### Everything at once

```bash
( cd backend && mvn test ) && ( cd frontend && npm test )
```

> Note on test scope: the suites are unit/slice-level and mock the auth/CSRF
> plumbing. For the real filter-chain + cookie flow, boot the app and run the
> end-to-end sanity check below — a full-stack integration test is recommended
> and not yet present.

## End-to-end sanity check (curl)

With the backend running:

```bash
B=http://localhost:8080
# prime the CSRF cookie, capture it, then register
curl -s -c jar.txt -o /dev/null -X POST -H "Content-Type: application/json" -d '{}' $B/api/register
T=$(grep XSRF-TOKEN jar.txt | awk '{print $NF}')
curl -s -b jar.txt -c jar.txt -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $T" \
  -d '{"username":"demo","email":"demo@example.com","password":"correcthorsebattery"}' $B/api/register
# login, then hit the protected greeting
T=$(grep XSRF-TOKEN jar.txt | awk '{print $NF}')
curl -s -b jar.txt -c jar.txt -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $T" \
  -d '{"username":"demo","password":"correcthorsebattery"}' $B/api/login
curl -s -b jar.txt $B/api/hello    # -> {"message":"Hello, demo"}
```

## API endpoints

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/api/ping` | none | health check |
| POST | `/api/register` | none (CSRF) | register a USER account |
| POST | `/api/login` | none (CSRF) | start a session |
| POST | `/api/logout` | session (CSRF) | end the session |
| GET | `/api/hello` | session | personalized greeting |
| POST | `/api/password-reset/request` | none (CSRF) | request a reset (stub email logs the link) |
| POST | `/api/password-reset/confirm` | none (CSRF) | set a new password with a token |
| GET | `/api/admin/users` | ADMIN | list users (no password hashes) |
| PATCH | `/api/admin/users/{id}/enabled` | ADMIN (CSRF) | enable/disable an account |
| PATCH | `/api/admin/users/{id}/role` | ADMIN (CSRF) | change role USER/ADMIN |
| DELETE | `/api/admin/users/{id}` | ADMIN (CSRF) | delete an account |

Admin mutations reject an admin acting on **their own** account. The password-reset email is a stub that logs the reset link instead of sending mail.

## Notes / demo limitations

- H2 is in-memory: **data resets on restart** (schema is portable to Postgres/MySQL).
- IP-throttle and session stores are **in-memory** — swap for a shared store (e.g. Redis) in production.
- Local dev runs over HTTP; a real deployment must sit behind HTTPS for `Secure` cookies.
