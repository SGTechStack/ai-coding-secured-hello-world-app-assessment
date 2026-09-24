# Hello World Auth App

Reference implementation of secure username/password auth: React SPA on
`localhost:3000` calling a Spring Boot REST API on `localhost:8080`
(two origins, cookie sessions, CORS with credentials).

Canonical requirements: `assessment-prd.md`. Decisions:
`.scratch/hello-world-auth/map.md` + `issues/`.

Features: self-registration, login with per-account lockout plus
independent per-IP throttling, logout with true server-side invalidation
(Spring Session JDBC), CSRF-protected cookie sessions, single-use
password-reset flow with a stubbed email service, admin user management
(list / enable-disable / role change / delete with self-action guards),
config-driven admin seeding, and structured JSON audit logging.

## Backend (`backend/`)

Java 21, Maven, Spring Boot 4.1.1 / Security 7.1 / Data JPA (H2 locally, MySQL or PostgreSQL in production) /
Spring Session JDBC, with Liquibase managing all schemas.

```sh
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev   # http://localhost:8080
mvn test                                            # MockMvc + @SpringBootTest suite
```

- `POST /api/auth/register` — `{username, email, password}` (password ≥ 12 chars)
- `POST /api/auth/login` / `POST /api/auth/logout`
- `POST /api/auth/password-reset/request` / `…/confirm` — the reset link is
  *logged* by the stubbed `EmailService` (no SMTP exists); copy it into the SPA
- `GET /api/auth/csrf` — CSRF bootstrap; `GET /api/auth/me` — session probe
- `GET /api/hello` — protected greeting
- `GET /api/admin/users`, `PATCH …/{id}/status`, `PATCH …/{id}/role`,
  `DELETE …/{id}` — ADMIN only
- `GET /actuator/health`, dev-only `http://localhost:8080/h2-console`

### Dev credentials

The `dev` profile seeds an initial admin on first boot
(`application-dev.yml`):

- **Username:** `admin`
- **Password:** `admin-local-dev-password`

These are documented dev-only defaults — the `prod` profile has no
fallbacks and refuses to start without `APP_ADMIN_USERNAME` /
`APP_ADMIN_PASSWORD` set.

### Audit logging

Security events emit one structured JSON line each on stdout via a
dedicated `audit` logger (logstash-logback encoder): `login_success`,
`login_failure` (`reason`: bad_credentials / disabled / locked /
ip_throttled), `account_locked`, `password_reset_requested`,
`password_reset_completed`, and `admin_status_changed` /
`admin_role_changed` / `admin_user_deleted` with actor + target.
Passwords and tokens never appear in audit lines.

### Maven behind TLS-inspecting proxies (Zscaler etc.)

If `mvn` fails with `PKIX path building failed`, the JDK trust store doesn't
know the proxy's CA while Windows does. Point Maven at the Windows root store:

```sh
MAVEN_OPTS="-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT" mvn spring-boot:run
```

## Frontend (`frontend/`)

Vite + TypeScript + React, Tailwind v4, shadcn/ui plumbing
(`components.json`, `@/` alias, `cn()`), port pinned to 3000.

```sh
cd frontend
npm install
npm run dev                # http://localhost:3000
npm run build              # tsc -b && vite build
```

`src/lib/api.ts` is the single fetch seam: `credentials: 'include'` always,
`X-XSRF-TOKEN` header from the `XSRF-TOKEN` cookie on mutations.

## Browser acceptance evidence

The full PRD (`assessment-prd.md`) acceptance suite was exercised end-to-end
against a live stack with Playwright — **22/22 test cases PASS**:
`artifacts/browser-test/prd-acceptance-report.md` (one screenshot per test
case under `artifacts/browser-test/screenshots/`, backend log under
`logs/`). Coverage: registration + duplicate/weak-password rejections,
login success and identical generic failures, logout with server-side
session invalidation (replayed cookie → 401), single-use password-reset
flow incl. session revocation, admin list/role/status/delete with
self-action guards, USER→`/api/admin/**` 403, IP throttling (429 on the
5th attempt — including with correct credentials), CSRF 403 without the
token, and cookie/security-header checks.

Not browser-verifiable in a live run (covered by the MockMvc suite):
per-account lockout is unreachable from a single source IP by design
(`ip-throttle.max-failures 4 < lockout.max-failures 5` — the anti-DoS
invariant), expired reset tokens (15-min wall-clock TTL), and admin-seed
dedup across restarts (dev H2 is in memory).

### Deployment note — single-origin bundle

The acceptance run tested a **single-origin** deployment: `frontend/dist`
copied into `backend/src/main/resources/static` and served by the backend
on `:8080` (reset links pointed at `:8080` via
`--app.password-reset.link-base-url=http://localhost:8080/reset-password`).
Caveat: the jar used for that run was built with SPA-hosting glue
(`SpaForwardController`, a self-referential CSP, and permit-all for
non-API GETs) that is **not in the current source tree** — the committed
`SecurityConfig` is the API-only posture and would 401 the static assets
and block the SPA's scripts via `default-src 'none'`. Rebuilding from
source requires re-adding that hosting delta; the two-origin layout above
remains the supported dev setup.

## Security review

