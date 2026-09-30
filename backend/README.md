# Secured Hello World: backend

## Running locally

Every secret and origin is required and has no default in any profile (ADR-062). Startup refuses before the port opens
if one is missing or malformed. Supply them as environment variables (below), as files mounted under `/run/secrets/`
named by property (for example `/run/secrets/app.admin.password`), or, in the `dev` profile only, in a `.env` file of
`property=value` lines in the working directory. `.env` is git-ignored and never read outside `dev`; a mounted file
wins over it:

| Variable | Property | Value |
|---|---|---|
| `APP_MFA_TOTP_ENCRYPTION_KEY` | `app.mfa.totp.encryption.key` | 32 random bytes, padded Base64 |
| `APP_MFA_TOTP_ENCRYPTION_KEYVERSION` | `app.mfa.totp.encryption.key-version` | `0`–`255`, e.g. `1` |
| `APP_SECURITY_HMAC_TOMBSTONE_KEY` | `app.security.hmac.tombstone.key` | 32 random bytes, padded Base64 |
| `APP_SECURITY_HMAC_TOMBSTONE_VERSION` | `app.security.hmac.tombstone.version` | `0` or more, e.g. `1` |
| `APP_SECURITY_HMAC_LOG_KEY` | `app.security.hmac.log.key` | 32 random bytes, padded Base64 |
| `APP_SECURITY_LOCKOUT_DEVICE_SECRET` | `app.security.lockout.device.secret` | 32 random bytes, padded Base64; the device-cookie key (ADR-075). Under `dev` a published value in `application-dev.yml` applies unless you set one |
| `APP_ADMIN_USERNAME` | `app.admin.username` | the seed administrator's username |
| `APP_ADMIN_PASSWORD` | `app.admin.password` | the seed administrator's initial password |
| `APP_ORIGINS_SPA` | `app.origins.spa` | e.g. `http://localhost:5173` |
| `APP_ORIGINS_API` | `app.origins.api` | e.g. `http://localhost:8080` |

Generate each key separately. The four keys must be different: startup refuses a reused key. Use a CSPRNG
(R-CFG-008):

```sh
openssl rand -base64 32
```

```powershell
$b = [byte[]]::new(32); [System.Security.Cryptography.RandomNumberGenerator]::Fill($b); [Convert]::ToBase64String($b)
```

Never use PowerShell's `Get-Random`. Never commit a key, or put one in a committed properties or YAML file.

Rotating the TOTP key (yearly, ADR-022; R-CFG-005): generate a new key, raise `APP_MFA_TOTP_ENCRYPTION_KEYVERSION`, and
keep the old key under its old version as `APP_MFA_TOTP_ENCRYPTION_RETIREDKEYS_<old version>`
(`app.mfa.totp.encryption.retired-keys.<old version>`). New secrets are sealed under the new version, and rows still
sealed under a retired version keep opening with its key. Re-sealing each row under the new key, and then dropping the
retired key, is the operator's rotation procedure; a row whose version has no configured key fails with a 500 naming
the version. A retired key is checked like any other key and must differ from every other key.

Then start the app under the `dev` profile:

```sh
mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev
```

At startup the log shows one `Key loaded` line per key, with its property, version and an 8-hex-character
fingerprint. The key itself is never logged. Compare fingerprints to confirm which key is live.

Before the port opens, startup also ends the stored sessions of every account that is disabled, deleted, locked,
password-capped or under a tier-2 factor disable, and writes one "Sessions reconciled at startup." audit row
(`event.action` `session-end`) with the counts (ADR-039). A lost session kill is repaired by restarting the app.

The tests need none of these variables. The test harness supplies test-only canary values (`TestSecrets`).

### Rate limits while developing

