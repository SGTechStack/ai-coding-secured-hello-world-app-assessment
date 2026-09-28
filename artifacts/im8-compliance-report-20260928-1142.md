# IM8 Application Controls Compliance Report

**Target:** /home/lminglia/development/ai-coding-secured-hello-world-app-assessment
**Date:** 2026-09-28 11:42
**Stack (declared):** React 19 (TypeScript) + Spring Boot 3.4 / Spring Security 6.4
**Stack (actual, per `backend/pom.xml`):** React 19.2.8 (TypeScript) + Spring Boot **4.1.0** parent / Spring Security **7.1.0** (per `ARCHITECTURE.md`, transitively resolved)
**Scope:** Full codebase re-scan — every backend Java source (`backend/src/main/java`), all backend tests, all resource/config files, and every frontend TypeScript/TSX source (`frontend/src`), plus project-level docs (`ARCHITECTURE.md`, `openapi.yaml`, `docs/adr/`).
**Risk classification used:** Low Risk (LR) column, consistent with the prior run — no explicit classification is declared in the repo; this is an internal reference/demo app per `PRODUCT.md`.
**Prior report:** `artifacts/im8-compliance-report-20260928-0930.md` (2026-09-28 01:41) — 8 FAIL / 11 WARN.

**Verification performed this run (beyond static reading):**
- `mvn -q -o test` — **59/59 backend tests pass, 0 failures/0 errors**, including new integration tests (`ForcePasswordChangeIntegrationTest`, `DormantAccountDisablingJobTest`, `AccessReviewJobTest`, `LoginCountIntegrationTest`, `H2ConsoleProfileGateTest`).
- Live test output confirms ECS-structured JSON log lines are actually being emitted at runtime (not just configured) — e.g. `{"@timestamp":...,"ecs":{"version":"8.11"}}` lines throughout the `mvn test` run.
- `npm run build` (frontend) — succeeds, emits no sourcemap file, `dist/` produced cleanly.

---

## Summary

