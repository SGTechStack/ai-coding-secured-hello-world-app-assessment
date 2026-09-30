# Running the app

Two processes, two origins:

| Process | Origin | Start from |
| --- | --- | --- |
| Spring Boot API | `http://localhost:8080` | `backend/` |
| Vite dev server (React) | `http://localhost:3000` | `frontend/` |

## Prerequisites

- JDK 21 or newer (the code targets Java 21; JDK 26 is installed on the authoring machine).
- Node.js 20 or newer with npm.
- No Maven install needed: `mvnw` / `mvnw.cmd` downloads Apache Maven 3.9.15 into `~/.m2/wrapper` on first use.

## 1. Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

(macOS/Linux: `./mvnw spring-boot:run`.)

`spring-boot:run` activates the **dev** profile automatically (configured in `pom.xml`), which means:

- in-memory H2 database (data is lost on restart, the admin is re-seeded);
- CORS allows `http://localhost:3000`;
- session cookie without the `Secure` flag (plain-HTTP localhost);
- Swagger UI at <http://localhost:8080/swagger-ui.html>;
- a seeded admin: username `admin`, password `ChangeMe-Secure-2026!` (from `application-dev.yaml`; note the policy also rejects passwords that contain the username).

Wait for `Started HelloWorldApplication` in the console.

### Backend tests

```powershell
cd backend
.\mvnw.cmd test
```

Runs the unit tests, the ArchUnit layering test and the MockMvc integration tests (registration, login, lockout and IP throttling, logout replay, protected greeting, password reset, admin role enforcement and self-action guards, admin bootstrap).

## 2. Frontend

```powershell
cd frontend
npm install
npm run dev
```

Open <http://localhost:3000>. The API base URL defaults to `http://localhost:8080`; override it with a `frontend/.env.local` containing `VITE_API_BASE_URL=...` (see `.env.example`).

### Frontend checks

```powershell
cd frontend
npm run lint
npm run typecheck
npm test
```

## 3. Walkthrough

1. Open <http://localhost:3000> — you are redirected to **Log in**.
2. Click **Register**, create a user (password must be at least 12 characters), then log in. The home page shows `Hello, <username>` fetched from `GET /api/hello`.
3. Log out; the session is gone server-side (try the back button — the home page redirects to login again).
4. Try five wrong passwords: the account locks for 15 minutes (`app.security.login.*`). The error message is identical for wrong password, unknown user, locked and disabled accounts.
5. **Forgot your password?** — enter the email. The backend console prints a line like
   `[EMAIL STUB] To: you@example.com | Password reset link: http://localhost:3000/reset-password?token=...`. Open that link, set a new password (this also logs the user out everywhere), then log in with it. The link works exactly once and expires after 20 minutes.
6. Log in as `admin` / `ChangeMe-Secure-2026!` → **Admin** in the header lists users, and lets you disable/enable, change role and delete anyone except yourself.

## Running the packaged jar (non-dev)

```powershell
cd backend
.\mvnw.cmd package -DskipTests
$env:APP_ADMIN_USERNAME="admin"; $env:APP_ADMIN_EMAIL="admin@example.com"; $env:APP_ADMIN_PASSWORD="<12+ chars>"
$env:APP_SECURITY_ALLOWED_ORIGINS_0="https://app.example.com"
java -Dnet.bytebuddy.experimental=true -jar target\secured-hello-world-backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

Without `--spring.profiles.active=dev` the jar runs with the hardened base config: `Secure` cookies, no CORS origins, `ddl-auto=validate` (bring a migrated database), docs disabled, and it refuses to start unless the admin credentials above are supplied on an empty database. Put it behind HTTPS (see `docs/architecture.md`).

## Troubleshooting

- **`Cannot start maven from wrapper`** — PowerShell script execution is blocked. Run `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` once, or use the Maven bundled with your IDE.
- **Byte Buddy / "Java 26 is not supported"** — the `pom.xml` already passes `-Dnet.bytebuddy.experimental=true` to `spring-boot:run` and to Surefire. If you run `java -jar` yourself, pass the flag as shown above.
- **Port in use** — backend: `server.port` in `application.yaml`; frontend: `server.port` in `vite.config.ts` (also update `app.security.allowed-origins`).
- **CORS / 403 in the browser** — make sure you open the app on `http://localhost:3000` exactly (not `127.0.0.1` unless you add it to the allow-list) and that the backend is on `dev` profile.
