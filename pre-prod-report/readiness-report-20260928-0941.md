# Pre-Production Readiness Report

> Project: ai-coding-secured-hello-world-app-assessment
> Generated: 2026-09-28T09:41:00+08:00
> **Last rescanned: 2026-09-28T11:42:00+08:00** (post-remediation rerun, 7 parallel subagents)
> Audited by: Kiro (automated, 7 parallel subagents)
> Stack: React 19 (TypeScript) frontend + Spring Boot 4.1.0 / Spring Framework 7.0.8 / Spring Security 7.1.0 (Maven) backend — upgraded from Spring Boot 3.3.4 since the initial audit

## Executive Summary

| Gate | Status | Blockers | Warnings |
|------|--------|----------|----------|
| 1. API Code Documentation | **PASS** ~~WARN~~ | 0 | 0 (backend: 0/51 undocumented public types, was 13/38) |
| 2. Authorization Matrix | **PASS** ~~WARN~~ | 0 | 0 (14 endpoints, all explicit; H2-console profile-gate fixed) |
| 3. Threat Modeling | WARN | 0 | 2 Low Open (was 2 Medium + several Low) |
| 4. Tech Architecture & Vuln Scan | **PASS** ~~FAIL~~ | 0 ~~2 High CVE~~ | 0 |
| 5. IM8/ARC Compliance | **WARN** ~~FAIL~~ | 0 ~~8 FAIL~~ | 11 WARN (unchanged count, see notes) |
| 6. Observability Readiness | **PASS** ~~FAIL~~ | 0 ~~1 Critical~~ | 0 ~~2 High~~ |
| 7. Secrets & Config Audit | PASS | 0 | 4 (only in generated doc/report artifacts, not source — see note) |

**Overall Verdict: CONDITIONALLY READY** (was **NOT READY**)

Zero FAIL gates remain (previously 3: Gates 4, 5, 6). Two gates carry WARN status requiring manual/organisational sign-off before full READY: Gate 3 (2 Low-severity residual threats) and Gate 5 (11 WARN controls, none blocking, but including one documentation-accuracy issue and one control reclassification that needs org sign-off — see below).

## What changed between runs

Between the 09:41 baseline and this 11:42 rescan, the following remediation landed (uncommitted working-tree changes, not yet committed):
- `spring-boot-starter-parent` upgraded 3.3.4 → 4.1.0 (Spring Framework 7.0.8, Spring Security 7.1.0, Tomcat 11.0.22) — closes both Gate 4 High CVEs.
- `LoggingEmailService` no longer logs the plaintext password-reset token — closes the Gate 6 Critical finding.
- New `ForcePasswordChangeFilter` + `User.forcePasswordChange` + `PasswordChangeController`/`Service` — forces bootstrap admin credential rotation (IM8 ac-6, Gate 3 Medium threats).
- New `logging.LogSanitizer` + `@Pattern` constraint on `RegistrationRequest.username` — closes CRLF/log-injection risk (Gate 6 High, IM8 lm-19).
- `ApiExceptionHandler` now has a catch-all `@ExceptionHandler(Exception.class)` logging the throwable (Gate 6, IM8 lm-4).
- Native Spring Boot structured/ECS logging configured in `application.yml`/`application-dev.yml` (Gate 6, IM8 lm-15).
- `spring-boot-starter-actuator` added, `health,info` exposed (IM8 lm-16); new `/actuator/health` endpoint is `permitAll` and now appears in the authorization matrix.
- New `lifecycle` package: `DormantAccountDisablingJob` (IM8 ac-3) and `AccessReviewJob` (IM8 ac-4), both scheduled and tested.
- `SecurityConfig.java` now profile-gates the H2 console matcher itself (`denyAll()` outside `dev`), closing the defense-in-depth gap flagged in Gates 2 and 3; covered by new `H2ConsoleProfileGateTest.java`.
- IP-based login throttling (`ClientIpResolver`, in-memory per-IP map) was removed entirely rather than hardened, closing the X-Forwarded-For spoofing and unbounded-map threats (Gate 3) as a side effect.
- Password-reset token lookup changed from a full-table scan to an indexed `selector` lookup, closing the timing-oracle threat (Gate 3).
- New `ARCHITECTURE.md`, `openapi.yaml`, `docs/adr/` — closes IM8 pm-6.
- New ADR reclassifying IM8 ac-8 as N/A (documented interpretation change — flagged below as needing organisational sign-off, not treated as an equivalent code fix).