Latest pass: **2026-09-22** —
`artifacts/webapp-security-review/hello-auth-recheck-report.md`
(supersedes `hello-auth-report.md` / `hello-auth-post-fix-report.md`).
Re-verified statically **and** live (dev profile, `curl` against a fresh
build): **8/9 checklist sections PASS**, §3 HTTPS `NOT VERIFIED`
(TLS/HSTS is a deployment concern — see assumptions below).
0 Critical / 0 High / 0 Medium; residual items are all LOW/INFO —
unthrottled reset `confirm`, `log-reset-link` defaulting on in the base
profile, the stale `static/` bundle, register-409 enumeration (accepted),
and the in-memory demo datastore (F-09..F-13). Top remediation: wire
TLS + `forward-headers-strategy` at the proxy, then flip the
`log-reset-link` default.

## Profiles and deployment

`dev` — in-memory H2 + console, `Secure=false` cookies, localhost CORS,
documented admin seed defaults. `prod` (`application-prod.yml`):

| Env var | Required | Purpose |
| --- | --- | --- |
| `DB_URL` | yes | MySQL or PostgreSQL JDBC URL (no embedded fallback) |
| `DB_USERNAME` | yes | Database login |
| `DB_PASSWORD` | yes | Database password, supplied through secret injection |
| `APP_ADMIN_USERNAME` | yes | Initial admin login name |
| `APP_ADMIN_PASSWORD` | yes | Initial admin password |
| `APP_ADMIN_EMAIL` | no (`admin@localhost`) | Initial admin email |
| `APP_CORS_ALLOWED_ORIGINS` | no (blank = none) | Comma-separated SPA origin allow-list |

prod additionally sets `server.servlet.session.cookie.secure=true` and
disables the H2 console. All security tunables live under `app.*`
(`lockout.*`, `ip-throttle.*`, `password-reset.*`, `password-min-length`);
startup refuses to boot when
`app.ip-throttle.max-failures >= app.lockout.max-failures`, which would
otherwise reintroduce single-IP account lockout.

### Database setup and schema changes

Provision an empty **PostgreSQL 17+** database or **MySQL 8.4+** database before
starting with `prod`. The JDBC driver and Hibernate dialect are detected from
`DB_URL`; both drivers are included. Example URLs (replace host/database and
configure the server CA trust):

- PostgreSQL: `jdbc:postgresql://db.example.com:5432/helloauth?sslmode=verify-full`
- MySQL: `jdbc:mysql://db.example.com:3306/helloauth?sslMode=VERIFY_IDENTITY`

Inject `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and the admin settings above, then
run `java -jar backend/target/hello-auth-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod`.
Do not activate `dev` alongside `prod`. Missing database settings fail startup.
The local/default profile continues to use disposable H2; this is not a backup
store for production. Choose one production database per deployment.

Liquibase runs before Hibernate validation and owns both application tables and
Spring Session tables. Hibernate uses `ddl-auto: validate`; Spring SQL and Session
schema initializers are disabled. Restarts retain accounts, lockouts, reset tokens
and unexpired sessions in the external database. IP throttling remains in memory.

The initial migrations are in `backend/src/main/resources/db/changelog/`. Session
DDL is frozen from Spring Session JDBC 4.1.1, with the appropriate binary type and
MySQL InnoDB options. Application timestamps use microsecond precision (timezone
aware on PostgreSQL/H2; Hibernate UTC instants on MySQL). Roles use portable VARCHAR.
Use MySQL InnoDB and UTF-8 (`utf8mb4`); choose database collation deliberately,
as username/email uniqueness can be case-insensitive with MySQL's default collation.

For each schema change, add a new changelog and include it from the master; never
edit an applied changeset or reset Liquibase checksums to bypass drift. Test on
both target databases and back up before deployment. The initial migration targets
an empty schema: existing manually managed schemas need a reviewed baseline and
data migration; do not run `changelogSync` blindly.

The application login needs CRUD access to application/session tables and, by
default, migration DDL rights. To separate privileges, inject
`SPRING_LIQUIBASE_USER` and `SPRING_LIQUIBASE_PASSWORD` for a migration account on
the same database. Keep these credentials outside source control.

`mvn test` checks H2 migrations and persistence across a full application restart,
and generates each vendor's SQL. To run the actual migration, Hibernate validation,
timestamp and session round-trip test, point `TEST_DATABASE_URL`,
`TEST_DATABASE_USERNAME`, `TEST_DATABASE_PASSWORD` at an **empty disposable** MySQL
or PostgreSQL database, then run `mvn -f backend/pom.xml -Dtest=DatabaseMigrationTests test`.
Run once for each vendor. The test leaves its migration schema and bootstrap admin
in that disposable database. SQL generation and H2 tests alone do not establish
vendor compatibility.

### Deployment assumptions (documented, not built)

- **HTTPS required in real deployments.** `Secure` cookies only travel over
  TLS — terminate HTTPS in front of the app (load balancer / reverse proxy).
  Local dev over HTTP is the accepted gap.
- **Client IP comes from `getRemoteAddr()` only.** The IP throttle keys off
  the direct peer address and never parses `X-Forwarded-For`, which any
  client can rotate. Deploy **only behind a trusted proxy that strips
  client-supplied forwarding headers**; when one fronts the app, enable
  `server.forward-headers-strategy=framework` so `getRemoteAddr()` reflects
  the real client IP.
- **JWT alternative:** session cookies are the primary mechanism by design;
  the documented JWT trade-off (bearer token + `jti` revocation blacklist)
  lives in `assessment-prd.md`, *Appendix: JWT Alternative*.
