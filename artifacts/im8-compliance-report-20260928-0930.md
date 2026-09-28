# IM8 Application Controls Compliance Report

**Target:** /home/lminglia/development/ai-coding-secured-hello-world-app-assessment
**Date:** 2026-09-28
**Stack (declared):** React 19 (TypeScript) + Spring Boot 3.4 / Spring Security 6.4
**Stack (actual, per `backend/pom.xml`):** React 19.2.8 (TypeScript) + Spring Boot **3.3.4** (parent POM) / Spring Security (version resolved transitively, not pinned)
**Scope:** Full codebase — all backend Java sources (`backend/src/main/java`), all backend tests, all resource/config files, and all frontend TypeScript/TSX sources (`frontend/src`), plus project-level docs.
**Risk classification used:** Not explicitly declared in the repo. Given this is an internal reference/demo app with no PII beyond username/email and no financial/health data (per `PRODUCT.md`), this audit assesses against the **Low Risk (LR)** column. Where a control has no LR/MR distinction, Level 1 default is used per the skill's instructions.

> **Note on applicability:** This application is an internal, unbranded reference/demo ("Secured Hello World App") per `PRODUCT.md` — not a public-facing citizen/business service, and serves no external public users. Controls scoped to public-facing or citizen-facing systems (`lm-18` WOGAA, `st-3` vuln disclosure, `ac-7` Singpass/Corppass, `dp-8`) are evaluated against this fact.

---

## Summary

| Control | Status | Severity | Finding |
|---------|--------|----------|---------|
| as-1  | WARN | Low | Controller-layer `@Valid`/Bean Validation present on all backend DTOs; frontend has no runtime/schema validation library — relies on HTML attributes + backend errors only. |
| as-2  | PASS | — | Spring Data JPA (no raw SQL/`Statement`); frontend paths are static, no dynamic query/URL concatenation of user input. |
| as-3  | PASS | — | No HTML string construction server-side; no `dangerouslySetInnerHTML`/`innerHTML`/`eval` in frontend source. |
| as-4  | WARN | Medium | Account lockout implemented (backend, DB-backed) and IP throttling implemented (in-memory); no `bucket4j` dependency; frontend has no 429-specific handling or submit-throttle UI. |
| as-5  | WARN | Low | Backend/frontend both enforce ≥12-char minimum length; no complexity rules, no strength indicator/zxcvbn on frontend. |
| as-6  | PASS | — | `BCryptPasswordEncoder` bean; no NoOp/MD5/SHA for passwords; no `localStorage`/`sessionStorage` password persistence; correct `autocomplete` attributes used. |
| as-7  | WARN | Low | URL-rule based authorization (`.hasRole("ADMIN")`, `.authenticated()`) present and correctly ordered; no method-level `@PreAuthorize`/`@EnableMethodSecurity` as defense-in-depth; frontend has no route guard component (uses runtime state + 403 handling only). |
| as-8  | WARN | Medium | No hardcoded secrets in source; `application.yml` has a default admin bootstrap password (`ChangeMe123456!`) intended to be overridden via env/config — flagged as a config-hygiene risk, not a hardcoded literal misuse; `.env` is gitignored; no localStorage token/secret storage in frontend. |
| as-9  | FAIL | Medium | No CSP configured anywhere — no Spring Security `.headers(...).contentSecurityPolicy(...)`, no CSP `<meta>` tag in `index.html`. |
| as-10 | PASS | — | HSTS is Spring Security 6.x default and is not explicitly disabled anywhere in `SecurityConfig`. |
| as-11 | WARN | Medium | No explicit `server.servlet.session.timeout` configured (falls back to container default, not ≤15 min); session-fixation protection present (`ChangeSessionIdAuthenticationStrategy`); logout invalidates session; frontend has no idle-timeout detection. |
| as-12 | N/A | — | Application has no file-upload feature anywhere in the codebase (no upload endpoints, no `MultipartFile`, no file-input components) — malware-scanning control does not apply. |
| as-13 | WARN | Medium | Error responses generic via `@RestControllerAdvice`; no explicit `server.error.include-*` properties set (defaults are safe); no Actuator dependency (so no exposure risk there); frontend has no React Error Boundary component; sourcemap/console-strip production build settings not configured in `vite.config.ts`. |
| as-14 | PASS | — | No custom crypto beyond `BCrypt` (via Spring Security) and `SecureRandom` for password-reset tokens; no DES/RC4/MD5/SHA-1 usage; no `Math.random()` for security-sensitive frontend logic (none present). |
| as-15 | N/A | — | No account-lockout-triggered forced password-reset flow exists; per control's own applicability note this is evaluated as a gap under `ac-6`/`as-4`, not required as N/A justification — see Manual Review. Marked N/A here because self-service unlock is time-based (auto-expiring `lockedUntil`), not admin-driven reset issuance. |
| lm-4  | WARN | Low | Login success/failure and admin actions (`enable/disable`, `role-change`, `delete`) are logged via SLF4J; no dedicated audit-event framework (`AuditApplicationEvent`) or `@CreatedBy`/`@LastModifiedBy`; no frontend audit/correlation-ID propagation. |
| lm-15 | FAIL | Critical | No structured/JSON logging configured — no `logging.structured.format.file: ecs` in `application.yml`, no `logback-spring.xml`/`logback.xml` with ECS/JSON encoder anywhere in the repo. Plain SLF4J default logging only. |
| lm-16 | FAIL | Critical | No Actuator dependency in `pom.xml`, no custom Micrometer meters (`Counter`/`Timer`/`Gauge`) or `MeterRegistry` bean anywhere in backend source — no metrics foundation exists at all. |
| lm-18 | N/A | — | Internal reference/demo app, not public-facing (confirmed via `PRODUCT.md`: "not a mass consumer audience", no public registration flow beyond assessment context) — WOGAA does not apply. |
| lm-19 | WARN | Low | No log-masking layout/filter exists; however, manual review of all `log.*()` call sites found no direct logging of passwords, tokens, or other sensitive fields (registration test `RegistrationIntegrationTest.registersNewUserWithBCryptHashedPassword` explicitly asserts the plaintext password is never logged) — this is a defense-in-depth gap (WARN), not an active leak. |
| ck-1  | N/A | — | Application does not generate or manage cryptographic keys — only uses BCrypt for password hashing (not "key establishment") and `SecureRandom` for token generation. No `KeyGenerator`/`KeyPairGenerator`/`KeyAgreement` usage found. |
| ck-2  | N/A | — | No application-managed cryptographic keys exist to rotate. |
| ck-4  | N/A | — | No hardcoded key material, no `.pem`/`.key`/`.p12`/`.jks`/`.keystore` files anywhere in the source tree (confirmed via glob search — zero matches); no application-level key management exists; TLS/keystore is out of scope per PRD (deployment-layer concern). |
| ga-8  | N/A | — | No Generative AI features exist anywhere in the codebase. |
| ac-1  | WARN | Low | URL-role mapping present with correct specific-before-broad ordering and a default-deny (`anyRequest().authenticated()`); no method-level security (`@EnableMethodSecurity`/`@PreAuthorize`) as defense-in-depth; admin self-action guards exist in service layer (`AdminUserService.requireNotSelf`); frontend has centralized 403 handling in `AdminUsersPage` but no reusable permission hook/route guard. |
| ac-2  | N/A | — | No MFA/step-up implementation exists anywhere in the codebase. Per `PRODUCT.md`, MFA/2FA is explicitly documented as out of scope for this reference app. Applicability requires privileged-account/action re-verification; team has explicitly scoped this out — flagged for manual confirmation this is an accepted risk for the ADMIN role. |
| ac-3  | FAIL | Medium | `User.enabled` field exists and can disable an account, but there is no scheduled job (`@Scheduled`+lock), batch process, or SCIM integration anywhere in the codebase that detects 90-day dormancy or account/role expiry and disables accounts automatically. Only manual admin-triggered disable exists (`AdminUserService.updateEnabled`). |
| ac-4  | FAIL | Medium | No scheduled access-review/entitlement-reconciliation job exists. No declared per-account permission baseline (allow-list/entitlements file) to compare against. Only two roles (`USER`/`ADMIN`) exist with manual admin-driven changes — no periodic review mechanism. |
| ac-6  | FAIL | Medium | No `forcePasswordChange`/`mustResetPassword` field exists on `User` entity (confirmed via full read of `User.java`); the seeded admin bootstrap account (`AdminBootstrapRunner`) and any admin-created accounts do not force a password change on first login. Password reset via `PasswordResetService` exists but is user-initiated (forgot-password), not an admin-issued temporary-credential flow with forced change. |
| ac-7  | N/A | — | No Singpass/Corppass integration exists; per `PRODUCT.md` there are no external public/citizen users or corporate users — this is an internal reference/demo app with self-registration for the assessment context only, and no "high-risk transactions" (financial, legal, permit-issuing) exist in scope. |
| ac-8  | FAIL | Medium | No SCIM/provisioning endpoint, no JIT SSO provisioning exists (the app uses local username/password only, no OIDC/SAML2 client registration anywhere in `SecurityConfig` or `pom.xml`). All account lifecycle (create via self-registration, admin bootstrap) is manual/local — this control's applicability is for **internal users**; the ADMIN role here functions as an internal/privileged account with no automated lifecycle management. |
| ac-12 | N/A | — | No SSO configuration exists (no OIDC/SAML2 client registration in `pom.xml` or `application.yml`). Per `PRODUCT.md`, this is explicitly a local username/password reference app, not an internal-service context with a corporate IdP — control does not apply to this deployment model. |
| dp-3  | WARN | Medium | No `server.ssl.enabled`/keystore or SSL bundle configured in `application.yml`/`application-dev.yml` — app runs over plain HTTP; `application-dev.yml` explicitly sets `cookie.secure: false` for local dev with a code comment stating production should use `application.yml`'s `secure: true` default (confirmed: base `application.yml` sets `secure: true`). No hardcoded `http://` URLs to sensitive third-party endpoints found. No custom trust-all `X509TrustManager`/`HostnameVerifier` found. TLS termination is a deployment-layer concern per PRD, not configured in-app — flagged for manual verification of the proxy/LB layer. |
| dp-8  | N/A | — | Applies only to internal applications serving public officers; this app serves USER/ADMIN reference-app roles with no data-classification scheme in its product scope, and is not built for public-officer government use — marked N/A per applicability guidance, pending explicit organisational confirmation. |
| pm-6  | FAIL | Medium | No `ARCHITECTURE.md`, no `docs/architecture/` tree, no ADR directory (`/adr` or `/docs/adr` — confirmed absent via glob; only `docs/agents/*` tooling docs exist), no network topology diagram, no committed OpenAPI/Swagger spec file. `PRODUCT.md` and `DESIGN.md` exist but document product/design intent, not system architecture, data flows, or deployment topology. `frontend/README.md` and repo-root docs are setup-only. |
| st-3  | N/A | — | Internal reference/demo application, not public-facing (confirmed via `PRODUCT.md`) — public vulnerability disclosure programme does not apply. |

