# Secured Login App — consolidated specification

The locked plan. Every decision on the [wayfinder map](../map.md) assembled into one document, so a build can proceed without re-deciding anything.

**This document is an index with the decisions inlined, not a replacement for the tickets.** Where a choice looks arbitrary, the linked ticket carries the reasoning and the citations. Where it looks like a deviation from the PRD, see [`prd-deltas.md`](prd-deltas.md).

**Standards pin:** `App-Standards/` is a git submodule at `ff5ab8205fdfb641210164eeaa2365e48e04846b`. Every `path:line` citation across this plan is against that pin, so **re-pinning invalidates line numbers map-wide**. Clone with `git clone --recurse-submodules`, or run `git submodule update --init`; without it every citation points at an empty directory ([06](../issues/06-standards-repo-checkin.md)).

---

## 1. Tech baseline

Fixed by [15](../issues/15-tech-baseline-and-module-structure.md). The pin is the standards', not ours — all eight binding recipes state Boot 4.x with Security 7.x.

**Backend** — Java 21 · Spring Boot 4.0.x · Spring Security 7.0.x · Maven with a committed wrapper.

**Frontend** — Vite · React 19 · TypeScript · react-router v7 · **axios** (specifically, because `Std:437` requires a global `401`/`403` interceptor).

**Layout** — one repo, sibling `backend/` and `frontend/`. Feature-first packages under `com.assessment.auth`, with `audit/` the single logging owner and `common/` holding the global exception handler, the MDC filters and the `ProblemDetailWriter`.

§4 Separation of Concerns (`:411-417`) prescribes **endpoint** boundaries, not a package layout — the layout is a free choice, and the frontend is wholly unconstrained.

**Dependencies of note** — Liquibase; Spring Session JDBC; Micrometer Tracing with the **OpenTelemetry** starter (W3C propagation by default, `Std:277`) plus Actuator as its carrier; **Bucket4j** (rate-limit algorithm) **and Caffeine** (its TTL-evicting backing map — both, with distinct jobs, [07](../issues/07-lockout-and-ip-throttling.md)); `dependency-check-maven` in a non-default profile with `failBuildOnCVSS=7`.

**Secrets** — environment variables, no committed defaults. A missing `APP_ADMIN_PASSWORD` **fails startup** rather than seeding a guessable admin. A committed `.env.example` documents the set.