Sign-in and token fetches are throttled per source address and per username (`app.security.rate-limit.*` in
`application.yml`; refusals are 429 with `Retry-After`). Locally everything arrives from one loopback address, so
scripted loops can reach a budget: wait out the `Retry-After`, or raise the budget's `burst` with a command-line
property such as `--app.security.rate-limit.login.username.burst=1000`. The shared test contexts raise them the same
way, from `src/test/resources/harness-budgets.properties`; `CtxBudgetTest` runs on the real values.

Five wrong passwords within 20 minutes lock an account for 20 minutes (then 40, then 60 as locks repeat); so does
the tenth wrong password since the last sign-in however spread out, and every fifth after it. 100 in a row disable its
password until it is reset (`app.security.lockout.*`). A source that has locked five accounts in an
hour gets 429 for any other username (`app.security.rate-limit.lockout-cardinality.*`). Restarting clears the 429,
not the lock, which is stored on the account. Startup refuses a lockout ladder faster than the one committed.

A token fetch with no session gets 429 with `Retry-After: 60` while new anonymous sessions are shed: when
`SPRING_SESSION` holds 100,000 rows, or the database volume's free space falls under 25.5 KB per row plus
`app.security.session.shedding.floor` (256 MB). An episode lasts at least 60 seconds (ADR-041). While it runs,
`/actuator/health/storage` reports `DOWN` (503), and `/actuator/health`, the endpoint to point load balancers at,
stays `UP`: signed-in users are unaffected, so the node must stay in rotation.

### Metrics

Metrics are pushed over OTLP and are off by default. To export them, add the `otlp` profile and give the collector's
https URL in `OTLP_METRICS_URL`, for example `--spring.profiles.active=dev,otlp`. Without the URL, or with one that is not https
(`dev` included), startup stops
(ADR-061).

### Shared dev demo values

For local development and demos only, anyone who checks out this branch can paste one of these blocks into a
terminal and then run the `spring-boot:run` command above, from the repository root.