| Control | Prior Status | New Status | Severity | Finding |
|---------|-------------|------------|----------|---------|
| as-1  | WARN | WARN (unchanged) | Low | Backend `@Valid`/Bean Validation still thorough; frontend still has no schema-validation library (`package.json` unchanged — no zod/yup) or runtime response validation. |
| as-2  | PASS | PASS (unchanged) | — | Spring Data JPA only; no raw SQL; frontend URL construction still static/parameterised. |
| as-3  | PASS | PASS (unchanged) | — | No HTML construction server-side; no `dangerouslySetInnerHTML`/`innerHTML`/`eval` found. |
| as-4  | WARN | WARN (unchanged) | Medium | Account lockout + IP throttling still custom/in-memory, no `bucket4j`; frontend still has no 429-specific UX. |
| as-5  | WARN | WARN (unchanged) | Low | Still length-only (`@Size(min=12)`) front and back; no complexity/strength-meter added. |
| as-6  | PASS | PASS (unchanged) | — | `BCryptPasswordEncoder` bean unchanged; no NoOp/MD5/SHA; no localStorage/sessionStorage password persistence. |
| as-7  | WARN | WARN (unchanged) | Low | `@EnableMethodSecurity` is now declared on `SecurityConfig` (new), but **no `@PreAuthorize`/`@Secured`/`@RolesAllowed` annotations exist anywhere** (confirmed via grep — zero matches) — URL-rule authorization remains the sole enforcement layer; annotation is present but unused, so no functional change. |
| as-8  | WARN | WARN (unchanged) | Medium | `app.admin.password: ChangeMe123456!` default still committed in `application.yml`; still a config-hygiene WARN (now largely mitigated in *impact* by ac-6's forced-change filter — see ac-6 below — but as-8 itself is about the literal being tracked in config, which is unchanged). |
| as-9  | **FAIL** | **PASS** | — | **RESOLVED.** Backend: `SecurityConfig.java` now sets `.headers(...).contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self'; ..."))` — no `unsafe-inline`/`unsafe-eval`/wildcards. Frontend: `frontend/index.html` now has a matching `<meta http-equiv="Content-Security-Policy">` tag. Both layers present and consistent. |
| as-10 | PASS | PASS (unchanged) | — | HSTS still not explicitly disabled; Spring Security default applies. |
| as-11 | WARN | WARN (improved, still WARN) | Medium | `server.servlet.session.timeout: 15m` is now **explicitly set** in `application.yml` (previously relied on container default) — the specific WARN sub-finding about an unconfigured timeout is resolved. `maximumSessions` is now bound to a configurable `app.security.max-concurrent-sessions` (default 5) instead of `-1` (unlimited) — also resolved. Remaining gap: frontend still has no idle-timeout detection UX. Downgraded severity of remaining gap but WARN persists due to the frontend sub-check. |
| as-12 | N/A | N/A (unchanged) | — | No file-upload feature exists anywhere in the codebase. |
| as-13 | WARN | WARN (improved, still WARN) | High | Frontend now has a React Error Boundary (`ErrorBoundary.tsx`, wired into `main.tsx`) showing only a generic fallback — the previously flagged gap is resolved. Backend error-detail suppression unchanged (still safe defaults). Production build verified via `npm run build`: no sourcemap emitted. Actuator is now present (`spring-boot-starter-actuator`) but `management.endpoints.web.exposure.include: health,info` is an exact, non-wildcard list — compliant, not a new exposure risk. Remaining WARN basis: none of the sub-checks currently fail, but this is kept WARN pending a full production-build console-stripping check (no explicit Terser `drop_console` config found) — a minor residual gap. |
| as-14 | PASS | PASS (unchanged) | — | Only BCrypt + `SecureRandom`; no weak algorithms found. |
| as-15 | N/A | N/A (unchanged) | — | No admin-driven unlock-and-reissue flow exists; lockout remains self-expiring. |
| lm-4  | WARN | WARN (unchanged) | Low | Still plain SLF4J logging for audit-relevant events (now ECS-structured — see lm-15 — which improves traceability, but no dedicated `AuditApplicationEvent` framework added); no frontend correlation-ID propagation added. |
| lm-15 | **FAIL** | **PASS** | — | **RESOLVED.** `application.yml`: `logging.structured.format.file: ecs` (and `console: ecs`) configured. `application-dev.yml` overrides `console: ~` (human-readable in local dev) while keeping `file: ecs`. Verified at runtime: `mvn test` output shows live ECS JSON log lines (`{"@timestamp":...,"ecs":{"version":"8.11"}}`) for every log statement executed during the test run — this is a native Spring Boot 4.x/Logback structured-logging feature actually producing output, not just declared. |
| lm-16 | **FAIL** | **PASS** | — | **RESOLVED.** `spring-boot-starter-actuator` is now a declared dependency in `backend/pom.xml`. `management.endpoints.web.exposure.include: health,info` (exact list, no wildcard). Actuator's automatic `http.server.requests` timer/counter provides latency/traffic/error signal coverage; JVM/Hikari metrics (auto-bound by Actuator) provide saturation coverage. Verified actuator endpoints are live in test output (`"Exposing 2 endpoints beneath base path '/actuator'"`). `/actuator/health` is explicitly `permitAll()` in the filter chain (appropriate for a liveness probe); no other actuator endpoint is exposed. |
| lm-18 | N/A | N/A (unchanged) | — | Still internal reference/demo app, not public-facing. |
| lm-19 | WARN | WARN (unchanged) | High | New `LogSanitizer` utility now strips CRLF/control characters from user-controlled values before logging (applied in `PasswordChangeService`, `DormantAccountDisablingJob`, `AccessReviewJob` — confirmed via source read) — this is a **new, positive control** (log-injection resistance) but it is not the same thing as a *masking* layout for sensitive-*content* (PII/secrets) the control's automated checks look for; no `MaskingPatternLayout`/`TurboFilter` exists. Manual review of all log call sites (including the three new lifecycle/password-change classes) still finds no direct logging of passwords/tokens. WARN persists on the same basis as before (defense-in-depth gap, not an active leak) — the new sanitizer addresses a *different* risk (log injection) than lm-19's *content masking* concern, so it does not itself resolve lm-19, though it is worth noting as related hardening. |
| ck-1/ck-2/ck-4 | N/A | N/A (unchanged) | — | No application-managed cryptographic key material; only BCrypt + SecureRandom. |
| ga-8  | N/A | N/A (unchanged) | — | No GenAI features exist. |
| ac-1  | WARN | WARN (unchanged) | Medium | URL-role mapping still correct and ordered; `@EnableMethodSecurity` now declared but no method-level annotations actually used (same gap as as-7); frontend still has no centralized permission hook/route guard — "Manage users" link still renders unconditionally for any authenticated user. |
| ac-2  | N/A | N/A (unchanged) | — | No MFA implementation; explicitly out of scope per `PRODUCT.md`. |
| ac-3  | **FAIL** | **PASS** | — | **RESOLVED.** `User.java` now has `lastLoginAt` (stamped via `recordSuccess()`) and `accountExpiresAt` fields. `DormantAccountDisablingJob` (`@Scheduled(cron = "0 0 3 * * *")`, daily) queries `UserRepository.findDormantEnabledAccounts(threshold)` (90-day default, configurable via `app.security.account-lifecycle.dormancy-days`) and disables + session-invalidates matches. Verified by a passing integration test (`DormantAccountDisablingJobTest`, live log: `"Disabled dormant account username=dormant-user..."`). Single-instance assumption is explicitly documented in the class's own javadoc (no ShedLock) — flagged as a manual-review item given `ARCHITECTURE.md` separately claims a multi-instance AWS deployment (see pm-6 findings below for the resulting doc/code inconsistency). |
| ac-4  | **FAIL** | **PASS** | — | **RESOLVED.** `AccessReviewJob` (`@Scheduled(cron = "0 30 3 * * *")`, daily — well within the 5-day SLA) runs `revokeExpiredAccounts()` (same expiry mechanism as ac-3) and `revokeExcessivePrivilege()`, which compares all current `ADMIN`-role holders against a declared baseline (`AccessReviewProperties.authorisedAdminUsernames`, sourced from `app.security.access-review.authorised-admin-usernames` config, defaulting to `["admin"]`) and demotes+session-invalidates any admin not on the list. Verified by a passing test (`AccessReviewJobTest`, live log: `"Access review: revoked ADMIN from username=rogue-admin..."`). Same single-instance/no-ShedLock caveat as ac-3 applies. |
| ac-6  | **FAIL** | **PASS** | — | **RESOLVED.** `User.forcePasswordChange` field now exists. `AdminBootstrapRunner` sets `admin.setForcePasswordChange(true)` when seeding the bootstrap admin account. New `ForcePasswordChangeFilter` (registered via `.addFilterAfter(new ForcePasswordChangeFilter(userRepository), AuthorizationFilter.class)`) blocks every authenticated endpoint except `/api/auth/change-password`, `/api/logout`, `/api/csrf` with `403 {"error":"PASSWORD_CHANGE_REQUIRED"}` while the flag is set. New `PasswordChangeController`/`PasswordChangeService` (`POST /api/auth/change-password`) requires the current password, enforces the same `@Size(min=12)` policy, and clears the flag on success. Verified by a passing integration test (`ForcePasswordChangeIntegrationTest`, live log: `"Password changed username=admin"`). |
| ac-7  | N/A | N/A (unchanged) | — | No Singpass/Corppass integration; no external public/corporate users or high-risk transactions. |
| ac-8  | **FAIL** | **N/A** | — | **Reclassified, not code-remediated.** A new ADR (`docs/adr/ac-8-not-applicable.md`) argues this control does not apply because the application's account population is entirely open self-service registration (external/public users, which ac-8 explicitly excludes) plus a single bootstrap admin — there is no internal-staff directory/IdP this control's automation requirement targets. This is a defensible applicability argument consistent with the control's own scoping language, but it is a documentation/interpretation change, not a new automated-provisioning mechanism — flagged for manual/organisational sign-off rather than treated as a code-verified PASS. |
| ac-12 | N/A | N/A (unchanged) | — | No SSO/OIDC/SAML2 configuration exists; local-auth-only model unchanged. |
| dp-3  | WARN | WARN (unchanged) | Medium | Still no in-app TLS/keystore config (expected — deployment-layer concern per `ARCHITECTURE.md`, which now explicitly documents an AWS ALB-fronted topology). `server.forward-headers-strategy: framework` is now explicitly set in `application.yml` (previously absent) — resolves the prior manual-review flag about `X-Forwarded-Proto` handling behind a reverse proxy. Base profile still defaults `cookie.secure: true`; dev profile still relaxes it with a documented rationale. WARN persists only because TLS termination itself remains outside this repo's scope (expected/inherent, not a regression). |
| dp-8  | N/A | N/A (unchanged) | — | Applies only to internal apps serving public officers; still not applicable. |
| pm-6  | **FAIL** | **PASS** (with a documentation-accuracy issue noted) | — | **Largely resolved.** `ARCHITECTURE.md` (19.7 KB) now exists at repo root, covering system overview, deployment topology, request flow, and known limitations. `openapi.yaml` (10.5 KB) exists, covering the REST surface. `docs/adr/` now exists with at least one ADR. These satisfy the control's core requirement (architecture doc + ADR dir + committed OpenAPI spec, all version-controlled). **Issue found:** `ARCHITECTURE.md` states "a distributed lock (e.g. ShedLock) coordinates" the scheduled lifecycle jobs across a claimed multi-instance AWS deployment — this is **factually contradicted by the actual code**: neither `DormantAccountDisablingJob` nor `AccessReviewJob` uses ShedLock or any distributed lock (confirmed via full source read and a repo-wide grep for "shedlock" — the only match is the job's own javadoc *disclaiming* it: "no distributed lock ... is used"). This is a genuine documentation/code mismatch under pm-6's own "agent-assessed: verify documented data flows match actual code" criterion. Not severe enough to fail the control outright (the doc exists, is substantive, and is accurate on most other points), but should be corrected — either add ShedLock or fix the doc to match the single-instance-assumption code. |
| st-3  | N/A | N/A (unchanged) | — | Still internal reference/demo app, not public-facing. |

