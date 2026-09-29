# PRD deltas

Every place the built application will differ from [`prd/assessment-prd.md`](../../../prd/assessment-prd.md), with the ruling that caused it.

**Read this before reviewing the build against the PRD.** Each row below is a deliberate decision from the wayfinder map, not a defect. The map's authority order (see [`map.md`](../map.md)) is: Appfw standards win wherever both they and the PRD speak; the PRD's *explicit exclusions* hold; standard-only subsystems serving no PRD story are out of scope.

Ticket links point at the full reasoning. Nothing here restates it.

---

## 1. Changed — a PRD criterion now reads differently

### Roles

| PRD | Built | Ruling |
|---|---|---|
| `role` is a Java `enum` of `USER`, `ADMIN` (`prd:135`) | Validated `String`, values `USER` / `USER_MANAGER` | [03](../issues/03-role-model-reconciliation.md) |
| Story 10: "change another user's role between USER and ADMIN" (`prd:95`) | …between `USER` and `USER_MANAGER` | [03](../issues/03-role-model-reconciliation.md) |
| "**Admin** — account holder with role `ADMIN`" (`prd:29`) | Role is named `USER_MANAGER`; a user holds exactly **one** role | [03](../issues/03-role-model-reconciliation.md) |

`ADMIN` → `USER_MANAGER` is a pure rename; no third role exists. Role definitions live in `application.yml` **and** a minimal read-only `roles` table, because `Std:407`/`:415` make persisting them an enforced constraint.

### Paths

| PRD | Built | Ruling |
|---|---|---|
| `GET /api/hello` (Story 5) | `GET /api/v1/hello` | [01](../issues/01-context-topology-and-api-surface.md) |
| `/api/admin/**` prefix (Stories 8–11) | **Gone.** Resource-oriented `/api/v1/users/**`, guarded by role | [01](../issues/01-context-topology-and-api-surface.md) |
| Story 8: `GET /api/admin/users` (`prd:87`) | `GET /api/v1/users` | [01](../issues/01-context-topology-and-api-surface.md) |
| Test: "a `USER` calling any `/api/admin/**` endpoint receives 403" (`prd:160`) | "a `USER` calling `GET /api/v1/users` receives 403" | [01](../issues/01-context-topology-and-api-surface.md) |

The base path is externalized as `api.base-path`. Login is **JSON-only** at `POST /api/v1/auth/login` via a custom filter — `Std:44` requires one and no recipe supplies it, which is why [14](../issues/14-test-and-validation-plan.md) gives it a dedicated test block.

### Lockout

| PRD | Built | Ruling |
|---|---|---|
| Cooldown "e.g. 15 minutes" (`prd:52`) | **20 minutes** | [07](../issues/07-lockout-and-ip-throttling.md) — `Std:367` |
| "N consecutive failed attempts **within a window**" (`prd:52`) | **No window.** A consecutive counter that never decays, reset only on successful login | [07](../issues/07-lockout-and-ip-throttling.md) — the standard says only "consecutive" (`Std:32`, `:366`); `Qs:374` fixes the reset rule |

The no-window reading is **stricter** than the PRD: four failures a day apart still leave the account one failure from lockout.

### Password reset

| PRD | Built | Ruling |
|---|---|---|
| Token expiry "short-lived (e.g. 15–30 min)" (`prd:148`) | **30 minutes** | [11](../issues/11-password-reset-flow.md) — `Std:126` |
| Stub `EmailService` "**logs the link** instead of sending mail" (`prd:20`, `prd:75`) | **Prohibited as specified.** The stub logs an event with **no token and no link**; the reset URL goes to `System.out` in the `dev` profile only, never through SLF4J | [11](../issues/11-password-reset-flow.md) — `Std:319` forbids reset-token plaintext in logs, absolutely; `Std:515` makes it a test |

The `EmailService` change is the sharpest PRD conflict on the map. A reset link in a log line is a live credential, and [19](../issues/19-log-format-and-custom-encoder.md)'s pipeline would carry it into the durable file appender and the separately-classified audit destination.

### Session and transport

| PRD | Built | Ruling |
|---|---|---|
| "`Secure` (**prod**)" cookie attribute (`prd:117`) | `Secure=true` in **every** profile, including local dev over HTTP | [09](../issues/09-http-security-csrf-cors-headers.md) — `Std:349`; browsers treat `localhost` as a trustworthy origin |
| "`SameSite` cookie attributes" (unspecified value, `prd:117`) | `SameSite=Lax`, fixed | [09](../issues/09-http-security-csrf-cors-headers.md) — `Std:350` |