> **These values are public.** They are committed to git, so treat them as compromised. Never use them outside a
> local `dev` run: generate fresh keys for any shared, staging or production environment. Without the `dev` profile,
> startup refuses any of these keys (in any key variable) and this admin password, or an earlier published one
> (`PublishedDemoValues` holds only their fingerprints and digests). The same goes for the demo accounts' values in
> `application-dev.yml` (see [Demo accounts](#demo-accounts-dev-only)).

PowerShell (current window only):

```powershell
$env:APP_MFA_TOTP_ENCRYPTION_KEY         = "mOGKko7uLAPLYyxIHIZuSZGoPezdG3RxHbQwve0/GUM="
$env:APP_MFA_TOTP_ENCRYPTION_KEYVERSION  = "1"
$env:APP_SECURITY_HMAC_TOMBSTONE_KEY     = "BHTFVzkUqlCU8Z1m+CEmMPGnv+oZWNKtp00vvZX4Yg8="
$env:APP_SECURITY_HMAC_TOMBSTONE_VERSION = "1"
$env:APP_SECURITY_HMAC_LOG_KEY           = "Y1l63mWeV+COTPcyKMi1tQf3dBUFFNbpONXPDeR6u58="
$env:APP_ADMIN_USERNAME                  = "demo-admin"
$env:APP_ADMIN_PASSWORD                  = "lantern-orchard-copper-tide"
$env:APP_ORIGINS_SPA                     = "http://localhost:5173"
$env:APP_ORIGINS_API                     = "http://localhost:8080"
mvn -f backend/pom.xml spring-boot:run "-Dspring-boot.run.profiles=dev"
```

bash / Git Bash:

```sh
export APP_MFA_TOTP_ENCRYPTION_KEY="mOGKko7uLAPLYyxIHIZuSZGoPezdG3RxHbQwve0/GUM="
export APP_MFA_TOTP_ENCRYPTION_KEYVERSION="1"
export APP_SECURITY_HMAC_TOMBSTONE_KEY="BHTFVzkUqlCU8Z1m+CEmMPGnv+oZWNKtp00vvZX4Yg8="
export APP_SECURITY_HMAC_TOMBSTONE_VERSION="1"
export APP_SECURITY_HMAC_LOG_KEY="Y1l63mWeV+COTPcyKMi1tQf3dBUFFNbpONXPDeR6u58="
export APP_ADMIN_USERNAME="demo-admin"
export APP_ADMIN_PASSWORD="lantern-orchard-copper-tide"
export APP_ORIGINS_SPA="http://localhost:5173"
export APP_ORIGINS_API="http://localhost:8080"
mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev
```

Any replacement for `APP_ADMIN_PASSWORD` must pass the password policy that `PasswordService` runs on every password
set: at least 15 characters, at most 72 UTF-8 bytes, not a breached or banned password, not containing the username,
the email local part or the service name, and a zxcvbn score of 3 or more (ADR-005). `APP_ADMIN_USERNAME` must be a valid
username (lower case, `[a-z0-9._-]{3,32}`) and not a reserved name such as `admin`, `administrator` or `root`
(`Identifiers.RESERVED_USERNAMES`). A bad value in either stops startup before the port opens.

Then start the SPA in a second terminal (`cd frontend; npm run dev`) and open http://localhost:5173. Check the
backend with `curl http://localhost:8080/actuator/health`, which returns `{"status":"UP"}`.

To get an account, register at http://localhost:5173/register. No mail is sent: under `dev` the activation link is
written at `DEBUG` to the dev-only logger `sg.securedhello.email.ResetLinkLogger` in the backend's console (ADR-057).
Open it, set a password, then sign in. A forgotten password works the same way: request a reset at
http://localhost:5173/forgot-password and the reset link, valid for 30 minutes, appears on the same logger. Outside
`dev` that logger is refused at startup, so a link is delivered nowhere.

### Demo accounts (dev only)

For demos, a `dev` start seeds two ready-to-use accounts on an empty database, before the administrator bootstrap
runs. Their values are committed in `src/main/resources/application-dev.yml` under `app.dev.demo-accounts`:

| Account | Role | Password | Second factor |
|---|---|---|---|
| `demo-user` (`demo-user@demo.invalid`) | `USER` | `violet-harbour-signal-meadow` | none |
| `demo-admin` (`demo-admin@demo.invalid`) | `ADMIN` | `granite-falcon-ember-quarry` | TOTP, pre-enrolled with the Base32 secret `X47C675OXQB5LUEX3C5MGK6A445A23M2` |

Both are activated and enabled, and neither has to change its password. `demo-admin` is an enrolled, authenticable
administrator from the first start: its TOTP secret is stored sealed, exactly as an enrolment stores it. The console
logs `Demo accounts: seeded demo-user` and `Demo accounts: seeded demo-admin, enrolled in TOTP`, then
`Administrator bootstrap: an ADMIN account exists, so none is seeded`. A restart seeds nothing again, and an account
that already exists, or whose username or address a tombstone holds, is left as it is.

The sign-in page shows a **Demo accounts** panel with each account's username, email address and password (and
**Copy** and **Fill in** buttons) and `demo-admin`'s current six-digit code, which refreshes as each 30-second step
ends. The panel reads `GET /api/dev/demo-accounts`, which always lists both accounts and computes the code on the
server from the committed secret; no demo value, address included, is compiled into the SPA. A password changed since
seeding is replaced by a note, and the code is dropped once `demo-admin`'s authenticator has been reset. An account
the database does not hold as seeded (deleted, or a `demo-admin` that an older `dev` start's administrator bootstrap
created as `demo-admin@admin.invalid`) is listed as **Not available**, with nothing read from it. To add `demo-admin`
to an authenticator app instead, type in the secret above (time-based, 6 digits, 30 seconds).

The addresses serve the forgot-password flow: submit one on **Forgot password?** and the reset link is logged by the
dev link logger like any other (`Dev-only PASSWORD_RESET link (no mail is sent): …`); the `.invalid` domain only
guarantees that no real mailbox could ever receive it.

The seed state lives in the H2 database under `backend/data/` (`app.db.data-dir`). Stop the app and delete that
folder to return to a clean seed: the next `dev` start seeds both accounts afresh, with their committed passwords and
the pre-enrolled factor.

**These accounts exist only under `dev`.** Outside `dev` nothing seeds them, `GET /api/dev/demo-accounts` has no
handler and no authorization-matrix row, so it is refused exactly as an unknown route is, and startup refuses any
`app.dev.demo-accounts` property, the two demo passwords as `APP_ADMIN_PASSWORD`, and a database that still holds an
activated demo account (a `dev` database copied elsewhere). To start under `dev` without them, set
`APP_DEV_DEMOACCOUNTS_ENABLED=false` on an empty database.

### Signing in as the seeded administrator

Without the demo accounts (`APP_DEV_DEMOACCOUNTS_ENABLED=false`), and in every other profile, the first start against
an empty database seeds one administrator from `APP_ADMIN_USERNAME` and `APP_ADMIN_PASSWORD` (ADR-047); with the demo
values above that is `demo-admin` / `lantern-orchard-copper-tide`. It seeds only while no `ADMIN` account exists, so
later starts seed nothing and changing the variables afterwards has no effect. The database is the H2 file under
`backend/data/`: stop the app and delete that folder to start over. The folder is `app.db.data-dir`
(`APP_DB_DATA_DIR`), and the default datasource URL names a file inside it; if you point `spring.datasource.url`
somewhere else, set `app.db.data-dir` to a directory that holds that file too, or startup refuses (R-OBS-019).

The seeded password is a forced-change credential (ADR-046):

1. Sign in at http://localhost:5173 as `demo-admin`. The app goes straight to the Change password page, and until the
   change is made every API route except sign-in, sign-out, the CSRF token, the self-read and the change itself
   answers 403 `PASSWORD_CHANGE_REQUIRED`.
2. Enter `lantern-orchard-copper-tide` as the current password and a new one that passes the policy. The session
   stays signed in and is no longer confined.
3. Enrol an authenticator app (ADR-023). After the change the app goes straight to **Two-factor authentication**
   (http://localhost:5173/settings/mfa). Choose **Generate QR code**, scan the code with any TOTP app (or type the key
   shown under it: time-based, 6 digits, 30 seconds), then enter the app's current code and **Confirm**. The key is
   shown once: generating again replaces it, and nothing re-displays it. Enrolment is refused until the password has
   been changed. Then choose **Continue to the user list**. Every later sign-in asks for a code on **TOTP
   Verification** before the admin pages open.

An unchanged seed password expires 30 days after the first start: sign-in then fails with the uniform 401. Delete
`backend/data/` to get a fresh seed. With the demo accounts on, the same forced change and enrolment can be seen on a
new administrator: invite one as `ADMIN`, activate it from the logged link, disable and re-enable it before it enrols
(a re-enable issues a forced-change credential), then sign in as it.

The issuer the app shows beside the account is `app.mfa.totp.issuer`, `Secured Hello World` in `application.yml`; it
is required and may not be blank.

### User administration

The admin pages (`/admin/users`) need the password and a verified TOTP code. Reads accept a code verified at any
point in the session; every change needs one from the last 10 minutes, and the app asks for a fresh code (step-up)
before replaying the change (ADR-021). From the user list and each user's page an administrator can:

- invite a user as `USER` or `ADMIN`, and see the activation link once to pass on; inviting the same pending
  username and email again re-issues it (ADR-006). A self-registration left unactivated for 24 hours no longer holds
  its username or email against an invite. An invite whose link expired unredeemed lapses the same way: the invitee
  may then register themselves (as a `USER`), and another address may take its username, unless an administrator
  re-invites it first (ADR-007; ADR-032);
- enable or disable an account (a disable ends its sessions; a re-enable makes the user change their password at the
  next sign-in), change its role between `USER` and `ADMIN`, or delete it, leaving a tombstone that blocks the
  username and email (ADR-044);
- issue a password-reset link, shown once, for any activated, enabled account, their own included (ADR-006);
- unlock another account's password lockout and TOTP lock, with a reason (REJ-072);
- reset another administrator's authenticator, which ends their sessions and makes them enrol again (ADR-049).

No administrator can disable, demote, delete, unlock or reset the factor of their own account, and disabling,
demoting or deleting either of exactly two enrolled administrators is refused (ADR-048). A change that has to wait
too long for other changes to the administrator accounts is refused with 503 `SERVICE_BUSY` and `Retry-After: 1`,
having changed nothing; the page says the server was busy and to try again.

### Recovering an account with the recovery runner

When no administrator can sign in (a lost password or authenticator, the 100-failure cap, a tier-2 factor disable),
the operator recovers offline with the application jar itself (ADR-072). It is a planned outage: **stop the
application first**. While it runs the H2 file is locked, and the runner refuses.

1. Build the jar (`mvn -f backend/pom.xml package -Ddependency-check.skip=true` locally) and use the same environment
   variables, profile and datasource as the application: the runner starts the same context, so the same startup
   checks apply. It never migrates, never seeds, and opens only an existing database file.
2. Dry run, which changes nothing and prints a digest of the account state:

   ```sh
   java -jar backend/target/secured-hello-world-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev \
     --rebind --scope=password --username=demo-admin --operator=<your staff id>
   ```

   `--scope` is `password`, `totp` or `both`, never implied. `totp` deletes the factor so the admin enrols again.
   `--batch=<file>` (one username per line, at most 500) instead of `--username` invalidates passwords and mints
   nothing: each account then recovers through a password reset, which an enrolled administrator issues in-app
   (**Issue password reset link** on the user's page) once the application is back. Invalidating also cancels the
   account's pending reset tokens, the administrator's included, as setting a password does.
3. Compare the printed database path, schema version and file time with the deployed database, then apply with the
   digest and a reason. A stale or wrong digest is refused, so the same command never fires twice:

   ```sh
   java -jar backend/target/secured-hello-world-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev \
     --rebind --scope=password --username=demo-admin --operator=<your staff id> \
     --confirm=<digest> --reason="lost authenticator"
   ```

   For `password` scope with `--username`, the runner prompts for the new password without echoing it. With no
   terminal, pass `--non-interactive` and pipe the password as the first line of stdin. A password is never an
   argument: `--password` is refused. The password must pass the policy, and the account must change it at its next
   sign-in, within 30 days.
4. Restart the application. The runner's dry-run, intent and outcome rows are in the audit file, with the enrolled-admin
   count before and after; it bypasses the two-admin guard on purpose, so zero after is possible and is the alert.

Exit status: `0` done (or a dry run), `1` the context did not start (missing file, database in use, other schema
version, refused configuration), `2` a refused invocation, `3` refused (account state, digest, no password), `4` the
apply failed and rolled back, `5` an unexpected failure after start: check the audit file for an intent row with no
outcome row.

### The fixture backend

To demo sign-in without registering, use the fixture backend: it creates the accounts and needs none of the variables
above.

```powershell
mvn -f backend/pom.xml test-compile spring-boot:test-run "-Dspring-boot.run.main-class=sg.securedhello.e2e.E2eBackend" "-Dspring-boot.run.arguments=--server.port=8080 --app.origins.spa=http://localhost:5173 --app.origins.api=http://localhost:8080"
```

Sign in as `e2e-chromium-hello` with password `e2e-password-correct-horse`. The fixture accounts are inserted directly,
not through `PasswordService`, so their fixed password is never checked against the policy; changing it from the
Change password page is. The fixture backend uses a fresh
temporary H2 file on every start.

## Browser tests (Playwright)

`npm run test:e2e` in `frontend/` starts both servers itself and needs none of the variables above:

- the backend from its **test** sources, `sg.securedhello.e2e.E2eBackend`, via
  `mvn -f backend/pom.xml test-compile spring-boot:test-run -Dspring-boot.run.main-class=sg.securedhello.e2e.E2eBackend`.
  It is the real application under the `dev` profile on port 18010, with a fresh temporary H2 file, the test-only
  `TestSecrets`, the fixture accounts the browser tests sign in as, and a mailbox that replaces the dev link logger:
  the registration and reset tests read their links from `GET /e2e/mailbox?to=<address>`, never from a log. Nothing
  of it reaches the production build;
- the production SPA build under `vite preview` on port 15110, built with `VITE_API_ORIGIN=http://localhost:18010`.

Set `PW_API_PORT` or `PW_PREVIEW_PORT` to move either port. Both must be free: the config never reuses a running
server.

## Release build

With no CI pipeline, a developer's green `verify` is the release gate (ADR-068; ADR-069). The release command is:

```sh
mvn -f backend/pom.xml clean verify
```

Never add `-DskipITs`, `-Dmaven.test.skip` or `-Ddependency-check.skip=true` to a release build. The first two skip the
Failsafe-run traceability and drift gates (R-BLD-009); the third skips the dependency scan (R-BLD-007). Surefire and
Failsafe are pinned at exactly 3.6.0, so `-DskipTests` no longer skips the Failsafe gates (R-BLD-005).

`verify` runs these gates:

| Gate | What fails the build | Where |
|---|---|---|
| `@Proves` traceability (ADR-068) | a test cites an unknown T-ID; the test plan is missing, unreadable, empty or has the wrong header (T-BLD-007; T-BLD-008); a row has no test (strict: there is no pending ledger) | `TraceabilityGateIT` |
| Register drift (ADR-069; T-BLD-006) | a committed rendering (`docs/register/deferral-register.md`, `docs/register/handover.md`) differs from what `docs/register/register.md` regenerates, or the table breaks the register schema | `RegisterDriftIT` |
| Audit log inventory (R-AUD-027; T-AUD-017) | `docs/audit/log-inventory.md` differs from what `AuditEvent` generates; regenerate with `-Daudit-inventory.regenerate=true` | `AuditInventoryDriftIT` |
| OWASP Dependency-Check (R-BLD-007; R-BLD-010) | a dependency finding at CVSS 7.0 or higher | `dependency-check-maven`, bound to `verify` |

Dependency-Check reads `backend/dependency-check-suppressions.xml`, which suppresses only verified false positives: each entry
is scoped to named artifacts and CVE ids, with a `<notes>` reason. Add an entry only after checking the CPE's real
product and platform, never for a whole CPE.

Set `NVD_API_KEY` in the environment before a release build. Without a key the NVD download is slow or refused, and
the first download takes a long time either way.

## Local inner loop

Skip only the dependency scan, never the tests:

```sh
mvn -f backend/pom.xml verify -Ddependency-check.skip=true
```

## Register renderings

Edit `docs/register/register.md` only. Then regenerate both renderings:

```sh
mvn -f backend/pom.xml verify -Ddependency-check.skip=true -Dregister.regenerate=true
```

## Traceability gate

Every row of `docs/test-plan/test-plan.md` must be cited by a test: `@Proves("T-…")` in Java, the ID in the test name
in Vitest and Playwright. A retired ID leaves the table (its reason stays in the plan's prose) and no test may cite it.
To run only the gate:

```sh
mvn -f backend/pom.xml verify -Ddependency-check.skip=true -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=TraceabilityGateIT
```

Its failure message lists every row with no test and every citation that is not a row.

## Mutation testing

```sh
mvn -f backend/pom.xml -Pmutation verify
```

PIT runs at a mutation threshold of 85 over the security-decision classes (spec, Mandatory gates 7), listed by class
name in the `pitest-maven` configuration.
