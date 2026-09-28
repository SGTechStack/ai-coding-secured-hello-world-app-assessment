# Remediation Plan — Pre-Production Readiness Report (2026-09-28)

Source: `pre-prod-report/readiness-report-20260928-0941.md`. Tracked as a live todo list (17 items) alongside this document. Ordered by risk/severity; each phase is independently verifiable without waiting for later phases.

## ⚠️ Deviation flag — superseded by explicit user decision

The original draft of this plan defaulted to a same-line patch bump (3.3.4 → 3.3.13) to fix the two CVEs with minimal risk, and proposed a major-version upgrade only as optional future follow-up. **The user has since explicitly requested upgrading to Spring Boot 4** — item #2 below now targets **4.1.x** directly (current stable minor; 4.0 loses OSS support Dec 2026, so 4.1 is the right landing spot, not 4.0).

Context on why this is a bigger lift than the original patch-bump plan, so effort is budgeted correctly:
- Spring Boot 4.0/4.1 moves to **Spring Framework 7.0 / Spring Security 7.0**, **Jakarta Servlet 6.1**, **Jakarta Persistence 3.2** (Hibernate 7.2), **Jakarta Validation 3.1**, **Jackson 3.0**, **H2 2.4**, **Tomcat 11.0**.
- Checked this codebase for the most common migration blocker: zero `javax.*` imports found anywhere in `backend/src/main/java` (already on Jakarta namespace) — reduces risk, but doesn't eliminate it.
- Hibernate 7 / Jackson 3 / Spring Security 7 can still introduce behavioral breaks (serialization defaults, deprecated API removal) independent of the namespace change — this needs a real build-and-test pass, not just a version bump, and should be its own reviewed commit separate from the security/logging fixes in P0–P1.
- **Both original High CVEs (Spring path traversal, Tomcat RCE) are fixed by 4.1.x** as a side effect of the dependency bump, same as they would have been by 3.3.13 — so item #2 still directly closes Gate 4's blockers.

---

## P0 — Immediate (security-critical, fix first)

### 1. Stop logging the plaintext password-reset token (Gate 6 Critical)
- **File:** `backend/src/main/java/com/assessment/securedhelloworld/passwordreset/LoggingEmailService.java:20`
- **Current:** `log.info("Password reset requested for {} - reset link: {}", toAddress, resetLink);` — `resetLink` embeds the raw plaintext token from `PasswordResetService.java:64-65`.
- **Fix:** Log only that an email was sent, e.g. `log.info("Password reset email sent to {}", toAddress);`. Never pass `resetLink`/`plaintextToken` to any logger.
- **Verify:** grep the whole backend for `resetLink` and `plaintextToken` in `log.*` calls — must return zero matches. Confirm via a test that asserts the reset-request flow doesn't emit the token to logs (capture appender in a test, or manual log inspection).

### 2. Upgrade `spring-boot-starter-parent` to Spring Boot 4.1.x (Gate 4 — 2 High CVEs, per explicit user decision to go to Spring Boot 4)
- **File:** `backend/pom.xml:9`
- **Fix:** `<version>3.3.4</version>` → `<version>4.1.x</version>` (latest 4.1 release at implementation time — confirm exact patch via `mvn versions:display-parent-updates` or the Spring Boot GitHub releases page).
- **Follow Spring's official 3.5→4.0 migration guide** (this app is on 3.3, so first check whether the guide expects a 3.3→3.5 stop first, or supports jumping directly — verify against the actual migration doc before starting, don't assume).
- **Known/likely touch points for this codebase specifically** (based on dependency-upgrade list, not yet verified against actual compile errors):
  - `pom.xml` — parent version bump; drop any now-redundant explicit version overrides Spring Boot 4 manages itself.
  - Java version — Spring Boot 4 requires a current LTS JDK; confirm `java.version` in `pom.xml` (currently 21) is still within Boot 4.1's supported range.
  - Spring Security config (`SecurityConfig.java`) — Spring Security 7.0 is a major bump; re-check `CookieCsrfTokenRepository`, `CsrfTokenRequestAttributeHandler`, and any other API surface used there against the Security 7.0 changelog for renamed/removed methods.
  - JPA/Hibernate (`User`, `PasswordResetToken` entities + repositories) — Hibernate 7.2 under Jakarta Persistence 3.2; run the full data-layer test suite (not just compile) since ORM behavior changes (e.g. type mapping, lazy-loading defaults) don't always show up as compile errors.
  - Bean Validation (`RegistrationRequest`, other `@Valid` DTOs) — Jakarta Validation 3.1 / Hibernate Validator 9.0; re-verify constraint annotations still validate as expected.
  - H2 (`com.h2database:h2`) — Boot 4 pins H2 2.4; confirm the embedded DB still initializes cleanly (schema, dialect) since H2 2.4 is a jump from the current 2.2.224.
  - JSON handling — Jackson 3.0 is a major version with package/behavior changes; check any custom serialization if present, and any frontend contract assumptions about JSON field naming/null-handling.
  - This upgrade should land as its **own commit**, separate from the P0/P1 security/logging fixes, so a regression can be bisected to "the framework bump" vs. "the security fix" if something breaks.
