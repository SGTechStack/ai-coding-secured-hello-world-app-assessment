# Architecture

> Last updated: 2026-09-28. Written to satisfy IM8 pm-6 (system documentation). Supersedes the
> auto-generated `pre-prod-report/tech-architecture.md` snapshot, which reflects the pre-remediation
> state (Spring Boot 3.3.4, before the lifecycle/observability/CSP work below landed) and is kept only
> as a point-in-time audit artefact.

## System overview

Secured Hello World App is a two-origin reference implementation of username/password
authentication. A React 19 + Vite + TypeScript single-page frontend (`frontend/`, served at
`localhost:3000` in dev) talks over REST/JSON to a Spring Boot 4.1.0 + Spring Security 7.1.0 +
Spring Session + Spring Data JPA backend (`backend/`, served at `localhost:8080` in dev).
Authentication is server-side session via a secure `HttpOnly` cookie (not JWT), so logout and
password reset can invalidate sessions immediately and unconditionally.

The backend is a pure JSON API — it never serves the frontend's HTML/JS/CSS. This matters for
where security controls (like CSP) must live: a response header set by the backend has no effect
on the SPA's own page load, since the backend never serves that document.

**Database**: production and all deployed environments run **PostgreSQL**. An embedded H2
database is used only for local dev convenience and for the test suite. To keep dialect-sensitive
behaviour (identifier quoting/case-folding, date/time functions, `LIMIT`/`OFFSET` syntax, boolean
handling, etc.) consistent between what's exercised locally/in CI and what actually runs in
production, H2 should be run in **PostgreSQL compatibility mode** (`MODE=PostgreSQL` on the JDBC
URL, paired with Hibernate's `PostgreSQLDialect`) rather than H2's default mode — see "Query
strategy" below for the exact config and current status. This does not make H2 and Postgres
identical — it narrows the gap enough that JPQL/Criteria-based queries which pass against
H2-in-Postgres-mode are highly likely to behave the same against real Postgres, which plain
default-mode H2 does not guarantee.

## Deployment topology

Two independently deployable artifacts:

- **Backend**: a Spring Boot fat JAR (`mvn spring-boot:run` in dev, `java -jar` in production),
  listening on port 8080, deployed to **AWS running multiple instances** behind a load balancer
  (e.g. ECS/Fargate tasks or EC2 Auto Scaling instances behind an ALB — exact provisioning is
  managed outside this repo). Running multiple instances has two direct architectural
  consequences that the codebase already accounts for:
  - **Session state** is not held in-process only. Because a request can land on any instance,
    session data (`SessionRegistry`, the authenticated `SecurityContext`) must be externalised —
    e.g. via Spring Session backed by a shared store (Redis/JDBC) — rather than relying on a
    single JVM's in-memory session map. `LoginCountService`'s Micrometer counter is explicitly
    documented as per-instance-only for this reason; the fleet-wide-correct total is the
    database-backed `login_counts` table instead (see Observability below).
  - **Scheduled lifecycle jobs** (`DormantAccountDisablingJob`, `AccessReviewJob`) must not run
    redundantly on every instance. A distributed lock (e.g. ShedLock) coordinates so each
    scheduled run executes exactly once across the fleet, not once per instance — see the javadoc
    on both jobs for the exact locking mechanism in use.
  - **The database is the single source of truth** shared by every instance — PostgreSQL, not an
    embedded/in-process database, since an embedded H2 instance cannot be shared across separate
    JVMs/containers.
- **Frontend**: a static SPA build (`npm run build` → `frontend/dist/`), deployable to any static
  host/CDN independently of the backend (e.g. S3 + CloudFront in an AWS deployment).
  `VITE_API_BASE_URL` (default `http://localhost:8080`) points it at the backend origin.
- No committed container/orchestration config exists in this repo (no `Dockerfile`,
  `docker-compose.yml`, or Kubernetes/CDK/Terraform manifests) — infra-as-code provisioning of the
  AWS resources (ALB, compute, RDS/Aurora Postgres, networking) is out of scope for this reference
  implementation and lives in whatever deployment tooling wraps it.
- TLS termination point is not defined in this repo; `server.servlet.session.cookie.secure: true`
  is the base-profile default (relaxed only in the `dev` profile, which runs over plain HTTP
  locally), so the ALB (or equivalent) fronting the fleet must terminate TLS and forward
  `X-Forwarded-*` headers correctly, or the `Secure` cookie will never be returned by the browser.
- IP-based rate limiting/DDoS mitigation is expected to be handled by a WAF or equivalent
  edge/CDN layer in front of this app, not by the application itself — see "Known architectural
  limitations" below and `docs/adr/no-ip-rate-limiting-waf-delegated.md` for why.

## Request flow (example: login)

1. Browser sends `POST /api/login` with the session cookie (if any) and an `X-XSRF-TOKEN` header.
2. Spring Security's filter chain (`SecurityConfig`) validates CORS origin, then the CSRF token
   against the cookie.
3. `LoginController` → `LoginService.login()`:
   - Looks up the user; a missing user or bad password both throw the same generic
     `AuthenticationFailedException` (enumeration resistance).
   - Checks the account's time-boxed lock state and its `forcePasswordChange` flag is handled
     separately, after authentication, by `ForcePasswordChangeFilter` (see below).
   - On success: rotates the session ID (`ChangeSessionIdAuthenticationStrategy`), persists the
     `SecurityContext` into the HTTP session, registers it with `SessionRegistry`, records
     `lastLoginAt`, and increments both an in-process Micrometer counter and the database-backed
     `login_counts` table (the latter is the fleet-wide-correct total; the former is per-instance
     only — see `LoginCountService`'s javadoc).
4. `ForcePasswordChangeFilter` runs after authorization on every subsequent request: it re-reads
   the current user's `forcePasswordChange` flag from the database (not the session-cached
   principal, so a password change takes effect immediately) and blocks every endpoint except
   `/api/auth/change-password`, `/api/logout`, and `/api/csrf` while the flag is set.
5. `ApiExceptionHandler` (a `@RestControllerAdvice`) converts any thrown domain exception into a
   structured JSON error body, and has a catch-all `Exception` handler that logs unexpected
   failures at ERROR (with the throwable attached) while returning a generic message to the
   client — no internal exception detail is ever echoed to a caller.

## Background processes

- **`AdminBootstrapRunner`** (on startup): seeds a configured initial `ADMIN` account
  (`app.admin.*`) if none exists yet, with `forcePasswordChange = true` so the seed credential can
  never be used beyond the first login.
- **`DormantAccountDisablingJob`** (daily, `app.security.account-lifecycle.*`): disables any
  enabled account whose `lastLoginAt` (or, if never logged in, `createdAt`) is older than the
  configured dormancy threshold (default 90 days), invalidating its sessions.
- **`AccessReviewJob`** (daily, `app.security.access-review.*`): disables accounts past their
  declared `accountExpiresAt`, and demotes any `ADMIN` account whose username is not on the
  declared `authorised-admin-usernames` baseline back to `USER` — a genuine privilege-drift check,
  not just inactivity-based (see `docs/adr/ac-8-not-applicable.md` for why SCIM/JIT provisioning
  is separately judged not applicable to this app's self-service account model).

## Module map (backend)

| Package | Responsibility |
|---|---|
| `auth` | Login/logout orchestration, per-account lockout, per-IP throttling, forced password change (`PasswordChangeService`), database-backed login counting (`LoginCount*`). |
| `user` | `User` JPA entity (credentials, role, lockout/dormancy/expiry state) and its repository. |
| `registration` | New-account creation with uniqueness + password-policy validation. |
| `passwordreset` | Single-use hashed token reset flow; stub email delivery (`LoggingEmailService`) per `PRODUCT.md`'s explicit no-real-SMTP scope. |
| `admin` | Role-gated (`ADMIN`) user management: list/enable/disable/role-change/delete, with self-action guardrails. |
| `bootstrap` | Seeds the initial admin account on first startup. |
| `lifecycle` | The two scheduled account-lifecycle jobs described above. |
| `config` | Central Spring Security filter chain: CORS, CSRF, session management, authorization rules, CSP header, the `ForcePasswordChangeFilter`. |
| `web` | `ApiExceptionHandler` — domain-exception-to-JSON translation and the unhandled-exception catch-all. |
| `logging` | `LogSanitizer` — strips control characters from user-controlled values before they reach a log line, preventing log injection/forging. |
| `hello` | The namesake authenticated greeting endpoint. |

## Data model

Two tables, no ORM associations beyond a plain `Long` foreign key (kept simple deliberately, no
`@ManyToOne` needed at this scale):

- **`users`**: `id, username (unique), email (unique), password_hash, role, enabled,
  force_password_change, failed_login_attempts, locked_until, created_at, last_login_at,
  account_expires_at`.
- **`password_reset_tokens`**: `id, user_id (FK), token_hash (unique), expires_at, used_at`.
- **`login_counts`**: `outcome (SUCCESS|FAILURE, PK), count` — the fleet-wide login counter (see
  Request flow above).

No formal migration tool (Flyway/Liquibase) is used; Hibernate DDL-auto generates the schema from
the `@Entity` annotations, which are the sole source of truth for table structure in this
reference build. In production this runs against PostgreSQL, so DDL-auto is generating
Postgres-flavoured DDL (via `PostgreSQLDialect`), not H2's — schema output can differ subtly from
what a developer sees against the local H2 dev database (e.g. column type mapping, sequence vs.
identity strategy), which is one more reason the dev/test H2 instance is run in
PostgreSQL-compatibility mode (see below) rather than H2's default mode.

## Query strategy: JPA/JPQL and Criteria only, no native SQL

Because the deployed database (PostgreSQL) differs from the dev-time embedded database (H2), this
codebase deliberately avoids hand-written native SQL (`@Query(nativeQuery = true)`,
`EntityManager.createNativeQuery`, `JdbcTemplate` raw SQL) anywhere in the persistence layer.
Native SQL locks a query to one database's dialect/syntax, and a query validated only against H2
provides no real assurance it will run — or run correctly — against Postgres in production.
Instead:

- Simple lookups use **Spring Data derived query methods** (`findByUsername`, `existsByEmail`,
  etc. — see `UserRepository`), which Spring Data translates to the active `Dialect` at runtime.
- Multi-condition queries with a fixed shape use **JPQL** (`@Query("SELECT u FROM User u WHERE
  ...")`, as in `UserRepository.findDormantEnabledAccounts`/`findExpiredEnabledAccounts` today).
  For queries whose predicates are built up **conditionally at runtime** (e.g. an admin
  user-search endpoint filtering on an arbitrary combination of role/enabled/date-range), new code
  should use the **JPA Criteria API** — typically via `JpaSpecificationExecutor<T>` +
  `Specification<T>` — rather than string-concatenated SQL or JPQL built by hand with `StringBuilder`.
  Both JPQL and Criteria are compiled down to the active Hibernate `Dialect`, so the same query
  definition produces correct SQL against H2 in dev/test and Postgres in production without
  maintaining two versions of a query. (No `Specification`/`Criteria`-based query exists in the
  codebase yet, since no current endpoint needs runtime-conditional filtering — this is the
  pattern to reach for the first time one does, not a retrofit of existing queries.)
- No repository in this codebase currently uses `@Query(nativeQuery = true)`,
  `EntityManager.createNativeQuery`, or raw `JdbcTemplate` SQL — that should stay true going
  forward for the reason above. If a future requirement genuinely needs database-specific SQL
  (e.g. a Postgres-only feature like `jsonb` operators or full-text search), that query must be
  isolated behind its own repository method, documented as Postgres-only in its javadoc, and
  explicitly excluded from dialect-portability tests — it should not be the default way queries
  are written in this codebase.

### H2 in PostgreSQL-compatibility mode

Dev and test configuration should point H2 at PostgreSQL compatibility mode rather than H2's
default mode, so that query behaviour exercised locally and in CI matches Postgres far more
closely. **This is not yet configured in `application.yml`/`application-dev.yml`** — those
currently start H2 with no `MODE` parameter (plain H2 dialect). To close that gap:

- JDBC URL: `jdbc:h2:mem:securedhelloworld;MODE=PostgreSQL;DB_CLOSE_DELAY=-1` (the `MODE=PostgreSQL`
  parameter switches H2's SQL parser/type-coercion rules to emulate Postgres — identifier
  case-folding, `LIMIT`/`OFFSET`, string concatenation with `||`, boolean literals, etc.).
- Hibernate dialect: `spring.jpa.database-platform:
  org.hibernate.dialect.PostgreSQLDialect` (forcing Hibernate to generate Postgres SQL syntax
  against the H2 connection, instead of letting it auto-detect and use `H2Dialect`).
- A separate `prod`/deployed-environment datasource configuration (URL, driver
  `org.postgresql.Driver`, credentials sourced from environment/secrets, not committed) is
  required to actually point at PostgreSQL in AWS — the `postgresql` JDBC driver dependency also
  needs to be added to `backend/pom.xml` (currently only `h2` is declared, as a `runtime`-scope
  dependency for dev/test).
- This is a compatibility approximation, not a substitute for testing against real Postgres —
  H2's PostgreSQL mode does not implement every Postgres-specific function, extension, or edge
  case. Any query relying on genuinely Postgres-specific behaviour should be covered by an
  integration test against a real Postgres instance (e.g. Testcontainers) before being considered
  verified, not just against H2-in-PostgreSQL-mode.

## Observability

- **Structured logging**: ECS-formatted JSON on the console (`logging.structured.format.console:
  ecs`, native to Spring Boot 4.1 — no third-party encoder dependency).
- **Metrics**: `spring-boot-starter-actuator` + Micrometer. `/actuator/health` is public (standard
  liveness-probe convention); everything else under `/actuator/**` requires authentication, and
  only `health` and `info` are exposed (`management.endpoints.web.exposure.include`) — never a
  wildcard. Business-event counters (`app.auth.login`, `app.registration.completed`,
  `app.password_reset.*`) are tagged Micrometer counters; login counts specifically also persist
  to the database (`login_counts` table) since a Micrometer counter is per-instance and would
  under-count in a multi-instance deployment.
- **Log injection hardening**: every log call site that interpolates a user-controlled value
  (username, client IP) passes it through `LogSanitizer.sanitize()` first, stripping control
  characters that could forge additional log lines.

## Security controls quick reference

- **CSRF**: cookie-based double-submit (`CookieCsrfTokenRepository`), primed via `GET /api/csrf`.
- **CORS**: single allowed origin (`app.frontend.origin`), credentials enabled.
- **CSP**: delivered two ways — a `<meta>` tag in `frontend/index.html` (the one that actually
  protects the SPA, since the backend never serves that document) and a matching response header
  from the backend (covers any HTML the backend itself might render, e.g. framework error pages).
- **Password storage**: BCrypt (`BCryptPasswordEncoder`), never anything weaker.
- **Session security**: `HttpOnly`, `Secure` (outside `dev`), `SameSite=Lax` cookie; session ID
  rotated on login; sessions invalidated server-side on logout, password reset, and forced
  password change.
- **Default credentials**: the bootstrap admin account is created with `forcePasswordChange = true`
  and cannot reach any other endpoint until it is changed (IM8 ac-6).

## Intentionally public endpoints

Confirmed sign-off (Gate 2 of the pre-prod readiness review) that the following endpoints are
deliberately reachable without authentication — each is required to be anonymous for the flow it
serves:

| Endpoint | Why it must be public |
|---|---|
| `POST /api/register` | Account creation happens before any credential exists. |
| `POST /api/login` | Authentication happens before any session exists. |
| `POST /api/password-reset/request` | A user who forgot their password cannot authenticate. |
| `POST /api/password-reset/confirm` | Same — reset completion is token-authenticated, not session-authenticated. |
| `GET /api/csrf` | Primes the CSRF cookie before any other request; must be reachable pre-session. |
| `GET /actuator/health` | Standard liveness/readiness-probe convention; reveals only UP/DOWN. |
| `GET /h2-console/**` | Dev-only DB console — see "H2 console" below; **not** public outside the `dev` profile. |

Everything else falls under `anyRequest().authenticated()` in `SecurityConfig`, with
`/api/admin/**` additionally requiring `ROLE_ADMIN`. No endpoint is unintentionally public — this
list is exhaustive and was cross-checked against every `@*Mapping` in the codebase (see
`openapi.yaml`, which documents the same 12 endpoints plus `/actuator/health`).

### H2 console profile gate

The H2 console's `permitAll` rule is gated on the **active Spring profile inside
`SecurityConfig` itself** (`env.getActiveProfiles()` contains `"dev"`), not only on
`application-dev.yml`'s `spring.h2.console.enabled` flag. If `spring.h2.console.enabled` were ever
accidentally set to `true` in a non-dev profile, `SecurityConfig` still denies the route outright
(`denyAll()`) — the two layers are independent, so a config mistake in one doesn't expose the
console. See `H2ConsoleProfileGateTest` for the regression test covering this specifically.

**Spring Boot 4 dependency note:** Boot 4's modularized auto-configuration split moved the H2
console's auto-configuration out of the classic monolithic `spring-boot-autoconfigure` jar into a
dedicated module (`spring-boot-h2console`) — having only the `h2` JDBC driver on the classpath is
no longer sufficient to auto-register the console servlet (confirmed by a live run: without this
dependency, `/h2-console/**` 500s with `NoResourceFoundException` instead of reaching Spring
Security at all). `backend/pom.xml` declares `spring-boot-h2console` explicitly for this reason.

## Known architectural limitations (accepted, not oversights)

- **IP-based rate limiting/throttling is intentionally not implemented in this application.**
  It is expected to be handled by a WAF (Web Application Firewall) or equivalent edge/CDN layer
  sitting in front of this application — not by the application itself. A WAF can see and
  rate-limit traffic before it ever reaches this app (including at L3/L4, across many app
  instances, and using threat-intel IP reputation this app has no visibility into), which an
  application-layer in-memory throttle fundamentally cannot do correctly in a multi-instance or
  DDoS-scale scenario. `LoginAttemptService` only tracks per-account failed-login lockout, which
  is a distinct control from IP-based rate limiting. See
  `docs/adr/no-ip-rate-limiting-waf-delegated.md` for the full decision record, including the
  deployment prerequisite this creates (a WAF/edge layer is required in front of this app).
- No real SMTP; `LoggingEmailService` is an explicit, PRD-documented stub — see
  `PRODUCT.md`.
- No SCIM/JIT account provisioning — judged not applicable to this app's self-service
  registration model; see `docs/adr/ac-8-not-applicable.md`.