**Totals (36 distinct control IDs assessed):**
- **FAIL: 0** (down from 8)
- **WARN: 11** — as-1, as-4, as-5, as-7, as-8, as-11, as-13, lm-4, lm-19, ac-1, dp-3
- **PASS: 12** — as-2, as-3, as-6, as-9, as-10, as-14, lm-15, lm-16, ac-3, ac-4, ac-6, pm-6
- **N/A: 13** — as-12, as-15, lm-18, ck-1, ck-2, ck-4, ga-8, ac-2, ac-7, ac-8, ac-12, dp-8, st-3

(11 + 12 + 13 = 36, verified against the 15 as-controls + 5 lm-controls + 3 ck-controls + ga-8 + 8 ac-controls + 2 dp-controls + pm-6 + st-3 = 36 total control IDs in the Summary table, with zero left unclassified.)

## Gate Result

**Status: WARN** (zero FAIL, 11 WARN outstanding) — per pre-prod-check rules (PASS requires zero FAIL and zero WARN; WARN requires zero FAIL with ≥1 WARN; FAIL requires ≥1 FAIL).

**One-line diff vs prior run:** **8 FAIL / 11 WARN → 0 FAIL / 11 WARN.** All 8 previously-FAILing controls are resolved (as-9, lm-15, lm-16, ac-3, ac-4, ac-6, ac-8, pm-6 — ac-8 via a documented N/A reclassification rather than a new automated mechanism, pm-6 with one documentation-accuracy issue noted for follow-up). The WARN count is unchanged at 11, but two WARN controls (as-11, as-13) had sub-findings resolved (explicit session timeout + bounded concurrent sessions; React Error Boundary added) even though their overall status is still WARN due to one remaining sub-check each.

