# AGENTS.md

Guidance for running the secured Hello World auth app (React + Spring Boot).

## Running the backend — required command

The backend seeds its initial `ADMIN` account **only** from environment variables
(IM8 as-8: no plaintext credentials are committed). You **must** run the backend with
the admin env vars set, or no usable admin is created.

From the `backend/` directory, run:

```bash
APP_ADMIN_USERNAME="admin" APP_ADMIN_EMAIL="admin@example.com" APP_ADMIN_PASSWORD="ChangeMeNow123" mvn spring-boot:run
```

This is the canonical run command. Do not run `mvn spring-boot:run` on its own for a
usable admin — without these variables the seeder skips (or, with blank values, seeds a
broken admin) and you can only register regular `USER` accounts.

### Shell notes

- **Git Bash / Linux / macOS:** use the command above as-is (inline `VAR=value` exports
  apply to that single command). `set VAR=value` does **not** work in Bash — use `export`
  or the inline form.
- **PowerShell:** set the variables first, then run:
  ```powershell
  $env:APP_ADMIN_USERNAME="admin"; $env:APP_ADMIN_EMAIL="admin@example.com"; $env:APP_ADMIN_PASSWORD="ChangeMeNow123"; mvn spring-boot:run
  ```
- **cmd.exe:** `set APP_ADMIN_USERNAME=admin` (etc.) on separate lines, then `mvn spring-boot:run`.

### Confirming the seed

On success the startup log shows:

```
event=admin_seeded username="admin"
Seeded initial ADMIN account 'admin' (must change password on first login).
```

If `username=""` appears, the env vars did not reach the app (usually a shell mismatch).

## Credentials

- **Admin:** the values you passed above — `admin` / `ChangeMeNow123`. On first login the
  app forces a password change before any admin action is allowed (IM8 ac-6).
- **Regular user:** register via the Register page or `POST /api/register`. Passwords must
  be at least 12 characters.
- These are examples, not hardcoded defaults. Choose your own; nothing is baked in.

## Running the frontend

In a second terminal, from `frontend/`:

```bash
npm install   # first time only
npm run dev   # http://localhost:3000, proxies /api to :8080
```

Open http://localhost:3000.

## Database

Dev uses in-memory **H2** (`jdbc:h2:mem:helloauth`), so all data — including the seeded
admin — is wiped on every restart and re-seeded on next launch. The `prod` profile targets
an external database via `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`.

## Tests

```bash
cd backend
mvn test      # 24 security integration tests
```

## More documentation

See `docs/ARCHITECTURE.md` for architecture, API specification, data model, data-flow,
network topology, security controls, and the IM8 compliance/waiver register.