- **Verify:** full `mvn clean verify` (compile + all backend tests) passes with zero changes to test *expectations* (only fix genuine compile/behavior breaks caused by the upgrade — don't loosen assertions to make it pass). `mvn dependency:tree` confirms Spring Framework ≥7.0 and Tomcat ≥11.0 resolved (both comfortably clear of the CVE-affected 6.1.13/10.1.30 ranges). Manually smoke-test login, registration, password-reset, and admin flows since these exercise Security + JPA + Validation together. Rerun the dependency-vuln-scan gate — both High findings should clear.

---

## P1 — High priority

### 3. Sanitize user-controlled values before logging (Gate 6 High — log injection)
- **Root cause:** `RegistrationRequest` has no CRLF/control-char restriction on `username`/`email`; both logged raw at 9+ call sites (`LoginService.java`, `LoginAttemptService.java`, `LogoutController.java`, `AdminUserService.java`, `PasswordResetService.java`, `RegistrationService.java`). `ClientIpResolver.java:17-19` trusts raw `X-Forwarded-For`, logged at `LoginAttemptService.java:91`.
- **Fix:** Add a `@Pattern(regexp = "^[a-zA-Z0-9_.-]+$")` constraint on `username` in `RegistrationRequest` (and any other DTO accepting username). For values that can't be pattern-restricted (e.g. IP header), add a small log-sanitization helper stripping `\r`/`\n` before interpolation, applied consistently at all listed call sites.
- **Verify:** attempt registration/login with a username containing `\r\n` — must be rejected at validation (400), never reach a log call.

### 4. Add exception-boundary logging in `ApiExceptionHandler`
- **Fix:** Add a catch-all `@ExceptionHandler(Exception.class)` that logs at ERROR with the throwable attached (`log.error("Unhandled exception", ex)`) while the HTTP response body stays generic (no `ex.getMessage()` leaked — keeps as-13 compliant).
- **Verify:** trigger an unexpected exception in a test; assert a log line at ERROR level with a stack trace, and assert the HTTP response body contains no exception detail.

### 5. Force password change for bootstrap/default admin credential (IM8 ac-6; also root cause of Gate 3's 2 Medium threats)
- **Root cause:** `application.yml:15` seeds `app.admin.password: ChangeMe123456!` via `AdminBootstrapProperties`/`AdminBootstrapRunner.java`, with no forced-change mechanism.
- **Fix:**
  - Add `forcePasswordChange` boolean to the `User` entity.
  - `AdminBootstrapRunner` sets it `true` on the seeded admin account.
  - Add a servlet filter/interceptor that blocks all endpoints except password-change and logout while the flag is `true`.
  - Add/confirm a password-change endpoint that clears the flag on success.
- **Note:** this mechanism substantially overlaps IM8 **as-15** (Password Change) — implement once, satisfies both.
- **Verify:** fresh bootstrap → login with seed password → confirm all non-password-change/logout endpoints return 403 with a `PASSWORD_CHANGE_REQUIRED`-style code → change password → confirm normal access resumes.

### 6. Add Content-Security-Policy (IM8 as-9 FAIL)
- **File:** `backend/src/main/java/com/assessment/securedhelloworld/config/SecurityConfig.java`
- **Fix:** `.headers(h -> h.contentSecurityPolicy(csp -> csp.policyDirectives(...)))` with a restrictive policy — no `unsafe-inline`, no `unsafe-eval`, no wildcard origins — scoped to the frontend's actual script/style/connect-src origins.
- **Verify:** check response headers include `Content-Security-Policy`; manually exercise the frontend to confirm nothing breaks under the policy (no console CSP violations in devtools).

---

## P2 — Medium priority

### 7. Add structured/JSON logging (IM8 lm-15 FAIL)
- Since item #2 now upgrades to Spring Boot 4.1 (which includes Boot 3.4+'s native structured logging support), prefer the native path: set `logging.structured.format.console` (and/or `.file`) to `ecs` in `application.yml` rather than adding a third-party encoder dependency. Only fall back to `logback-spring.xml` + `net.logstash.logback:logstash-logback-encoder` if the native ECS format doesn't cover a specific need.
- **Sequencing note:** do this after item #2 lands, so it's built on the final framework version rather than being redone.
- **Verify:** application log output is valid JSON/ECS lines with consistent fields.

### 8. Add metrics/observability foundation (IM8 lm-16 FAIL)
- Add `spring-boot-starter-actuator` + an appropriate Micrometer registry. Expose only `health,info` via `management.endpoints.web.exposure.include` (never `*`, per as-13). Secure actuator endpoints. Add `@Timed`/`Counter` on login, registration, password-reset paths.
- **Verify:** `/actuator/health` reachable and secured per policy; metrics visible for the instrumented paths.

### 9. Add account dormancy/expiry auto-disable job (IM8 ac-3 FAIL)
- Add `lastLoginAt` tracking if not already present; add a daily `@Scheduled` job (ShedLock if ever multi-instance) disabling accounts >90 days dormant or past an expiry date, invalidating their sessions.
- **Verify:** integration test with a seeded stale account confirms it's disabled after the job runs.

### 10. Add periodic access review mechanism (IM8 ac-4 FAIL)
- Define a declared per-account-type role baseline; add a scheduled job/report flagging (or auto-revoking) drift, running frequently enough to close gaps within 5 days per policy.
- **Verify:** seed an account with an out-of-baseline role; confirm the job flags/revokes it.

### 11. Add automated account lifecycle management (IM8 ac-8 FAIL)
- **Scope decision needed before implementing** — this is a small assessment app with local admin-managed accounts and no external IdP. Confirm with the team whether a lightweight provisioning webhook is actually in-scope, or whether this control should be marked **N/A with documented justification** instead. Do not build SCIM/JIT machinery speculatively.

### 12. Write architecture documentation (IM8 pm-6 FAIL)
- Create `docs/adr/` with key decisions, an `ARCHITECTURE.md` (can adapt `pre-prod-report/tech-architecture.md` generated during the audit), and a committed `openapi.yaml` (hand-written or via `springdoc-openapi`).
- **Verify:** docs describe actual current architecture/data flows, not just setup instructions (matches the pm-6 FAIL condition bar).

---

## P3 — Lower priority / cleanup

### 13. Backend Javadoc coverage for public types (Gate 1 WARN)
13 of 38 public types undocumented (`AdminUserController`, `HelloController`, `LoginController`, `LogoutController`, `PasswordResetController`/`Service`/`TokenRepository`, `AppUserDetailsService`, `AdminBootstrapProperties`, request DTOs). Add class- and method-level Javadoc.

### 14. Confirm intentional-public endpoint list + profile-gate H2 console (Gate 2 WARN)
Get explicit sign-off that the 6 `permitAll` endpoints (register, login, password-reset×2, csrf, h2-console) are intentional; document in `ARCHITECTURE.md`. Separately, harden `/h2-console/**` so it isn't `permitAll` unconditionally in `SecurityConfig.java` itself (currently only kept disabled outside dev via `application-dev.yml`) — gate the matcher registration behind an `@Profile("dev")` check or environment-driven conditional.

### 15. Address remaining Low-severity threat model items (Gate 3 WARN)
Fix `X-Forwarded-For` spoofing (only trust behind a known reverse proxy, else use remote address), bound the in-memory throttle map (eviction/TTL), add rate limiting to `/register` and `/password-reset/request`, consider `SameSite=Strict` where compatible.

### 16. Address remaining IM8 WARN controls
`as-1, as-4, as-5, as-7, as-8, as-11, as-13, lm-4, lm-19, ac-1, dp-3` — review each in `artifacts/im8-compliance-report-20260928-0930.md`, apply targeted fixes or document as accepted risk. Note overlaps: `as-4`/rate-limiting overlaps item 15; `lm-19` overlaps item 3; `dp-3` needs TLS-termination topology confirmation — likely a documentation task, not code.

---

## Verification (final gate)

### 17. Rerun affected gates, confirm no regressions
After P0/P1 (and P2 if done in the same pass): rerun `im8-review` (targeted at as-9, ac-6, lm-15, lm-16), rerun `dependency-vuln-scan`, rerun `spring-logging-review`, run full backend + frontend test suites and builds. Confirm previously-FAIL gates move to PASS/WARN with no new FAILs introduced.

---

## Suggested execution order

1. Item 1 (P0, log leak) — do today, trivial change, high impact.
2. Item 2 (P0, Spring Boot 4.1 upgrade) — do next, **as its own isolated commit/PR**, before other backend changes pile on top of it. Budget more time than a patch bump: full build, full test run, manual smoke test of every major flow (login/register/reset/admin). This also closes both CVEs.
3. Items 3–6 (P1) — implement after the framework upgrade lands cleanly, so they're written against the final API surface (e.g. Security 7.0), not against soon-to-be-replaced 6.x APIs.
4. Items 7–12 (P2) — larger effort; item 7 (structured logging) benefits from doing after item 2; item 11 needs a scope decision first.
5. Items 13–16 (P3) — cleanup, can run in parallel with P2 by a separate contributor, but should still wait until after item 2 to avoid rebasing docs/tests through the framework bump.
6. Item 17 — after each meaningful batch, not just once at the end. Run it once right after item 2 specifically, before anything else lands on top, to confirm the upgrade alone didn't regress any gate.