⚠️ **The `Secure`-on-localhost claim is the one load-bearing assertion on this map that was not verified.** [14](../issues/14-test-and-validation-plan.md) makes it the **first** test to run. If it fails, the fix is a `dev`-only `secure: false` — one line.

### Audit

| PRD | Built | Ruling |
|---|---|---|
| Nine audit events (`prd:122`) | **27 events** | [12](../issues/12-audit-and-logging-contract.md), [05](../issues/05-logging-standards-applicability.md) — `Std:239` binds regardless of recipe coverage |
| "role change/enable/disable/delete (**actor + target**)" (`prd:122`) | Actor is `user.id`; target is **`target_user_id`**, a non-schema underscore key | [12](../issues/12-audit-and-logging-contract.md) — the schema defines no target field at all |
| "Never log passwords" (`prd:122`) | **No log line contains a password, a password hash, a reset token, a session id, a username, or an email address** | [12](../issues/12-audit-and-logging-contract.md) — `Std:331` bans raw usernames/emails; the `LS:105` policy hatch is deliberately shut |

Reading the audit trail therefore requires database access to resolve UUIDs. That is the standard's privacy posture and it has a real operational cost.

---

## 2. Added — required by the standards, absent from the PRD

| Addition | Ruling |
|---|---|
| **Password history** — 3 previous + current = 4 blocked values | [04](../issues/04-password-policy-and-history.md) — `Std:355` |
| **`requirePasswordChange` flag + tier-0 filter** blocking all but four endpoints | [04](../issues/04-password-policy-and-history.md), [08](../issues/08-authorization-matrix.md) |
| **Self-service change-password endpoint** `PATCH /api/v1/currentUser/changePassword` | [04](../issues/04-password-policy-and-history.md) |
| **Self-read endpoint** `GET /api/v1/currentUser` at `Q23a` Moderate visibility | [10](../issues/10-account-lifecycle-and-delete-semantics.md) |
| **`GET /api/v1/users/{userId}`** — admin user-detail | [08](../issues/08-authorization-matrix.md) — `Q:498`, `Std:414` |
| **`GET /api/v1/roles`** | [03](../issues/03-role-model-reconciliation.md) |
| **`PATCH /api/v1/users/{userId}/unlock`** — admin unlock | [07](../issues/07-lockout-and-ip-throttling.md) — `Std:367`, `:451` |
| **`PATCH /api/v1/users/{userId}/resetPassword`** — admin-initiated reset, issuing a **token** | [11](../issues/11-password-reset-flow.md) — `Std:401` is an enforced constraint |
| **`GET /api/v1/csrf`** — CSRF bootstrap, mandatory before *any* state-changing call including login and registration | [09](../issues/09-http-security-csrf-cors-headers.md) — `Std:238` |
| **A third counter**: per-account rate limiting, 10/min | [07](../issues/07-lockout-and-ip-throttling.md) — `Qs:400`, `Std:378` |
| **`429` + `Retry-After`** on all three limiters | [07](../issues/07-lockout-and-ip-throttling.md) — `Std:260` ("must include") |
| **Rate limiting on both reset endpoints**, IP-keyed | [11](../issues/11-password-reset-flow.md) — `Std:113`, `:379` |
| **Timing equalisation** on login and reset-request | [11](../issues/11-password-reset-flow.md) — `Std:247` says "response timing" |
| **Deleted-user tombstones**, retained indefinitely | [10](../issues/10-account-lifecycle-and-delete-semantics.md) — `Std:408` |
| **Session limits**: idle 15m, absolute 8h, max 1 concurrent | [13](../issues/13-session-policy.md) — `Std:343-345`, `:398`, `:409` |
| **`Clear-Site-Data` on logout** | [13](../issues/13-session-policy.md) — `Std:391` |
| **`sendPasswordChangedEmail(...)`** stub | [11](../issues/11-password-reset-flow.md) — `Std:65`, `:71` |
| **Re-enabling a disabled account sets `requirePasswordChange`** | [10](../issues/10-account-lifecycle-and-delete-semantics.md) — `Std:130` |
| **Micrometer Tracing + Actuator** — `trace.id`/`span.id` on every line, no exporter | [05](../issues/05-logging-standards-applicability.md) — `Std_Logging:321` |
| **Custom structured log encoder** — the only mechanism discharging `Std_Logging:326`'s masking constraint | [19](../issues/19-log-format-and-custom-encoder.md) |
| **Three log appenders** — console, application, audit | [19](../issues/19-log-format-and-custom-encoder.md) — `Std:275` and `:327` are distinct clauses |
| **RFC 9457 `ProblemDetail`** error envelope with a `code` extension | [21](../issues/21-error-contract-shape.md) |
| **Five ArchUnit rules** | [15](../issues/15-tech-baseline-and-module-structure.md), [13](../issues/13-session-policy.md) |
| **`docs/adr/`** (18's client-IP ADR) and **`docs/logging/log-inventory.md`** (`Std_Logging:284`) | [18](../issues/18-client-ip-in-logs.md), [12](../issues/12-audit-and-logging-contract.md) |

---

## 3. Removed — in the PRD's shape, ruled out

| Removal | Ruling |
|---|---|
| **Bulk/batch user operations** — `PATCH /users/batchResetPassword` | [10](../issues/10-account-lifecycle-and-delete-semantics.md) — no PRD story, and it returns N plaintext passwords in one body |
| **Self-service profile updates** | [10](../issues/10-account-lifecycle-and-delete-semantics.md) — `Q:584` warns against email change where email drives reset, which is exactly this app |
| **System-generated passwords** (`Std:357-361`) | [11](../issues/11-password-reset-flow.md) — under the token model no password is ever generated, so the clause has no subject |
| **`@Scheduled` anything**, including the 30-day forced-change grace period | [04](../issues/04-password-policy-and-history.md), [15](../issues/15-tech-baseline-and-module-structure.md) — hygiene jobs are out of scope; an ArchUnit rule enforces it |
| **`X-Forwarded-For` trust** | [18](../issues/18-client-ip-in-logs.md) — single-instance, no gateway, so it is attacker-controlled |

The PRD's own exclusions are unchanged and still hold: JWT, MFA, real SMTP, containerization/CI-CD/hosting, local HTTPS, fine-grained per-resource authz.

**One reading worth flagging:** the containerization exclusion is read as covering **deployment packaging, not test infrastructure**, so [14](../issues/14-test-and-validation-plan.md)'s Testcontainers PostgreSQL run is in scope. That is a judgement call, recorded as one in [02](../issues/02-persistence-and-session-backend.md).

---

## 4. Data model

The PRD's `users` table (`prd:127-139`) plus:

| Change | Ruling |
|---|---|
| `role` — `enum` → validated `String`, FK-ish to `roles` | [03](../issues/03-role-model-reconciliation.md) |
| `+ require_password_change` | [04](../issues/04-password-policy-and-history.md) |
| `+ last_login_at` | [10](../issues/10-account-lifecycle-and-delete-semantics.md) — `Q23a` Moderate |
| `+ disabled_at` | [10](../issues/10-account-lifecycle-and-delete-semantics.md) |
| PKs are `UUID`; every timestamp is `TIMESTAMP WITH TIME ZONE`, UTC, read through an injectable `Clock` | [02](../issues/02-persistence-and-session-backend.md) |
| **No `account_non_locked` boolean** — `locked_until` alone, because two sources of truth is a silent auth bypass | [07](../issues/07-lockout-and-ip-throttling.md) |

New tables: **`password_history`** (04, `ON DELETE CASCADE`), **`deleted_users`** (10), **`roles`** (03, read-only), **`SPRING_SESSION`** + **`SPRING_SESSION_ATTRIBUTES`** (02, from Spring Session's packaged per-vendor DDL via Liquibase `sqlFile`).

`password_reset_tokens` is exactly the PRD's (`prd:141-149`). Schema is owned by **Liquibase** with `ddl-auto: validate`; one versioned changelog file per logical change.

---

## 5. Two documented limitations

Not defects, and not testable as passing behaviour — they must be read as accepted constraints:

1. **IP-throttle and per-account rate-limit counters are in-memory.** Not restart-durable, not multi-instance-safe. Honest under the single-instance topology [01](../issues/01-context-topology-and-api-surface.md) fixed. After a restart the audit log shows throttle rejections with no preceding accumulation — [07](../issues/07-lockout-and-ip-throttling.md) flags this so it is not read as a bug. Account **lockout** state *is* durable (`Std:451`).
2. **Single-instance only.** `Std:453`'s distributed-deployment test has no subject.

Three expiries to watch, all in [15](../issues/15-tech-baseline-and-module-structure.md)'s deployment assumptions:

- **Introducing any proxy or load balancer** silently collapses both the IP throttle and the audit trail's `source.ip` to one bucket, because `X-Forwarded-For` is rejected outright ([18](../issues/18-client-ip-in-logs.md)).
- **Behind a corporate NAT**, 50 requests/minute is the *whole office's* budget ([07](../issues/07-lockout-and-ip-throttling.md)) — `Qs:407` advises against IP limiting for internal apps for exactly this reason, and it ships anyway because PRD Story 3 requires it.
- **Splitting the SPA and API across different registrable domains** breaks `SameSite=Lax` and therefore session auth ([09](../issues/09-http-security-csrf-cors-headers.md)).
