## Agent skills

### Issue tracker

Issues and specs are tracked as local markdown files under `.scratch/<feature-slug>/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Default five canonical roles, unchanged: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## Running the app

Two processes, backend first.

### 1. Backend (Spring Boot, port 8080)

```bash
cd backend
export APP_ADMIN_USERNAME="admin"
export APP_ADMIN_PASSWORD="choose-a-strong-bootstrap-password"
mvn spring-boot:run
```

Wait for `Started SecuredHelloWorldApplication` in the log. Runs on the `dev` profile by
default (H2 file DB at `backend/data/`, `Secure` cookie attribute off since local dev is plain
HTTP). `APP_ADMIN_PASSWORD` must be set to a real value or the admin bootstrap skips (logs a WARN)
rather than hashing a blank password — no ADMIN account gets seeded until you provide one.

If `mvn` can't resolve dependencies (corporate TLS-intercepting proxy on some machines), prefix
with:

```bash
export MAVEN_OPTS="-Djavax.net.ssl.trustStore=$HOME/.m2/cacerts-with-zscaler.jks -Djavax.net.ssl.trustStorePassword=changeit"
```

In PowerShell, use `$env:VAR = "value"` instead of `export VAR="value"`.

### 2. Frontend (Vite dev server, port 3000)

```bash
cd frontend
npm install   # first time only
npm run dev
```

If `npm install` can't resolve packages for the same proxy reason:

```bash
export NODE_EXTRA_CA_CERTS="$HOME/.m2/zscaler-root.pem"
```

Then open **http://localhost:3000**. If you load it before the backend is up, the CSRF-priming
call on page load fails silently — just refresh once the backend log shows it started.

### Using it

- Register → log in → see the greeting → log out, all from the UI.
- The seeded admin (`APP_ADMIN_USERNAME`/`APP_ADMIN_PASSWORD` above) is forced to change their
  password before any `/api/admin/**` endpoint works (`PASSWORD_CHANGE_REQUIRED`, IM8 `ac-6`).
  There's no "change my own password" screen — use the same forgot/reset-password flow everyone
  else uses, against the admin's email (`<APP_ADMIN_USERNAME>@admin.local` unless the bootstrap
  runner's synthesized-email logic changes).
- Password reset never sends real email (PRD-accepted stub, out of scope by design). The reset
  link, including the plaintext token, is printed to the **backend's own console/log** at DEBUG
  level, `dev` profile only — never at INFO, never in the audit stream, never emitted under
  `prod`. Grep the backend log for `reset-password?token=` to find it.

### Stopping

`Ctrl+C` in each terminal. To reset all data, delete `backend/data/` before the next backend
start.
