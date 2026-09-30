# Secured Hello World

Secured Hello World is a reference "hello world" application built to a production security baseline, from the
product requirements in [`prd/assessment-prd.md`](prd/assessment-prd.md). A React single-page app (SPA) on one
origin talks to a Spring Boot REST API on another. Visitors register with a username and an email address and set
their password from an activation link. Users sign in with a username and password, get a server-side session in an
`HttpOnly` cookie, and see `Hello, <username>`. They can sign out and reset a forgotten password. Administrators
also use a TOTP authenticator app, and can list, enable, disable, promote, demote, delete and invite accounts. The
build specification is [`docs/spec.md`](docs/spec.md). Where it departs from the PRD, the reason is recorded in
[`docs/prd-coverage.md`](docs/prd-coverage.md).

- [Setup](#setup): from a clean machine to a running app ready for user acceptance testing
- [User Acceptance Test (UAT)](#user-acceptance-test-uat): the 12 PRD user stories and their test scripts
- [Security Concepts and Considerations](#security-concepts-and-considerations)

## Setup

These steps take a manual tester from a clean machine to a running app, in **dev** mode, on
http://localhost:5173 (SPA) and http://localhost:8080 (API). They use Windows PowerShell. A Git Bash (or macOS or
Linux) variant follows each PowerShell command where it differs. Operator detail, such as key rotation, metrics,
the release build and the recovery runner, is in [`backend/README.md`](backend/README.md). This page does not
repeat it.

### 1. Prerequisites

| Tool | Version | Check with |
|---|---|---|
| Git | any recent | `git --version` |
| JDK | **21** (the build targets Java 21; tested with Temurin 21.0.5), on `PATH`, with `JAVA_HOME` pointing at it | `java -version` |
| Maven | **3.9.x** (there is no Maven wrapper; tested with 3.9.9). On Windows, unzip it and add its `bin` folder to `PATH` | `mvn -v` |
| Node.js and npm | Node **20.19+ or 22.12+**, as Vite 8 requires (tested with Node 24.18, npm 11.16) | `node -v` |
| A browser | a current Chrome, Edge or Firefox, with its developer tools | |
| An authenticator app | any TOTP app: Google Authenticator, Microsoft Authenticator, Aegis, 1Password, and so on | |
| curl | needed for the health check and three UAT checks. Windows 10 and later ship `curl.exe` | `curl.exe --version` |

Ports **8080** and **5173** must be free. Both addresses are fixed: the API allows only the SPA's exact origin, and
Vite refuses to move to another port.

### 2. Get the code

```powershell
git clone https://github.com/SGTechStack/ai-coding-secured-hello-world-app-assessment.git
cd ai-coding-secured-hello-world-app-assessment
git checkout zacharylim
```

Run every command below from this repository root unless a step says otherwise.

### 3. Install the SPA's dependencies

```powershell
cd frontend
npm ci
cd ..
```

### 4. Start the backend (terminal 1)

The backend needs nine `APP_*` variables. It has no defaults and refuses to start without them. For a local
**dev** run, use the shared demo values from
[`backend/README.md` → Shared dev demo values](backend/README.md#shared-dev-demo-values). They are public, so they
are accepted only under the `dev` profile, and startup refuses them under any other profile.

First, clear any `APP_*` variables left over from earlier work, because an environment variable overrides the
matching `app.*` setting (see [Troubleshooting](#8-troubleshooting)):

```powershell
Get-ChildItem Env:APP_* | Remove-Item
```

Then paste the PowerShell block from
[Shared dev demo values](backend/README.md#shared-dev-demo-values) into the same window. It sets the variables and
starts the app with `mvn -f backend/pom.xml spring-boot:run "-Dspring-boot.run.profiles=dev"`. To keep a searchable
copy of the console, which is where the emailed links appear (step 6), run the last line of that block like this
instead:

```powershell
mvn -f backend/pom.xml spring-boot:run "-Dspring-boot.run.profiles=dev" | Tee-Object -FilePath backend-console.log
```

```sh
# Git Bash: unset stray variables, run every line of the bash block from backend/README.md except the last, then:
unset $(env | grep -o '^APP_[A-Z_]*')
mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev | tee backend-console.log
```

The first start downloads the Maven dependencies, so it can take several minutes. Every log line is one JSON
object. The backend is ready when a line says `Tomcat started on port 8080`. On first start it also seeds the two
dev-only demo accounts (step 7) and logs `Demo accounts: seeded demo-user`, `Demo accounts: seeded demo-admin,
enrolled in TOTP` and `Administrator bootstrap: an ADMIN account exists, so none is seeded`. Check it from another
terminal:

```powershell
curl.exe http://localhost:8080/actuator/health
# {"status":"UP"}
```

The data lives in `backend/data/` (an H2 database file) and the audit log in `backend/logs/audit.ndjson`. Both are
git-ignored. To start from an empty database, stop the backend and delete `backend/data/`.

### 5. Start the SPA (terminal 2)

```powershell
cd frontend
npm run dev
```

Open **http://localhost:5173**. Use `localhost`, not `127.0.0.1`: the API allows the exact origin
`http://localhost:5173` only. The app opens on the **Sign in** page.

### 6. Find the "emailed" links

No mail is ever sent. Under the `dev` profile the stubbed email service writes each activation and password-reset
link to the **backend console** (terminal 1), at `DEBUG`, on the dev-only logger `sg.securedhello.email.ResetLinkLogger`
([ADR-057](docs/adr/0057-dev-only-reset-link-logger.md)). The line's `message` reads:

```text
Dev-only ACTIVATION link (no mail is sent): http://localhost:5173/activate#token=…
Dev-only PASSWORD_RESET link (no mail is sent): http://localhost:5173/reset#token=…
```

Copy the URL, which is everything from `http` to the closing quote, and open it in the browser. If you started the
backend with `Tee-Object` or `tee`, a second terminal can print the newest link:

```powershell
(Select-String -Path backend-console.log -Pattern 'Dev-only PASSWORD_RESET' | Select-Object -Last 1).Line -replace '.*: (http[^"]+)".*','$1'
```

```sh
grep -o 'http://[^"]*/reset#token=[^"]*' backend-console.log | tail -1
```

For an activation link, use `ACTIVATION` in place of `PASSWORD_RESET` (PowerShell) or `/activate` in place of `/reset`
(bash). An activation link lasts 24 hours and a reset
link 30 minutes. Each works once, and a newer link for the same account replaces an older one.

### 7. Sign in with the demo accounts panel

Under the `dev` profile the **Sign in** page shows a **Demo accounts** panel. It lists two accounts the backend
seeded on first start, with their passwords and **Copy** buttons:

- `demo-user`, an ordinary user;
- `demo-admin`, an administrator already enrolled in TOTP, with its **current six-digit code**. The code refreshes
  by itself when its 30-second step ends, so no authenticator app is needed for this account.

1. Choose **Fill in demo-user**, then **Sign in**. **Hello, demo-user** opens. Choose **Sign out**.
2. Choose **Fill in demo-admin**, note the code, then **Sign in**. On **TOTP Verification**, enter the code and
   choose **Verify**. The **Users** list opens.

Neither account has to change its password first. The panel, the endpoint behind it and the accounts exist only
under `dev`: every value comes from the backend at run time, nothing is compiled into the SPA, and any other profile
refuses them (details in [`backend/README.md` → Demo accounts](backend/README.md#demo-accounts-dev-only)). To see the
forced password change and authenticator enrolment, invite a new administrator, as
[UAT-12 part B](docs/uat/uat-guide.md#part-b-a-new-administrator-forced-change-and-enrolment) does.

The app is now ready to use. Stop either server with **Ctrl+C** in its terminal.

### 8. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Unexpected behaviour, wrong origins or startup refusals you did not expect | A stray `APP_*` environment variable is overriding configuration: Spring binds `APP_ORIGINS_SPA` to `app.origins.spa`, `APP_SECURITY_…` to `app.security.…`, and so on. List them with `Get-ChildItem Env:APP_*` (bash: `env \| grep ^APP_`), clear them with `Get-ChildItem Env:APP_* \| Remove-Item` (bash: `unset $(env \| grep -o '^APP_[A-Z_]*')`), and check the user and system environment variables in Windows settings too. Then set only the nine demo values. |
| Maven fails to download with `PKIX path building failed` | A corporate proxy re-signs TLS, and the JDK does not trust its certificate. On Windows, make the JDK use the Windows certificate store: `$env:MAVEN_OPTS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"` (bash: `export MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`), then rerun. |
| Startup stops with `Startup refused: … holds a published demo value` | The demo values were used without the `dev` profile. Add `-Dspring-boot.run.profiles=dev`, or generate fresh values as described in [`backend/README.md`](backend/README.md#running-locally). |
| Startup stops naming a missing or malformed property | One of the nine `APP_*` variables is unset or malformed in this terminal. Paste the whole demo block again. |
| `Port 5173 is already in use`, or the backend reports port 8080 in use | Stop whatever holds the port (`Get-NetTCPConnection -LocalPort 8080`). Both ports are fixed (step 1). |
| The SPA says "The service is unavailable. Try again later." | The backend is not running, or the page was opened on another origin (such as `127.0.0.1`). |
| `npm ci` or `npm run dev` fails with "npm.ps1 cannot be loaded because running scripts is disabled on this system" | Windows PowerShell's default execution policy blocks npm's PowerShell shim. Run `npm.cmd ci` and `npm.cmd run dev` instead, or allow local scripts once with `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`. |
| "Too many attempts. Wait a moment, then try again." | A rate limit was hit. Everything local comes from one address, so the per-source limits are shared by every browser window. Most limits refill within seconds. Reset requests for one email address allow 3 and then one more every 20 minutes ("Too many reset requests for this address…"). A source that has driven five accounts into lockout is refused for other usernames for up to an hour. |
| The correct password is refused as "The username or password is not correct." | The account may be locked: 5 wrong passwords within 20 minutes lock it for 20 minutes, and the message never says so. Wait, or have an administrator choose **Unlock account**. For `demo-admin`, see the recovery runner in [`backend/README.md`](backend/README.md#recovering-an-account-with-the-recovery-runner). A forced-change credential also expires 30 days after it is issued. |
| The sign-in page has no **Demo accounts** panel | The backend is not running under `dev`, `APP_DEV_DEMOACCOUNTS_ENABLED` is `false`, or the database predates the demo accounts: stop the backend, delete `backend/data/`, and start it again. |
| A TOTP code is refused | Check that the phone's clock is set automatically. A code works once only, so wait for the next one (the demo panel shows it). After 10 wrong codes in 20 minutes the factor locks for 20 minutes. |
| You want a clean start | Stop the backend, delete `backend/data/` (and optionally `backend/logs/`), and start it again. The demo accounts are seeded afresh. |

## User Acceptance Test (UAT)

The UAT guide, [`docs/uat/uat-guide.md`](docs/uat/uat-guide.md), has one script per PRD user story. Each script
quotes its acceptance criteria and gives the preconditions, numbered browser steps, the expected result of each step
and a Pass/Fail box. The scripts assume the [Setup](#setup) above on a fresh database. They exercise the PRD's
negative paths too: wrong passwords, lockout, throttling, invalid and expired links, and self-action refusals. Where
the app deliberately differs from the PRD's wording, such as the two-step registration or the 15-character
minimum, the script says so and cites the record. The administrator scripts sign in as the pre-enrolled `demo-admin`
from the sign-in page's demo panel; UAT-12 shows the forced password change and authenticator enrolment on a newly
invited administrator, and the configuration-driven bootstrap with the demo accounts turned off.

| ID | User story | Script |
|---|---|---|
| 1 | Register an account | [UAT-01](docs/uat/uat-guide.md#uat-01-register-an-account) |
| 2 | Log in with username and password | [UAT-02](docs/uat/uat-guide.md#uat-02-log-in) |
| 3 | Account lockout and IP throttling | [UAT-03](docs/uat/uat-guide.md#uat-03-lockout-and-throttling) |
| 4 | Log out | [UAT-04](docs/uat/uat-guide.md#uat-04-log-out) |
| 5 | Personalized greeting (`GET /api/hello`) | [UAT-05](docs/uat/uat-guide.md#uat-05-personalized-greeting) |
| 6 | Request a password reset | [UAT-06](docs/uat/uat-guide.md#uat-06-request-a-password-reset) |
| 7 | Set a new password with a reset token | [UAT-07](docs/uat/uat-guide.md#uat-07-reset-the-password-with-a-token) |
| 8 | Admin: list all users | [UAT-08](docs/uat/uat-guide.md#uat-08-admin-lists-users) |
| 9 | Admin: enable or disable an account | [UAT-09](docs/uat/uat-guide.md#uat-09-admin-enables-or-disables-an-account) |
| 10 | Admin: change a user's role | [UAT-10](docs/uat/uat-guide.md#uat-10-admin-changes-a-role) |
| 11 | Admin: delete an account | [UAT-11](docs/uat/uat-guide.md#uat-11-admin-deletes-an-account) |
| 12 | Initial admin bootstrap | [UAT-12](docs/uat/uat-guide.md#uat-12-initial-admin-bootstrap) |

## Security Concepts and Considerations

This is a summary. Each item links to the record that governs it. The full picture is in
[`docs/spec.md`](docs/spec.md), the threat model in
[`docs/threat-model/report.md`](docs/threat-model/report.md), the error contract in
[`docs/api/error-contract.md`](docs/api/error-contract.md) and the decision index in
[`docs/adr/README.md`](docs/adr/README.md).

**Credentials**

- **Password policy.** Passwords need at least 15 characters and at most 72 bytes. They are checked against a
  bundled, version-pinned breached-password list and a list of context words, must not contain the username or email
  local part, and need a zxcvbn strength score of 3 or more. The last three passwords cannot be reused. There are no
  composition rules, and each refusal names the rule it failed. The SPA's strength meter is advisory only.
  ([ADR-002](docs/adr/0002-fifteen-character-password-minimum.md),
  [ADR-005](docs/adr/0005-zxcvbn-gate-replaces-composition-rules.md))
- **Hashing.** BCrypt at cost 12 behind Spring's `DelegatingPasswordEncoder`, with no pepper. One service sets every
  password, so no new password skips the policy. ([ADR-001](docs/adr/0001-bcrypt-cost-12-delegating-encoder.md))
- **Credential tokens.** Activation and reset tokens are 256-bit random values, stored only as SHA-256 hashes, and
  single-use. They last 24 hours and 30 minutes. Links carry the token in the URL **fragment** (`#token=`), which the
  browser never sends to a server, and are built only from the configured SPA origin. Administrators issue tokens,
  never passwords. ([ADR-007](docs/adr/0007-one-credential-tokens-table.md),
  [ADR-006](docs/adr/0006-admins-issue-tokens-not-passwords.md))
- **Enumeration resistance.** Every failed sign-in, whether the user is unknown, the password is wrong, or the
  account is locked, disabled or unactivated, returns the same `401`. Registration and reset requests return the
  same answer whether or not the email is registered.
  ([ADR-033](docs/adr/0033-password-lockout-never-on-the-wire.md),
  [ADR-032](docs/adr/0032-two-step-enumeration-resistant-registration.md))

**Brute-force defences**

- **Lockout and the NIST cap.** Five wrong passwords within 20 minutes lock an account for 20 minutes. Repeated
  locks escalate to 40 and then 60 minutes, after five locks at each length. The lock lifts on its own. After 100 consecutive failures the password is disabled until it is
  reset. ([ADR-011](docs/adr/0011-escalating-lockout-ladder.md),
  [ADR-013](docs/adr/0013-nist-cap-disables-password-authenticator.md))
- **Rate limiting.** Sign-in, registration, activation, reset, CSRF-token and TOTP routes have per-source budgets.
  Sign-in also has a per-username budget, and reset requests a per-email budget. A refusal is a `429` with `Retry-After`, and it never counts against an account. One source may
  drive at most five accounts into lockout per hour.
  ([ADR-010](docs/adr/0010-dual-rate-limiting.md), [ADR-015](docs/adr/0015-lockout-cardinality-axis.md))

**Multi-factor authentication for administrators**

- **TOTP.** Every `/api/admin/**` route requires a verified authenticator code. Reads accept a code verified at any
  point in the session, and changes need one from the last 10 minutes (**step-up**). The TOTP secret is encrypted
  with AES-GCM under a key supplied outside the database, and a code can never be replayed.
  ([ADR-021](docs/adr/0021-session-scoped-factor-authority.md),
  [ADR-022](docs/adr/0022-totp-key-outside-the-database.md))
- **Two-tier TOTP lockout.** Ten wrong codes in 20 minutes lock the factor for 20 minutes. One hundred in total
  disable it, which forces a password change and ends the admin's sessions.
  ([ADR-027](docs/adr/0027-two-tier-totp-lockout.md))

**Sessions and browser protections**

- **Server-side sessions.** Sessions are stored in the database (Spring Session JDBC), not in JWTs, and carried by an
  `HttpOnly`, `SameSite=Strict` cookie, which is `Secure` and named `__Host-SESSION` outside dev. An account has one
  session at a time, and the newest sign-in wins. Sessions time out after 15 minutes idle or 8 hours in total. The id
  rotates at sign-in, factor verification and password change.
  ([ADR-029](docs/adr/0029-server-side-session-cookies-not-jwt.md),
  [ADR-030](docs/adr/0030-spring-session-jdbc.md), [ADR-058](docs/adr/0058-samesite-strict-not-lax.md),
  [ADR-038](docs/adr/0038-session-id-rotation-and-auth-instant.md))
- **Session termination.** Sign-out invalidates the server session and sends `Clear-Site-Data`. A password reset,
  disable, role change, delete or factor reset ends the account's sessions once the change commits. A password change
  ends the other sessions only. At startup, the app ends any stored sessions of accounts that should have none.
  ([ADR-037](docs/adr/0037-session-invalidation-triggers.md),
  [ADR-039](docs/adr/0039-after-commit-session-invalidation.md),
  [ADR-035](docs/adr/0035-credential-change-ends-other-sessions.md))
- **Anonymous session shedding.** Only the CSRF-token route creates a session for an anonymous visitor. New anonymous
  sessions are refused when session rows or free disk space cross a limit, so a flood cannot fill the database, and
  signed-in users are unaffected. ([ADR-041](docs/adr/0041-anonymous-session-shedding.md))
- **CSRF.** Every state-changing request, sign-in and sign-out included, needs a session-bound synchronizer token.
  The token is sent in the `X-CSRF-TOKEN` header only: never as a cookie, and never as a form parameter.
  ([ADR-036](docs/adr/0036-session-bound-header-only-csrf-token.md))
- **CORS.** Exactly one allowed origin, the SPA's, with credentials. No wildcard. Only the `Accept`, `Content-Type`
  and `X-CSRF-TOKEN` request headers are allowed. ([ADR-036](docs/adr/0036-session-bound-header-only-csrf-token.md))
- **Security headers.** The API sends `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` and no-store cache
  headers. The SPA document carries a Content-Security-Policy (no inline script in the production
  build) and `Referrer-Policy: no-referrer` in meta tags, and the Vite dev and preview servers also send the policy
  with `frame-ancestors 'none'` as headers. A production static host must send its own header set. HTTPS, and with it `Secure` cookies and HSTS, is a deployment duty. Local
  dev runs over HTTP, as the PRD accepts. ([ADR-060](docs/adr/0060-three-layer-document-csp.md))
- **Uniform errors.** Every error is one RFC 9457 `application/problem+json` shape with a closed set of `code`
  values and constant messages. No stack trace or exception text reaches a client.
  ([error contract](docs/api/error-contract.md))

**Administration and operations**

- **Guarded admin actions.** One guard runs on every admin change. An admin cannot disable, demote, delete, unlock or
  reset the factor of their own account. Disabling, demoting or deleting an administrator is refused if it would leave
  fewer than two administrators with an authenticator enrolled (the **two-admin invariant**). A USER gets `403` on
  every admin route. ([ADR-048](docs/adr/0048-two-admin-invariant-guarded-and-monitored.md))
- **Bootstrap.** The first administrator is seeded from configuration only when no `ADMIN` account exists. The seeded
  password must be changed at first sign-in, and it expires 30 days after it is issued unless it has been changed. The admin must then enrol TOTP before
  the admin pages open. ([ADR-047](docs/adr/0047-bootstrap-refresh-validation-runner-seeding.md),
  [ADR-046](docs/adr/0046-lazy-forced-change-expiry-pre-authentication.md))
- **Deletion tombstones.** A deleted account leaves a tombstone, which holds the username and a keyed hash of the
  email, so neither can be registered again. ([ADR-044](docs/adr/0044-deletion-leaves-a-tombstone.md))
- **Offline recovery runner.** When no administrator can sign in, an operator stops the app and runs the same jar in
  recovery mode. A dry run prints a digest of the account state, and applying the change requires that digest and a
  reason. A password is read from a no-echo prompt, or from stdin with `--non-interactive`, and never from the command line. Every run is audited.
  ([ADR-072](docs/adr/0072-offline-recovery-runner.md))
- **Configuration validation.** Secrets have no default in any profile and are supplied from the environment or a
  mounted secret, never from a committed file.
  Startup fails before the port opens when a key is missing, malformed or reused, when configuration is prohibited,
  or when the public demo values are used outside the `dev` profile. Each key's fingerprint, never the key itself, is
  logged at startup. ([ADR-062](docs/adr/0062-secrets-bind-through-configuration-properties.md))
- **Audit logging.** Security events go to a dedicated audit file and to stdout as ECS JSON lines. They include
  sign-in success and failure, lockout, resets, session start and end, and every admin action with actor and target.
  No password, token or TOTP secret is logged. The one deliberate exception is the dev-only link logger, which
  writes activation and reset links to the console and which startup refuses outside dev. IP addresses and session ids appear only as keyed hashes.
  ([ADR-055](docs/adr/0055-data-driven-audit-event-enum.md),
  [ADR-054](docs/adr/0054-keyed-hash-log-correlation-fields.md), [log inventory](docs/audit/log-inventory.md))
- **Build gates.** `mvn -f backend/pom.xml clean verify` fails on a dependency with a CVSS 7.0+ vulnerability (OWASP
  Dependency-Check), on a test-plan row with no test (`@Proves` traceability), and on drift in the generated register,
  audit inventory and error contract. The `-Pmutation` profile runs PIT at an 85% mutation threshold on the
  security-decision classes. ([ADR-068](docs/adr/0068-traceability-gate-with-proves.md),
  [backend README → Release build](backend/README.md#release-build))

Known, accepted gaps are listed in the [deferral register](docs/register/deferral-register.md). They include local
HTTP, no real mail transport (self-service reset works in dev only), and no recovery codes. Items a deployer must
provide, such as TLS, a second enrolled administrator and log shipping, are in the
[handover document](docs/register/handover.md).
