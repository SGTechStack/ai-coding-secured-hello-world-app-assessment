# Builder Day

## Prerequisites

Install these and make sure `java`, `mvn` and `npm` are on your `PATH`:

- JDK 21 (with `JAVA_HOME` pointing at it)
- Maven 3.9 or later
- Node.js 22.12 or later (includes `npm`)

Check with `java -version`, `mvn -version` and `npm -version`.

Run every command below from the repository root.

## How the build works

Packaging the Spring Boot application also produces and embeds the frontend. During Maven's `generate-resources` phase, the backend build runs `npm ci --ignore-scripts` and `npm run build` in `frontend/`, then copies `frontend/dist/` into `static/` in the JAR. The JAR serves the built SPA, so there is no separate frontend deployment.

> The build runs `npm` from your `PATH`. To use a specific install, pass `-Dnpm.executable=<path to npm>`.

There are two kinds of environment: **local** (your machine) and **cloud** (`dev`, `sit`, `prod`, all deployed the same way).

## Local

### Values to set

None. The `local` Spring profile (`application-local.yml`) supplies everything: an in-memory H2 database, local-only Liquibase seed data from `backend/local/` (never packaged into the JAR).

### Commands

Start the backend (builds the frontend first):

```powershell
# Windows PowerShell
mvn -f backend\pom.xml spring-boot:run '-Dspring-boot.run.profiles=local'
```

```bash
# macOS/Linux
mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
```

