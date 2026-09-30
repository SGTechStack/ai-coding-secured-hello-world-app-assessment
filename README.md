# Secured Hello World

A reference application demonstrating a secure username/password account lifecycle:
registration, login, logout, password recovery, and admin account management. A React +
TypeScript single-page app (`frontend`) talks to a Spring Boot 4 JSON API (`backend`)
across origins, with server-side Sessions, session-bound CSRF tokens and a structured
audit log.

Start here:

| Document | What it is |
| --- | --- |
| [`CONTEXT.md`](CONTEXT.md) | The domain vocabulary. Use these terms — Account, Locked vs Disabled vs Deleted Account, Reset Token, Password Change, IP Throttle, Bootstrap Admin, Client Event. |
| [`prd/assessment-prd.md`](prd/assessment-prd.md) | The original Product Requirements Document. |
| [`.scratch/secured-hello-world/spec.md`](.scratch/secured-hello-world/spec.md) | The spec the app was built from: stories, decisions, API contract, schema. |
| [`docs/adr/`](docs/adr/) | Architecture decisions, including every knowing deviation from the PRD and the App-Standards ([ADR 0001](docs/adr/0001-app-standards-override-prd.md)) and the CSRF design ([ADR 0002](docs/adr/0002-session-bound-csrf-bootstrap.md)). |