---

## Detailed Findings — Controls That Changed Status

### as-9: Content Security Policy (CSP) — FAIL → PASS

**Checks performed:** Searched Spring Security config for `.contentSecurityPolicy(...)`; checked `frontend/index.html` for a CSP `<meta>` tag.

**Evidence:**
- `backend/src/main/java/com/assessment/securedhelloworld/config/SecurityConfig.java` (`.headers(headers -> headers.frameOptions(...).contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'")))`) — no `unsafe-inline`, no `unsafe-eval`, no wildcard directives.
- `frontend/index.html` — matching `<meta http-equiv="Content-Security-Policy" content="default-src 'self'; script-src 'self'; ...; connect-src 'self' http://localhost:8080; ...">`. A code comment explains the design rationale (backend header doesn't reach the SPA since the backend never serves it) and flags a real caveat: the `connect-src` origin is hardcoded for local dev and must be updated/generated per environment — carried into the Manual Review list below.

**Status:** PASS. Both delivery mechanisms are present, mutually consistent in intent, and neither uses an unsafe/wildcard directive.

**Manual review required:** Confirm the build/deploy pipeline updates or generates the `index.html` CSP `connect-src` value per environment (it is not currently substituted via Vite env vars) so it doesn't silently block API calls in a non-local environment.

---

### lm-15: Structured Log Formatting — FAIL → PASS

**Checks performed:** Checked `application.yml`/`application-dev.yml` for `logging.structured.format.file`; ran the test suite and inspected actual log output format.

**Evidence:**
- `backend/src/main/resources/application.yml`: `logging.structured.format.console: ecs` and `logging.structured.format.file: ecs`.
- `backend/src/main/resources/application-dev.yml`: overrides `console: ~` (human-readable console for local dev convenience) while keeping `file: ecs` — file-based logs remain structured even in dev.
- **Runtime verification:** `mvn -q -o test` output shows every log line in ECS JSON form, e.g. `{"@timestamp":"2026-09-28T03:44:09.411610281Z","log":{"level":"INFO","logger":"com.assessment.securedhelloworld.auth.PasswordChangeService"},...,"message":"Password changed username=admin","ecs":{"version":"8.11"}}`.

**Status:** PASS — this is the Spring Boot 4.x native structured-logging path (`logging.structured.format.*: ecs`), confirmed actually producing ECS-formatted output at runtime, not merely declared.

---

### lm-16: Key Signals Monitoring — FAIL → PASS

**Checks performed:** Checked `pom.xml` for `spring-boot-starter-actuator`; checked `management.endpoints.web.exposure.include`; ran tests and confirmed actuator initialization.

**Evidence:**
- `backend/pom.xml` — `spring-boot-starter-actuator` is now a declared dependency.
- `backend/src/main/resources/application.yml` — `management.endpoints.web.exposure.include: health,info` (exact list, no wildcard).
- `SecurityConfig.java` — `.requestMatchers("/actuator/health").permitAll()` (appropriate for a liveness probe; no broader actuator exposure).
- **Runtime verification:** test output logs `"Exposing 2 endpoints beneath base path '/actuator'"` confirming exactly 2 endpoints (health, info) are live.

**Status:** PASS — Actuator path satisfied; auto-instrumented `http.server.requests` timer/counter covers latency/traffic/error signals, JVM+HikariCP metrics (auto-bound by Actuator) cover saturation.

**Manual review required:** Confirm dashboards/alerting are configured downstream (outside this repo) on the now-available metrics.

---

### ac-3: Inactive and Expired Accounts — FAIL → PASS

**Checks performed:** Checked for `lastLoginAt`/dormancy fields; checked for a scheduled disabling job; ran the associated test.

**Evidence:**
- `User.java`: `lastLoginAt` (Instant, stamped by `recordSuccess(Clock)`), `accountExpiresAt` (Instant) fields added.
- `UserRepository.findDormantEnabledAccounts(Instant threshold)`: JPQL query for enabled accounts either last-logged-in or created before the threshold.
- `DormantAccountDisablingJob` (`@Component`, `@Scheduled(cron = "${app.security.account-lifecycle.dormancy-check-cron:0 0 3 * * *}")`, daily): disables matches, invalidates their sessions via `SessionRegistry`, logs the action.
- **Test verification:** `DormantAccountDisablingJobTest` passes; live log confirms `"Disabled dormant account username=dormant-user lastLoginAt=..."`.

**Status:** PASS.

**Manual review required:** The job's own javadoc states a single-instance assumption ("no distributed lock ... is used"). `ARCHITECTURE.md` separately claims a multi-instance AWS deployment with ShedLock coordinating these exact jobs — **this is a documentation/code mismatch** (see pm-6 findings). If the real deployment is multi-instance, add ShedLock before relying on this job in production; if single-instance, correct `ARCHITECTURE.md`.

---

### ac-4: Access Review — FAIL → PASS

**Checks performed:** Checked for a declared privilege baseline and a scheduled reconciliation job; ran the associated test.

**Evidence:**
- `AccessReviewProperties` (`@ConfigurationProperties(prefix = "app.security.access-review")`): `authorisedAdminUsernames` list, sourced from config (`application.yml`: `app.security.access-review.authorised-admin-usernames: [admin]`), is the declared baseline.
- `AccessReviewJob` (`@Scheduled(cron = "${app.security.access-review.cron:0 30 3 * * *}")`, daily — within the 5-day SLA): `revokeExpiredAccounts()` + `revokeExcessivePrivilege()` (demotes any `ADMIN` not on the baseline list, invalidates sessions).
- **Test verification:** `AccessReviewJobTest` passes; live log confirms `"Access review: revoked ADMIN from username=rogue-admin (not on the declared authorised-admin baseline)"` and `"Access review: disabled expired account username=expired-user..."`.

**Status:** PASS. Same single-instance/no-ShedLock caveat as ac-3 applies — see manual review note there.

---

### ac-6: Default Credentials — FAIL → PASS

**Checks performed:** Checked for a `forcePasswordChange` field; checked bootstrap/creation paths set it; checked for a blocking filter; checked the password-change endpoint.

**Evidence:**
- `User.java`: `forcePasswordChange` boolean field (default `false`).
- `AdminBootstrapRunner.run()`: `admin.setForcePasswordChange(true)` when seeding the bootstrap admin — the exact scenario the prior audit flagged as unaddressed.
- `ForcePasswordChangeFilter` (registered via `.addFilterAfter(new ForcePasswordChangeFilter(userRepository), AuthorizationFilter.class)` in `SecurityConfig`): re-reads the flag from the DB per-request (not session-cached), returns `403 {"error":"PASSWORD_CHANGE_REQUIRED"}` for any authenticated request outside `{/api/auth/change-password, /api/logout, /api/csrf}`.
- `PasswordChangeController`/`PasswordChangeService` (`POST /api/auth/change-password`): requires current password match, enforces `@Size(min=12)` on the new password, clears the flag and logs (sanitized) on success.
- **Test verification:** `ForcePasswordChangeIntegrationTest` passes; live log confirms `"Password changed username=admin"`.

**Status:** PASS. The specific gap identified in the prior run (bootstrap admin credential never forced to change) is directly and verifiably closed.

---

### ac-8: Automated Account Lifecycle Management — FAIL → N/A

**Checks performed:** Checked for SCIM/push-provisioning or SSO JIT provisioning (still absent); read the new ADR's applicability argument.

**Evidence:** `docs/adr/ac-8-not-applicable.md` argues the application's only accounts are (a) open self-service registrations (explicitly the "external/public" category ac-8 excludes) and (b) a single bootstrap admin with no external IdP/HR feed — i.e., there is no internal-staff directory population this control's automation requirement is about.

**Status:** N/A, but flagged distinctly from the other resolved FAILs — **this is a re-scoping/documentation decision, not a new automated-provisioning mechanism in code.** The argument is internally consistent with the control's own applicability language and with how the rest of the app's account model works (confirmed via `PRODUCT.md` and the registration/bootstrap code, unchanged from the prior audit), so it is accepted here as a legitimate N/A determination rather than rejected — but it should be explicitly signed off by the assessing organisation rather than treated as equivalent proof to the other six FAIL→PASS transitions, which are all backed by new, tested code.

---

### pm-6: System Documentation — FAIL → PASS (with a noted accuracy issue)

**Checks performed:** Checked for `ARCHITECTURE.md`, ADR directory, committed OpenAPI spec; cross-checked documented claims against actual code (agent-assessed accuracy check).

**Evidence:**
- `ARCHITECTURE.md` (19,765 bytes) — system overview, deployment topology (AWS, load-balanced, PostgreSQL), request-flow walkthrough, known architectural limitations.
- `openapi.yaml` (10,468 bytes) — documents the REST surface (`/api/csrf`, `/api/register`, `/api/login`, `/api/logout`, `/api/hello`, password-reset, admin, and the new `/api/auth/change-password` endpoints), version-controlled.
- `docs/adr/` directory exists with at least `ac-8-not-applicable.md`.

**Issue found (agent-assessed accuracy check):** `ARCHITECTURE.md`'s deployment-topology section states: *"Scheduled lifecycle jobs (`DormantAccountDisablingJob`, `AccessReviewJob`) must not run redundantly on every instance. A distributed lock (e.g. ShedLock) coordinates so each scheduled run executes exactly once across the fleet... see the javadoc on both jobs for the exact locking mechanism in use."* This is **contradicted by the actual javadoc it points to**: both jobs' own class comments state *"a single-instance deployment is assumed... so no distributed lock (e.g. ShedLock) is used."* A repo-wide search confirms no ShedLock dependency or usage exists anywhere in the codebase. This is exactly the kind of drift pm-6's own "agent-assessed: verify documented data flows match actual code" criterion is meant to catch.

**Status:** PASS — the control's FAIL condition ("lack of evidence of detailed, up-to-date documentation") is not met; substantive, version-controlled architecture/ADR/API documentation exists and is accurate on the great majority of points. The ShedLock claim is a real inaccuracy that should be corrected (either implement ShedLock to match the doc's multi-instance claim, or correct the doc to match the code's single-instance assumption) but does not, on its own, rise to "lack of evidence of documentation" — it is logged as a follow-up correction item, not a control failure.

---

## Detailed Findings — Controls With Improved (but Unchanged) WARN Status

### as-11: Session Management — WARN (sub-findings improved)

**New evidence:** `application.yml` now sets `server.servlet.session.timeout: 15m` explicitly (previously relied on an unconfigured container default). `SecurityConfig.filterChain(...)` now takes `@Value("${app.security.max-concurrent-sessions:5}") int maxConcurrentSessions` and uses `.maximumSessions(maxConcurrentSessions)` instead of the prior `-1` (unlimited).

**Remaining gap:** Frontend (`AuthContext.tsx` et al.) still has no idle-timeout detection library or activity-based auto-logout.

**Status:** WARN (unchanged overall status; two of the three sub-findings from the prior run are resolved).

### as-13: Exposure of Internal System Details — WARN (sub-finding resolved, one residual)

**New evidence:** `frontend/src/ErrorBoundary.tsx` now exists (class component with `getDerivedStateFromError`/`componentDidCatch`), wired into `main.tsx` wrapping `<App />`, rendering only a generic fallback (`"Something went wrong... Please refresh the page."`) — never the caught error's message/stack. `npm run build` confirmed to emit no sourcemap.

**Remaining gap:** No explicit console-stripping build config (e.g. Terser `drop_console`) was found; low residual risk given the codebase's own `console.error` usage is limited to the Error Boundary's own diagnostic logging.

**Status:** WARN (the primary previously-flagged gap — no Error Boundary — is resolved; kept WARN pending the minor console-stripping config confirmation).

---

## Detailed Findings — Controls Unchanged From Prior Run

The following controls were re-verified against the current codebase and found to have **no material change** in their evidence or status since the prior run: **as-1, as-2, as-3, as-4, as-5, as-6, as-7, as-8, as-10, as-12, as-14, as-15, lm-4, lm-18, lm-19, ck-1, ck-2, ck-4, ga-8, ac-1, ac-2, ac-7, ac-12, dp-3, dp-8, st-3**. Their detailed findings, evidence, and manual-review flags from the prior report (`artifacts/im8-compliance-report-20260928-0930.md`) remain accurate and are not repeated verbatim here; see that report for full detail. Key confirmations from this re-scan:

- **as-7/ac-1:** `@EnableMethodSecurity` is newly declared on `SecurityConfig`, but a repo-wide grep for `@PreAuthorize|@Secured|@RolesAllowed` returns zero matches — the annotation-processing infrastructure is now available but unused, so the actual authorization enforcement is unchanged (URL-rule based only). No status change.
- **as-8:** The `app.admin.password: ChangeMe123456!` default is still present in `application.yml`, unchanged. Its *impact* is now substantially mitigated by ac-6's forced-password-change filter (the credential can only ever be used once), but the control itself is about the literal being tracked in config, which persists.
- **lm-19:** A new `LogSanitizer` utility (log-injection/CRLF stripping) was added and is used in the three new lifecycle/password-change classes — a genuine, positive addition — but it addresses a different risk (log injection) than lm-19's core concern (sensitive-content masking), so it does not change lm-19's status.
- **dp-3:** `server.forward-headers-strategy: framework` is newly set in `application.yml`, resolving one specific manual-review flag from the prior report (about `X-Forwarded-Proto` handling behind a reverse proxy) — but the control's overall WARN status is unchanged since in-app TLS termination remains (correctly, per the now-documented AWS ALB topology) out of scope for this repo.

---

## Manual Review Checklist

Carried over / updated from the prior run:

- [ ] as-1: Consider adding a runtime validation library (Zod) to the frontend — still absent.
- [ ] as-4: Consider `bucket4j` or equivalent for horizontal-scale-safe rate limiting if the app is ever deployed multi-instance (cross-reference the ARCHITECTURE.md multi-instance claim — the in-memory IP-throttle state in `LoginAttemptService` has the same single-instance assumption issue flagged for ac-3/ac-4's scheduled jobs).
- [ ] as-5: Confirm length-only password policy is an accepted, deliberate design.
- [ ] as-7/ac-1: Now that `@EnableMethodSecurity` is enabled, consider actually adding `@PreAuthorize` to admin-mutating service/controller methods as defense-in-depth, since the annotation processing is already wired up and unused.
- [ ] as-8: Confirm `app.admin.password` default is always overridden via environment/secrets manager in any non-local deployment — the forced-change filter (ac-6) reduces but does not eliminate the value of externalizing this.
- [ ] **New — as-9:** Confirm the build/deploy pipeline substitutes or regenerates the `index.html` CSP `connect-src` value per environment (currently hardcoded to `http://localhost:8080`).
- [ ] as-11: Confirm 15-minute session timeout and bounded concurrent-session limit (5) meet organisational policy; frontend idle-timeout UX still not implemented.
- [ ] as-13: Confirm production build pipeline strips `console.*` calls (no explicit Terser/plugin config found).
- [ ] ac-2: Confirm with the assessing organisation whether MFA-for-ADMIN should be reclassified from N/A to FAIL given `PRODUCT.md`'s explicit scoping-out.
- [ ] **New — ac-3/ac-4/pm-6:** Resolve the ShedLock documentation/code mismatch — either add ShedLock to both scheduled jobs (if the real deployment is the multi-instance AWS topology `ARCHITECTURE.md` describes) or correct `ARCHITECTURE.md` to match the jobs' documented single-instance assumption. This also affects `LoginAttemptService`'s in-memory IP-throttle state, which has the identical caveat.
- [ ] **New — ac-8:** Obtain explicit organisational sign-off on the N/A reclassification (documented via ADR, not code-verified against a real IdP/directory absence the way the other lifecycle controls were).
- [ ] dp-3: Confirm the AWS ALB deployment topology now documented in `ARCHITECTURE.md` actually terminates TLS and forwards `X-Forwarded-*` headers correctly (the app now correctly reads them via `forward-headers-strategy: framework`, but this only helps if the proxy actually sends them).
- [ ] lm-4: Consider a dedicated audit-event framework if plain (now ECS-structured) SLF4J logging is insufficient for audit-trail retention requirements.
- [ ] lm-19: Confirm the `LoggingEmailService` reset-link-in-logs pattern remains an accepted, documented dev/demo stand-in and is replaced before any production use.

### Files Reviewed (this run, in addition to the full file list from the prior report)

**Backend (new/changed since prior run)**
- backend/src/main/java/com/assessment/securedhelloworld/config/SecurityConfig.java
- backend/src/main/java/com/assessment/securedhelloworld/config/ForcePasswordChangeFilter.java
- backend/src/main/java/com/assessment/securedhelloworld/config/TimeConfig.java
- backend/src/main/java/com/assessment/securedhelloworld/auth/PasswordChangeController.java
- backend/src/main/java/com/assessment/securedhelloworld/auth/PasswordChangeService.java
- backend/src/main/java/com/assessment/securedhelloworld/auth/PasswordChangeRequest.java
- backend/src/main/java/com/assessment/securedhelloworld/auth/InvalidCurrentPasswordException.java
- backend/src/main/java/com/assessment/securedhelloworld/auth/LockoutPolicy.java
- backend/src/main/java/com/assessment/securedhelloworld/auth/LoginCount.java, LoginCountRepository.java, LoginCountService.java, LoginOutcome.java
- backend/src/main/java/com/assessment/securedhelloworld/lifecycle/DormantAccountDisablingJob.java
- backend/src/main/java/com/assessment/securedhelloworld/lifecycle/AccessReviewJob.java
- backend/src/main/java/com/assessment/securedhelloworld/lifecycle/AccessReviewProperties.java
- backend/src/main/java/com/assessment/securedhelloworld/logging/LogSanitizer.java
- backend/src/main/java/com/assessment/securedhelloworld/user/User.java (updated)
- backend/src/main/java/com/assessment/securedhelloworld/user/UserRepository.java (updated)
- backend/src/main/java/com/assessment/securedhelloworld/bootstrap/AdminBootstrapRunner.java (updated)
- backend/src/main/resources/application.yml (updated)
- backend/src/main/resources/application-dev.yml (updated)
- backend/pom.xml (updated — actuator, Spring Boot 4.1.0 parent)
- backend/src/test/java/.../auth/ForcePasswordChangeIntegrationTest.java
- backend/src/test/java/.../lifecycle/DormantAccountDisablingJobTest.java
- backend/src/test/java/.../lifecycle/AccessReviewJobTest.java
- backend/src/test/java/.../auth/LoginCountIntegrationTest.java
- backend/src/test/java/.../config/H2ConsoleProfileGateTest.java
- backend/src/test/java/.../web/ApiExceptionHandlerTest.java

**Frontend (new/changed since prior run)**
- frontend/src/ErrorBoundary.tsx (new)
- frontend/src/main.tsx (updated — wires ErrorBoundary)
- frontend/index.html (updated — CSP meta tag)

**Project-level (new since prior run)**
- ARCHITECTURE.md
- openapi.yaml
- docs/adr/ac-8-not-applicable.md

**Verification commands run**
- `mvn -q -o compile` (backend/) — exit 0
- `mvn -q -o test` (backend/) — exit 0, 59/59 tests pass, 0 failures/errors
- `npm run build` (frontend/) — exit 0, clean production build, no sourcemap emitted