**`TZ=Asia/Singapore`** pinned explicitly ([05](../issues/05-logging-standards-applicability.md)'s UTC+8 log rendering); all *stored* timestamps are UTC.

---

## 2. Data model

Liquibase owns every table, `ddl-auto: validate`, one versioned changelog file per logical change under `db/changelog/` ([02](../issues/02-persistence-and-session-backend.md)). PKs are `UUID`; every timestamp is `TIMESTAMP WITH TIME ZONE` stored UTC and read through an injectable `Clock`.

**PostgreSQL is the declared target.** H2 is confined to `dev` (file-based, so bootstrap idempotence is observable) and `test` (in-memory), both in `MODE=PostgreSQL`.

### `users`

`id` · `username` (unique) · `email` (unique) · `password_hash` · `role` · `enabled` · `require_password_change` · `failed_login_attempts` · `locked_until` · `disabled_at` · `last_login_at` · `created_at`

**No `account_non_locked` boolean.** `locked_until` alone: it is self-expiring, so automatic lift needs no write, and two sources of truth is a silent auth bypass ([07](../issues/07-lockout-and-ip-throttling.md)).

### `password_history` ([04](../issues/04-password-policy-and-history.md))

`id` · `user_id` FK → `users` **`ON DELETE CASCADE`** · `password_hash` · `created_at`

Holds the current hash as its newest row. Four values are blocked: current + three previous.

### `deleted_users` — tombstones ([10](../issues/10-account-lifecycle-and-delete-semantics.md))

`id` (the **same** id the live row had, so audit `user.id` stays resolvable) · `username` · `email` · `role` · `enabled` · `deleted_at` · `deleted_by_user_id`

**No `password_hash`, ever.** Retained **indefinitely**; purging has no owner, deliberately.

### `password_reset_tokens` ([11](../issues/11-password-reset-flow.md))

Exactly the PRD's: `id` · `user_id` FK · `token_hash` (SHA-256, **unique-indexed** — it is the lookup key) · `expires_at` · `used_at`

### `roles` ([03](../issues/03-role-model-reconciliation.md))

Minimal, read-only, seeded from `application.yml` at startup. `Std:407`/`:415` make persisting role definitions an enforced constraint.

### `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES` ([02](../issues/02-persistence-and-session-backend.md))

Via Liquibase `sqlFile` against Spring Session's **packaged** per-vendor script under `dbms` guards. The recipe's literal `initialize-schema: always` **cannot be copied** — it re-runs `CREATE TABLE` every boot and breaks on the second boot of file-based dev H2.

⚠️ The `PRINCIPAL_NAME` index is load-bearing (Story 7's "invalidate all sessions") and `ddl-auto: validate` does **not** catch its absence. Liquibase's checksum on that file is the tripwire for a Spring Session upgrade changing the DDL — a checksum failure is the intended loud failure, not a bug to suppress.

---

## 3. Endpoint inventory and authorization matrix

`app.security.authorization-matrix`, **flat, ordered, first match wins**, the **sole** authorization mechanism ([08](../issues/08-authorization-matrix.md)). `@EnableMethodSecurity` is out, and all five recipe `@PreAuthorize` annotations are **deleted, not rewritten** — they name authorities (`USERS_CREATE`, `SELF_READ`, …) that do not exist under [03](../issues/03-role-model-reconciliation.md)'s role model.

> **The matrix changed after 08 closed.** [10](../issues/10-account-lifecycle-and-delete-semantics.md) removed `batchResetPassword` (bulk operations out of scope); [07](../issues/07-lockout-and-ip-throttling.md) added `unlock`. Still 21 rows.

| # | Method | Path | Access |
|---|---|---|---|
| 1 | `GET` | `/api/v1/csrf` | `permitAll` |
| 2 | `POST` | `/api/v1/auth/login` | `permitAll` |
| 3 | `POST` | `/api/v1/auth/register` | `permitAll` |
| 4 | `POST` | `/api/v1/auth/password-reset/request` | `permitAll` |
| 5 | `POST` | `/api/v1/auth/password-reset/confirm` | `permitAll` |
| 6 | *any* | `/error` | `permitAll` |
| 7 | `GET` | `/api/v1/roles` | `hasRole('USER_MANAGER')` |
| 8 | `GET` | `/api/v1/users` | `hasRole('USER_MANAGER')` |
| 9 | `POST` | `/api/v1/users` | `hasRole('USER_MANAGER')` |
| 10 | `GET` | `/api/v1/users/{userId}` | `hasRole('USER_MANAGER')` |
| 11 | `PATCH` | `/api/v1/users/{userId}/status` | `hasRole('USER_MANAGER')` |
| 12 | `PATCH` | `/api/v1/users/{userId}/role` | `hasRole('USER_MANAGER')` |
| 13 | `PATCH` | `/api/v1/users/{userId}/unlock` | `hasRole('USER_MANAGER')` |
| 14 | `PATCH` | `/api/v1/users/{userId}/resetPassword` | `hasRole('USER_MANAGER')` |
| 15 | `DELETE` | `/api/v1/users/{userId}` | `hasRole('USER_MANAGER')` |
| 16 | *any* | `/api/v1/users/**` | `hasRole('USER_MANAGER')` — **backstop** |
| 17 | `GET` | `/api/v1/currentUser` | `authenticated` |
| 18 | `PATCH` | `/api/v1/currentUser/changePassword` | `authenticated` |
| 19 | `POST` | `/api/v1/auth/logout` | `authenticated` |
| 20 | `GET` | `/api/v1/hello` | `hasRole('USER')` |
| 21 | *any* | *any* | `denyAll()` |

**Four things about this table that are decisions, not incidentals:**

- **Row 20 is the only `hasRole('USER')` row**, which is what keeps `role-hierarchy: ROLE_USER_MANAGER > ROLE_USER` load-bearing rather than dead. Rows 17–19 are `authenticated` because self-discovery cannot be gated on a role vocabulary.
- **Row 6 exists because `AuthorizationFilter.filterErrorDispatch` defaults `true`** (verified in `spring-security-web-7.0.6`). Without it, every Spring Boot `/error` forward returns `403` under `denyAll()`.
- **Row 21 closes `/actuator/**` and Swagger with no explicit row** — a positive answer, not a gap.
- **No bare `*` path segments, ever.** `RBAC:43`'s `/api/v1/users/*` is single-segment and would have granted batch reset while denying `/{userId}/status`, `/role` and `/resetPassword` — Stories 9 and 10 would have `403`'d for the role that owns them.

### Self-action guard

One `SelfActionGuard` component, called by rows 11, 12, 13 and 15. Returns **`403` + `SELF_ACTION_NOT_ALLOWED`** ([10](../issues/10-account-lifecycle-and-delete-semantics.md), `Std:474`, `:266`). A `USER` calling `GET /users/{ownId}` gets `403` — `Std:429` is explicit that user-management endpoints reject non-administrators **including on their own account**. This is not a second self-read path.

### Three projections, never shared types

**List** (row 8): username, email, role, enabled, createdAt. **Detail** (row 10): the list fields plus `locked_until`, `failed_login_attempts`, `require_password_change`, `lastLoginAt`. **Self-read** (row 17): username, email, role, `requirePasswordChange`, createdAt, lastLoginAt, lastPasswordChangeAt — and **no** lock state or attempt count (`Q23a:603`).

Detail and self-read must be **different Java types**: sharing a DTO is how `Std:414`'s two halves erode into one, and the erosion runs toward disclosure.

---

## 4. Security configuration

### Filter chain order ([09](../issues/09-http-security-csrf-cors-headers.md))

1. `CorrelationIdFilter` — `@Order(SecurityProperties.DEFAULT_FILTER_ORDER - 1)`, **before** Spring Security's `-100`, or failed logins and `403`s carry no correlation id
2. `RateLimitFilter` — per-account and per-IP; before authentication; outside the matrix
3. `CsrfFilter`
4. **Custom JSON `AuthenticationFilter`** — `addFilterAt(…, UsernamePasswordAuthenticationFilter.class)`
5. `AnonymousAuthenticationFilter`
6. `MdcUserFilter` — `addFilterAfter(…, AnonymousAuthenticationFilter.class)`, registered **in the `SecurityFilterChain` bean**, never as a `@Component`
7. `AbsoluteSessionTimeoutFilter`
8. `PasswordChangeFilter` — tier 0, **preempts the matrix**
9. `AuthorizationFilter`

**`response.sendError` is banned chain-wide** — three filters would otherwise reach for it. `filterErrorDispatch=true` means an `ERROR` dispatch is itself authorized, and `sendError` cannot carry a `code`. ArchUnit rule #5.

### CSRF

Session-bound **Synchronizer Token** via `HttpSessionCsrfTokenRepository` + `XorCsrfTokenRequestAttributeHandler` (both Spring defaults). `CookieCsrfTokenRepository` is **strictly prohibited** (`Std:238`), so **no `XSRF-TOKEN` cookie exists** — and its absence is an acceptance test.

`GET /api/v1/csrf` returns the token as JSON, with **explicit no-store** (`Std:238`, `:443` — the recipe's controller omits it). Header is `X-CSRF-TOKEN`.

**No endpoint is CSRF-exempt.** Login, register and both reset endpoints are `permitAll` **and** CSRF-protected, so the SPA bootstrap `GET /csrf` → `POST /auth/login` → `GET /currentUser` is **mandatory**. Logout keeps CSRF deliberately (`Std:438` forbids "fixing" the expired-session `401`).

**Recorded deviation:** `Std:446` (previously redeemed tokens rejected) is unsatisfiable by the mandated mechanism; read as rotation-on-authentication.

### CORS

Chain-level `CorsConfigurationSource`. Origins from `app.security.allowed-origins` (dev `http://localhost:3000`; prod is an integrator value). Methods `GET, POST, PUT, PATCH, DELETE, OPTIONS`; headers `Authorization, Content-Type, X-CSRF-TOKEN`; `allowCredentials=true`; `maxAge=3600`.

Three recipe defects fixed: the chain never calls `.cors(...)`; `SecurityProperties` is undefined; `${api.base-path}` sits unresolved inside a Java string literal.

### Headers

`Content-Security-Policy: default-src 'self'; object-src 'none';` · `Permissions-Policy: geolocation=(), microphone=(), camera=()` · `X-Frame-Options: DENY` · `X-Content-Type-Options: nosniff` · `Strict-Transport-Security: max-age=31536000; includeSubDomains` (**no `preload`**) · `Referrer-Policy: no-referrer` (adopted from the SSO standard; an addition beyond the binding set)

`management.endpoints.web.exposure.include: health`, and nothing else.

### Cookies and sessions ([13](../issues/13-session-policy.md))

`HttpOnly=true`, `Secure=true` (**every** profile), `SameSite=Lax`. Idle **15m**, absolute **8h** (filter-enforced — there is no config key), **1** concurrent session via **`SpringSessionBackedSessionRegistry`** (`SessionRegistryImpl` would violate `Std:409`'s "across requests and restarts"). Session fixation: `changeSessionId()`, the framework default.

**No `InvalidSessionStrategy`** — `Std:90` prescribes a redirect that `Std:438` forbids, and setting one makes `CsrfConfigurer` insert an `InvalidSessionAccessDeniedHandler` ahead of the JSON handler, silently breaking the error contract on the expiry path.

Logout: `200` + `Clear-Site-Data: "cache","cookies","storage"` via a custom `LogoutSuccessHandler`. Login: **`200`, empty body**.

Session revocation is **direct `FindByIndexNameSessionRepository` deletion** (three recipes against one parenthetical note; `deleteById` removes where `expireNow()` only marks). Called on: password reset confirm, self-service change, admin disable, admin role change, admin delete.

---

## 5. Password policy ([04](../issues/04-password-policy-and-history.md))

Min **12**, max **72**, **no composition rules**, full printable ASCII, bundled denylist, must not contain the username or email local part. **BCrypt cost 12.**

**72 is not arbitrary:** BCrypt in the pinned Spring Security 7.0.6 *throws* above 72 bytes (verified in the jar), so `Questions.md:296`'s recommended 128 and the recipe's 1024 both turn a long passphrase into a `500`. ASCII-only makes a 72-character cap an exact byte cap.

**Composition is deliberately dropped** — `Priv:106`'s regex is recipe-only, the standard's one-of-each-class clause is scoped to the admin-generated password (now inapplicable, [11](../issues/11-password-reset-flow.md)), and the recipe's own note says to remove it. A 12-character all-lowercase passphrase **with spaces** must be accepted.

**History blocks four values** — current + three previous, the literal reading of `Std:355`. ⚠️ This makes `Self-Service:269-272`'s verification procedure **wrong for us**; do not transcribe it.

Enforced by an explicit `PasswordPolicy` + `PasswordHistoryService` called by **all four write paths** — registration, admin-create, reset-confirm, self-service change — not by Bean Validation.

**HIBP is declined, not skipped**: a variable-latency third-party call on the registration path undermines `Std:247`'s identical-timing clause.

### `requirePasswordChange`

Set by: admin-create, bootstrap seed, re-enable of a disabled account. Cleared by: self-service change, reset-confirm. **Not** set by admin-initiated reset (the user picks their own password at confirm).

The tier-0 filter allows exactly four paths: `GET /csrf`, `GET /currentUser`, `PATCH /currentUser/changePassword`, `POST /auth/logout`. **Logout is our addition** to the recipe's three — without it a flagged user cannot end their own session.

**No 30-day grace period** — auto-disable needs a sweep, and `@Scheduled` is banned.

---

## 6. Lockout and rate limiting ([07](../issues/07-lockout-and-ip-throttling.md))

**Three counters.**

| | Key | Threshold | Window | Effect | Store |
|---|---|---|---|---|---|
| Account lockout | username | 5 consecutive | **none** | locked 20 min | DB, durable |
| Per-account rate limit | username | 10/min | sliding | `429` | in-memory |
| Per-IP throttle | `getRemoteAddr()` verbatim | 50/min | sliding | `429` | in-memory |

All three return **`429` + `Retry-After`** — mandated (`Std:260` "must include"), not chosen. The `Std:247` conflict resolves because `:247` scopes to *authentication* outcomes and a rate-limit rejection is pre-authentication. A **locked** account still returns the generic `401` (`Std:258`), so lockout and throttling produce different statuses by design.

Reset endpoints are additionally rate-limited, **IP-keyed** (request 5/min, confirm 10/min) — a recorded deviation from `Std:124`'s per-account rule, because an anonymous endpoint has no account to key on.

Admin unlock is **required** (`Std:367`, `:451`, `:282`), without a mandatory reason field (`Qs:384` over `:386`).

---

## 7. Password reset ([11](../issues/11-password-reset-flow.md))

`Q14` answered **"Both admin-generated and self-service"** — the recommended option — on **one token model**.

`SecureRandom`, **32-char alphanumeric** (~190 bits), stored as an **unsalted SHA-256** (`Std:66` — the token is already high-entropy and needs no adaptive hash; this is what makes it a lookup key). **30-minute** TTL, single-use.

- **Issuing** a new token **deletes** any prior unused row (`Std:112`); `used_at` therefore means exactly one thing: redeemed.
- **Expired or already-used** returns `400` with a **single merged error** (`Std:261`) — distinguishing them tells an attacker they guessed a real token.
- **On confirm:** validate → policy + history → write credential → write history row → mark `used_at` → **invalidate all sessions** → **clear `requirePasswordChange`** → `sendPasswordChangedEmail`.

**Admin-initiated reset issues a token, not a password** — overturning `Priv:414-463` on the strength of `Std:401`, an enforced constraint. `Std:131` beats `Priv:441-442`: **the lock is not cleared** by a reset; the dedicated unlock endpoint is the remedy.

**Enumeration resistance covers body, timing and the audit log.** Identical generic `200` for any email; a **response-time floor** on login and reset-request (a **partial** discharge of `Std:247`, not constant time — assert the bound, don't claim more); and **one identical audit event** for known and unknown email.

⚠️ **The `EmailService` stub must not log the reset link** — see [`prd-deltas.md`](prd-deltas.md) §1.

---

## 8. Account lifecycle ([10](../issues/10-account-lifecycle-and-delete-semantics.md))

**Delete** = write a `deleted_users` tombstone, then **hard-delete** the `users` row, atomically, then kill sessions. `Std:31`'s glossary ("a read-only **archive record**") settles `:408`'s "soft-delete" wording in favour of the recipe's mechanism. Story 8's list needs no filter as a result.

**Username *and* email are both permanently burned** — the recipe checks only username against tombstones (`Priv:149`), which is a bug; `Std:96` states email uniqueness unqualified. `password_history` rows cascade away.

**Disable** is reversible: `enabled=false` + `disabled_at`, row retained, sessions killed. **Re-enabling sets `requirePasswordChange=true`** (`Std:130`). A disabled user's login returns the generic `401` (`Std:85`, `:259`).

**Out of scope:** self-service profile updates (`Q23`), bulk operations (`Q24`).

---

## 9. Admin bootstrap ([16](../issues/16-admin-bootstrap-and-seeding.md))

A **profile-independent `ApplicationRunner`**, not a Liquibase changeset — `Common_Automatic…:276` explicitly blesses startup runners for "bootstrap user seeding", and a changeset cannot call `PasswordEncoder`, so the hash would be a literal in version control (twice, counting the history row).

**Existence check: does any user hold `USER_MANAGER`, in any state.** Not the recipe's `count == 0` (roles are seeded every boot; self-registration makes the user count non-zero), and not "any *enabled*" (a disabled sole admin would cause a username collision).

`APP_ADMIN_PASSWORD` is validated against §5's policy **before** any write; absent or weak **fails startup**. Seeds one `users` row + one `password_history` row, with `require_password_change = true`.

⚠️ **A tombstoned admin username can never be re-seeded** — startup must fail explicitly rather than skip silently.

### First boot is three steps

**Log in → change password → log in again** (the change kills all sessions including the caller's). This must be in the run instructions or it reads as broken software on the first thing a reviewer does.

---

## 10. Error contract ([21](../issues/21-error-contract-shape.md))

**RFC 9457 `ProblemDetail`**, `Content-Type: application/problem+json`, with **`code`** as an extension property (`setProperty` verified in `spring-web-7.0.8`; `@JsonAnyGetter` makes it serialize flat; Boot 4's mixin is gated on spring-web, **not MVC**, which is what lets filters use it).

| Code | Status | Raised by |
|---|---|---|
| `PASSWORD_CHANGE_REQUIRED` | `403` | tier-0 filter |
| `ACCESS_DENIED` | `403` | `accessDeniedHandler` |
| `SELF_ACTION_NOT_ALLOWED` | `403` | `SelfActionGuard` |
| `LAST_USER_MANAGER` | `409` | service layer |
| `CURRENT_PASSWORD_INVALID` | `400` | service layer |

`type` stays `about:blank`. `detail` is human-facing and **must never** carry the enumeration-sensitive distinctions `Std:247` forbids.

**One `ProblemDetailWriter` bean in `common/`**, used by the advice and four off-MVC sites. **The `401` has an empty body** (`HttpStatusEntryPoint` cannot write one, and that is `Std:247`'s most generic response).

**The SPA's axios interceptor must inspect `code` before logging out.** A bare `401` is session death; a `403` carrying `PASSWORD_CHANGE_REQUIRED` or `SELF_ACTION_NOT_ALLOWED` is an in-app error. This refines `Std:437` and is the reason the `code` field exists.

---

## 11. Audit and logging ([12](../issues/12-audit-and-logging-contract.md))

**23 events** — see the ticket for the full table with fields and levels. ~2.5× the PRD's seven.

**Format:** ECS via Spring Boot's **native** `logging.structured.format.*`, plus a **custom encoder** that discharges both `Std:328` (masking — the only available mechanism under this route) and `Std:186` (error nesting). ⚠️ The encoder **creates** the `error` object when no throwable exists rather than stripping the keys, deviating from `Encoder:408` because the silent drop violates `Std:167` — without it, **nine fields across the three most important security events vanish with no failing build**.

**Three appenders:** `CONSOLE`, rolling `APPLICATION` (`Std:275`'s durable buffer), rolling `AUDIT` (`Std:327`'s separate destination). Bound **by logger name, not marker**, so ArchUnit can enforce that only `com.assessment.auth.audit` writes to it.

**Identity fields:** `user.id` is **always the actor**; the target is **`target_user_id`**, an underscore key following the standard's own `error_*` precedent. `event.reason` (closed 21-value vocabulary) is the discriminator, because `event.action`'s enum cannot name seven of our operations.

**`user.name` is never logged.** No log line contains a username or an email address — reading the audit trail requires database access to resolve UUIDs.

**`source.ip` in cleartext on seven event classes only**, from `getRemoteAddr()` verbatim, never `X-Forwarded-For`, never in MDC ([18](../issues/18-client-ip-in-logs.md) fixed six; [12](../issues/12-audit-and-logging-contract.md) added forced-change denial as the seventh).

**`trace.id` / `span.id` on every line** via Micrometer Tracing — mandatory (`Std:321`), with **no exporter or collector needed** (`Trace:14`). `correlation.id` is used **nowhere**.

**Retention:** 90 days minimum, an **integrator** obligation (`Std:287`); the application does rotation only.

**Deliverables:** `docs/adr/` (18's client-IP ADR) and `docs/logging/log-inventory.md` (`Std:283`) — content specified in their tickets, files to be landed during the build.

---

## 12. Test plan and definition of done ([14](../issues/14-test-and-validation-plan.md))

**Full-HTTP `TestRestTemplate`**, not MockMvc, for the security-critical suite — real `Set-Cookie` attributes, the CSRF round-trip and a real `getRemoteAddr()` are exactly what MockMvc smooths over. H2 by default; **the entire** suite re-runs on Testcontainers PostgreSQL at `verify`. Fixed `Clock`; `ListAppender` for log routing assertions.

§5's thirteen categories merged with the PRD's eight; **four recorded vacuous** (account hygiene, bulk operations, admin-generated password composition, multi-instance). **No coverage threshold** — the named required-test list is the gate.

### Per slice, inside `/do-work`

1. `mvn verify` green — unit, integration (H2), Testcontainers PostgreSQL
2. **Five ArchUnit rules** — no `System.out`/`printStackTrace`; only `com.assessment.auth.audit` logs to the audit logger; no `@Async`/`@Scheduled`; no manual HTTP clients; **no `response.sendError`**
3. `semgrep` clean on changed code
4. Frontend: `tsc --noEmit`, lint, Vitest

### Once, before the build is done

5. `im8-review` clean — 05 removed the excuse: logging conformance needs no external platform
6. `dependency-check-maven`, no CVSS ≥ 7 *(needs an NVD API key; first run is slow)*
7. `browser-test` against the twelve PRD stories, **including §9's three-step first boot**
8. All ArchUnit rows green

**`pre-prod-check` is not required** — three of its seven gates re-derive this map's work, and it terminates in a human sign-off rather than a build gate. Recommended before any real deployment; not a condition for calling this build done.

---

## 13. The tests that would otherwise ship silently

Four components have **no standards recipe behind them**, so their tests are the only thing standing in for a missing review: the custom JSON `AuthenticationFilter`, the `authenticationEntryPoint`/`accessDeniedHandler` pair, the `PasswordChangeFilter`'s response writing, and the custom log encoder.

The full list is in [14](../issues/14-test-and-validation-plan.md). The five highest-value:

1. **A `Secure` session cookie round-trips over `http://localhost`** — the map's one unverified claim; everything else depends on it; run it first.
2. **Lockout emits `error.code == 423`** with the full triplet — guards 19's deliberate deviation; a regression here guts the audit trail with no failing build.
3. **A successful login's audit event carries authentication method `password`** — `AuthN:112-118` switches on the authentication *class name*, and 01 chose a custom subclass, so the recipe's resolver silently yields `"unknown"` and breaches `Std:240`.
4. **Unauthenticated `GET /hello` returns `401`, not `403`** — without the explicit entry point, `createDefaultEntryPoint` returns `Http403ForbiddenEntryPoint`.
5. **An unknown username and a wrong password produce equally indistinguishable audit lines** — a correct HTTP response with a leaky log line passes every other test.