**This is a reference implementation, not a deployed service.** It is missing controls a
real deployment must add around it, and it accepts deviations a real deployment must
revisit. [Deploying outside `dev`](#deploying-outside-dev) lists all of them. Read that
section before putting this anywhere but a laptop.

## Repository layout

```
.
├── backend/                  Spring Boot 4 API (Java 21, Maven wrapper)
│   └── src/main/resources/
│       ├── application.properties       every default, every knob
│       ├── application-dev.properties   local development only
│       └── db/migration/{h2,postgresql,mysql}/   Flyway migrations, one set per vendor
├── frontend/                 Vite + React + TypeScript SPA (npm)
├── CONTEXT.md                domain vocabulary
├── docs/adr/                 architecture decision records
├── prd/assessment-prd.md     the original PRD
└── .scratch/                 specs and issues (local markdown issue tracker)
```

## Requirements

- **JDK 21** (the build targets Java 21; `java -version` must report 21 or later).
- **Node.js on an even-numbered line, recent patch: 22.22.2+, 24.15+ or 26+**, and npm.
  That is the intersection of the toolchain's own declared `engines.node` ranges, not a
  guess: `vitest` excludes 20, 23 and 25 entirely, and `jsdom` and `react-router` rule out
  the earlier 22.x and 24.x patches. Check with `node -v` before `npm install` — npm does
  not enforce `engines` unless you ask it to, so an unsupported version fails later, inside
  `npm test` or `npm run dev`, rather than at install time.
- No Maven install needed: use the wrapper (`./mvnw`).

## Running locally (`dev`)

From a clean checkout, in two terminals.

**Terminal 1 — the API on `http://localhost:8080`:**

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**Terminal 2 — the SPA on `http://localhost:3000`:**

```bash
cd frontend
npm install
npm run dev
```

Then open <http://localhost:3000>.

The `dev` profile is not the default and must be named explicitly. Without it the app
fails at startup, on purpose: no CORS allowlist, no IP-hash key and no Bootstrap Admin
credentials are configured, and every one of those is required outside `dev`.

What `dev` gives you, and nothing else does:

- An H2 file database at `backend/data/secured-hello-world.mv.db`, and the H2 console at
  <http://localhost:8080/h2-console>. Delete the `backend/data` directory to start over.
- A CORS allowlist of exactly `http://localhost:3000`.
- `Secure=false` on the Session cookie, because browsers (Safari in particular) reject
  `Secure` cookies on `http://localhost`.
- A fixed, non-secret IP-hash key.
- A **Bootstrap Admin fallback of `admin` / `password` / `admin@localhost`**, created with
  a WARN in the log when no Bootstrap Admin is configured. It skips the password policy.
  It exists so a clean checkout has an Admin to log in as. **Never use it outside local
  development** — see [The Bootstrap Admin](#4-the-bootstrap-admin).

Logs are written under `backend/logs/`: `application.log`, `audit.log`, and `email.log`,
which stands in for a mailbox — the `EmailService` is a stub, so in `dev` the password-reset
link appears there rather than in an inbox. All three are ECS JSON, one object per line.

Actuator listens separately on <http://127.0.0.1:9090/actuator/health> and
`/actuator/prometheus`, bound to the loopback address. `prometheus` needs HTTP Basic
credentials; in `dev` they are `prometheus` / `dev-only-local-scrape-password`.

### Tests

```bash
cd backend  && ./mvnw verify          # unit + HTTP integration tests, 80% coverage gate
cd frontend && npm test               # vitest
cd frontend && npm run typecheck      # tsc
cd frontend && npm run lint           # oxlint
```

Three checks are opt-in, because each needs something the ordinary build must not depend
on — an NVD API key, the npm registry, or a Docker daemon:

```bash
cd backend  && NVD_API_KEY=… ./mvnw -P security verify           # OWASP Dependency-Check, fails on critical CVEs
cd frontend && npm run security                                  # npm audit --audit-level=critical
cd backend  && ./mvnw -P migrations test -Dtest=NonH2MigrationTest   # Postgres + MySQL migrations against real databases
```

The last one is described under [The database](#10-the-database); run it after any change
to a Flyway migration or a JPA entity.

## Configuration

Every knob has a default and is documented in place in
[`backend/src/main/resources/application.properties`](backend/src/main/resources/application.properties);
[`application-dev.properties`](backend/src/main/resources/application-dev.properties)
holds the local-development overrides. Override any of them the usual Spring Boot ways.
For an environment variable, uppercase the property and drop the hyphens:
`app.ip-hash.key` becomes `APP_IPHASH_KEY`, `app.cors.allowed-origins` becomes
`APP_CORS_ALLOWEDORIGINS`, `app.bootstrap-admin.password` becomes
`APP_BOOTSTRAPADMIN_PASSWORD`.

The properties a deployment must set or must think about:

| Property | Default | Notes |
| --- | --- | --- |
| `spring.profiles.active` | none | `dev` for local development. Any other value, or none, enforces the production rules below. |
| `spring.datasource.url` / `.username` / `.password` | `dev` only | Postgres is the production target; MySQL migrations are maintained too. Credentials come from the secrets manager. |
| `app.cors.allowed-origins` | `dev` only | Comma-separated exact origins. **Startup fails outside `dev` when unset**, and any wildcard or path is rejected. |
| `app.ip-hash.key` | `dev` only | HMAC-SHA-256 key for `source.ip_hash` in audit events. **Startup fails outside `dev` when unset.** |
| `app.bootstrap-admin.username` / `.password` / `.email` | `dev` fallback | Required outside `dev`. |
| `app.password-reset.frontend-origin` | `http://localhost:3000` | The SPA origin the emailed reset link points at. |
| `app.session.cookie-secure` | `true` (`false` in `dev`) | Leave it `true`. It needs TLS. |
| `app.session.idle-timeout` / `.absolute-timeout` | `15m` / `8h` | |
| `app.session.max-concurrent-per-account` | `1` | One Session per Account. |
| `app.logging.directory` | `logs` | Root for `application-file`, `audit-file` and `email-file`. Point it at a volume a log-forwarding agent can read. |
| `app.logging.audit-max-history-days` | `90` | On-disk audit history. Validated: it cannot be set below 90. |
| `management.server.port` / `.address` | `9090` / `127.0.0.1` | **Must never be publicly routed.** |
| `app.management.prometheus.username` / `.password` | `prometheus` / `dev` only | The scraper's HTTP Basic credential for `/actuator/prometheus`. The password comes from the secrets manager. **Unset, every scrape is refused (401)**; startup does not fail. |
| `server.forward-headers-strategy` | `none` | Change only behind a proxy you trust — see [Client addresses](#6-client-addresses-reverse-proxy-and-nat). |
| `app.api.base-path` | `/api` | Must match the SPA's build. |
| `app.api.max-request-body-bytes` | `16384` | Bodies above this get 400 `request_too_large` before any controller runs. |
| `app.lockout.threshold` / `.duration` | `5` / `20m` | |
| `app.ip-throttle.threshold` / `.window` / `.block-duration` | `20` / `15m` / `15m` | See the NAT caveat below. |
| `app.rate-limit.*.capacity` / `.period` | see the file | Login, registration, reset request (per email and per IP), reset confirmation, client events. |
| `app.credential.min-length` / `.max-length` / `.bcrypt-cost` / `.history-length` | `12` / `64` / `12` / `3` | The maximum is 64 characters and 72 UTF-8 bytes, because BCrypt reads no more. |
| `app.password-reset.token-expiry` | `30m` | |
| `logging.structured.ecs.service.environment` | `local` (`dev` in `dev`) | The `service.environment` field on every log line. Set it per environment. |

The SPA reads two build-time variables, both baked in at `npm run build` — they are not
runtime configuration:

| Variable | Notes |
| --- | --- |
| `VITE_API_ORIGIN` | The API's bare origin, scheme and host and optional port only: no path, query or fragment. It goes into the CSP's `connect-src`, so a wrong value breaks every API call. |
| `VITE_SECURITY_CONTACT` | The `Contact:` line in `/.well-known/security.txt`. A production build **fails** when it is unset. |

`frontend/.env.development` holds the local defaults for both. A production build reads
neither: supply them from the environment or a `.env.production` file, which is
git-ignored.

## Deploying outside `dev`

Everything in this section is the deployment's job, not the app's. The app enforces what
it can at startup; the rest is yours.

### 1. Run exactly one instance

The IP Throttle and every rate limiter are in-memory, so two instances would each enforce
their own counts and an attacker could double their budget by being load-balanced.
**Run a single API instance** (ADR 0001). Lockout state and Sessions live in the database
and survive a restart, so a single instance is not a single point of data loss — only of
throughput. Running more would need a shared store, for example bucket4j over JDBC.

### 2. TLS 1.2 or later, everywhere

The app does not terminate TLS; the deployment does (ADR 0001). Nothing about this is
optional: the Session cookie is `Secure`, the app sends HSTS with a one-year `max-age` and
`includeSubDomains` on every response, and a `Secure` cookie over plain HTTP is a cookie
the browser throws away.

- Terminate TLS in front of both the API and the SPA, and redirect HTTP to HTTPS.
- **Never enable SSLv3, TLS 1.0 or TLS 1.1.** TLS 1.2 is the floor; prefer 1.3.
- **The database connection uses TLS too.** Put it in the JDBC URL and require
  verification, not just encryption: Postgres `?sslmode=verify-full`, MySQL
  `?sslMode=VERIFY_IDENTITY`. Certificate and hostname validation are never disabled.
- HSTS with `includeSubDomains` applies to every subdomain of whatever host answers.
  Make sure that is what you want before the first response goes out, because browsers
  remember it for a year.

### 3. Every secret from a secrets manager

Three secrets are required outside `dev`. None of them may be committed, written into a
properties file, or baked into an image. Inject them as environment variables from the
secrets manager:

| Secret | Environment variable |
| --- | --- |
| Bootstrap Admin password (and username, email) | `APP_BOOTSTRAPADMIN_PASSWORD`, `APP_BOOTSTRAPADMIN_USERNAME`, `APP_BOOTSTRAPADMIN_EMAIL` |
| IP-hash HMAC key | `APP_IPHASH_KEY` |
| Datasource credentials | `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` |

Startup fails outside `dev` when the CORS allowlist, the IP-hash key or the Bootstrap
Admin values are missing — a missing secret stops the deployment rather than silently
weakening it. None of these values is ever logged: the properties that hold them mask
themselves in `toString()`.

**Rotate the IP-hash key periodically** through the secrets manager. The key only salts
`source.ip_hash` in audit events, so rotation costs nothing but the ability to correlate
one address's events across the rotation point; hashes either side of it will not match.
Every hash is versioned: it travels with `source.ip_hash_key_id`, a 16-hex-character
fingerprint derived from the key itself (the start of the key's HMAC of a fixed label), so
the id changes on its own when the key does and there is no second setting to keep in step.
An investigator reading old audit records sees exactly where each rotation falls, and which
key to use when hashing a suspect address; keep superseded keys in the secrets manager's
version history for as long as the audit log they produced is retained. The fingerprint
reveals nothing about the key or any address.

### 4. The Bootstrap Admin

At startup, when no Account holds the Admin role, the app creates one from
`app.bootstrap-admin.*` and sets Required Password Change on it. **That Admin can do
nothing but view itself, change its password and log out until it changes the password**,
in every profile — the password whoever deployed the app typed never stays in use.

- Give it a password that passes the policy (12–64 characters, upper, lower, digit and
  special, not a common password), then discard your copy after the first login.
- Startup fails with a clear message when the configured username already belongs to an
  Account or to a tombstone of a Deleted Account.
- **The `dev` fallback `admin` / `password` must never be used outside local
  development.** It exists only so a clean checkout has an Admin; it skips the password
  policy and logs a WARN. Every profile other than `dev` fails at startup rather than
  falling back, so the only way to ship it is to deploy with `dev` active. Do not.

### 5. The CORS allowlist

Set `app.cors.allowed-origins` to the exact production SPA origins, comma-separated, for
example `https://app.example.gov`. The default `http://localhost:3000` applies in `dev`
only; any other profile fails at startup with no allowlist, and rejects a wildcard or a
path. Credentials are allowed on CORS requests, which is exactly why the list can hold no
wildcard.

### 6. Client addresses: reverse proxy and NAT

The IP Throttle and three of the rate limiters key on the client address, and the audit
log records its HMAC. By default the app takes the **direct connection address** and
ignores forwarded headers (`server.forward-headers-strategy=none`, set explicitly so that
Spring Boot does not start trusting `X-Forwarded-For` on a detected cloud platform).

- **Behind a reverse proxy** every request appears to come from the proxy, so the IP
  Throttle sees one address for the whole internet and either blocks everybody or nobody.
  Set **`server.forward-headers-strategy=native`** and configure the proxy to *overwrite*
  `X-Forwarded-For` rather than append to it, so a client cannot choose its own throttle
  key and its own `source.ip_hash`. Trust exactly one proxy, the one you run.
  - **Use `native`, not `framework`.** `native` is Tomcat's `RemoteIpValve`, which honours
    `X-Forwarded-For` only from a peer matching
    `server.tomcat.remoteip.internal-proxies`. `framework` is Spring's
    `ForwardedHeaderFilter`, which has no trusted-proxy notion at all and believes the
    header from whatever connects — which hands the throttle key back to the client, the
    exact thing this section exists to prevent.
  - **Set `server.tomcat.remoteip.internal-proxies` if your proxy is not in a private
    range.** It defaults to the private and loopback blocks (`10/8`, `172.16/12`,
    `192.168/16`, `127/8`, `100.64/10`, link-local, `::1`, `fc00::/7`, `fe80::/10`). A
    proxy outside those — a public-IP load balancer, say — fails the check silently: the
    header is ignored, no error is logged, and the throttle keys on the proxy again.
- **Behind a corporate NAT** a whole office shares one address, so 20 failed logins from
  20 different people trip the throttle for all of them. Raise
  `app.ip-throttle.threshold`, widen `app.ip-throttle.window`, or accept the block
  knowingly.

### 7. The management port

Actuator runs on its own port — `management.server.port=9090`, bound to
`management.server.address=127.0.0.1` — and exposes only `health` (status only, no
components, no details) and `prometheus`, read-only, with no links page. The API port
serves no Actuator endpoint.

`prometheus` answers only to the scraper's HTTP Basic credential,
`app.management.prometheus.username` (default `prometheus`) and
`app.management.prometheus.password`, injected from the secrets manager. Configure the
scrape job with `basic_auth`. With no password configured every scrape gets 401, so a
missing secret shows up as a failing scrape target, not as open metrics. `health` stays
unauthenticated, because it answers with the status alone and probes carry no credential.

**Never route the management port publicly.** No ingress rule, no load-balancer target,
no security-group rule, no port publication that reaches it from outside the host. The
loopback binding and the scrape credential are the app doing what it can; they are not a
substitute for keeping the port off every public route.

The loopback binding has a trade-off: the Prometheus scraper and the health check must run
on the same host, or in the same network namespace. A platform that probes a container
from outside it has to widen `management.server.address` to `0.0.0.0` — and then keeping
the port off every public route and ingress, plus the scrape credential, is all that is left
protecting it.

### 8. Forward the audit log to central logging

`logs/audit.log` is a separate ECS JSON file carrying only security events. The app keeps
90 days of it on disk (`app.logging.audit-max-history-days`, which cannot be set lower)
as a local buffer. The platform owns the rest, and none of it is built here
(it is out of scope in the spec, and listed in ADR 0001 as deliberately not built):

- **Forward it to central logging** with a collector that tails the file.
- **Retain it for at least 90 days** centrally.
- **Write-once storage**, so a compromised app host cannot rewrite its own history.
- **Restricted access**: audit records name Accounts and carry emails. Data
  classification is Confidential.
- **Alert when audit events stop arriving.** Silence is the failure mode that matters:
  an app that cannot write its audit log looks exactly like an app nobody is using.

`logs/application.log` (7 days on disk) is worth forwarding too, but it is not the audit
trail. In `dev`, `logs/email.log` stands in for a mailbox and **contains plaintext Reset
Tokens** (ADR 0001). In every other profile the stub withholds the reset link: the email is
recorded (recipient masked, `reset.link_withheld: true`) but no Reset Token is written
anywhere, so self-service password reset cannot be completed until the stub is replaced
with real email delivery.

### 9. Hosting the SPA

The SPA is a static bundle: `cd frontend && npm run build` writes `dist/`.

- **Set `VITE_SECURITY_CONTACT`** before building, and `VITE_API_ORIGIN` to the API's
  bare origin. The production build fails without the contact, and the origin ends up in
  the CSP's `connect-src`, so a wrong value breaks every API call. Both are baked in at
  build time; changing either needs a rebuild.
- `/.well-known/security.txt` (RFC 9116) is emitted into the bundle with that contact and
  an `Expires` date 180 days out. **It expires**: rebuild and redeploy before it does, or
  the file tells a researcher it is stale.
- **The host must send the security headers on its own responses.** `index.html` carries
  a CSP `<meta>` tag, but a meta tag cannot set HSTS and is not a substitute for a header.
  Configure the SPA's host or CDN to send:

  ```
  Strict-Transport-Security: max-age=31536000; includeSubDomains
  Content-Security-Policy: default-src 'self'; connect-src 'self' https://api.example.gov; object-src 'none'
  X-Frame-Options: DENY
  X-Content-Type-Options: nosniff
  Permissions-Policy: accelerometer=(), autoplay=(), camera=(), display-capture=(), fullscreen=(), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(), usb=()
  ```

  with `connect-src` naming your API origin. The API already sends this set on its own
  responses, with one difference: its CSP is `default-src 'self'; object-src 'none'` and
  has no `connect-src`, because the API serves no HTML and so nothing it returns makes
  outbound requests. The `connect-src` above belongs to the origin that serves the HTML.
- Serve `index.html` for unknown paths, so the SPA's client-side routes work on a reload.

### 10. The database

The schema comes only from Flyway; Hibernate never generates it
(`spring.jpa.hibernate.ddl-auto=validate`, which fails startup if the schema and the
entities disagree). Migrations are per vendor, selected by
`spring.flyway.locations=classpath:db/migration/{vendor}`, and three sets are maintained:
`h2` (local development and tests), `postgresql` (the production target) and `mysql`.

- Point `spring.datasource.url` at the real database and let Flyway run at startup.
  Spring Session's tables come from a migration too (`V1__spring_session.sql`), not from
  auto-initialisation (`spring.session.jdbc.initialize-schema=never`).
- Grant the application's database user only what it needs on the application schema.
  Flyway needs DDL rights at deploy time; consider a separate migration user.
- The Postgres and MySQL migration sets are exercised by an opt-in profile that runs them
  against real databases in throwaway containers, with `ddl-auto=validate` proving the
  entities still match:

  ```bash
  cd backend && ./mvnw -P migrations test -Dtest=NonH2MigrationTest
  ```

  It needs a working Docker daemon and pulls the `postgres` and `mysql` images, which is
  why it is not in the ordinary build. **Run it before any deployment that targets
  Postgres or MySQL**, and after any change to a migration or an entity.

### Deployment checklist

- [ ] Exactly one API instance.
- [ ] TLS 1.2+ terminating in front of the API and the SPA; HTTP redirected; TLS to the
      database with certificate and hostname verification.
- [ ] `spring.profiles.active` set to something other than `dev`.
- [ ] `APP_IPHASH_KEY`, `APP_BOOTSTRAPADMIN_*`, `APP_MANAGEMENT_PROMETHEUS_PASSWORD` and the
      datasource credentials injected from the secrets manager; none in the repository, an
      image or a config file.
- [ ] IP-hash key rotation scheduled.
- [ ] Bootstrap Admin password policy-compliant, changed at first login, and your copy
      discarded. `dev` fallback nowhere in sight.
- [ ] `app.cors.allowed-origins` set to the production SPA origins.
- [ ] `server.forward-headers-strategy` decided: `none` unless there is a proxy you own
      that overwrites `X-Forwarded-For`; IP Throttle thresholds reviewed against NAT.
- [ ] Management port unreachable from any public route; the Prometheus scrape job sends
      the scrape credential and its target is up.
- [ ] `audit.log` forwarded to central logging: 90+ days, write-once, access-restricted,
      alerting when events stop.
- [ ] `EmailService` stub replaced with real email delivery, or password reset accepted
      as unusable (outside `dev` the stub withholds reset links).
- [ ] `VITE_API_ORIGIN` and `VITE_SECURITY_CONTACT` set for the production build;
      `security.txt` `Expires` date tracked.
- [ ] SPA host sends HSTS, CSP, `X-Frame-Options`, `X-Content-Type-Options` and
      `Permissions-Policy`.
- [ ] `./mvnw -P migrations test -Dtest=NonH2MigrationTest` green against the target
      vendor.
- [ ] `NVD_API_KEY=… ./mvnw -P security verify` and `npm run security` green.
- [ ] Every item in [Accepted deviations](#accepted-deviations) reviewed by the risk
      owner.

## Accepted deviations

This is a reference implementation for an assessment, not a service for real users, and
the PRD mandates local username/password accounts. On that basis the following controls
are **not built**, and were accepted rather than met. [ADR
0001](docs/adr/0001-app-standards-override-prd.md) records each with its reasoning.
**Any real deployment must revisit every one of them.**

IM8 application controls accepted as deviations:

| Control | What is missing | What stands in for it |
| --- | --- | --- |
| **ac-2** MFA | Admins log in with a password only; there is no MFA or 2FA, for Admins or anyone. | Lockout (5 wrong passwords, 20 minutes), a per-username login limit, the IP Throttle, one Session per Account, and an audit event for every admin action. |
| **ac-3** inactive accounts | No inactivity disablement and no scheduled account-hygiene job. | The Admin Account list shows role, enabled, Locked and creation date for a manual sweep. |
| **ac-4** access review | No declared per-Account permission baseline and no periodic review. | The same list, reviewed by hand. |
| **ac-7** Singpass / Corppass | Not integrated. | Local accounts by self-registration, as the PRD requires. |
| **ac-8** automated lifecycle | No SCIM or JIT provisioning. | Self-registration and admin management. |
| **ac-12** SSO | No SSO (WOG AAD). | Local accounts. |
| **lm-18** WOGAA | Not embedded. | Nothing; the app is not a public government digital service. |

Standard deviations an operator should know about, because the deployment has to make up
the difference:

- **Single instance.** In-memory rate limiters and IP Throttle
  ([§1](#1-run-exactly-one-instance)).
- **TLS is a deployment requirement,** not enforced by the app
  ([§2](#2-tls-12-or-later-everywhere)).
- **Central log-platform configuration is not built:** forwarding, retention, write-once
  storage, access control and alerting on audit-logging failure are all yours
  ([§8](#8-forward-the-audit-log-to-central-logging)).
- **The `EmailService` is a stub** that logs each email to `email.log` with the recipient
  masked. Only in `dev` does it write the reset link, Reset Token and all; elsewhere the
  link is withheld. No real email, and therefore no delivery retry.
- **Reset confirmation is rate-limited per client address, not per Account,** because an
  invalid token identifies no Account.
- **No automatic Required Password Change when an Account is re-enabled.** An Admin
  applies it explicitly when compromise is suspected.
- **A registration conflict returns one combined `user exist` error,** so registration
  still reveals that *some* matching Account exists. Closing that needs email
  verification, which is not built.
- **Deletion keeps a tombstone** rather than a soft-deleted row: the Account row goes, and
  `deleted_users` keeps its UUID, username, email, deletion time and deleting Admin
  indefinitely. A deleted username can never be registered again.
- **Accounts are self-registered,** not created by an administrator.
- **OWASP Dependency-Check runs locally** through a Maven profile; there is no CI gate,
  and CI/CD is out of scope.
- **BCrypt cost 12,** which the PRD names, rather than the Argon2id the Standard prefers
  for new systems. This is why passwords cap at 64 characters and 72 UTF-8 bytes.

## About this repository

This repository is an AI-assisted coding assessment: the application was generated from
[`prd/assessment-prd.md`](prd/assessment-prd.md). Everyone works independently on their
own branch named after themselves — full name, lowercase letters only, no digits, spaces
or punctuation (`Jane Doe` → `janedoe`).

```bash
git checkout main && git pull origin main
git checkout -b janedoe
git push -u origin janedoe
```

Do not commit to `main`, and do not commit to anyone else's branch.