**Totals:** 3 FAIL-eligible controls at Critical severity (lm-15, lm-16 — critical; ac-3/ac-4/ac-6/ac-8/pm-6/as-9 — medium), 0 controls at High, remainder WARN/PASS/N/A. See Blockers in the Gate Result section for the full FAIL list.

## Detailed Findings

### as-1: Input Validation

**Status:** WARN

**Severity:** Low (LR:1 → WARN)

**Checks performed:**
- Scanned all `@RestController` classes for `@RequestBody` + `@Valid`.
- Checked DTO Bean Validation annotations.
- Checked frontend for schema validation library and runtime response validation.

**Evidence:**
- `backend/src/main/java/com/assessment/securedhelloworld/registration/RegistrationController.java:23` — `@Valid @RequestBody RegistrationRequest request` ✓
- `backend/src/main/java/com/assessment/securedhelloworld/registration/RegistrationRequest.java:20-26` — `@NotBlank`, `@Email`, `@Size(min=12)` ✓
- `backend/src/main/java/com/assessment/securedhelloworld/auth/LoginController.java:19` — `@Valid @RequestBody LoginRequest request` ✓
- `backend/src/main/java/com/assessment/securedhelloworld/auth/LoginRequest.java:6,9` — `@NotBlank` on both fields ✓
- `backend/src/main/java/com/assessment/securedhelloworld/admin/AdminUserController.java:34,42` — `@Valid @RequestBody` on status/role updates ✓
- `backend/src/main/java/com/assessment/securedhelloworld/passwordreset/PasswordResetController.java:19,25` — `@Valid @RequestBody` ✓
- `backend/src/main/java/com/assessment/securedhelloworld/passwordreset/PasswordResetConfirmRequest.java:5-10` — `@NotBlank`, `@Size(min=12)` ✓
- `frontend/package.json:11-24` — no `zod`/`yup`/`joi`/`valibot` dependency present.
- `frontend/src/api.ts:52-70` — `apiFetch` parses JSON response directly with no runtime schema validation (`response.json()` cast via `as T`, no `.parse()`).
- `frontend/src/ResetPasswordForm.tsx:5` — `useParams`-equivalent (`URLSearchParams`) token read directly into state with no format validation before submission.

**Issues:**
- Backend validation is thorough at the controller boundary — no gap there.
- Frontend has no runtime validation layer; TypeScript types on `AdminUserView`/API responses are compile-time only and are not verified at runtime. This is a defense-in-depth gap, consistent with WARN per the evidence-standard rule (framework/absence of library ≠ FAIL when backend is authoritative and enforces the same rules).

**Manual review required:**
- Confirm frontend and backend password/username rules stay in sync as they evolve (currently both enforce ≥12 chars).
- Confirm no team decision was made to add runtime frontend validation later in the product roadmap.

---

### as-2: Parameterised Interfaces

**Status:** PASS

**Severity:** —

**Checks performed:**
- Searched for raw `java.sql.Statement`, native `@Query`, string-concatenated SQL, `JdbcTemplate` misuse.
- Checked frontend for URL string concatenation of user input.

**Evidence:**
- `backend/src/main/java/com/assessment/securedhelloworld/user/UserRepository.java:1-18` — pure Spring Data JPA derived-query methods (`findByUsername`, `existsByEmail`, etc.), no `@Query(nativeQuery=true)`, no `JdbcTemplate`, no `Statement` usage anywhere in the codebase.
- `backend/src/main/java/com/assessment/securedhelloworld/passwordreset/PasswordResetTokenRepository.java:1-10` — same pattern, derived query only.
- `frontend/src/api.ts:96-127` — all API paths are static string literals or template literals with only numeric IDs interpolated (e.g. `` `/api/admin/users/${id}/status` ``), no raw user text ever placed into a URL.

**Issues:** None.

**Manual review required:**
- None beyond the standard note: verify no `Runtime.exec`/`ProcessBuilder` exists — confirmed absent via full source read of `backend/src/main/java`.

---

### as-3: Output Sanitisation

**Status:** PASS

**Severity:** —

**Checks performed:**
- Checked for HTML string construction server-side, `text/html` content-type responses, `dangerouslySetInnerHTML`, direct DOM manipulation, `eval`/`new Function`.

**Evidence:**
- All backend `@RestController` methods return `ResponseEntity<Void>`, `ResponseEntity<Map<String,String>>`, `List<AdminUserView>`, or a plain `String` (`HelloController.java:14` — `"Hello, " + principal.getUsername()`, returned as `text/plain` via default Spring MVC negotiation, not treated/rendered as HTML) — no HTML construction anywhere.
- `frontend/src/*.tsx` — full read of all 8 `.tsx` files confirms zero occurrences of `dangerouslySetInnerHTML`, `innerHTML`, `insertAdjacentHTML`, `document.write`, `eval`, or `new Function`.

**Issues:** None.

**Manual review required:** None.

---

### as-4: Authentication Mechanism Rate-Limiting

**Status:** WARN

**Severity:** Medium (LR:1 → WARN)

**Checks performed:**
- Checked User entity for failed-attempt/lockout fields and lockout-triggering logic.
- Checked for `bucket4j` dependency and rate-limit filter config.
- Checked frontend for 429 handling and submit throttling.

**Evidence:**
- `backend/src/main/java/com/assessment/securedhelloworld/user/User.java:38-45` — `failedLoginAttempts` (int) and `lockedUntil` (Instant) fields present.
- `backend/src/main/java/com/assessment/securedhelloworld/auth/LoginAttemptService.java:47-57` — `recordFailedAttempt` increments `failedLoginAttempts` and sets `lockedUntil` once `maxAccountAttempts` (config: `application.yml:23` — `max-attempts: 5`) is reached.
- `backend/src/main/java/com/assessment/securedhelloworld/auth/LoginAttemptService.java:66-100` — separate in-memory per-IP throttling (`assertIpNotThrottled`/`recordFailureForIp`), config `application.yml:26-27` (`max-attempts: 20`, `window-minutes: 15`).
- `backend/src/test/java/com/assessment/securedhelloworld/auth/IpThrottlingIntegrationTest.java:52-77` — integration test proves IP throttling triggers independently of account lockout.
- `backend/pom.xml:1-70` — no `bucket4j-spring-boot-starter` dependency.
- `frontend/src/LoginForm.tsx:13-27` — `submitting` state disables the submit button during the in-flight request (basic double-submit guard), but there is no specific handling for HTTP 429 (`ApiError.status === 429`) and no countdown/backoff UI.