## Blockers (must resolve before production)

**None.** All blockers from the 09:41 baseline (2 High CVEs, 8 IM8 FAIL controls, 1 Critical log leak) are resolved — see Gate sections below for verification evidence.

## Warnings (manual review recommended)

**Gate 3 (Threat model, 2 Low Open, down from 2 Medium + several Low):**
- **T4 — `SameSite=Lax` session cookie.** `application.yml` still sets `cookie.same-site: lax`. Unchanged from baseline.
- **D3 — No rate limiting on `/api/register` or `/api/password-reset/request`.** No `Bucket4j`/throttle implementation found on either endpoint. Unchanged from baseline.
- All other prior Open threats are now Mitigated (default admin credential force-change, X-Forwarded-For spoofing, unbounded throttle map, H2-console gating, linear BCrypt scan on reset-confirm) — see Gate 3 detail below.

**Gate 5 (IM8, 11 WARN controls — same count as baseline, contents shifted):**
- `as-1, as-4, as-5, as-7, as-8, as-11, as-13, lm-4, lm-19, ac-1, dp-3` — same control IDs as the baseline list. Two now have partial sub-fixes without changing overall WARN status: **as-11** (explicit 15-minute session timeout, `maximumSessions` bounded to 5 instead of unlimited) and **as-13** (new React `ErrorBoundary` with generic fallback added to frontend).
- **New follow-up — documentation accuracy (pm-6, currently PASS but flagged):** `ARCHITECTURE.md` claims `ShedLock` coordinates the new scheduled jobs (`DormantAccountDisablingJob`, `AccessReviewJob`) across a claimed multi-instance deployment, but the job code's own Javadoc states no distributed lock is used and there is zero `ShedLock` dependency anywhere in the repo (confirmed via repo-wide grep). Fix the doc or add `ShedLock` before relying on this claim in a multi-instance deployment.
- **New follow-up — control reclassification needs sign-off:** IM8 **ac-8** (automated account lifecycle management) was reclassified FAIL → N/A via a new ADR (`docs/adr/ac-8-not-applicable.md`) arguing the app only has self-service external accounts, not an internal-staff directory. This is a documented interpretation change, not a code fix — needs explicit organisational/compliance sign-off, not just engineering review, before being accepted as closed.
- **New follow-up — dormant wiring:** `as-7`/`ac-1` gained `@EnableMethodSecurity` on `SecurityConfig`, but zero `@PreAuthorize`/`@Secured`/`@RolesAllowed` annotations exist anywhere yet (URL-rule-based authorization only, unchanged enforcement model). Quick win now that the annotation processing is wired up but unused.
- Full control-by-control detail: `artifacts/im8-compliance-report-20260928-1142.md`.

**Gate 6 (Observability):** One residual gap noted but does not block PASS — no correlation IDs / MDC anywhere in the codebase. Recommended as a follow-up, not a blocker, since structured logging is otherwise in place and no critical leakage remains. One minor inconsistency: `AdminBootstrapRunner.java:58` logs `admin.getUsername()` unsanitized (bootstrap-seeded value, low risk, but inconsistent with the new `LogSanitizer` pattern used elsewhere).

**Gate 7 (Secrets):** 4 warnings + 3 info findings, all located in generated documentation/report artifacts (`docs/javadoc/`, `frontend/docs/tsdoc/`, `pre-prod-report/*.html`) — not in hand-written application source or config. A targeted rescan of all actual new application code (logging, lifecycle, PasswordChangeService, ForcePasswordChangeFilter) returned 0 findings. Recommend excluding generated-doc directories from future Gate 7 scans to keep results scoped to source. The 2 baseline informational notes (bootstrap admin password externally-overridable seed default; dev-only `cookie.secure: false`) remain valid and unchanged.

**Cross-cutting note:** all remediation described in this report exists only as **uncommitted working-tree changes** (`git status` shows the backend/frontend source, config, and new files as modified/untracked, not committed). Nothing here is production-deployed or version-controlled yet — commit and review before treating any gate result as durable.