Open [https://localhost:8443/login](https://localhost:8443/login). The backend always serves HTTPS with a self-signed certificate generated at startup, so the browser shows a certificate warning.

Optional frontend hot reload: start the backend first (its build runs `npm ci`, which fails while Vite holds `node_modules`), then:

```bash
cd frontend
npm run local
```

Open [http://127.0.0.1:5173/login](http://127.0.0.1:5173/login). Vite proxies `/csrf` and `/api` to `https://127.0.0.1:8443`.

## Cloud (dev, sit, prod)

### Values to set

Required on every start:

| Variable | Value |
| --- | --- |
| `DATASOURCE_URL` | `jdbc:postgresql://<host>:5432/<database>` |
| `DATASOURCE_USERNAME` | Database user |
| `DATASOURCE_PASSWORD` | Database password. Must not be blank: it also keys the Session hash in the logs (`session.hash`), so rotating it rotates that key. |
| `APP_PUBLIC_BASE_URI` | Absolute `https://` URI users reach the app on. Emailed links are built from it. |

Optional, except on first boot where they create the first Admin (see [Admin bootstrap](#admin-bootstrap)):

| Variable | Value |
| --- | --- |
| `ADMIN_USERNAME` | Username of the first Admin |
| `ADMIN_PASSWORD` | Password of the first Admin (plain text; a secret, so never commit it) |

Optional overrides, with their defaults in `backend/src/main/resources/application.yml` (the rate limits are explained in [Rate limiting](#rate-limiting)):

| Area | Variables |
| --- | --- |
| Connection pool | `SPRING_DATASOURCE_MAX_POOL_SIZE` (10), `SPRING_DATASOURCE_MIN_IDLE` (2) |
| Session | `APP_SESSION_ABSOLUTE_TIMEOUT` (8h) |
| Rate-limit store | `RATE_LIMIT_RETENTION` (48h), `RATE_LIMIT_CLEANUP_CRON` (every 15 min) |
| Login | `LOGIN_RATE_LIMIT_ATTEMPTS` (10), `LOGIN_RATE_LIMIT_WINDOW` (1m), `LOGIN_LOCKOUT_THRESHOLD` (5), `LOGIN_LOCKOUT_DURATION` (20m) |
| Registration | `REGISTRATION_REJECTED_ATTEMPT_THRESHOLD` (10), `REGISTRATION_RATE_LIMIT_WINDOW` (5m), `REGISTRATION_LOCK_DURATION` (24h) |
| Password | `PASSWORD_HISTORY_LENGTH` (3) |
| Password reset | `PASSWORD_RESET_TOKEN_LIFETIME` (30m), `PASSWORD_RESET_REQUEST_RATE_LIMIT_ATTEMPTS` (5) / `_WINDOW` (15m), `PASSWORD_RESET_CONFIRM_RATE_LIMIT_ATTEMPTS` (10) / `_WINDOW` (15m), `PASSWORD_RESET_EMAIL_CAP_ATTEMPTS` (3) / `_WINDOW` (1h) |
| Account hygiene | `ACCOUNT_DISABLE_AFTER` (90d), `ACCOUNT_DELETE_AFTER` (180d), `ACCOUNT_HYGIENE_CRON` (01:00 daily), `ACCOUNT_HYGIENE_ZONE` (UTC) |

### Commands

Build the JAR (embeds the frontend):

```powershell
# Windows PowerShell
mvn -f backend\pom.xml package
```

```bash
# macOS/Linux
mvn -f backend/pom.xml package
```

Set the values and start it:

```powershell
# Windows PowerShell
$env:DATASOURCE_URL = 'jdbc:postgresql://<host>:5432/<database>'
$env:DATASOURCE_USERNAME = '<username>'
$env:DATASOURCE_PASSWORD = '<password>'
$env:APP_PUBLIC_BASE_URI = 'https://<public host>'
# Optional: needed on first boot only, to create the first Admin (see Admin bootstrap). Remove afterwards.
$env:ADMIN_USERNAME = 'your.admin'
$env:ADMIN_PASSWORD = '<admin password>'
java -jar backend\target\builderday-0.0.1-SNAPSHOT.jar
```

```bash
# macOS/Linux
export DATASOURCE_URL='jdbc:postgresql://<host>:5432/<database>'
export DATASOURCE_USERNAME='<username>'
export DATASOURCE_PASSWORD='<password>'
export APP_PUBLIC_BASE_URI='https://<public host>'
# Optional: needed on first boot only, to create the first Admin (see Admin bootstrap). Remove afterwards.
export ADMIN_USERNAME='your.admin'
export ADMIN_PASSWORD='<admin password>'
java -jar backend/target/builderday-0.0.1-SNAPSHOT.jar
```

The default configuration runs the packaged Liquibase changelog, which has no local seed data, so every cloud environment runs the same changelog.

### Deployment notes

The application always serves HTTPS on port 8443 with a self-signed certificate generated in memory at startup. It is meant to sit behind an AWS ALB that holds the public certificate, so traffic stays encrypted from the ALB to the application. The ALB does not validate target certificates, so none needs to be installed. Configure the target group with protocol `HTTPS`, port `8443`, and health check `HTTPS` on `/actuator/health` with success code `200`. It is public, creates no session and answers only `{"status":"UP"}`, or `503` when the database is unreachable.

Logs go to `logs/` under the working directory: `spring.log` (application, also on the console), `audit.log` (Security audit events) and `access.log` (one Request log entry when each request arrives and one when it completes; not on the console). Until a real mail sender exists, `spring.log` also holds every Password reset link, in every environment. Each link is a live credential for 30 minutes, so treat log access as privileged (ADR 0004).

## Admin bootstrap

Registration only ever creates ordinary users. The first Admin is created at startup from `ADMIN_USERNAME` and `ADMIN_PASSWORD`, once in the database's lifetime (see `docs/adr/0009-admin-bootstrap-once-only.md`):

- `ADMIN_USERNAME`: trimmed and lowercased, 5–100 characters, only `a-z`, `0-9`, `.`, `_` and `-`, and not starting with `.`, `_` or `-`.
- `ADMIN_PASSWORD`: 12–72 printable ASCII characters, with an uppercase letter, a lowercase letter, a digit and a symbol. It must not contain the username and must not be a well-known common password.

The variables are **read only when no account with the Admin role has ever existed**. A disabled or deleted Admin still counts, so setting them and restarting never creates a second Admin and is never a recovery path.

Both are plain-text environment variables, set with the [Cloud](#cloud-dev-sit-prod) start commands. The password is a secret: typing it on the command line saves it in your shell history, so clear that history afterwards and never commit it or store it in deployment manifests or CI/CD variables.

On success it creates the Admin and logs one line with the new account id (never the username or password). Sign in through the ordinary login form, then **remove both variables from the environment**. The application logs a warning on every start while they are still set.

Failure modes:

- Setting only one of the two variables fails startup.
- A username or password that breaks the rules fails startup; the log names the violation codes, never the values.
- A username that already belongs to any account fails startup, so configuration can never promote an existing user to Admin.
- With neither variable set, the application starts normally and logs a warning that no Admin exists.

A seeded Admin who never signs in is disabled by account hygiene after 90 days and cannot be re-seeded, so log in promptly after the first boot.

## Rate limiting

Every limit below is enforced by the application itself (ADR 0003, ADR 0004). All defaults can be changed with the environment variables listed.

| Limit | Applies to | Default | Over the limit | Variables |
| --- | --- | --- | --- | --- |
| Login rate limit | `POST /api/auth/login`, per source IP | 10 attempts per 1 min | `429` "Authentication temporarily unavailable." | `LOGIN_RATE_LIMIT_ATTEMPTS`, `LOGIN_RATE_LIMIT_WINDOW` |
| Login lockout | One account | 5 consecutive failed logins lock it for 20 min | Silent: the same `401` as a wrong password, even with the correct one | `LOGIN_LOCKOUT_THRESHOLD`, `LOGIN_LOCKOUT_DURATION` |
| Registration lockout | `POST /api/auth/register`, per source IP | 10 rejected registrations within 5 min lock the IP for 24 h | `429` "Registration is temporarily unavailable. Please try again later." | `REGISTRATION_REJECTED_ATTEMPT_THRESHOLD`, `REGISTRATION_RATE_LIMIT_WINDOW`, `REGISTRATION_LOCK_DURATION` |
| Password reset request limit | `POST /api/auth/password-reset`, per source IP | 5 requests per 15 min | `429` "Too many requests. Please try again later." | `PASSWORD_RESET_REQUEST_RATE_LIMIT_ATTEMPTS`, `PASSWORD_RESET_REQUEST_RATE_LIMIT_WINDOW` |
| Password reset confirm limit | `POST /api/auth/password-reset/confirm`, per source IP | 10 attempts per 15 min | `429` "Too many requests. Please try again later." | `PASSWORD_RESET_CONFIRM_RATE_LIMIT_ATTEMPTS`, `PASSWORD_RESET_CONFIRM_RATE_LIMIT_WINDOW` |
| Password reset email cap | One account, whoever asks | 3 reset emails per 1 h | Silent: the requester still gets the usual `200`, but no email is sent | `PASSWORD_RESET_EMAIL_CAP_ATTEMPTS`, `PASSWORD_RESET_EMAIL_CAP_WINDOW` |

How they count:

- **The Login rate limit and both Password reset per-IP limits** use a fixed window. Every attempt counts, whatever its outcome, and the full allowance comes back at once when the window ends. A successful attempt never resets the window early. The Login rate limit is checked before the password, so an attempt over the limit costs nothing and reveals nothing.
- **Login lockout** counts only wrong passwords against an existing account. A successful login or a completed Password reset sets the count back to zero, and a Password reset also lifts an active lock. Attempts during a lock are not counted and never extend it; expiry is the only other way out. The lockout is silent so it cannot be used to find out which usernames exist, and each lock is recorded as an `account-lockout` Security audit event.
- **Registration lockout** counts only submissions the server rejects: an unreadable body, field-rule violations, or a username or email that is already taken. Successful registrations never count. The count refills gradually, so it approximates a rolling window: rejections spaced out more slowly than 10 per 5 minutes never lock the IP.
- **The Password reset email cap** is silent so that the answer never reveals whether an email belongs to an account. It also stops one inbox from being flooded from many IPs.

Every rate-limited attempt is recorded in `audit.log` with reason `rate_limited` (or `capped` for the email cap).

### Source IP

The per-IP limits key on the client address after Tomcat's `RemoteIpValve` has applied `X-Forwarded-For` (`server.forward-headers-strategy: native`). Tomcat trusts that header only from callers on a private address, such as the ALB. So allow inbound traffic only from the ALB (its security group): anything else on a private address that can reach the application directly could set `X-Forwarded-For` itself and dodge the per-IP limits.

### Shared store

Rate-limit state is stored in PostgreSQL (Bucket4j, table `rate_limit_buckets`), not in memory. Every instance shares the same counts, and they survive a restart. A scheduled cleanup, run by one instance at a time through ShedLock, deletes state once it has been unused for longer than the retention period.

| Setting | Default | Variable |
| --- | --- | --- |
| Retention after a bucket is full again | 48 h | `RATE_LIMIT_RETENTION` |
| Cleanup schedule (Spring cron) | `0 */15 * * * *` (every 15 min) | `RATE_LIMIT_CLEANUP_CRON` |

Locally the store is the in-memory H2 database, so all rate-limit state is lost when the backend stops.

## Build gates

`mvn package` fails when backend line coverage (JaCoCo) or frontend line coverage (Vitest) drops below 90%, or when a backend method's cyclomatic complexity exceeds 15. `-DskipTests` skips both coverage gates.

The frontend build also fails on code Prettier would reformat. To fix or check it:

```bash
cd frontend
npm run format   # rewrite files Prettier would change
npm run lint     # ESLint plus the same format check
```

## Dependency check

Run this before a release to scan the shipped dependencies for known vulnerabilities. No API key is needed; package names and versions are sent to osv.dev and the npm registry.

```powershell
# Windows PowerShell
mvn -f backend\pom.xml verify -Pdependency-scan
```

```bash
# macOS/Linux
mvn -f backend/pom.xml verify -Pdependency-scan
```

It fails on any finding:

- Backend: OSV-Scanner (downloaded by Maven and checked against its pinned SHA-256) scans the CycloneDX SBOM of the non-test dependencies and fails on any known vulnerability.
- Frontend: `npm audit --omit=dev --audit-level=high` fails on high or critical findings.

To accept a reviewed backend finding, add it to `backend/osv-scanner.toml` with a reason and an `ignoreUntil` date.
