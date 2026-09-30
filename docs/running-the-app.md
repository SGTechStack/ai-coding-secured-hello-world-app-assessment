# Running the app

Two parts: `backend/` (Spring Boot) and `frontend/` (Vite). Start the backend first — the frontend proxies
to it.

## Prerequisites

- Java 21, and Docker running (only for `mvn verify`, which re-runs the whole suite on Testcontainers
  PostgreSQL)
- Node 20+
- `git submodule update --init` if `App-Standards/` is empty — every `path:line` citation in the plan
  points there

## 1. Backend

`APP_ADMIN_PASSWORD` is **mandatory**. Startup fails without it rather than seeding a guessable
administrator, and the value must satisfy the real password policy: 12–72 printable ASCII characters, not
on the denylist, and not containing the username or the email local part.

```bash
cd backend
APP_ADMIN_PASSWORD='orchard signal ridge' ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile uses **file-based** H2 at `.data/dev` — deliberately, so that bootstrap idempotence is
observable across restarts (a second start must seed no duplicate administrator). Delete `.data/` to start
from a clean database.

Listens on `http://localhost:8080`, API base path `/api/v1`.

## 2. Frontend

```bash
cd frontend
npm install
npm run dev
```

Serves `http://localhost:5173` and proxies `/api` to `:8080`. The proxy is not a convenience: the session
cookie is `Secure` and `SameSite=Lax` in every profile, so calling `:8080` cross-origin from `:5173` would
have the browser withhold the cookie on exactly the requests that matter. Proxying makes every call
same-origin, which is also how the built bundle is served.

> If Vite reports `Port 5173 is in use` it falls back to 5174 and prints the URL it chose. The proxy works
> either way. Note that Vite binds `127.0.0.1`; if `http://localhost:<port>` does not load, try
> `http://127.0.0.1:<port>`.

## 3. The first boot is three steps

**Say this in any demo, or the first thing a reviewer does will read as broken software.** The seeded
administrator is flagged `requirePasswordChange`, and a successful change invalidates *every* session for
the account including the caller's own.

1. **Sign in** as `admin` with your `APP_ADMIN_PASSWORD`. Every screen except the change-password one
   answers `403 PASSWORD_CHANGE_REQUIRED` — the tier-0 filter sits ahead of the authorization matrix.
2. **Change the password.** You are signed out. This is correct, not a bug.
3. **Sign in again** with the new password. The administrator screens are now reachable.

The old password cannot be reused: reuse history blocks the current password plus the three previous.

## Verifying

```bash
# Backend: unit + ArchUnit, then the whole integration suite twice -- H2, then Testcontainers PostgreSQL.
# Needs Docker. Around three and a half minutes.
cd backend && ./mvnw verify

# Frontend
cd frontend && npm run typecheck && npm run lint && npm test && npm run build
```

Not wired into either command, and both still open: `im8-review`, `browser-test`, and
`mvn -Powasp verify` (which needs an `NVD_API_KEY`). See [`limitations.md`](limitations.md).

## Reset flow without a mail server

There is no mail server, and the reset link never passes through the logging framework. In `dev` only, the
link is printed to the console by `DevConsoleResetLinkChannel`. An administrator can also issue a token
directly with `PATCH /api/v1/users/{id}/resetPassword`, which returns it in the response — that is how the
user-detail screen shows one.