## Gate 1: API Code Documentation
### Status: PASS (was WARN)
TypeDoc (frontend) succeeded — 0 errors, 0 undocumented public exports (unchanged, clean). Javadoc (backend) succeeded — BUILD SUCCESS, and **all 51/51 public top-level types now carry class-level Javadoc** (0% undocumented, up from 13/38 undocumented / 34% at baseline; total backend type count also grew 38 → 51 with 13 new types, all documented). Verified directly by reading source for the four previously-flagged classes (`LoginController`, `HelloController`, `AdminUserController`, `PasswordResetService`) — all now have class Javadoc — and by a 1:1 cross-check of generated Javadoc HTML pages against source type count. Remaining 100 Javadoc warnings are constructor/method/param-level only (non-public-API-blocking). Artefacts: `frontend/docs/tsdoc/index.html`, `docs/javadoc/index.html` (both regenerated).

## Gate 2: Authorization Matrix
### Status: PASS (was WARN)
14 total endpoints (up from 12) across 8 controllers, **all 14 with explicit authorization rules**, zero unprotected-by-omission. Two endpoints are new since baseline: `POST /api/auth/change-password` (protected, `anyRequest().authenticated()`, also allow-listed under `ForcePasswordChangeFilter`) and `GET /actuator/health` (permitAll, intentionally public, only `health,info` exposed). The baseline's H2-console warning is resolved: `SecurityConfig.java` now branches on the active Spring profile and calls `.denyAll()` on the H2-console matcher outside `dev` (previously this depended solely on `application-dev.yml` disabling the console feature, not on a filter-chain-level gate) — covered by a new regression test, `H2ConsoleProfileGateTest.java`, which asserts a 401 outside the dev profile even if the H2 console feature flag is forced on. Admin endpoints (`/api/admin/users/**`) now carry a class-level `@PreAuthorize("hasRole('ADMIN')")` in addition to the existing `SecurityConfig` URL rule — defense in depth, not a gap.

## Gate 3: Threat Modeling
### Status: WARN (was WARN — severity reduced)
Re-assessed all 22 threats from the baseline STRIDE model (`docs/artifacts/threat-model-secured-hello-world.md`) against current code. **Zero Open threats remain at Medium or above** (down from 2 Medium): the default-admin-credential threats are Mitigated by the new `ForcePasswordChangeFilter`/`PasswordChangeService` flow. The X-Forwarded-For spoofing and unbounded-throttle-map threats are Mitigated by removal — per-IP login throttling was deleted entirely rather than hardened (an architectural choice worth confirming is intentional, since it also removes any IP-based defense-in-depth, deferring that concern to a WAF/edge layer per the updated `LoginAttemptService` Javadoc). H2-console gating and the password-reset-confirm linear-BCrypt-scan (timing oracle) are also now Mitigated. **2 Low-severity threats remain Open**: `SameSite=Lax` session cookie, and no rate limiting on `/api/register` or `/api/password-reset/request`. New code (`ForcePasswordChangeFilter`, `lifecycle` package jobs, `LogSanitizer`, `LoginCountService`) was reviewed and introduces no new Medium+ threats.

## Gate 4: Technical Architecture & Dependency Vulnerabilities
### Status: PASS (was FAIL)
`pre-prod-report/tech-architecture.md` (+ `.html`) and `pre-prod-report/dependency-vuln-scan.md` (+ `.html`) regenerated in place. `spring-boot-starter-parent` confirmed at **4.1.0** in `backend/pom.xml` (was 3.3.4), resolving to Spring Framework 7.0.8, Spring Security 7.1.0, Tomcat 11.0.22, Hibernate 7.4.1, Jackson 3.1.4 via `mvn dependency:tree`. Both baseline CVEs are confirmed fixed: CVE-2024-38819 (fixed at Spring 6.1.14, now on 7.0.8) and CVE-2024-50379/CVE-2024-56337 (fixed at Tomcat 11.0.3, now on 11.0.22) — verified against `target/dependency-check-report.json`, zero matches for any of the three CVE IDs across 99 scanned dependencies. **Full vulnerability summary: 0 Critical / 0 High / 0 Medium / 0 Low** across both npm audit (frontend, unchanged 0 vulnerabilities) and OWASP dependency-check (backend, full check goal completed against a same-morning-cached NVD mirror). Dependency counts: backend 97 compile/runtime + 31 test-scope; frontend 4 prod + 88 dev + 46 optional (unchanged). One scanner note: the OWASP plugin's own live-NVD-update sub-step failed on a missing API key after the scan/report had already completed successfully — documented per the skill's fail-gracefully rule, does not affect result validity.