**Issues:**
- Account lockout and IP throttling are both implemented and tested, satisfying the substance of this control, but via a custom in-memory/DB mechanism rather than the specific `bucket4j` pattern the control's automated checks look for. Per the evidence-standard rule, a custom mechanism that is fully implemented and tested in the application's own code is credit-worthy, not merely "delegated" — hence WARN rather than FAIL, with the gap being (a) IP-throttle state is in-memory only (won't survive restart or scale horizontally — documented as an accepted limitation in `LoginAttemptService.java:18-19`), and (b) no frontend 429-specific UX.

**Manual review required:**
- "Verify rate-limit thresholds (5 account attempts / 15 min lockout; 20 IP attempts / 15 min window) are appropriate for the assessed risk level."
- "If deployed with multiple instances or behind a load balancer, verify in-memory IP-throttle state doesn't create inconsistent enforcement across nodes, and that `X-Forwarded-For` (read in `ClientIpResolver.java:15-17`) is trusted only from a known proxy."

---

### as-5: Password Requirements

**Status:** WARN

**Severity:** Medium (LR:1 → WARN)

**Checks performed:**
- Checked for password validation logic and minimum length on backend.
- Checked frontend password fields for validation, strength feedback, and type.

**Evidence:**
- `backend/src/main/java/com/assessment/securedhelloworld/registration/RegistrationRequest.java:24-25` — `@Size(min = 12, message = "Password must be at least 12 characters long")`.
- `backend/src/main/java/com/assessment/securedhelloworld/passwordreset/PasswordResetConfirmRequest.java:8-9` — same `@Size(min=12)` rule applied consistently at password-reset confirmation.
- `frontend/src/RegisterForm.tsx:56-63` — `<input type="password" minLength={12} required autoComplete="new-password">`.
- `frontend/src/ResetPasswordForm.tsx:47-54` — same `minLength={12}`, `autoComplete="new-password"`.
- No complexity requirements (uppercase/digit/symbol) exist at either layer, and no strength-meter/zxcvbn or inline complexity feedback exists in any `.tsx` form.

**Issues:**
- Minimum length is enforced consistently front and back (≥12 chars, which exceeds the control's own recommended baseline of 8). The gap is the complete absence of complexity feedback/strength indication, which the control's frontend checks explicitly look for.

**Manual review required:**
- "Confirm whether password complexity (beyond length) is a requirement per organisational policy, or whether a length-only NIST-800-63B-style policy is the deliberate, accepted design (length-only policies are increasingly considered best practice, but should be an explicit decision, not a gap)."

---

### as-6: Password Salting and Hashing

**Status:** PASS

**Severity:** —

**Checks performed:**
- Located `PasswordEncoder` bean and verified type.
- Searched for `NoOpPasswordEncoder`/MD5/SHA usage.
- Checked frontend for client-side hashing, HTTP-only transport assumption, localStorage/sessionStorage password storage, and `autocomplete` attributes.

**Evidence:**
- `backend/src/main/java/com/assessment/securedhelloworld/config/SecurityConfig.java:33-36` — `@Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }`.
- Full-repo search for `NoOpPasswordEncoder`, `MD5`, `SHA-1`, `MessageDigest` in backend source: zero matches.
- `backend/src/main/java/com/assessment/securedhelloworld/passwordreset/PasswordResetService.java:118-124` — reset tokens are hashed with `BCrypt.hashpw`/`checkpw` before storage (not plaintext).
- `frontend/src/LoginForm.tsx:41-48`, `RegisterForm.tsx:56-63`, `ResetPasswordForm.tsx:47-54` — all password inputs `type="password"` with correct `autocomplete="current-password"`/`"new-password"`.
- No password hashing logic anywhere in `frontend/src/*.tsx` (passwords sent as plaintext over the wire, which is correct — TLS is the transport protection, per `as-6`'s own frontend check).
- Full-repo search for `localStorage.setItem`/`sessionStorage.setItem`: zero matches in `frontend/src`.

**Issues:** None.

**Manual review required:** None.

---

### as-7: Access Control Check Enforcement

**Status:** WARN

**Severity:** Low (LR:1 → WARN)

**Checks performed:**
- Checked for `@EnableMethodSecurity` and method-level annotations.
- Checked `SecurityFilterChain` `.authorizeHttpRequests()` rule coverage and ordering.
- Checked frontend for route guards, role-based rendering, and 401/403 handling.

**Evidence:**
- `backend/src/main/java/com/assessment/securedhelloworld/config/SecurityConfig.java:70-77` — `.requestMatchers("/api/csrf").permitAll()`, `.requestMatchers("/api/register","/api/login").permitAll()`, `.requestMatchers("/api/password-reset/**").permitAll()`, `.requestMatchers("/api/admin/**").hasRole("ADMIN")`, `.anyRequest().authenticated()` — specific-before-broad ordering is correct; default-deny via `anyRequest().authenticated()` (no `permitAll()` catch-all).
- No `@EnableMethodSecurity` anywhere in `backend/src/main/java` (confirmed via full read of `SecurityConfig.java` and grep for the annotation) — method-level security is not enabled; all authorization is URL-rule based only.
- `backend/src/main/java/com/assessment/securedhelloworld/admin/AdminUserService.java:36,45,55` — service-layer self-action guards (`requireNotSelf`) exist but are not Spring-Security-annotation-based; they are plain Java conditionals.
- `frontend/src/AdminUsersPage.tsx:16-24` — 403 handled locally per-component (`setForbidden(true)` on `ApiError.status === 403`), not via a global interceptor or reusable `ProtectedRoute`/`useAuth`-based route guard; `frontend/src/App.tsx:60-66` gates the `admin` view only by `view` state, not by role — a `USER`-role account that manually sets `view = 'admin'` would still hit `AdminUsersPage`, which then correctly receives a 403 from the backend and shows the forbidden message (so the backend is authoritative, but there's no proactive frontend role check preventing the render attempt).
- `frontend/src/AuthContext.tsx:16-27` — global `401` handling exists in `refresh()` (transitions to `anonymous` state), but there is no global axios/fetch interceptor auto-attaching this on every request beyond the `credentials: 'include'` cookie mechanism (which is itself correct and sufficient, since auth is cookie/session based, not header-token based).

**Issues:**
- URL-based authorization is correctly implemented and is the authoritative backend control — this is a legitimate WARN, not FAIL, since the control accepts either method-level annotations OR URL-rule coverage, and URL-rule coverage is present and correctly ordered.
- The gap is the missing defense-in-depth layer (method-level annotations) and the frontend's reliance on reactive 403 handling rather than proactive role-based route gating.

**Manual review required:**
- "Verify the two-role model (USER/ADMIN) is sufficiently coarse-grained for the app's actual risk profile, or whether method-level `@PreAuthorize` is warranted as defense-in-depth given the admin endpoints' destructive capabilities (delete/disable/role-change)."

---

### as-8: Secrets Management

**Status:** WARN

**Severity:** Medium (LR:1 → WARN)

**Checks performed:**
- Scanned `application.yml`/`application-dev.yml` for hardcoded secrets.
- Searched Java/TS source for hardcoded passwords/API keys/tokens.
- Checked `.gitignore` for `.env` coverage and searched for committed `.env` files.
- Checked frontend for localStorage token storage.

**Evidence:**
- `backend/src/main/resources/application.yml:15-16` — `app.admin.password: ChangeMe123456!` is a literal default value for the bootstrap admin account, read via `@ConfigurationProperties(prefix = "app.admin")` in `AdminBootstrapProperties.java:1-24` — this is a placeholder intended to be overridden by an externalized production value (its name literally says "ChangeMe"), but as committed it is a real credential value in a tracked config file.
- `backend/src/main/java/com/assessment/securedhelloworld/bootstrap/AdminBootstrapRunner.java:36-42` — this value is used directly to seed a real ADMIN account with no forced-change flag (cross-reference `ac-6` FAIL).
- Full-repo search for `private.*key\s*=\s*"`, `secret\s*=\s*"`, `token\s*=\s*"` patterns in Java source: zero matches beyond the config property above.
- `.gitignore:3` — `.env` is listed; `frontend/.gitignore:11` also excludes `*.local`. Glob search for `**/*.env*` across the whole repo returned zero files — no `.env` file exists (committed or otherwise).
- `frontend/src/api.ts:1` — `const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';` — this is a public base-URL config, not a secret, correctly prefixed `VITE_`.
- Full-repo search for `localStorage.setItem`/`sessionStorage.setItem` with token/auth-related keys in `frontend/src`: zero matches — session is cookie-based (`credentials: 'include'` in `api.ts:39`), never stored in Web Storage.

**Issues:**
- The `app.admin.password` default value in a tracked YAML file is the primary finding. While clearly named as a placeholder, IM8-style secrets-management review conventionally flags any credential-shaped literal in a tracked config file, since this file ships as-is in a build artifact if the production profile does not override it. This is a config-hygiene WARN, not FAIL, since (a) the value is scoped to dev/bootstrap use, (b) it is externalizable via standard Spring `@ConfigurationProperties` binding, and (c) no team decision confirms it reaches production unchanged.

**Manual review required:**
- "Confirm the `app.admin.password` default is always overridden via environment variable or secrets manager in any non-local deployment, and is never the value actually used in a live environment."
- "Verify no secrets manager (AWS Secrets Manager, Vault) integration is required for this reference app's deployment model, or whether one should be added before any real deployment."

### as-9: Content Security Policy (CSP)

**Status:** FAIL

**Severity:** Medium (LR:1 → FAIL)

**Checks performed:**
- Searched Spring Security config for `.contentSecurityPolicy(...)`.
- Checked `application.yml` for CSP-related properties.
- Checked `frontend/index.html` for a CSP `<meta>` tag.

**Evidence:**
- `backend/src/main/java/com/assessment/securedhelloworld/config/SecurityConfig.java:59-77` — the `.headers(...)` block only configures `frameOptions(frame -> frame.sameOrigin())` (`SecurityConfig.java:64`); no `.contentSecurityPolicy(...)` call exists anywhere in the file or the rest of `backend/src/main/java`.
- Full-repo grep for `contentSecurityPolicy|Content-Security-Policy|hsts|actuator`: zero matches anywhere in `backend/`.
- `frontend/index.html:1-13` — full file read confirms no `<meta http-equiv="Content-Security-Policy">` tag present.

**Issues:**
- Per the control's own rule: "If no CSP found in either backend headers or frontend meta tag, FAIL." Neither exists. This is a genuine gap, not a framework-delegation ambiguity.

**Manual review required:**
- "Define and implement a CSP appropriate to the app's actual resource origins (self only — no CDN scripts/styles observed in `index.html` or any `.tsx` file) before any production deployment."

---

### as-10: HTTP Strict Transport Security (HSTS)

**Status:** PASS

**Severity:** —

**Checks performed:**
- Checked for explicit HSTS disabling in Spring Security config.

**Evidence:**
- `backend/src/main/java/com/assessment/securedhelloworld/config/SecurityConfig.java:59-77` — no `.httpStrictTransportSecurity(hsts -> hsts.disable())` or any HSTS override exists; Spring Security 6.x's default (enabled, max-age 1 year, includeSubDomains) applies unmodified.

**Issues:** None found in application code. HSTS is only meaningful once served over HTTPS, which is a deployment-layer concern (see `dp-3`).

**Manual review required:**
- "Confirm the production deployment topology serves the app over HTTPS so the HSTS header has any effect (see dp-3)."

---

### as-11: Session Management

**Status:** WARN

**Severity:** Medium (LR:1 → WARN)

**Checks performed:**
- Checked for `server.servlet.session.timeout`.
- Checked `.sessionManagement()` config, `maximumSessions`, fixation protection.
- Checked frontend for idle-timeout detection, token-expiry handling, and logout state-clearing.

**Evidence:**
- `backend/src/main/resources/application.yml` and `application-dev.yml` — full file reads confirm no `server.servlet.session.timeout` property is set anywhere (falls back to the servlet container's default, typically 30 minutes, exceeding the control's recommended ≤15-minute guidance).
- `backend/src/main/java/com/assessment/securedhelloworld/config/SecurityConfig.java:66-69` — `.sessionManagement(session -> session.maximumSessions(-1).sessionRegistry(sessionRegistry).expiredSessionStrategy(...))` — `maximumSessions(-1)` means **unlimited concurrent sessions per user** are explicitly allowed (not a bounded limit).
- `backend/src/main/java/com/assessment/securedhelloworld/auth/LoginService.java:45` — `sessionAuthenticationStrategy = new ChangeSessionIdAuthenticationStrategy()` — session-fixation protection is correctly implemented (session ID is regenerated on login).
- `backend/src/main/java/com/assessment/securedhelloworld/auth/LogoutController.java:20-24` — logout calls `session.invalidate()` and `SecurityContextHolder.clearContext()`.
- `backend/src/test/java/com/assessment/securedhelloworld/auth/LogoutAndHelloIntegrationTest.java` — test confirms replayed session cookies post-logout are rejected.
- `frontend/src/AuthContext.tsx:1-56` — full file read: no idle-timer library (`react-idle-timer`), no `mousemove`/`keydown` activity tracking, no JWT-`exp` decoding (not applicable — session is cookie-based, not JWT), no client-side session-expiry countdown.
- `frontend/src/AuthContext.tsx:29-32` — `logout()` clears in-memory React state (`username`, `status`) — no `localStorage`/`sessionStorage` to clear since none is used (consistent with `as-6`/`as-8` findings).

**Issues:**
- Session-fixation protection and logout/session-invalidation are correctly implemented and tested. The gaps are: (1) no explicit session timeout configured (relies on container default rather than an explicit, auditable value), (2) `maximumSessions(-1)` explicitly permits unlimited concurrent sessions rather than a bounded limit, (3) no frontend idle-timeout UX.

**Manual review required:**
- "Confirm the servlet container's default session timeout value and whether it meets the ≤15-minute organisational policy threshold, or set `server.servlet.session.timeout` explicitly."
- "Confirm whether unlimited concurrent sessions (`maximumSessions(-1)`) is an accepted product decision or should be bounded."

---

### as-12: Malware Scanning of Uploaded Files

**Status:** N/A

**Severity:** —

**Checks performed:**
- Searched the entire codebase for file-upload endpoints, `MultipartFile` parameters, `<input type="file">` elements, and any SFS/scanner client integration.

**Evidence:**
- Full read of all 15 backend Java source files under `backend/src/main/java` and all 11 frontend `.tsx`/`.ts` files under `frontend/src`: no `MultipartFile`, no upload controller/endpoint, no `<input type="file">`, no dropzone/uploader component of any kind exists anywhere in the application.
- `backend/pom.xml` has no multipart/file-scanning related dependency.

**Issues:** None — control is not applicable; this application has no file-upload feature.

**Manual review required:** None (confirm no future roadmap item adds file upload without revisiting this control).

---

### as-13: Exposure of Internal System Details

**Status:** WARN

**Severity:** High (LR:2 → WARN)

**Checks performed:**
- Checked `server.error.include-*` properties.
- Checked for `@RestControllerAdvice`/`@ExceptionHandler` generic error responses.
- Checked Actuator exposure.
- Checked frontend for Error Boundaries, verbatim API-error display, console-log stripping, and sourcemap config.

**Evidence:**
- `backend/src/main/resources/application.yml` and `application-dev.yml` — no `server.error.include-stacktrace`, `include-message`, or `include-binding-errors` properties set anywhere — Spring Boot's safe defaults (`never`) apply unmodified. ✓ PASS on this sub-check.
- `backend/src/main/java/com/assessment/securedhelloworld/web/ApiExceptionHandler.java:22-64` — `@RestControllerAdvice` exists with dedicated `@ExceptionHandler`s for every custom exception type, each returning a generic `Map.of("error", ...)` body; none call `e.getMessage()` on a raw framework exception or expose `e.getStackTrace()`. The class-level Javadoc (`ApiExceptionHandler.java:17-19`) explicitly documents this intent.
- `backend/pom.xml` — no `spring-boot-starter-actuator` dependency present anywhere — so there is no actuator-endpoint exposure risk (this sub-check is N/A within the control, not a finding).
- `frontend/src/*.tsx` — full read of all components: **no React Error Boundary** (`componentDidCatch`, `getDerivedStateFromError`, or `react-error-boundary`) exists anywhere in the app. An unhandled render exception in any component would surface React's default white-screen/dev-overlay behavior rather than a controlled fallback.
- `frontend/src/LoginForm.tsx:23`, `RegisterForm.tsx:20`, `ForgotPasswordForm.tsx:14`, `ResetPasswordForm.tsx:20`, `AdminUsersPage.tsx:20,32,42,52` — all consistently render `err instanceof ApiError ? err.message : <generic fallback>` — `ApiError.message` is sourced from the backend's own generic error body (e.g., `"Login failed"`), not a raw stack trace or internal detail, so this pattern does not leak internals given the backend's `ApiExceptionHandler` is itself generic.
- `frontend/vite.config.ts:1-10` — full file read: no `build.sourcemap` setting (Vite's production default is `sourcemap: false`, so absence is safe here) and no console-stripping plugin/config (Terser `drop_console` or ESLint `no-console` rule) — `frontend/.oxlintrc.json` was not confirmed to include a `no-console` rule.

**Issues:**
- Backend error-detail suppression is correctly implemented (would be PASS in isolation). The frontend's total absence of an Error Boundary is a genuine defense-in-depth gap — an uncaught render-time exception has no controlled fallback UI. Combined assessment is WARN rather than FAIL because the primary vector (verbatim backend error/stacktrace exposure) is well-guarded; the residual risk is an uncontrolled React crash screen, not a data leak.

**Manual review required:**
- "Add a top-level React Error Boundary with a generic fallback UI as defense-in-depth."
- "Confirm production build pipeline (`vite build`) does not emit sourcemaps by default in the deployed artifact — verify by inspecting a production build's `dist/` output."

---

### as-14: Secure Cryptographic Libraries

**Status:** PASS

**Severity:** —

**Checks performed:**
- Checked for weak/deprecated crypto algorithms (DES, RC4, MD5, SHA-1 for signatures) in backend and frontend.
- Verified `SecureRandom` usage for security-sensitive randomness.
- Checked for hardcoded IVs/keys.

**Evidence:**
- `backend/src/main/java/com/assessment/securedhelloworld/passwordreset/PasswordResetService.java:20,109-112` — `SecureRandom SECURE_RANDOM = new SecureRandom()` used to generate the 32-byte plaintext reset token (`generatePlaintextToken()`), not `java.util.Random`.
- Full-repo grep for `DES|RC4|RC2|MD5|SHA-1` in `backend/src/main/java`: zero matches (BCrypt, used via `BCryptPasswordEncoder` and `org.springframework.security.crypto.bcrypt.BCrypt`, is the only cryptographic primitive in use).
- No frontend cryptographic operations exist anywhere in `frontend/src` (no Web Crypto API, no `crypto-js`, no `Math.random()` for security-sensitive values) — control's frontend checks are not triggered.

**Issues:** None.

**Manual review required:** None.

---

### as-15: Password Change

**Status:** N/A

**Severity:** —

**Applicability check performed:** This control concerns a *forced* password change following an account-unlock event (lockout → mandatory reset before further access). This application's lockout is purely time-boxed and self-expiring (`User.isLocked(Instant.now())`, `LoginAttemptService.java:47-53` sets `lockedUntil`, which lapses automatically) — there is no admin-driven "unlock" action that reissues a temporary credential, and therefore no event that should trigger a forced password change under this control's specific trigger condition (unlock-after-lockout).

**Checks performed:**
- Searched for a `forcePasswordChange`/`mustResetPassword` field on `User` — confirmed absent (`User.java:1-129`, full read).
- Searched for any unlock endpoint/flow — none exists (lockout self-expires only).

**Evidence:**
- `backend/src/main/java/com/assessment/securedhelloworld/user/User.java` — no forced-password-change field exists.
- No admin "unlock account" endpoint exists in `AdminUserController.java` (only enable/disable, role-change, delete).

**Issues:** None under this control's specific trigger — but note the closely related `ac-6` control (default/temporary credentials) **does FAIL** for a different reason (the seeded bootstrap admin credential is never forced to change). Do not conflate the two: this control is specifically about post-unlock forced reset, which has no applicable trigger event in this app's design.

**Manual review required:**
- "Confirm this time-boxed, self-expiring lockout design (no admin unlock, no forced reset after unlock) is an accepted product decision, not an oversight — cross-reference with ac-6 finding on the bootstrap admin credential."

---

### lm-4: Audit Logging

**Status:** WARN

**Severity:** Low (LR:1 → WARN)

**Checks performed:**
- Checked for an audit-logging framework (`AuditApplicationEvent`, `@CreatedBy`/`@LastModifiedBy`).
- Checked for logging of authentication events, authorization failures, and admin data modifications.
- Checked frontend for audit-supporting correlation IDs / event tracking.

**Evidence:**
- `backend/src/main/java/com/assessment/securedhelloworld/auth/LoginService.java:66,72,80` — `log.info("Login failed: unknown username...")`, `log.info("Login rejected: account locked...")`, `log.info("Login failed: bad credentials...")`, and `LoginService.java:84` — `log.info("Login succeeded username={}", ...)`.
- `backend/src/main/java/com/assessment/securedhelloworld/auth/LoginAttemptService.java:52` — `log.warn("Account locked username={} attempts={}", ...)`; `LoginAttemptService.java:88` — `log.warn("IP throttled ip={} attempts={}", ...)`.
- `backend/src/main/java/com/assessment/securedhelloworld/admin/AdminUserService.java:38-39,48-49,58` — every admin mutation (`enable/disable`, `role-change`, `delete`) logs `actor=` and `target=` fields.
- `backend/src/main/java/com/assessment/securedhelloworld/auth/LogoutController.java:26` — logout is logged with username.
- No `AuditApplicationEvent`/`@EventListener(AuditApplicationEvent.class)`, no `@CreatedBy`/`@LastModifiedBy` JPA auditing annotations exist anywhere — these are plain SLF4J log statements, not a structured audit-event framework.
- Frontend: no correlation-ID header (`X-Request-ID`) is attached in `frontend/src/api.ts:36-44`'s `apiFetch` headers construction; no analytics/event-tracking integration exists.

**Issues:**
- Security-relevant events (login success/failure, lockout, IP-throttle, all admin mutations, logout) are consistently logged with actor/target context — this substantively satisfies the control's intent, but via plain logs rather than a dedicated audit trail/event framework, and with no request correlation ID for traceability. Per the control's own note, frontend audit gaps are WARN not FAIL, and backend is authoritative here.

**Manual review required:**
- "Confirm plain SLF4J logging (without a structured `AuditApplicationEvent` framework) satisfies the organisation's audit-trail retention/immutability requirements, or whether a dedicated audit table/framework is required."
- "Consider adding a request-correlation-ID header for cross-service traceability."

---

### lm-15: Structured Log Formatting

**Status:** FAIL

**Severity:** Critical (LR:2 → FAIL)

**Checks performed:**
- Checked `application.yml` for `logging.structured.format.file: ecs`.
- Searched for `logback-spring.xml`/`logback.xml` with an ECS/Logstash/JSON encoder.

**Evidence:**
- `backend/src/main/resources/application.yml` and `application-dev.yml` — full file reads: no `logging.structured.format.file` property anywhere.
- `backend/src/main/resources/` directory listing — only two files exist (`application.yml`, `application-dev.yml`); no `logback-spring.xml` or `logback.xml` file exists anywhere in the repository.
- All logging observed throughout the codebase (e.g., `LoginService.java:66`, `AdminUserService.java:38`) uses default SLF4J/Logback plain-text pattern output (Spring Boot's un-configured default), not JSON/ECS.

**Issues:**
- Neither PASS condition is met. This is an unambiguous gap: no structured logging configuration exists anywhere in the backend.

**Manual review required:**
- "Add either `logging.structured.format.file: ecs` (Spring Boot 3.4+ native) or a `logback-spring.xml` with an ECS/JSON encoder before any environment where log aggregation/SIEM ingestion is required."

---

### lm-16: Key Signals Monitoring

**Status:** FAIL

**Severity:** Critical (LR:2 → FAIL)

**Checks performed:**
- Checked for `spring-boot-starter-actuator` dependency (Actuator path).
- Checked for custom Micrometer meters (`@Timed`/`Timer`/`Counter`/`Gauge`) plus a `MeterRegistry` bean (custom path).
- Checked frontend for RUM/performance instrumentation.

**Evidence:**
- `backend/pom.xml:22-58` — full dependency list reviewed: `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-session-core`, `h2`, plus test-scoped `spring-boot-starter-test`/`spring-security-test`. **No `spring-boot-starter-actuator`.**
- Full-repo grep for `@Timed|Timer|Counter|Gauge|MeterRegistry` across `backend/src/main/java`: zero matches.
- `frontend/package.json` — no `web-vitals`, no Sentry/Datadog browser SDK, no RUM/analytics package.

**Issues:**
- Neither the Actuator path nor the custom-Micrometer path exists. Per the control: "Absence of both paths is FAIL." No latency, traffic, error, or saturation signal is captured anywhere in this application.

**Manual review required:**
- "Add `spring-boot-starter-actuator` (minimum) before any production deployment to get baseline HTTP/JVM signal coverage, and confirm dashboards/alerting are configured downstream once metrics exist."

---

### lm-18: Whole of Government Application Analytics (WOGAA)

**Status:** N/A

**Severity:** —

**Applicability check:** Confirmed internal/reference-app via `PRODUCT.md` — "Assumed evaluation context: this is a coding-assessment reference/demo app... not a mass consumer audience" and no public-facing government service claim exists anywhere in the product documentation.

**Checks performed:** N/A — control skipped per applicability rule.

**Evidence:** `PRODUCT.md:13-15` (Users section — Visitor/User/Admin roles, no citizen-facing framing); no `.internal`/government domain claims but also no public-facing government-service claim of any kind.

**Manual review required:**
- "If this codebase is ever repurposed into an actual public-facing government digital service, revisit this control."

---

### lm-19: Log Sanitisation

**Status:** WARN

**Severity:** High (LR:2 | MR:1 → WARN)

**Checks performed:**
- Searched for masking layout/filter (`MaskingPatternLayout`, `TurboFilter`).
- Scanned all `log.*()` call sites for direct logging of sensitive fields.
- Checked `toString()`/`@ToString.Exclude` usage on entities with PII.
- Checked frontend logger/error-reporting for scrubbing config.

**Evidence:**
- No `MaskingPatternLayout`, custom `TurboFilter`, or regex-based log-scrubbing utility exists anywhere in `backend/src/main/java` (confirmed via full source read — 15 files).
- `backend/src/main/java/com/assessment/securedhelloworld/registration/RegistrationService.java:24-25` — explicit code comment: "Deliberately never log request.getPassword() (or any derivative of it) anywhere in this method, including in exception messages" — and the method body confirms no such call exists.
- `backend/src/main/java/com/assessment/securedhelloworld/registration/RegistrationService.java:38` — `log.info("Registered new user username={}", saved.getUsername())` — logs only the username, not email/password.
- `backend/src/main/java/com/assessment/securedhelloworld/passwordreset/LoggingEmailService.java:17-20` — the reset **link** (containing the plaintext single-use token) is logged by design as the email-stub mechanism, with an explicit code comment justifying this as "the one place it is legitimately handed to the user — never elsewhere in the logs" (`LoggingEmailService.java:11-13`). This token is short-lived (30 min per `application.yml:29`) and single-use.
- `backend/src/test/java/com/assessment/securedhelloworld/registration/RegistrationIntegrationTest.java:74-79` — an integration test actively asserts the plaintext password never appears in any log message (`plaintextLogged` assertion), providing executable proof of this control's core requirement for the registration path.
- `User.java` and `PasswordResetToken.java` have no Lombok `@ToString`/`@Data` at all (plain hand-written getters/setters, no auto-generated `toString()` that could leak `passwordHash` or `tokenHash`) — so the "WARN: `@Data` without `@ToString.Exclude`" sub-condition does not even arise.
- No frontend logging/error-reporting utility exists in `frontend/src` — this sub-section is not applicable per the control's own note ("skip if no frontend logger is present").

**Issues:**
- No masking framework exists (the formal WARN trigger per the control's own criteria), but manual review of every log call site found no active PII/secret leak — the one intentional token-in-log case (`LoggingEmailService`) is a deliberate, scoped, documented design substituting for real email delivery, not an oversight. This is squarely the control's own defined WARN case: "no log statements in the codebase actually log those entities" for sensitive fields other than the documented, scoped exception.

**Manual review required:**
- "Confirm the reset-link-in-logs pattern in `LoggingEmailService` is acceptable given it is an explicit development/demo stand-in for real email delivery (per PRD, real SMTP is out of scope) — this must be replaced with real email delivery (not console/log output) before any production use, since production log aggregation would otherwise capture live reset tokens."

### ck-1 / ck-2 / ck-4: Cryptographic Key Establishment / Rotation / Storage

**Status:** N/A (all three)

**Severity:** —

**Applicability check:** These controls apply only if the application manages its own cryptographic key material (not delegated to KMS, and not just password hashing/token generation). This app uses only:
1. `BCryptPasswordEncoder` for password hashing (a KDF, not "key establishment" in the sense the control means — no `KeyGenerator`/`KeyPairGenerator`/`KeyAgreement` usage exists).
2. `SecureRandom` for generating password-reset tokens (random value generation, not key management).

**Checks performed:**
- Full-repo grep for `KeyGenerator|KeyPairGenerator|KeyAgreement|SecretKeyFactory`: zero matches in `backend/src/main/java`.
- Glob search for `**/*.{pem,key,p12,pfx,jks,keystore}` across the entire repository: zero files found.
- Checked `application.yml`/`application-dev.yml` for any `server.ssl.key-store` or inline key material: none configured (no TLS keystore configured at all — app runs over plain HTTP in dev, per `dp-3` finding).
- Scanned frontend source for `-----BEGIN`/long Base64 strings assigned to key-named variables: zero matches.

**Evidence:**
- No cryptographic key management of any kind exists in this application's own code.

**Issues:** None — genuinely not applicable.

**Manual review required:**
- "If TLS termination is later configured at the application level (rather than at a proxy/LB), revisit ck-4 for keystore-password handling under as-8, not this control."

---

### ga-8: Inform Users about GenAI Risks and Limitations

**Status:** N/A

**Severity:** —

**Applicability check:** No Generative AI features exist anywhere in the application (confirmed via full read of `PRODUCT.md` and all backend/frontend source — this is a plain CRUD/auth reference app with no AI/LLM integration of any kind).

**Manual review required:**
- "If any GenAI feature is added in the future, revisit this control."

---

### ac-1: Principle of Least Privilege

**Status:** WARN

**Severity:** Medium (default Level 1 → WARN)

**Checks performed:**
- Checked URL-role mapping completeness and ordering.
- Checked for method-level security switch and annotations.
- Checked for ownership/tenancy checks in the service layer.
- Checked frontend for centralized 401/403 handling and permission-derived UI.

**Evidence:**
- `backend/src/main/java/com/assessment/securedhelloworld/config/SecurityConfig.java:70-77` — rule order is specific-to-broad (`/api/csrf`, `/api/register`, `/api/login`, `/api/password-reset/**` all `permitAll()`; `/api/admin/**` `hasRole("ADMIN")`; then `anyRequest().authenticated()`) — correct ordering, no shadowing, default-deny via `authenticated()` (not `permitAll()`).
- No `@EnableMethodSecurity` anywhere (same finding as `as-7`).
- `backend/src/main/java/com/assessment/securedhelloworld/admin/AdminUserService.java:64-68` — `requireNotSelf` is the one ownership-style check present (prevents an admin acting on their own account), implemented as plain Java, not a Spring Security expression.
- `frontend/src/AdminUsersPage.tsx:16-24` — 403 handled per-component; `frontend/src/AuthContext.tsx:16-27` — 401 handled centrally in the shared `refresh()` used by `AuthProvider`.
- No centralized `usePermission`/`hasRole` utility or `ProtectedRoute` wrapper component exists in `frontend/src` — role-based UI gating for the "Manage users" link is absent: `frontend/src/HelloPage.tsx:14-16` renders the "Manage users" button unconditionally for any authenticated user (not gated on `role === 'ADMIN'`), relying entirely on the backend's 403 to block non-admins after they click through.

**Issues:**
- Backend authorization is correctly implemented as the authoritative control (URL-rule based, correctly ordered, default-deny). The identified gaps are: (1) no method-level defense-in-depth, (2) the frontend shows the admin-navigation entry point to all authenticated users regardless of role, which is a UX/least-astonishment issue rather than a security bypass (backend still enforces the actual 403), but does not reflect the principle of least privilege in the UI layer as the control expects.

**Manual review required:**
- "Confirm whether frontend role-based hiding of the 'Manage users' entry point for non-admin users is required, or whether reactive 403 handling is an accepted design choice for this size of app."

---

### ac-2: Multi-Factor Authentication (MFA) Enforcement

**Status:** N/A

**Severity:** —

**Applicability check:** Applies to privileged account logins (ADMIN role exists and is privileged) and privileged actions (admin user-management mutations qualify). No MFA implementation of any kind exists.

**Checks performed:**
- Searched for MFA state fields (`mfaEnabled`, `totpEnabled`, `totpSecret`) on `User` entity: absent (`User.java:1-129`, full read).
- Searched for MFA verification endpoints (`/api/auth/mfa/verify`, `/api/auth/totp`): absent.
- Searched `pom.xml` for TOTP/WebAuthn libraries (`dev.samstevens.totp`, `com.warrenstrange:googleauth`, `webauthn4j`, `yubico-webauthn`): absent.
- Checked frontend for a step-up challenge UI: absent.

**Evidence:**
- `PRODUCT.md:42` — "Out of scope (explicitly, per PRD): JWT implementation, MFA/2FA, real SMTP, containerization/CI/CD/hosting infra, local HTTPS, granular per-resource authorization beyond the USER/ADMIN check." This is an explicit, documented, product-level scoping decision, not an oversight.

**Issues:** None from an automation-gap perspective — the absence is deliberate and documented. Marked N/A rather than FAIL because the applicability of this control depends on organisational policy scope, and the product has explicitly and visibly declared this out of scope for this reference/demo context.

**Manual review required:**
- "Confirm with the assessing organisation whether MFA-for-ADMIN is a hard requirement for this reference app's evaluation context, given `PRODUCT.md` explicitly scopes it out — if required, this becomes a FAIL against ac-2's applicability, not N/A."

---

### ac-3: Inactive and Expired Accounts

**Status:** FAIL

**Severity:** Medium (default Level 1 → FAIL)

**Checks performed:**
- Checked for an `enabled`/`active` field on `User` (exists).
- Checked for a scheduled job detecting 90-day dormancy or account/role expiry and auto-disabling.
- Checked for SCIM-based deprovisioning.

**Evidence:**
- `backend/src/main/java/com/assessment/securedhelloworld/user/User.java:33-34` — `private boolean enabled = true;` field exists and is settable.
- Full-repo grep for `@Scheduled` across `backend/src/main/java`: zero matches — **no scheduled job of any kind exists in this application.**
- No `lastLoginAt` or `accountExpiresAt` field exists on `User.java` at all, so even if a scheduled job were added, there is no data captured to evaluate dormancy or expiry against.
- No SCIM server dependency in `pom.xml`.

**Issues:**
- The only account-disabling mechanism is the manual admin action `AdminUserService.updateEnabled` (`AdminUserService.java:33-40`). There is no automated detection of inactivity or expiry, and critically, no `lastLoginAt` timestamp is even tracked, so this control cannot be satisfied without both a new field and a new scheduled process.

**Manual review required:**
- "Confirm whether automated dormancy/expiry detection is in scope for this reference app's evaluation, or whether it is an accepted, documented gap alongside the other explicitly out-of-scope items in PRODUCT.md (which does not currently mention this)."

---

### ac-4: Access Review

**Status:** FAIL

**Severity:** Medium (default Level 1 → FAIL)

**Checks performed:**
- Checked for a self-contained scheduled review-and-revoke pipeline comparing granted privileges against a declared baseline.

**Evidence:**
- No `@Scheduled` job exists anywhere (same finding as `ac-3`).
- No declared per-account permission baseline/entitlements file (e.g. `service-accounts.yml`) exists anywhere in the repo.
- The only two roles (`USER`, `ADMIN` — `Role.java`) are changed exclusively via the manual `AdminUserService.updateRole` admin action; there is no periodic reconciliation process.

**Issues:**
- This application does have application-managed accounts (local username/password with a role table), so the control is applicable, and no periodic access-review mechanism exists at all.

**Manual review required:**
- "If this reference app is representative of a real system, confirm the org's plan for periodic (≤5-day) access review of the ADMIN role grant."

---

### ac-6: Default Credentials

**Status:** FAIL

**Severity:** Medium (default Level 1 → FAIL)

**Checks performed:**
- Checked for a `forcePasswordChange`/`mustResetPassword` field on `User`.
- Checked whether this flag is set on account-creation/admin-reset/unlock paths.
- Checked for a filter blocking access to non-password-change endpoints while the flag is set.

**Evidence:**
- `backend/src/main/java/com/assessment/securedhelloworld/user/User.java:1-129` — full read confirms **no forced-password-change field exists** on the `User` entity at all.
- `backend/src/main/java/com/assessment/securedhelloworld/bootstrap/AdminBootstrapRunner.java:36-44` — the seeded ADMIN account is created with a password sourced directly from `app.admin.password` config (default value `ChangeMe123456!` per `application.yml:16`) and is immediately usable with **no forced change on first login.**
- No admin-created-user flow exists at all currently (self-registration via `RegistrationController` is the only account-creation path besides bootstrap), so there is no "admin creates account with temp password" scenario beyond the bootstrap admin itself — but that one scenario is exactly what this control targets and it fails the requirement.
- No servlet filter/interceptor exists anywhere that would block access pending a forced password change (consistent with the field not existing at all).

**Issues:**
- The bootstrap admin account is the clearest instance of a "default credential" in this codebase, and there is no mechanism forcing it to be changed before use. This is a genuine, concrete gap with direct exploitability if the `ChangeMe123456!` default is ever left unchanged in a real deployment.

**Manual review required:**
- "This is the highest-priority actionable gap in this report given the credential is on-disk in a tracked file (cross-reference as-8) and is never forced to be changed (ac-6) — treat as-8 + ac-6 together as a single remediation item: externalize the bootstrap password AND add a forced-change-on-first-login mechanism."

---

### ac-7: Singpass / Corppass for Public Users

**Status:** N/A (both Part A and Part B)

**Severity:** —

**Applicability check:** This application has no external public/citizen users and no corporate/business users — it is an internal reference/demo app with self-registration scoped to the assessment context (per `PRODUCT.md`). No high-risk transactions (financial disbursement, legal/binding submissions, permit issuance) exist anywhere in its feature set (registration, login, password reset, admin user CRUD).

**Manual review required:**
- "If this codebase is ever repurposed to serve real citizens or businesses with high-risk transactions, revisit both Part A and Part B."

---

### ac-8: Automated Account Lifecycle Management

**Status:** FAIL

**Severity:** Medium (default Level 1 → FAIL)

**Checks performed:**
- Checked for Pattern A (SCIM/equivalent push provisioning) — dependency and endpoints.
- Checked for Pattern B (SSO JIT provisioning) — custom `OAuth2UserService`/`OidcUserService`/SAML2 provisioning hook.

**Evidence:**
- `backend/pom.xml` — no SCIM library, no `com.okta`, no `com.microsoft.graph`, no Google Admin SDK dependency.
- Full-repo grep for `scim|OidcUserService|OAuth2UserService|Saml2Authentication` across `backend/src/main/java`: zero matches — no OIDC/SAML2 client registration exists anywhere (`SecurityConfig.java` uses only local form-based session auth).
- All account creation is either self-registration (`RegistrationController`) or the one-time bootstrap admin seed (`AdminBootstrapRunner`) — neither is an automated internal-user lifecycle mechanism as this control defines it.

**Issues:**
- This control specifically scopes to **internal user accounts**. The ADMIN role here functions as the closest analog to an "internal/privileged user" in this app's model, and its entire lifecycle (creation via bootstrap, disable/role-change/delete via manual admin action) has zero automation — no push provisioning, no JIT SSO.

**Manual review required:**
- "Confirm whether this reference app's single-admin-bootstrap model is an accepted design given its demo scope, or whether automated lifecycle management should be added if any internal-staff usage is intended beyond the assessment."

---

### ac-12: Single Sign-On (SSO) for Internal Services and Accounts

**Status:** N/A

**Severity:** —

**Applicability check:** Applies only to internal services/accounts. This application does not use SSO for any account type (all authentication is local username/password) and there is no declared "internal service" context distinct from the ADMIN role already assessed under `ac-2`/`ac-8`. Given `PRODUCT.md` explicitly documents this as a local-auth reference app with no IdP integration in scope, SSO enforcement for internal accounts is not an applicable requirement for this specific deployment model.

**Evidence:**
- No OIDC/SAML2 client registration exists anywhere in `application.yml`/`SecurityConfig.java` (same evidence as `ac-8`).
- `frontend/src/LoginForm.tsx:30-49` — a local username/password form exists and is the only login mechanism (no SSO redirect button/link anywhere in the frontend).

**Manual review required:**
- "If this app is ever deployed for actual internal staff (beyond ADMIN-as-demo-role), confirm whether SSO is required per organisational policy — currently local auth is used throughout, which would need revisiting."

---

### dp-3: Data in Transit Encryption

**Status:** WARN

**Severity:** Medium (default Level 1 → WARN)

**Checks performed:**
- Checked for `server.ssl.enabled`/SSL bundle configuration.
- Checked for deprecated TLS protocol configuration.
- Checked for hardcoded `http://` URLs to sensitive endpoints.
- Checked for disabled certificate/hostname validation.
- Checked frontend for `http://` references and CSP `upgrade-insecure-requests`.

**Evidence:**
- `backend/src/main/resources/application.yml` and `application-dev.yml` — no `server.ssl.enabled`, no `server.ssl.key-store`, no `spring.ssl.bundle` configured anywhere — the application has no in-app TLS termination configured at all.
- `backend/src/main/resources/application.yml:9-13` — `server.servlet.session.cookie: { http-only: true, same-site: lax, secure: true }` is the **base/default profile**, correctly defaulting the session cookie to `Secure` (HTTPS-only).
- `backend/src/main/resources/application-dev.yml:15-19` — the `dev` profile explicitly overrides `secure: false`, with a code comment explaining this is solely because local dev runs over plain HTTP and a `Secure` cookie would never be sent back by the browser over HTTP (`application-dev.yml:11-14` comment block) — this is a deliberate, documented, dev-only relaxation, not a production misconfiguration.
- No custom `X509TrustManager`, `TrustAllStrategy`, or `HostnameVerifier` override exists anywhere in `backend/src/main/java` (full-repo grep: zero matches) — no certificate/hostname validation is disabled.
- `frontend/src/api.ts:1` — `API_BASE_URL` defaults to `http://localhost:8080` for local dev, overridable via `VITE_API_BASE_URL` env var for other environments — no hardcoded `http://` reference to a real/sensitive external endpoint exists.
- No CSP exists at all (cross-reference `as-9` FAIL), so `upgrade-insecure-requests` is moot until a CSP is introduced.

**Issues:**
- No in-app TLS configuration exists, meaning HTTPS enforcement is entirely a deployment/proxy-layer responsibility that this audit cannot verify from source code alone — this is the expected pattern for an app fronted by a TLS-terminating load balancer/proxy, but it must be explicitly confirmed operationally. The base profile's `secure: true` cookie default is correctly restrictive.

**Manual review required:**
- "Determine deployment topology: confirm TLS termination occurs at a proxy/LB/ingress layer and that HTTP→HTTPS redirect occurs there, since the application itself has no TLS configuration."
- "If behind a TLS-terminating proxy, verify `server.forward-headers-strategy` is set — currently **absent** from both `application.yml` and `application-dev.yml`, meaning the app cannot correctly perceive `X-Forwarded-Proto`/original HTTPS status if deployed behind a reverse proxy; this could cause the `secure: true` cookie flag to behave unpredictably in that topology."

---

### dp-8: Data Classification Disclosure

**Status:** N/A

**Severity:** —

**Applicability check:** Applies only to internal applications serving public officers. This application's users (per `PRODUCT.md`) are a technical reviewer/assessment audience with generic USER/ADMIN roles, not public officers processing classified government data — no data-classification scheme (`OFFICIAL OPEN`, `RESTRICTED`, etc.) is referenced anywhere in the product's scope or its input fields (username, email, password — none of which carry a government classification level).

**Manual review required:**
- "If this app is repurposed for an actual public-officer-facing internal system handling classified data, revisit this control for every input field."

---

### pm-6: System Documentation

**Status:** FAIL

**Severity:** Medium (default Level 1 → FAIL)

**Checks performed:**
- Checked for `ARCHITECTURE.md`, `docs/architecture/`, network topology diagrams, ADR directory.
- Checked for software/hardware inventory artifacts.
- Checked for a committed OpenAPI/Swagger spec.
- Checked frontend documentation (README, Storybook).

**Evidence:**
- Glob search for `docs/**` from repo root returned only `docs/agents/issue-tracker.md`, `docs/agents/triage-labels.md`, `docs/agents/domain.md` — these are agent-tooling process docs (issue-tracker conventions, triage labels), **not** system/architecture documentation.
- No `ARCHITECTURE.md` exists at the repo root or anywhere else (confirmed via the full top-level directory listing).
- No `/adr` or `/docs/adr` directory exists anywhere in the repository (only agent-skill-related ADR *templates*/*format guides* exist under `.claude/skills/domain-modeling/ADR-FORMAT.md` and similar — these are tooling scaffolding, not the project's own recorded architecture decisions).
- `backend/pom.xml` serves as the software-inventory source of truth for the backend (acceptable per the control); `frontend/package.json`/`package-lock.json` likewise for the frontend — these sub-checks pass individually.
- No committed `openapi.yaml`/`openapi.json`/`swagger.json` exists anywhere in the repo — the API surface (11 REST endpoints across register/login/logout/hello/password-reset/admin) is undocumented as a formal, version-controlled spec.
- `frontend/README.md` (528 bytes area, per file listing) and the repo-root `PRODUCT.md`/`DESIGN.md` document product intent and design tokens, not system architecture, data flows, or deployment topology — per the control's own explicit rule: "A basic README with only setup/build instructions is NOT sufficient."

**Issues:**
- No architecture documentation, ADRs, network diagram, or OpenAPI spec exists anywhere in this repository. `PRODUCT.md` is a strong, detailed product-truth document, but it is not a substitute for system/architecture documentation per this control's explicit non-substitution rule.

**Manual review required:**
- "Add, at minimum, a committed OpenAPI spec for the 11 REST endpoints and a short ARCHITECTURE.md covering the auth/session model, data flow (frontend↔backend↔H2), and deployment assumptions (CORS origins, cookie/session mechanics) — much of this content already exists informally in code comments and PRODUCT.md and could be consolidated quickly."

---

### st-3: Public Vulnerability Disclosure Programme

**Status:** N/A

**Severity:** —

**Applicability check:** Internal reference/demo application, not public-facing (same basis as `lm-18`/`ac-7`/`dp-8` — confirmed via `PRODUCT.md`).

**Checks performed:** N/A — control skipped per applicability rule. For completeness: no `security.txt` file exists at `backend/src/main/resources/static/.well-known/` (no `static/` directory exists in the backend at all), and no "Report Vulnerability" footer link exists anywhere in the 8 frontend `.tsx` files.

**Manual review required:**
- "If this codebase is ever deployed publicly, add a `security.txt` or footer disclosure link before doing so."

---

## Manual Review Checklist

Items that cannot be fully automated and require human/organisational verification:

- [ ] as-1: Confirm frontend/backend validation rule parity as rules evolve; consider adding a runtime validation library (Zod) to the frontend for defense-in-depth.
- [ ] as-4: Confirm lockout/IP-throttle thresholds are appropriate; confirm `X-Forwarded-For` trust boundary if deployed behind a proxy; consider bucket4j or equivalent for horizontal-scale-safe rate limiting.
- [ ] as-5: Confirm whether a length-only password policy (≥12 chars, no complexity rule) is an accepted, deliberate design.
- [ ] as-7 / ac-1: Confirm whether method-level `@PreAuthorize` defense-in-depth is warranted given admin endpoints' destructive capability; confirm whether hiding the "Manage users" link from non-admins in the UI is required.
- [ ] as-8 / ac-6: **Priority item** — externalize the bootstrap admin password (`ChangeMe123456!` in `application.yml`) to a secrets store/environment variable for any non-local deployment, and add a forced-password-change-on-first-login mechanism for that account.
- [ ] as-9: Define and implement a Content-Security-Policy before any production deployment (currently absent — self-only origins observed).
- [ ] as-11: Set an explicit `server.servlet.session.timeout` (≤15 min per typical org policy) and confirm `maximumSessions(-1)` (unlimited concurrent sessions) is an accepted design.
- [ ] as-13: Add a React Error Boundary; verify production build strips console output and sourcemaps.
- [ ] as-15/ac-6: Confirm the self-expiring, non-admin-unlock lockout design is intentional, and cross-reference with ac-6's bootstrap-credential finding.
- [ ] ac-2: Confirm with the assessing organisation whether the PRD's explicit "MFA out of scope" decision is acceptable for this evaluation, or whether it should be reclassified as a FAIL.
- [ ] ac-3 / ac-4: Confirm whether automated dormancy/expiry detection and periodic access review are in scope for this reference app, or documented as accepted gaps.
- [ ] ac-8 / ac-12: Confirm whether any future internal-staff usage (beyond the assessment context) requires SSO/automated provisioning.
- [ ] dp-3: Confirm deployment topology (TLS-terminating proxy) and add `server.forward-headers-strategy` configuration if deployed behind one.
- [ ] lm-4: Confirm plain SLF4J logging is sufficient for audit-trail requirements, or whether a dedicated audit-event framework is needed; consider adding request correlation IDs.
- [ ] lm-15 / lm-16: **Priority items** — add structured/JSON logging (ECS format) and at minimum `spring-boot-starter-actuator` for baseline observability before any production use; currently zero metrics/structured-logs exist.
- [ ] lm-19: Confirm the reset-link-in-logs pattern (`LoggingEmailService`) is acceptable as a documented dev/demo stand-in, and must be replaced with real email delivery before production (so live reset tokens never appear in aggregated logs).
- [ ] pm-6: Add a committed OpenAPI spec and a short architecture/ADR document — much of the needed content already exists informally in code comments and PRODUCT.md.

### Files Reviewed

**Backend**
- backend/pom.xml
- backend/src/main/resources/application.yml
- backend/src/main/resources/application-dev.yml
- backend/src/main/java/com/assessment/securedhelloworld/SecuredHelloWorldApplication.java
- backend/src/main/java/com/assessment/securedhelloworld/config/SecurityConfig.java
- backend/src/main/java/com/assessment/securedhelloworld/config/CsrfTokenController.java
- backend/src/main/java/com/assessment/securedhelloworld/user/User.java
- backend/src/main/java/com/assessment/securedhelloworld/user/Role.java
- backend/src/main/java/com/assessment/securedhelloworld/user/UserRepository.java
- backend/src/main/java/com/assessment/securedhelloworld/auth/LoginController.java
- backend/src/main/java/com/assessment/securedhelloworld/auth/LoginService.java
- backend/src/main/java/com/assessment/securedhelloworld/auth/LoginAttemptService.java
- backend/src/main/java/com/assessment/securedhelloworld/auth/LoginRequest.java
- backend/src/main/java/com/assessment/securedhelloworld/auth/LogoutController.java
- backend/src/main/java/com/assessment/securedhelloworld/auth/AppUserDetails.java
- backend/src/main/java/com/assessment/securedhelloworld/auth/AppUserDetailsService.java
- backend/src/main/java/com/assessment/securedhelloworld/auth/ClientIpResolver.java
- backend/src/main/java/com/assessment/securedhelloworld/auth/AuthenticationFailedException.java
- backend/src/main/java/com/assessment/securedhelloworld/registration/RegistrationController.java
- backend/src/main/java/com/assessment/securedhelloworld/registration/RegistrationService.java
- backend/src/main/java/com/assessment/securedhelloworld/registration/RegistrationRequest.java
- backend/src/main/java/com/assessment/securedhelloworld/registration/DuplicateAccountException.java
- backend/src/main/java/com/assessment/securedhelloworld/passwordreset/PasswordResetController.java
- backend/src/main/java/com/assessment/securedhelloworld/passwordreset/PasswordResetService.java
- backend/src/main/java/com/assessment/securedhelloworld/passwordreset/PasswordResetToken.java
- backend/src/main/java/com/assessment/securedhelloworld/passwordreset/PasswordResetTokenRepository.java
- backend/src/main/java/com/assessment/securedhelloworld/passwordreset/PasswordResetConfirmRequest.java
- backend/src/main/java/com/assessment/securedhelloworld/passwordreset/PasswordResetRequestRequest.java
- backend/src/main/java/com/assessment/securedhelloworld/passwordreset/EmailService.java
- backend/src/main/java/com/assessment/securedhelloworld/passwordreset/LoggingEmailService.java
- backend/src/main/java/com/assessment/securedhelloworld/passwordreset/InvalidResetTokenException.java
- backend/src/main/java/com/assessment/securedhelloworld/admin/AdminUserController.java
- backend/src/main/java/com/assessment/securedhelloworld/admin/AdminUserService.java
- backend/src/main/java/com/assessment/securedhelloworld/admin/AdminUserView.java
- backend/src/main/java/com/assessment/securedhelloworld/admin/UpdateRoleRequest.java
- backend/src/main/java/com/assessment/securedhelloworld/admin/UpdateStatusRequest.java
- backend/src/main/java/com/assessment/securedhelloworld/admin/UserNotFoundException.java
- backend/src/main/java/com/assessment/securedhelloworld/admin/SelfActionForbiddenException.java
- backend/src/main/java/com/assessment/securedhelloworld/hello/HelloController.java
- backend/src/main/java/com/assessment/securedhelloworld/bootstrap/AdminBootstrapRunner.java
- backend/src/main/java/com/assessment/securedhelloworld/bootstrap/AdminBootstrapProperties.java
- backend/src/main/java/com/assessment/securedhelloworld/web/ApiExceptionHandler.java
- backend/src/test/java/com/assessment/securedhelloworld/SecuredHelloWorldApplicationTests.java
- backend/src/test/java/com/assessment/securedhelloworld/config/SecurityHardeningIntegrationTest.java
- backend/src/test/java/com/assessment/securedhelloworld/auth/LoginIntegrationTest.java
- backend/src/test/java/com/assessment/securedhelloworld/auth/IpThrottlingIntegrationTest.java
- backend/src/test/java/com/assessment/securedhelloworld/auth/LogoutAndHelloIntegrationTest.java
- backend/src/test/java/com/assessment/securedhelloworld/registration/RegistrationIntegrationTest.java
- backend/src/test/java/com/assessment/securedhelloworld/passwordreset/PasswordResetIntegrationTest.java
- backend/src/test/java/com/assessment/securedhelloworld/admin/AdminUserManagementIntegrationTest.java
- backend/src/test/java/com/assessment/securedhelloworld/bootstrap/AdminBootstrapIntegrationTest.java
- backend/src/test/java/com/assessment/securedhelloworld/user/UserRepositoryTest.java

**Frontend**
- frontend/package.json
- frontend/vite.config.ts
- frontend/index.html
- frontend/src/main.tsx
- frontend/src/App.tsx
- frontend/src/api.ts
- frontend/src/AuthContext.tsx
- frontend/src/LoginForm.tsx
- frontend/src/RegisterForm.tsx
- frontend/src/ForgotPasswordForm.tsx
- frontend/src/ResetPasswordForm.tsx
- frontend/src/AdminUsersPage.tsx
- frontend/src/HelloPage.tsx

**Project-level**
- PRODUCT.md
- DESIGN.md
- .gitignore
- frontend/.gitignore