## Gate 5: IM8/ARC Compliance
### Status: WARN (was FAIL)
Full-codebase rescan written to `artifacts/im8-compliance-report-20260928-1142.md`. Of 36 assessed controls: **0 FAIL** (down from 8), 11 WARN (unchanged count, see Warnings above for content changes), 12 PASS, 13 N/A. All 8 baseline FAIL controls are resolved: `as-9` (CSP header + meta tag), `lm-15` (ECS structured logging, confirmed live), `lm-16` (actuator + `health,info` exposure, confirmed live), `ac-3` (dormant-account job, tested), `ac-4` (access-review job, tested), `ac-6` (forced credential rotation, tested), `pm-6` (ARCHITECTURE.md/openapi.yaml/ADRs, substantive and version-controlled — but see the ShedLock doc-accuracy flag above), and `ac-8` (reclassified N/A via ADR — flagged above as needing organisational sign-off rather than treated as an equivalent code-verified fix). See Warnings section above for the two sub-fixes and three new follow-ups identified during this rescan.

## Gate 6: Observability Readiness
### Status: PASS (was FAIL)
### Logging
The baseline Critical finding — `LoggingEmailService` logging the plaintext password-reset token — is fixed; the method now logs only that an email was sent, with the reset link kept in an in-memory map for test access only, never logged (class Javadoc cites the internal standard forbidding this). The baseline High finding — unsanitized username/client-IP logged without CRLF stripping — is fixed via defense-in-depth: a new `LogSanitizer` strips control characters at every remaining username log call site, and `RegistrationRequest.username` now has a `@Pattern` constraint blocking CRLF at input validation; client-IP logging was removed entirely (not just sanitized) alongside the IP-throttling removal noted in Gate 3. `ApiExceptionHandler` now has a catch-all handler logging the throwable at ERROR with a generic response body. Native Spring Boot structured/ECS logging is configured in `application.yml`, with dev overriding console output to human-readable while keeping file output as ECS. One residual gap: no correlation ID/MDC support anywhere (noted as a follow-up, not a blocker). One minor inconsistency: `AdminBootstrapRunner.java:58` logs the bootstrap admin's username unsanitized.
### Health & Readiness Probes
N/A — confirmed no Dockerfile/docker-compose/k8s/Helm manifests. `spring-boot-starter-actuator` is now present and `/actuator/health` is exposed and `permitAll`, but nothing wires it into a deployment probe (consistent with no orchestration in use).
### Distributed Tracing
N/A — confirmed no Spring Cloud/Feign/Micrometer Tracing/Sleuth signals; single-service monolith, unchanged from baseline.

## Gate 7: Secrets & Config Audit
### Status: PASS (was PASS)
Semgrep (`p/security-audit`, `p/default`, `p/secrets`, `p/owasp-top-ten`) rerun across the full project — 291 files scanned (up from 65; rule packs also grew from 392 to 665 rules via upstream updates). **Zero blockers** — no injection, insecure deserialization, weak crypto, or hardcoded secrets/credentials anywhere, including across all new application code. 4 warnings + 3 info findings, all confirmed (via `git ls-files`/`git status --ignored`) to live entirely in generated, git-untracked documentation and report artifacts (`docs/javadoc/`, `frontend/docs/tsdoc/`, `pre-prod-report/*.html`) produced by other pre-prod-check gates — not hand-written source. A targeted re-scan of only the new application code (`logging/`, `lifecycle/`, `PasswordChangeService.java`, `ForcePasswordChangeFilter.java`) returned "0 findings" across 169 rules on 6 files, confirming the new remediation code introduced no new security findings. The two baseline informational notes remain valid and unchanged. Recommend excluding generated-doc directories from future Gate 7 scans.
