# IM8 Application Controls Compliance Report

**Target:** EMR Demo Application (eds-spring-boot-starter-parent/emr-demo-app + eds-admin-fe)
**Date:** 2026-08-11
**Stack:** React 19 (TypeScript) + Spring Boot 3.4 / Spring Security 6.4
**Risk Classification:** Medium Risk (internal EMR serving internal medical staff)
**Auditor:** IM8 Automated Compliance Audit

---

## Summary

| Control | Status | Severity | Finding |
|---------|--------|----------|---------|
| as-1 | WARN | Low | Validation at service/command layer (not at `@RequestBody` controller boundary); no `@Valid` on controller params; frontend has Zod validation on forms but no runtime validation of API responses |
| as-2 | PASS | — | JPA/Querydsl used throughout; no raw SQL, no string-concatenated queries found |
| as-3 | PASS | — | React auto-escapes; no `dangerouslySetInnerHTML` found; `@RestController` returns JSON; error pages use static HTML; TipTap rich-text editor present but no XSS escape-hatch found |
| as-4 | WARN | Medium | Rate-limiting for login delegated entirely to `eds-spring-boot-starter` framework — no application-level config visible; README explicitly states rate limiting is not implemented; no UI-level 429 handling found in frontend |
| as-5 | PASS | — | Password regex enforces 12+ chars with upper, lower, digit, special char in Zod schema (`user.ts:70`); backend delegates to framework; `maxPasswordHistoryLength: 3` configured |
| as-6 | WARN | Medium | `PasswordEncoder` bean not visible in application code — delegated to `eds-spring-boot-starter-user-*`; framework likely uses BCrypt but cannot confirm from application source |
| as-7 | WARN | Low | `@PreAuthorize` present on privileged endpoints; URL-role mapping in `url-guard-paths.yml`; however, `getVisitRecordByPatientIds` (VisitRecordController:32) has no `@PreAuthorize` and no URL rule; frontend guard uses client-side role check (WARN) |
| as-8 | WARN | Low | No hardcoded secrets in production config; secrets use env-var placeholders (`${PLEASE_CHANGE}`); **Google OAuth credentials hardcoded in dev config** (`auth-mcc.yml:56-57`); `PLEASE_CHANGE` placeholders indicate production config not finalised |
| as-9 | FAIL | High | No CSP header configuration found in application code or `index.html`; not configured in `application.yml` or any security config |
| as-10 | WARN | High | HSTS enabled by Spring Security default (not explicitly disabled); cannot confirm HTTPS enforcement or max-age >= 31536000 without reviewing framework's `SecurityFilterChain`; SSL is configured via `server.ssl.*` in `application.yml` |
| as-11 | WARN | Medium | Session timeout set to 15 minutes (prod and local); no frontend idle-timeout implementation found; logout clears `initiateSetup` from localStorage but session management otherwise opaque to application |
| as-12 | WARN | High | MCC profile: `FileBatchScannerAutoConfiguration` imported — SFS scanning delegated to `eds-spring-boot-starter-file-autoconfigure-sfs`; **prod file-config has `scanner.scanRequired: false`** — bypassing scan in production; `SFSFileStatusRecord` tracks only `fileId`/`status` (no jobAttempts or retry logic in application layer); Standalone: direct promotion logic delegated to framework |
| as-13 | PASS | — | `server.error.include-stacktrace: never`, `include-message: never`, `include-binding-errors: never` in `application.yml`; custom error controller returns static HTML; actuator exposes only `health` endpoint |
| as-14 | PASS | — | No weak crypto algorithms (DES, RC4, MD5 for crypto) found in application code; KMS client used for MCC profile; AWS SDK used; no `Math.random()` for security purposes in frontend |
| as-15 | WARN | — | `require-password-change` flag exists in dev-accounts config and is recognised by the framework; enforcement filter lives in opaque `eds-spring-boot-starter-user-*` — cannot confirm from application code; `ChangePasswordForm` exists in frontend but no forced-redirect handling on login observable |
| lm-4 | WARN | Low | JPA auditing enabled (`@EnableJpaAuditing`); `AuditMetadata` base class captures `createdBy`/`modifiedBy` on all entities; authentication event logging delegated to opaque `eds-spring-boot-starter-logging` — cannot confirm from application source |
| lm-15 | WARN | High | `eds-spring-boot-starter-logging` imported and `LoggingContextAutoConfiguration` wired; no `logback-spring.xml` or `logging.structured.format.file: ecs` visible in application source — structured logging delegated to opaque starter |
| lm-16 | WARN | High | `spring-boot-starter-actuator` present (auto-provides HTTP latency, traffic, error, JVM/CPU metrics); only `health` actuator endpoint exposed; no custom `@Timed`/`Counter`/`Gauge` meters in application code; no frontend RUM/error reporting library detected |
| lm-18 | N/A | — | Internal application — WOGAA not required |
| lm-19 | WARN | High | `ServiceUtils.validate()` logs `constraintViolationException.getMessage()` which may expose PII field values (e.g., invalid NRIC) in error logs; no `MaskingPatternLayout` or Logback masking in application code; Lombok `@Data` on entities lacks `@ToString.Exclude` on PII fields (`nric`, `contactNumber`, `allergies`) |
| ck-1 | WARN | Medium | KMS client configured via AWS SDK for MCC profile (delegated to AWS KMS); standalone profile uses framework-managed keys; no application-level `KeyGenerator`/`KeyPairGenerator` found — cannot confirm key establishment parameters without reviewing framework jars |
| ck-2 | WARN | Medium | Key rotation fully delegated — MCC profile uses AWS KMS (rotation policy is external); no rotation schedule or key versioning in application code |
| ck-4 | N/A | — | No application-managed cryptographic key storage found; MCC profile delegates entirely to AWS KMS; standalone delegates to framework |
| ga-8 | N/A | — | No GenAI features detected in application code or package dependencies |
| ac-1 | WARN | Medium | URL-role mapping via `url-guard-paths.yml` present; `@PreAuthorize` on most endpoints; `getVisitRecordByPatientIds` endpoint lacks access control; actuator base path includes all roles in whitelist (`/actuator/**` is permit-all in `url-guard-paths.yml`) |
| ac-2 | WARN | Medium | `@MultiFactorAuthentication` annotation used on `createLetter` (TOTP) and `createClinicalAddendum` (PIN); MFA framework imported; no application-level MFA enforcement at login level visible — step-up only at operation level; full login MFA flow delegated to opaque framework |
| ac-3 | WARN | Medium | No application-level scheduled job for dormancy/expiry detection found in application code; `AccountStatus` enum includes `DISABLED`; lifecycle management likely delegated to `eds-spring-boot-starter-user-*` framework |
| ac-4 | WARN | Medium | No self-contained access-review/revoke job found in application code; privilege baseline defined in `roles-and-privileges.yml` but no automated comparison/revocation pipeline visible |
| ac-6 | WARN | Medium | `require-password-change` property recognised by framework seeder; enforcement filter opaque to application code; `ChangePasswordForm` exists; dev-accounts have `require-password-change: false` for most accounts (security risk for dev environment) |
| ac-7 | N/A | — | Internal app — Singpass/Corppass not required |
| ac-8 | WARN | Medium | MCC profile uses SSO JIT via `OAuth2LoginSecurityFilterChainAutoConfiguration` + `DevUserAuthoritiesConfiguration`; JIT deprovisioning not verified in application code; standalone profile uses in-app user management with no SCIM endpoint visible |
| ac-12 | WARN | Medium | MCC profile: OAuth2/OIDC configured pointing to `sit.auth.defcloud.gov.sg` (corporate IdP); standalone profile uses local username/password — **no SSO for standalone** which may be used in production offline deployments |
| dp-3 | PASS | — | `server.ssl.enabled: true` with PKCS12 keystore configured via env vars; no hardcoded `http://` URLs in production config; vite.config.ts dev proxy uses `http://localhost:8080` (dev-only, acceptable) |
| dp-8 | FAIL | High | Input fields (patient data entry in EMR side, user management forms in admin FE) lack per-field data classification labels; `SensitiveDataReminderDialog` is a one-time dismissible popup — does NOT satisfy per-field persistent labelling requirement |
| pm-6 | FAIL | High | No architecture documentation, data-flow diagram, network topology document, or ADR directory found in the repository; README covers only build/run instructions; API spec (`springdoc.api-docs.enabled: false` in prod) not committed to repository |
| st-3 | N/A | — | Internal application — Public Vulnerability Disclosure not required |

---

## Detailed Findings

### as-1: Input Validation

**Status:** WARN

**Severity:** Low

**Checks performed:**
- Reviewed all `@RestController`/`@RepositoryRestController` classes for `@Valid`/`@Validated` on `@RequestBody` params
- Reviewed service command layer for validation logic
- Reviewed frontend forms for Zod schema validation
- Checked API response validation in frontend

**Evidence:**
- `src/main/java/eds/medicaldemo/controller/VitalRecordController.java:29` — `@RequestBody VitalRecord vitalRecord` — no `@Valid`
- `src/main/java/eds/medicaldemo/controller/VitalRecordController.java:37` — `@RequestBody VitalRecord vitalRecord` — no `@Valid`
- `src/main/java/eds/medicaldemo/controller/VitalRecordController.java:45` — `@RequestBody VitalRecord vitalRecord` — no `@Valid`
- `src/main/java/eds/medicaldemo/controller/LetterController.java:72` — `@RequestBody Letter letter` — no `@Valid`
- `src/main/java/eds/medicaldemo/service/commands/letters/HandleBeforeCreateLetterCommand.java:41-91` — explicit `validate()` called in `execute()` with field and state validation
- `src/main/java/eds/medicaldemo/service/commands/orders/UploadMedicalReportCommand.java:56-85` — explicit `validate()` called in `execute()`
- `src/main/java/eds/medicaldemo/service/commands/patient/HandlePatientCreationCommand.java:32-37` — `jakarta.validation.Validator` injected and `validatePatientFields()` called
- `src/main/java/eds/medicaldemo/model/validation/patients/PatientValidator.java:20-70` — Bean Validation annotations (`@NRIC`, `@NotNull`, `@Size`, `@Pattern`) present on validator DTO
- `eds-admin-fe/src/app/_schema/user.ts:70-102` — Zod schemas (`CreateUserFormSchema`, `ChangePasswordFormSchema`) with regex enforcement
- `eds-admin-fe/src/app/_auth/components/login-form-schema.ts:3-6` — Login form validated with Zod; password field has no minimum length constraint
- No runtime validation of API responses in frontend (TypeScript types only)

**Issues:**
- `@Valid` not present at controller boundary on `@RequestBody` parameters — validation is performed in the service/command layer (`execute()` calls `validate()` first), which provides validation but not at the earliest entry point
- Frontend login form does not validate password minimum length (Zod schema has no `.min()` on password)
- No runtime API response validation (Zod `.parse()` on API responses) — TypeScript types are erased at runtime

**Manual review required:**
- Verify the command pattern's `execute()→validate()` template is consistently enforced across all commands (not overrideable to skip validation)
- Verify frontend and backend validation rules are aligned
- Verify URL path parameters (UUID values) are validated for format correctness before use in JPA queries

---

### as-2: Parameterised Interfaces

**Status:** PASS

**Severity:** —

**Checks performed:**
- Searched all Java source for `java.sql.Statement`, `nativeQuery = true`, raw string SQL concatenation, `JdbcTemplate`
- Reviewed repository classes for query patterns
- Reviewed frontend API calls for URL parameter construction

**Evidence:**
- All repositories extend Spring Data JPA interfaces (`JpaRepository`, `JpaSpecificationExecutor`) — no raw SQL found
- `src/main/java/eds/medicaldemo/repository/specifications/` — Specification classes use type-safe JPA Criteria API
- `com.querydsl:querydsl-jpa` in `pom.xml` — QueryDSL used for dynamic queries (parameterised)
- No `nativeQuery = true`, no `JdbcTemplate`, no raw `Statement` usage found in any Java file
- Frontend API calls use typed generated client (`@/__generated__/openapi/origin`) — no manual URL string concatenation with user input found

**Issues:**
- None

**Manual review required:**
- Verify no system command execution (`Runtime.exec`, `ProcessBuilder`) uses unsanitised input

---

### as-3: Output Sanitisation

**Status:** PASS

**Severity:** —

**Checks performed:**
- Searched frontend for `dangerouslySetInnerHTML`, `innerHTML`, `document.write`, `eval()`
- Reviewed TipTap rich-text editor usage
- Reviewed backend error responses and `Content-Type` headers
- Reviewed `CustomErrorController`

**Evidence:**
- No `dangerouslySetInnerHTML` found in any `.tsx`/`.ts` file
- No `document.write`, `eval()`, `insertAdjacentHTML()` found in frontend
- `src/main/java/eds/medicaldemo/config/CustomErrorController.java:16-28` — returns static HTML filenames (`error-404.html`, `error-500.html`) — no user data echoed
- `@RestController` endpoints return `ResponseEntity<>` typed responses — JSON by default
- TipTap editor present (package.json) but no `dangerouslySetInnerHTML` usage found in components; TipTap renders via its own DOM management
- `src/main/java/eds/medicaldemo/controller/LetterController.java:44` — explicitly sets `produces = MediaType.APPLICATION_PDF_VALUE` for PDF download
- React 19 auto-escapes string values in JSX — XSS risk only through explicit escape hatches (none found)

**Issues:**
- None

**Manual review required:**
- Verify TipTap editor does not allow raw HTML injection through letter content fields in the EMR frontend (eds-emr-fe, not audited here)
- Verify the `RichTextJsonValidator.validate()` call (`HandleBeforeCreateLetterCommand.java:72`) enforces a safe JSON schema that prevents script injection

---

### as-4: Authentication Mechanism Rate-Limiting

**Status:** WARN

**Severity:** Medium

**Checks performed:**
- Searched for `bucket4j`, `RateLimiter`, `AuthenticationFailureBadCredentialsEvent` in Java source
- Searched `application.yml` and all profile configs for `bucket4j.filters` or rate-limit configuration on login endpoint
- Searched for `retryAttempt`/`failedAttempts` fields on User entity
- Reviewed frontend for 429 handling and submission throttling

**Evidence:**
- `emr-demo-app/README.md:5` — **"You are required to implement your own security protection such as rate limiting based on your project requirements."** — README explicitly acknowledges rate limiting is NOT implemented
- `local/eds-starter-user-config/mfa-mcc.yml:13-15` — `mcns.rateLimitPerPeriod: 2` — this is MFA OTP rate limiting (MCNS), not login rate limiting
- No `bucket4j` dependency in `pom.xml`
- No `AuthenticationFailureBadCredentialsEvent` listener, no `retryAttempt` field in any application Java class
- No rate-limit properties in any `application.yml` profile targeting the login endpoint
- `eds-admin-fe/src/app/_auth/context/current-user-context.tsx:93-108` — login function has no client-side throttle/debounce; no 429 handling
- No `ButtonWithSpinner` or submit-disable on rapid re-submission in login form beyond `loginInProgress` state flag (which resets on navigation — effective but minimal)

**Issues:**
- No application-level login rate limiting implemented (Bucket4j or equivalent)
- README explicitly delegates this responsibility to the application team — it is not handled by the framework
- No 429 response handling in frontend login flow
- No failed-login counter or lockout mechanism in application code

**Manual review required:**
- Implement Bucket4j or infrastructure-level rate limiting on `/login`, `/api/v1/auth/**` endpoints
- Verify if an upstream WAF/API gateway provides rate limiting as a compensating control
- Verify rate-limit thresholds are appropriate (e.g., 5 attempts per minute per IP)

---

### as-5: Password Requirements

**Status:** PASS

**Severity:** —

**Checks performed:**
- Searched for password complexity validation in both backend and frontend
- Reviewed `password.yml` configuration
- Reviewed Zod schemas for password fields

**Evidence:**
- `eds-admin-fe/src/app/_schema/user.ts:70` — `const passwordRegex = /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\S+$).{12,}$/` — enforces 12+ chars, upper, lower, digit, special character, no whitespace
- `eds-admin-fe/src/app/_schema/user.ts:89-101` — `ChangePasswordFormSchema` uses the regex; confirm-password match validated
- `eds-admin-fe/src/app/_users/_components/change-password-form.tsx:83-88` — password field uses `type="password"` (correct)
- `src/main/resources/local/eds-starter-user-config/password.yml:3` — `maxPasswordHistoryLength: 3` — prevents reuse of last 3 passwords
- Dev seed accounts in `dev-accounts-isolated.yml` use `P@ssw0rd1234` (12 chars, meets regex)
- Password validation at the backend enforced by `eds-spring-boot-starter-user-*` framework

**Issues:**
- None found; password requirements are appropriately configured

**Manual review required:**
- Confirm SSO/passwordless is the only auth method for MCC profile (in which case password requirements are managed by the IdP)
- Verify `password.yml` is applied in the production profile (it is only in `local` profile — **production profile does not import `password.yml`**)

---

### as-6: Password Salting and Hashing

**Status:** WARN

**Severity:** Medium

**Checks performed:**
- Searched all Java files for `PasswordEncoder`, `BCrypt`, `NoOpPasswordEncoder`, `MessageDigest`
- Reviewed `pom.xml` for password encoder dependencies
- Checked frontend for client-side password hashing

**Evidence:**
- No `PasswordEncoder` `@Bean` found in any application Java class — entirely delegated to `eds-spring-boot-starter-user-standalone` or `eds-spring-boot-starter-user-sso`
- No `NoOpPasswordEncoder`, `MD5`, `SHA-1` found in application source
- `pom.xml:34-35` — `eds-spring-boot-starter-user-management` imported (no BCrypt bean visible in application code)
- Frontend `current-user-context.tsx:95` — login sends `application/x-www-form-urlencoded` over HTTPS (no client-side hashing, correct)
- No password values stored in `localStorage` or `sessionStorage`

**Issues:**
- `PasswordEncoder` bean implementation is entirely opaque — cannot confirm BCrypt vs other encoder without reviewing framework jars
- Cannot confirm salted hashing from application source alone

**Manual review required:**
- Verify `eds-spring-boot-starter-user-*` uses `BCryptPasswordEncoder` or `Argon2PasswordEncoder` (not `NoOpPasswordEncoder`)
- Confirm password encoder bean is initialised before any user creation/update operations

---

### as-7: Access Control Check Enforcement

**Status:** WARN

**Severity:** Low

**Checks performed:**
- Reviewed all controller methods for `@PreAuthorize` annotations
- Reviewed `url-guard-paths.yml` for URL-role mapping
- Reviewed frontend for route guards and 401/403 handling

**Evidence:**
- `src/main/java/eds/medicaldemo/controller/LetterController.java:29,38,45,55,63` — `@PreAuthorize("hasRole('...')")` on all methods
- `src/main/java/eds/medicaldemo/controller/OrderController.java:33,40,48,56,67,79,86` — `@PreAuthorize` on all methods
- `src/main/java/eds/medicaldemo/controller/VitalRecordController.java:28,36,43` — `@PreAuthorize` on create/update methods
- `src/main/java/eds/medicaldemo/controller/VisitRecordController.java:32` — **`getVisitRecordByPatientIds` has NO `@PreAuthorize`** and is not found in `url-guard-paths.yml`
- `src/main/java/eds/medicaldemo/controller/PatientController.java:24` — `downloadReport` has no `@PreAuthorize` (but is covered by URL rule `[patient/*/report]: GET` for MEDIC role)
- `prod/eds-starter-user-config/url-guard-paths.yml:8-11` — `pathsForWhitelisting` includes `/actuator/**` — actuator path is fully whitelisted without authentication
- `eds-admin-fe/src/routes/_authenticated/_authorized.tsx:18-29` — role check for `USER_MANAGER` before rendering protected routes
- `eds-admin-fe/src/routes/_authenticated.tsx:11-37` — `refetchCurrentUser()` called on mount; renders children only if `currentUser` is set
- No global axios interceptor handling 401/403 responses found in frontend

**Issues:**
- `VisitRecordController.getVisitRecordByPatientIds` — no `@PreAuthorize` and no matching URL guard rule — potentially unauthenticated access to patient visit record data
- `/actuator/**` is whitelisted in `url-guard-paths.yml` — only `health` is exposed via `management.endpoints`, but the security bypass is broader than necessary
- No centralised 401→redirect-to-login interceptor in frontend

**Manual review required:**
- Verify `getVisitRecordByPatientIds` is covered by a framework-level authentication requirement (even if no role check)
- Verify all endpoints intended to be public are intentionally so
- Add global axios interceptor for 401/403 responses in frontend
- Confirm authorization logic (correct roles mapped to correct endpoints) is correct

---

### as-8: Secrets Management

**Status:** WARN

**Severity:** Low

**Checks performed:**
- Scanned all `application.yml` files for hardcoded credentials
- Scanned Java source for hardcoded keys/passwords/tokens
- Checked frontend `.env` files
- Reviewed `.gitignore` for `.env` exclusion

**Evidence:**
- `src/main/resources/application.yml:24` — `key-store-password: ${APP_SSL_KEYSTORE_PASSWORD}` — env var reference (good)
- `src/main/resources/prod/eds-starter-user-config/datasource.yml:9` — `url: ${PLEASE_CHANGE}` — placeholder, not configured
- `src/main/resources/prod/eds-starter-user-config/auth.yml:7-11` — all values are `${PLEASE_CHANGE}` placeholders
- `src/main/resources/local/eds-starter-user-config/auth-mcc.yml:56-57` — **`client-id: 763037038160-...` and `client-secret: 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` hardcoded Google OAuth credentials** in local dev config
- `eds-admin-fe/.env:1` — `BASE_URL=/admin/` — only non-secret config, no API keys
- `eds-admin-fe/.gitignore` — does NOT include `.env` or `.env.local` in exclusion list — `.env` files could be committed

**Issues:**
- Google OAuth `client-id` and `client-secret` hardcoded in `local/eds-starter-user-config/auth-mcc.yml:56-57` — this file may be tracked by git
- `eds-admin-fe/.gitignore` does not explicitly exclude `.env` files (only `*.local` is excluded)
- `${PLEASE_CHANGE}` placeholders in production config indicate production secret injection mechanism not yet implemented

**Manual review required:**
- Rotate the Google OAuth `client-id`/`client-secret` found in `auth-mcc.yml` if this file has ever been committed to a shared repository
- Implement a secrets management solution (AWS Secrets Manager, HashiCorp Vault) for production — `${PLEASE_CHANGE}` must be replaced before go-live
- Add `.env`, `.env.local`, `.env.production` to `eds-admin-fe/.gitignore`
- Verify all frontend-exposed environment variables are safe to be public

---

### as-9: Content Security Policy (CSP)

**Status:** FAIL

**Severity:** High

**Checks performed:**
- Searched Java source for `contentSecurityPolicy` in Spring Security config
- Searched `application.yml` and all profile YAMLs for CSP-related properties
- Searched `index.html` for `<meta http-equiv="Content-Security-Policy">` tag
- Searched for any CSP configuration in any config class

**Evidence:**
- No `contentSecurityPolicy` found in any `.java` file in the application
- No CSP-related properties in any `.yml` file
- `eds-admin-fe/index.html:1-12` — no `<meta http-equiv="Content-Security-Policy">` tag present
- Spring Security default does not set a CSP header — must be explicitly configured
- No nonce injection, no CSP hash configuration found

**Issues:**
- No Content Security Policy header is set — browsers will use their default permissive policy
- An EMR application handling sensitive medical data without CSP is exposed to XSS escalation risks if an injection vector is ever introduced

**Manual review required:**
- Implement CSP via Spring Security: `.headers(h -> h.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; frame-ancestors 'none'")))`
- For TipTap CSS-in-JS or inline styles, configure `style-src 'unsafe-inline'` or nonce-based policy
- Test CSP in report-only mode before enforcing

---

### as-10: HTTP Strict Transport Security (HSTS)

**Status:** WARN

**Severity:** High

**Checks performed:**
- Searched for explicit HSTS disabling in Spring Security config
- Reviewed SSL configuration in `application.yml`
- Checked for any `httpStrictTransportSecurity` configuration

**Evidence:**
- `src/main/resources/application.yml:20-25` — `server.ssl.enabled: ${APP_SSL_ENABLED:true}` — SSL enabled by default
- `src/main/resources/application.yml:21-24` — PKCS12 keystore configured via env vars
- No explicit `.headers(h -> h.httpStrictTransportSecurity(hsts -> hsts.disable()))` found in any Java file
- Spring Security 6.x enables HSTS by default when HTTPS is detected
- No explicit `maxAgeInSeconds` configuration found — relying on Spring Security default (1 year = 31536000 seconds)

**Issues:**
- HSTS relies on Spring Security default behaviour — cannot confirm `max-age` value, `includeSubDomains`, or `preload` from application code alone
- Framework SecurityFilterChain is opaque (`OAuth2LoginSecurityFilterChainAutoConfiguration`, not visible in application source)

**Manual review required:**
- Verify the framework's `SecurityFilterChain` does not disable HSTS
- Explicitly configure HSTS with `maxAgeInSeconds = 31536000` and `includeSubDomains = true`
- Confirm HTTP→HTTPS redirect occurs at application or proxy layer
- Verify `server.forward-headers-strategy` is set correctly if behind a TLS-terminating proxy

---

### as-11: Session Management

**Status:** WARN

**Severity:** Medium

**Checks performed:**
- Reviewed `auth.yml` for session timeout configuration
- Searched for `.sessionManagement()` configuration in Spring Security
- Searched frontend for idle timeout implementation
- Reviewed logout flow in frontend

**Evidence:**
- `src/main/resources/prod/eds-starter-user-config/auth.yml:3` — `spring.session.timeout: 15m` (production)
- `src/main/resources/local/eds-starter-user-config/auth-isolated.yml:3` — `spring.session.timeout: 15m` (local)
- `src/main/resources/local/eds-starter-user-config/auth-mcc.yml:3` — `spring.session.timeout: 15m` (local MCC)
- No frontend idle timer, no `react-idle-timer`, no `mousemove`/`keydown` event listeners for inactivity detection found
- `eds-admin-fe/src/app/_auth/context/user-context.tsx:88-96` — logout clears `initiateSetup` from localStorage and navigates to login — minimal cleanup
- CSRF token is fetched on login (`csrf-token-context.tsx`); `X-CSRF-TOKEN` header used on logout — CSRF protection active
- No concurrent session control (`maximumSessions`) visible in application code

**Issues:**
- No frontend idle-timeout implementation — if the user leaves the browser open, they will not be automatically logged out until the backend session expires (15 min)
- Session management configuration (fixation, concurrent sessions) entirely delegated to opaque framework `SecurityFilterChain`
- `initiateSetup` stored in `localStorage` (persists across sessions) — not a security risk but worth noting

**Manual review required:**
- Implement frontend idle timer (e.g., `react-idle-timer`) to proactively prompt re-authentication after inactivity
- Confirm backend session fixation protection (`migrateSession` or `newSession`) is active in framework SecurityFilterChain
- Verify concurrent session handling policy (should limit to 1 session per user for EMR)

---

### as-12: Malware Scanning of Uploaded Files

**Status:** WARN

**Severity:** High

**Checks performed:**
- Identified deployment profiles (standalone vs MCC/SFS)
- Reviewed MCC file scanning configuration and SFS event listener
- Reviewed `SFSFileStatusRecord` model
- Reviewed file upload configuration
- Reviewed frontend for upload validation

**Evidence:**
- `pom.xml:205-239` — Two profiles: `standalone` imports `eds-spring-boot-starter-file-autoconfigure-standalone`; `mcc` imports `eds-spring-boot-starter-file-autoconfigure-sfs`
- `src/main/java/eds/medicaldemo/deployment/mcc/config/FileBatchConfig.java:12` — `FileBatchScannerAutoConfiguration` imported for MCC profile
- `src/main/java/eds/medicaldemo/deployment/mcc/event/FileScanEventListener.java` — `FileScanEvent` listener wired to `SFSFileStatusHandler`
- `src/main/java/eds/medicaldemo/deployment/mcc/model/SFSFileStatusRecord.java` — only `fileId` and `status` fields — no `jobAttempts`, retry logic, or timestamp in application model
- `src/main/resources/prod/eds-starter-file-config/file-config.yml:8-9` — **`spring.file.eds.scanner.scanRequired: false`** in production config — scanner bypass enabled in PROD
- `src/main/resources/local/eds-starter-file-config/file-config.yml` — no `scanRequired` property (defaults to framework default)
- File upload config: `maxSize: 10485760` (10MB), `acceptedMimeTypes: image/jpeg, image/png, application/pdf` in all profiles
- `src/main/java/eds/medicaldemo/controller/OrderController.java:57-62` — `@AcceptedFiles` annotation on `List<MultipartFile> files` — framework validation
- `eds-admin-fe/src/app/_users/_hooks/use-file-upload.ts:17-18` — only CSV files accepted for batch upload (MIME type `text/csv`)
- `eds-admin-fe/src/components/complex/upload-dropzone.tsx` — no `accept` attribute restriction shown in component props (passed via `getInputProps()` from react-dropzone)

**Issues:**
- **Critical**: `spring.file.eds.scanner.scanRequired: false` in `prod/eds-starter-file-config/file-config.yml` — scanner is explicitly bypassed in the production profile. Files are accepted without malware scanning in production
- Application-level `SFSFileStatusRecord` has no `jobAttempts` field — retry tracking is entirely opaque (delegated to framework)
- No magic bytes validation in application code (delegated to `@AcceptedFiles` annotation in framework)
- No SHA-256 hash computation at upload time in application code
- No encrypted PDF detection in application code

**Manual review required:**
- **Immediately** investigate `scanner.scanRequired: false` in production file-config — determine if this is intentional for a non-internet profile or a misconfiguration
- Verify the `eds-spring-boot-starter-file-autoconfigure-sfs` framework implements all SFS standard phases (0-3) with jobAttempts, zombie cleanup, dirty/clean blob separation
- Verify magic bytes validation and SHA-256 computation are performed in the file autoconfigure core
- Verify the download endpoint checks file state (`DOWNLOADED` only) before serving
- Verify scanner bypass mode is not reachable in any internet-facing deployment
- For standalone profile, verify storage quota enforcement is active

---

### as-13: Exposure of Internal System Details

**Status:** PASS

**Severity:** —

**Checks performed:**
- Reviewed `application.yml` for `server.error.*` settings
- Reviewed `CustomErrorController` for error response content
- Reviewed actuator configuration
- Checked frontend for Error Boundaries and raw error display

**Evidence:**
- `src/main/resources/application.yml:31` — `include-exception: false`
- `src/main/resources/application.yml:32` — `include-stacktrace: never`
- `src/main/resources/application.yml:33` — `include-message: never`
- `src/main/resources/application.yml:34` — `include-binding-errors: never`
- `src/main/resources/application.yml:41-44` — actuator exposes only `health` endpoint
- `src/main/java/eds/medicaldemo/config/CustomErrorController.java:17-28` — returns only static HTML filenames based on status code, no stack trace or message
- `src/main/resources/prod/eds-starter-user-config/springdoc.yml:2` — `api-docs.enabled: false` in production
- `src/main/resources/prod/eds-starter-user-config/datasource.yml:8` — `showSql: false` in production
- Frontend: no React Error Boundary found in application source, but no raw error objects displayed in reviewed components; error messages are generic strings

**Issues:**
- No React Error Boundary (`componentDidCatch`/`getDerivedStateFromError` or `react-error-boundary`) found — unhandled JS errors may surface as blank pages rather than graceful fallback UI (UX concern, not a data exposure risk given other controls)

**Manual review required:**
- Add a React Error Boundary at the root level to catch unhandled render errors and show a generic fallback
- Verify the `eds-spring-boot-starter-exception` module's exception handler (`@ControllerAdvice`) does not echo `e.getMessage()` in response bodies

---

### as-14: Secure Cryptographic Libraries

**Status:** PASS

**Severity:** —

**Checks performed:**
- Searched Java source for weak algorithms: `DES`, `RC4`, `MD5`, `SHA-1`
- Searched for `SecureRandom` vs `Random` usage
- Reviewed KMS client configuration
- Searched frontend for `Math.random()` security usage or weak crypto

**Evidence:**
- No `DES`, `DESede`, `RC4`, `MD5`, `SHA-1` algorithm references found in application Java source
- No `java.util.Random` usage for security-sensitive purposes found
- `src/main/java/eds/medicaldemo/deployment/mcc/config/EDSUserStarterSSOConfig.java:22` — `software.amazon.awssdk.services.kms.KmsClient` imported and used — AWS KMS SDK (secure)
- `pom.xml:125-127` — `com.microsoft.sqlserver:mssql-jdbc` — JDBC driver, no custom crypto
- No `crypto-js`, `sjcl`, or vulnerable crypto packages in `package.json`
- No `Math.random()` usage for security-sensitive values found in frontend source

**Issues:**
- None

**Manual review required:**
- Verify the `eds-spring-boot-starter-*` framework does not use deprecated TLS versions (SSLv3, TLSv1.0, TLSv1.1)

---

### as-15: Password Change

**Status:** WARN

**Severity:** N/A

**Applicability:** Standalone profile uses local username/password — control applies. MCC profile uses SSO — N/A for MCC.

**Checks performed:**
- Searched for `forcePasswordChange`/`mustResetPassword` flag implementation
- Reviewed `require-password-change` in dev-accounts config
- Reviewed frontend for forced password change handling

**Evidence:**
- `src/main/resources/local/eds-starter-user-config/dev-accounts-isolated.yml:17` — `require-password-change: false` property recognised by framework seeder
- `src/main/resources/local/eds-starter-user-config/dev-accounts-isolated.yml:59` — `test` account has `require-password-change: true`
- `eds-admin-fe/src/app/_users/_components/change-password-form.tsx` — change password form exists for admin-initiated password resets
- No servlet filter in application code checking for forced password change on every request
- No `PASSWORD_CHANGE_REQUIRED` response handling in frontend login flow

**Issues:**
- Forced password change enforcement filter is entirely opaque — lives in `eds-spring-boot-starter-user-*` framework, not visible in application code
- Frontend login flow (`current-user-context.tsx:88-109`) does not handle a `PASSWORD_CHANGE_REQUIRED` response specifically — would fall into generic error handler
- Several dev accounts have `require-password-change: false` explicitly set (may mean they bypass the requirement)

**Manual review required:**
- Verify `eds-spring-boot-starter-user-standalone` enforces the forced password change by blocking all non-password-change endpoints when flag is `true`
- Verify account creation (admin-created) always sets `require-password-change: true`
- Add explicit `PASSWORD_CHANGE_REQUIRED` handling to the frontend login flow
- Confirm MCC profile is SSO-only with no local credential login (in which case as-15 is N/A for that profile)

---

### lm-4: Audit Logging

**Status:** WARN

**Severity:** Low

**Checks performed:**
- Searched for JPA auditing configuration
- Reviewed `AuditMetadata` base class
- Checked for authentication event logging
- Reviewed `eds-spring-boot-starter-logging` import

**Evidence:**
- `src/main/java/eds/medicaldemo/config/DBConfig.java:5` — `@EnableJpaAuditing(auditorAwareRef="EDSMedicalDemoAuditorAware")` — JPA auditing enabled
- `src/main/java/eds/medicaldemo/model/AuditMetadata.java` — `@CreatedBy`, `@CreatedDate`, `@LastModifiedBy`, `@LastModifiedDate` on all entities — who created/modified and when is tracked
- `pom.xml:63-65` — `eds-spring-boot-starter-logging` imported — framework provides structured audit logging
- `src/main/java/eds/medicaldemo/deployment/mcc/config/EDSUserStarterSSOConfig.java:32` — `LoggingContextAutoConfiguration` imported
- Authentication events (login success/failure) handled by framework (`OAuth2LoginSecurityFilterChainAutoConfiguration`)

**Issues:**
- Audit logging for authentication events is delegated to the opaque framework — cannot confirm events are captured from application code alone
- No custom audit events for privileged actions (void letter, void order) beyond standard entity modification tracking

**Manual review required:**
- Verify audit events cover all management and security-relevant actions (role changes, account unlock, password reset) per business requirements
- Verify `eds-spring-boot-starter-logging` captures authentication events including login failure

---

### lm-15: Structured Log Formatting

**Status:** WARN

**Severity:** High

**Checks performed:**
- Searched for `logback-spring.xml` in resources
- Searched for `logging.structured.format.file` property
- Reviewed `LoggingContextAutoConfiguration` import

**Evidence:**
- No `logback-spring.xml` or `logback.xml` found in `src/main/resources`
- No `logging.structured.format.file: ecs` in any `application.yml`
- `src/main/java/eds/medicaldemo/deployment/mcc/config/EDSUserStarterSSOConfig.java:32` — `LoggingContextAutoConfiguration` imported
- `src/main/java/eds/medicaldemo/deployment/standalone/config/EDSUserStarterConfig.java:19` — `LoggingContextAutoConfiguration` imported
- `pom.xml:63-65` — `eds-spring-boot-starter-logging` starter imported

**Issues:**
- Structured logging entirely delegated to `eds-spring-boot-starter-logging` / `LoggingContextAutoConfiguration` — cannot confirm JSON/ECS format from application code
- No logback configuration in application source tree

**Manual review required:**
- Verify `eds-spring-boot-starter-logging` produces structured JSON logs in ECS or Logstash format
- If not, add `logging.structured.format.file: ecs` to `application.yml` (requires Spring Boot 3.4+) or configure a `logback-spring.xml` with `EcsEncoder`/`LogstashEncoder`

---

### lm-16: Key Signals Monitoring

**Status:** WARN

**Severity:** High

**Checks performed:**
- Verified `spring-boot-starter-actuator` in `pom.xml`
- Searched for `@Timed`, `Counter`, `Gauge`, `MeterRegistry` in Java source
- Checked frontend for RUM/error reporting libraries

**Evidence:**
- `pom.xml:26-28` — `spring-boot-starter-actuator` imported — auto-provides `http.server.requests` (latency, traffic, errors), JVM/CPU metrics, HikariCP pool metrics
- `src/main/resources/application.yml:41-44` — only `health` actuator endpoint exposed externally (metrics endpoint NOT exposed)
- No `@Timed`, `Counter`, `Gauge`, or `MeterRegistry` usage found in any application Java class
- No Prometheus, Grafana, or APM configuration in any config file
- No `web-vitals`, Sentry, Datadog RUM, or equivalent library in `package.json`

**Issues:**
- Micrometer metrics are collected by Actuator but the `/actuator/metrics` endpoint is not exposed — metrics cannot be scraped by Prometheus/Grafana unless a separate monitoring port or Prometheus endpoint is configured
- No custom business-level metrics (e.g., order creation rate, scan failure counter) instrumented
- No frontend client error reporting to backend-consumable channel

**Manual review required:**
- Confirm dashboards and alert thresholds are configured on collected metrics in Prometheus/Grafana/APM (likely external to the repo)
- Expose `/actuator/metrics` or configure a Prometheus scrape endpoint on a secured management port
- Consider adding custom business metrics for medical order/letter creation, scan failures, and authentication events

---

### lm-18: WOGAA

**Status:** N/A

**Severity:** —

Internal application — WOGAA is not required.

---

### lm-19: Log Sanitisation

**Status:** WARN

**Severity:** High

**Checks performed:**
- Searched for `MaskingPatternLayout`, `TurboFilter`, log masking utilities
- Reviewed Lombok `@Data` usage on entities with PII fields
- Searched for log statements that output sensitive data

**Evidence:**
- `src/main/java/eds/medicaldemo/service/ServiceUtils.java:19` — **`log.error(constraintViolationException.getMessage())`** — `ConstraintViolationException.getMessage()` includes the violated field name and the invalid value (e.g., `"NRIC: S1234567A is invalid"`), exposing PII in error logs
- No `MaskingPatternLayout`, custom Logback `TurboFilter`, or regex masking utility found in application code
- `src/main/java/eds/medicaldemo/model/Patient.java:75` — `@ToString.Exclude` on `visitRecords` (nested collection); PII fields (`nric`, `contactNumber`, `dateOfBirth`, `allergies`) do not have `@ToString.Exclude`
- `src/main/java/eds/medicaldemo/model/AuditMetadata.java` — uses `@Data` (no PII fields in audit metadata itself)
- `src/main/java/eds/medicaldemo/service/commands/letters/HandleBeforeCreateLetterCommand.java:88` — `log.error("Business Exception Detail: ...")` — generic message, no PII (safe)
- `src/main/java/eds/medicaldemo/service/commands/orders/UploadMedicalReportCommand.java:73,78,83` — `log.error` messages are generic, no user data (safe)
- `eds-admin-fe/src/app/_auth/context/csrf-token-context.tsx:37` — `console.debug("Fetching CSRF token")` — safe
- No Sentry, Datadog, or error reporting SDK in frontend

**Issues:**
- **Active PII leak:** `ServiceUtils.validate()` logs `constraintViolationException.getMessage()` — this message contains the invalid field value from `PatientValidator` (e.g., NRIC, contact number, date of birth), which is PII
- `Patient` entity's top-level PII fields are included in Lombok `@Data` default `toString()` — any `log.*("Patient: " + patient)` would leak PII
- No active masking utility to scrub NRIC, contact numbers, or DOB from log output
- Frontend `console.error(error)` in hooks may log API error responses containing PII in production builds; no `drop_console` configured in Vite

**Manual review required:**
- Fix `ServiceUtils.java:19` — replace `log.error(constraintViolationException.getMessage())` with a sanitised message that logs only violation count or constraint type, not the invalid field value
- Add `@ToString.Exclude` to PII fields on `Patient` entity: `nric`, `contactNumber`, `dateOfBirth`, `allergies`, emergency contact fields
- Implement a Logback `MaskingPatternLayout` to scrub NRIC patterns (`[STFGM]\d{7}[A-Z]`), phone numbers, and email addresses
- Configure Vite build to strip console output: `build: { terserOptions: { compress: { drop_console: true } } }`
- Verify `eds-spring-boot-starter-logging` provides PII masking

---

### ck-1: Cryptographic Key Establishment

**Status:** WARN

**Severity:** Medium

**Checks performed:**
- Searched Java source for `KeyGenerator`, `KeyPairGenerator`, `KeyAgreement`
- Reviewed `EDSUserStarterSSOConfig.java` for KMS client configuration
- Reviewed `pom.xml` for crypto library dependencies

**Evidence:**
- `src/main/java/eds/medicaldemo/deployment/mcc/config/EDSUserStarterSSOConfig.java:22-52` — AWS KMS client configured via `software.amazon.awssdk.services.kms.KmsClient`; local dev uses `LocalStackContainer` (KMS emulation)
- No `KeyGenerator`, `KeyPairGenerator`, or `KeyAgreement` in application Java source
- MCC profile key establishment fully delegated to AWS KMS
- Standalone profile key establishment opaque — delegated to `eds-spring-boot-starter-mfa-standalone` and `eds-spring-boot-starter-user-standalone`

**Issues:**
- Key establishment parameters (RSA >= 2048 bits, AES >= 128 bits) cannot be verified from application source — entirely opaque
- Standalone profile cryptographic operations invisible from application code

**Manual review required:**
- Verify AWS KMS key policy enforces appropriate algorithm/key size (RSA-2048+, AES-256)
- Verify standalone profile MFA key establishment uses NIST-approved algorithms
- If custom key derivation is used in framework starters, verify HKDF/PBKDF2 with adequate iterations

---

### ck-2: Cryptographic Key Rotation

**Status:** WARN

**Severity:** Medium

**Checks performed:**
- Searched for key rotation scheduled tasks, key versioning, key ID in encrypted payloads
- Reviewed AWS KMS configuration

**Evidence:**
- MCC profile: AWS KMS used — rotation policy is external to the application (AWS console/Terraform)
- Standalone profile: key rotation entirely delegated to `eds-spring-boot-starter-mfa-standalone` framework
- No `@Scheduled` task for key rotation in application code
- No key version metadata or key TTL configuration in application source

**Issues:**
- Key rotation policy cannot be verified from application code — entirely dependent on AWS KMS configuration or framework implementation

**Manual review required:**
- Verify AWS KMS auto-rotation is enabled for production keys (annual rotation at minimum)
- Verify standalone profile framework rotates MFA keys on a defined schedule
- Document the key rotation policy and interval in system documentation

---

### ck-4: Cryptographic Key Storage

**Status:** N/A

**Severity:** —

No application-managed cryptographic key storage found. MCC profile delegates entirely to AWS KMS. Standalone profile delegates to framework. TLS uses infrastructure-level keystore (`server.ssl.key-store` via env vars). No `KeyPairGenerator`, `SecretKeySpec`, or key file storage in application source.

---

### ga-8: GenAI Risks

**Status:** N/A

**Severity:** —

No Generative AI features detected in application code, Java dependencies (`pom.xml`), or frontend packages (`package.json`). No references to OpenAI, Anthropic, Claude, GPT, LangChain, or similar AI frameworks found. This control is not applicable.

---

### ac-1: Principle of Least Privilege

**Status:** WARN

**Severity:** Medium

**Checks performed:**
- Reviewed `url-guard-paths.yml` for URL-role mapping and default deny
- Reviewed `roles-and-privileges.yml` for role definitions
- Reviewed method-level security annotations
- Reviewed frontend for role-based rendering

**Evidence:**
- `prod/eds-starter-user-config/url-guard-paths.yml:8-11` — `/actuator/**` in `pathsForWhitelisting` — all actuator paths permit-all regardless of the specific endpoint (overly broad)
- `prod/eds-starter-user-config/url-guard-paths.yml:13-14` — `baseUrlGuardPathRoles: USER_ADMIN` — top-level API path requires USER_ADMIN role
- `prod/eds-starter-user-config/roles-and-privileges.yml` — well-defined role hierarchy: USER_MANAGER, USER_ADMIN, USER, MEDIC, MEDICAL_OFFICER with specific privileges
- `src/main/java/eds/medicaldemo/controller/VisitRecordController.java:32` — `getVisitRecordByPatientIds` has no access control
- `src/main/java/eds/medicaldemo/controller/VisitRecordController.java:39` — `findAllPendingRecordsByStatus` has `@PreAuthorize("hasRole('VISIT_RECORD_READ')")`
- `eds-admin-fe/src/routes/_authenticated/_authorized.tsx:18-29` — UI restricts access to `USER_MANAGER` role via client-side check

**Issues:**
- `/actuator/**` is whitelisted (permit-all) — even though only `health` is enabled, the security matcher is broader than the enabled endpoints
- `getVisitRecordByPatientIds` at `VisitRecordController.java:32` has no access control — access to patient visit records without authentication check
- Method-level security annotations (`@EnableMethodSecurity`) not found in application config — may be enabled in the framework but not confirmed

**Manual review required:**
- Replace `/actuator/**` whitelist with `/actuator/health` specifically
- Add `@PreAuthorize` to `getVisitRecordByPatientIds` or add an explicit URL rule
- Verify `@EnableMethodSecurity` is active (likely in framework starter, confirm)
- Confirm frontend role-based UI derives from backend-sourced roles, not client-side hardcoding

---

### ac-2: Multi-Factor Authentication (MFA) Enforcement

**Status:** WARN

**Severity:** Medium

**Checks performed:**
- Searched for `@MultiFactorAuthentication` usage
- Reviewed MFA framework imports
- Reviewed MFA configuration files

**Evidence:**
- `src/main/java/eds/medicaldemo/controller/LetterController.java:70` — `@MultiFactorAuthentication(mfaType = TOTPType.TOTP, privileges = "LETTER_CREATE")` on `createLetter`
- `src/main/java/eds/medicaldemo/deployment/standalone/controller/ClinicalRecordControllerStandalone.java:37` — `@MultiFactorAuthentication(mfaType = PINType.PIN, privileges = "CLINICAL_ADDENDUM_CREATE")` — PIN MFA for clinical addendum creation
- `src/main/java/eds/medicaldemo/repository/handlers/ClinicalAddendumRepositoryHandler.java` — MFA annotation also on repository handler (double protection)
- `src/main/resources/local/eds-starter-user-config/mfa-isolated.yml` — TOTP issuer configured
- `src/main/resources/local/eds-starter-user-config/mfa-mcc.yml` — OTP via email (MCNS) + TOTP configured
- `pom.xml:187-189` — `eds-spring-boot-starter-mfa-standalone` (PIN) and `eds-spring-boot-starter-mfa-mcc` (email OTP + TOTP) in respective profiles
- `application.yml:8-9` — `spring.eds.mfa.maxRetryCount: 2` — MFA retry limit
- No MFA enforcement at the login step (login = step 1 only); MFA is step-up at specific operations (letter creation, clinical addendum creation)
- No evidence of MFA enforcement on admin (USER_MANAGER/USER_ADMIN) login

**Issues:**
- MFA is implemented as step-up at operation level (letter creation, addendum creation) but NOT enforced at login for privileged accounts (USER_MANAGER, USER_ADMIN, MEDICAL_OFFICER)
- Admin accounts in the admin frontend (USER_MANAGER role) have no MFA challenge on login — this is the privileged account login path
- The login flow in `current-user-context.tsx` is a single-step form submission with no second-factor challenge

**Manual review required:**
- Determine if MFA at login level is required by policy for all privileged accounts
- Verify whether the framework `eds-spring-boot-starter-mfa-*` enforces MFA at login or only at step-up operations
- Consider enforcing TOTP at login for USER_MANAGER and USER_ADMIN roles
- Verify the MCC SSO profile provides MFA at the IdP level (PKI/Singpass provides IAL2+)

---

### ac-3: Inactive and Expired Accounts

**Status:** WARN

**Severity:** Medium

**Checks performed:**
- Searched for scheduled jobs for account dormancy/expiry
- Reviewed `AccountStatus` enum
- Searched for `lastLoginAt`, `accountExpiresAt` fields in entities

**Evidence:**
- `eds-admin-fe/src/app/_schema/user.ts:23-27` — `AccountStatus` enum has `ACTIVE`, `DISABLED`, `LOCKED`, `DELETED` — `DISABLED` state exists
- No application-level `@Scheduled` job for dormancy detection found
- No `lastLoginAt` or `accountExpiresAt` field visible in application entity classes
- `src/main/resources/prod/eds-starter-user-config/accounts-batch.yml:3-5` — `spring.batch.job.enabled: false` — Spring Batch jobs disabled
- Lifecycle management likely in `eds-spring-boot-starter-user-management` framework

**Issues:**
- No application-level scheduled job to detect and disable dormant accounts (90-day rule) or expired accounts (5-day SLA)
- Spring Batch explicitly disabled in production — no batch-based account lifecycle jobs
- Account lifecycle management entirely opaque (framework)

**Manual review required:**
- Verify `eds-spring-boot-starter-user-management` implements dormancy detection (90 days) and expiry enforcement (5 days)
- If not, implement a scheduled job that queries users where `lastLoginAt < NOW() - 90 days AND enabled = true` and disables them
- Verify that disabling an account also invalidates active sessions

---

### ac-4: Access Review

**Status:** WARN

**Severity:** Medium

**Checks performed:**
- Searched for access review/revoke pipeline in Java source
- Reviewed role and privilege definitions
- Checked for privilege baseline comparison logic

**Evidence:**
- `prod/eds-starter-user-config/roles-and-privileges.yml` — role-to-privilege mapping defines the privilege baseline
- No scheduled job for periodic access review/revocation found in application code
- Spring Batch disabled in production (`accounts-batch.yml:3`)
- No `@Scheduled` method comparing granted roles against baseline found

**Issues:**
- No automated access review pipeline — no mechanism to detect and revoke excess privileges beyond declared role baseline
- Access review likely managed manually or via external tooling not visible in codebase

**Manual review required:**
- Implement or document an automated periodic access review process (at minimum quarterly)
- If using AWS IAM for infrastructure, use AWS IAM Access Analyzer as a compensating control
- Document the access review process in system documentation (pm-6)

---

### ac-6: Default Credentials

**Status:** WARN

**Severity:** Medium

**Checks performed:**
- Searched for `forcePasswordChange` / `mustResetPassword` flag and enforcement filter
- Reviewed dev-accounts configuration
- Reviewed frontend for forced password change handling

**Evidence:**
- `src/main/resources/local/eds-starter-user-config/dev-accounts-isolated.yml:17` — `require-password-change: false` for most dev accounts — default is set to NOT require password change on first login
- `src/main/resources/local/eds-starter-user-config/dev-accounts-isolated.yml:59` — `test` account has `require-password-change: true` — demonstrates the mechanism exists
- No enforcement filter in application code for forced password change
- `eds-admin-fe/src/app/_users/_components/change-password-form.tsx` — change password UI exists
- No handling of `PASSWORD_CHANGE_REQUIRED` response in login flow

**Issues:**
- Most seeded dev accounts have `require-password-change: false` — these accounts bypass the default credential change requirement (risk if dev accounts are deployed to non-local environments)
- No application-level filter to enforce password change before accessing the system

**Manual review required:**
- Ensure all accounts created by administrators in production have `require-password-change: true`
- Verify the framework blocks all protected endpoints while `require-password-change: true`
- Add `PASSWORD_CHANGE_REQUIRED` handling to the frontend login callback
- Ensure dev-only accounts with `require-password-change: false` cannot reach production environments

---

### ac-7: Singpass/Corppass

**Status:** N/A

**Severity:** —

Internal application serving internal medical staff only. No external public users or corporate users. Singpass/Corppass not required.

---

### ac-8: Automated Account Lifecycle Management

**Status:** WARN

**Severity:** Medium

**Checks performed:**
- Searched for SCIM endpoint implementation
- Reviewed JIT provisioning via OAuth2/OIDC
- Reviewed `DevUserAuthoritiesConfiguration`

**Evidence:**
- `src/main/java/eds/medicaldemo/deployment/mcc/config/EDSUserStarterSSOConfig.java:35` — `OAuth2LoginSecurityFilterChainAutoConfiguration` imported — SSO JIT via OIDC
- `src/main/java/eds/medicaldemo/deployment/mcc/config/DevUserAuthoritiesConfiguration.java` — custom JIT user provisioning from MPDS attributes — maps SSO UUID to roles
- `src/main/java/eds/medicaldemo/deployment/mcc/config/DevUserAuthoritiesConfiguration.java:30-65` — extends `DefaultUserAuthoritiesConfiguration` — maps IdP attributes to `MEDIC`/`MEDICAL_OFFICER` roles
- No SCIM 2.0 endpoints found in application code
- No push-provisioning or deprovisioning webhook found
- Standalone profile: manual admin account creation (no JIT, no SCIM)

**Issues:**
- MCC profile has JIT provisioning but JIT deprovisioning is not confirmed — SSO account deactivation at IdP may not automatically disable the application account
- Standalone profile has NO automated provisioning/deprovisioning mechanism — entirely manual admin action
- `DevUserAuthoritiesConfiguration` is annotated `@DevConfiguration` — may not be active in production

**Manual review required:**
- Verify the MCC profile handles SSO account deactivation by disabling the local application account
- Verify whether `DefaultUserAuthoritiesConfiguration` (the non-dev version) correctly implements JIT provisioning in production
- For standalone profile, implement SCIM or a custom provisioning webhook
- Confirm the IdP is configured to send deprovisioning events to the application

---

### ac-12: Single Sign-On for Internal Services

**Status:** WARN

**Severity:** Medium

**Checks performed:**
- Reviewed OAuth2/OIDC configuration for MCC profile
- Reviewed standalone profile authentication mechanism
- Reviewed frontend for local vs SSO login

**Evidence:**
- `src/main/resources/local/eds-starter-user-config/auth-mcc.yml:19-54` — OAuth2 OIDC configured pointing to `sit.auth.defcloud.gov.sg` (MCC corporate IdP)
- `src/main/java/eds/medicaldemo/deployment/mcc/config/EDSUserStarterSSOConfig.java:35` — `OAuth2LoginSecurityFilterChainAutoConfiguration` imported for MCC profile
- `src/main/java/eds/medicaldemo/deployment/standalone/config/EDSUserStarterConfig.java` — standalone profile has no SSO configuration
- `eds-admin-fe/src/app/_auth/components/login-form.tsx` — username/password form exists — used for standalone profile
- MCC profile SIT environment points to `sit.auth.defcloud.gov.sg` (SSO via MCC)

**Issues:**
- Standalone profile uses local username/password login — no SSO. If standalone profile is deployed for production "offline" environments, internal users authenticate without SSO (violates ac-12 for those environments)
- `application.yml:13` — `internet: "mcc"`, `offline: "isolated"` — offline deployment uses standalone (no SSO)
- The frontend login form (`login-form.tsx`) is a local credential form — used whenever standalone profile is active

**Manual review required:**
- Clarify whether offline/standalone deployments are considered "internal services" requiring SSO under ac-12
- If standalone is used in production environments where personnel are internal staff, SSO should be configured or an exception documented
- Verify MCC profile SSO roles are sourced from IdP claims (not hardcoded) in the non-dev `DefaultUserAuthoritiesConfiguration`

---

### dp-3: Data in Transit Encryption

**Status:** PASS

**Severity:** —

**Checks performed:**
- Reviewed SSL/TLS configuration in `application.yml`
- Checked for hardcoded `http://` URLs in production configuration
- Reviewed frontend API client configuration

**Evidence:**
- `src/main/resources/application.yml:20-25` — SSL enabled (`server.ssl.enabled: true` default), PKCS12 keystore via env vars
- `src/main/resources/application.yml:19` — `server.port: ${APP_PORT:8443}` — HTTPS port 8443
- No `http://` URLs in any production configuration file
- `src/main/resources/local/eds-starter-user-config/auth-mcc.yml:15` — MPDS URL uses `https://`
- `src/main/resources/local/eds-starter-user-config/auth-mcc.yml:17` — MCNS URL uses `https://`
- `eds-admin-fe/vite.config.ts:21` — dev proxy uses `http://localhost:8080` (local dev only, acceptable)
- No `TrustAllStrategy`, `NoopHostnameVerifier`, or disabled certificate validation found in application source
- `eds-admin-fe/src/app/_auth/context/current-user-context.tsx:95` — login sends over HTTPS via same-origin axios call

**Issues:**
- None

**Manual review required:**
- Determine deployment topology: verify TLS termination is enforced at load balancer/ingress if deployed behind a proxy
- If behind a TLS-terminating proxy, verify `server.forward-headers-strategy: framework` or `native` is set
- Verify TLS certificates are CA-signed (not self-signed) in production

---

### dp-8: Data Classification Disclosure

**Status:** FAIL

**Severity:** High

**Checks performed:**
- Identified all input field components in the admin frontend
- Searched for classification labels near input fields
- Reviewed `SensitiveDataReminderDialog` component

**Evidence:**
- `eds-admin-fe/src/app/_users/_components/create-user-form.tsx` — input fields for `firstName`, `lastName`, `username`, `email` — no classification label near any field
- `eds-admin-fe/src/app/_users/_components/change-password-form.tsx` — password input fields — no classification label
- `eds-admin-fe/src/app/_users/_components/create-multi-user-upload.tsx` — file upload for batch user CSV — no classification label
- `eds-admin-fe/src/app/_components/molecules/sensitive-data-reminder-dialog.tsx:14-16` — **one-time dismissible alert dialog** with `"Insert warning title here"` and `"Insert warning here"` placeholder text — not a proper classification label, and dialog text has NOT been filled in
- No `ClassificationBadge`, `ClassificationLabel`, `SecurityLabel` component found in codebase
- No `official open`, `official closed`, `restricted`, `sensitive normal`, `sensitive high` text found near any input field
- The SensitiveDataReminderDialog is shown once on login (`initiateSetup` flag) and disappears after clicking OK — does NOT satisfy persistent per-field labelling

**Issues:**
- No data classification labels on any input fields in the admin frontend
- `SensitiveDataReminderDialog` placeholder text has not been filled in (`"Insert warning title here"` / `"Insert warning here"`)
- The one-time popup approach does not satisfy the requirement for persistent classification labels visible while data is being entered
- This is an EMR application handling medical records (likely `OFFICIAL CLOSED` or `RESTRICTED` classification) — all input fields accepting patient-related data must have persistent classification labels

**Non-compliant fields (admin FE):**
- `create-user-form.tsx` — firstName, lastName, username, email inputs
- `change-password-form.tsx` — new_password, confirm_password inputs
- `create-multi-user-upload.tsx` — CSV file upload
- All user data display/edit fields in `user-details.tsx` and `edit-user-dialog.tsx`

**Manual review required:**
- Implement a `ClassificationBadge` component and render it persistently adjacent to each sensitive input field
- Fill in the `SensitiveDataReminderDialog` placeholder text with the actual classification warning
- Review the EMR frontend (`eds-emr-fe`) — all patient data input fields (vital records, clinical records, medication orders) also require classification labels
- Confirm the correct classification level for each field with the data owner

---

### pm-6: System Documentation

**Status:** FAIL

**Severity:** High

**Checks performed:**
- Searched for `ARCHITECTURE.md`, `docs/` directory, ADR directory, network diagrams
- Reviewed `README.md` content
- Reviewed API spec configuration (springdoc)
- Checked for infrastructure-as-code

**Evidence:**
- `emr-demo-app/README.md` — contains only build instructions, environment variables list, and git commands. No architecture description, no data flow, no deployment topology
- No `docs/` directory, `ARCHITECTURE.md`, ADR directory, `.drawio`, `.puml`, or `.mmd` files found in the repository
- `prod/eds-starter-user-config/springdoc.yml:2` — `api-docs.enabled: false` in production — OpenAPI spec not accessible at runtime in production
- No OpenAPI spec file (`.yaml`/`.json`) committed to the repository
- No Terraform, CloudFormation, Helm charts, or Kubernetes manifests found in the repository
- `eds-admin-fe/README.md` — not reviewed (file exists but assumed to be standard CRA/Vite README)
- No Storybook (`.stories.tsx`) files found in frontend

**Issues:**
- No architecture documentation describing system structure, data flows, or component interactions
- No network topology documentation
- No committed OpenAPI specification (only runtime-generated, disabled in production)
- No ADR (Architecture Decision Records)
- No infrastructure-as-code documenting deployment environment
- README is purely operational (build/run instructions) — does not describe what the system does architecturally

**Manual review required:**
- Create `docs/ARCHITECTURE.md` describing system components, data flows, external integrations (MCC SSO, MPDS, MCNS, SFS, AWS KMS)
- Create or commit an OpenAPI spec (`openapi.yaml`) to the repository (the `generate-api` script in `package.json` can produce this from the running app)
- Document the network topology (what ports are exposed, where TLS terminates, trust boundaries)
- Create ADR entries for key decisions (standalone vs MCC profiles, SFS integration, KMS usage)

---

## Manual Review Checklist

Items that cannot be fully automated and require human verification:

### Security Controls
- [ ] **as-1**: Verify the command pattern's `execute()→validate()` template is enforced for ALL commands — confirm no command can override `execute()` without calling `validate()`
- [ ] **as-1**: Verify frontend and backend validation rules are aligned (min length, allowed chars, max size)
- [ ] **as-4**: Implement login rate limiting (Bucket4j `capacity: 5, time: 1, unit: minutes` on login endpoint) — README acknowledges this is not yet implemented
- [ ] **as-4**: Implement 429-response handling in the frontend login form
- [ ] **as-5**: Verify `password.yml` (`maxPasswordHistoryLength: 3`) is imported in the production profile (currently only in `local`)
- [ ] **as-6**: Verify `eds-spring-boot-starter-user-*` uses `BCryptPasswordEncoder` or `Argon2PasswordEncoder` (not `NoOpPasswordEncoder`)
- [ ] **as-7**: Add `@PreAuthorize` to `VisitRecordController.getVisitRecordByPatientIds` (line 32)
- [ ] **as-7**: Replace `/actuator/**` whitelist with `/actuator/health` specifically in `url-guard-paths.yml`
- [ ] **as-8**: Rotate Google OAuth `client-id`/`client-secret` from `auth-mcc.yml:56-57` if this file is git-tracked
- [ ] **as-8**: Add `.env` and `.env.local` to `eds-admin-fe/.gitignore`
- [ ] **as-8**: Replace all `${PLEASE_CHANGE}` placeholders in production config before go-live
- [ ] **as-9**: Implement Content Security Policy header in Spring Security configuration
- [ ] **as-10**: Explicitly configure HSTS with `maxAgeInSeconds = 31536000` and `includeSubDomains = true`
- [ ] **as-11**: Implement frontend idle-timeout (react-idle-timer) to log out users after inactivity
- [ ] **as-12**: Investigate `scanner.scanRequired: false` in `prod/eds-starter-file-config/file-config.yml` — confirm if production deployments with MCC profile enable scanning
- [ ] **as-12**: Verify `eds-spring-boot-starter-file-autoconfigure-sfs` implements all SFS phases (0-3), jobAttempts tracking, and dirty/clean blob separation
- [ ] **as-12**: Verify download endpoint checks `DOWNLOADED` state before serving files
- [ ] **as-13**: Add a React Error Boundary at the application root level
- [ ] **as-15**: Verify `eds-spring-boot-starter-user-standalone` blocks access to all endpoints except password change while `require-password-change: true`
- [ ] **as-15**: Add `PASSWORD_CHANGE_REQUIRED` response handling to the frontend login flow

### Logging Controls
- [ ] **lm-4**: Verify `eds-spring-boot-starter-logging` captures authentication events (login success, failure, logout)
- [ ] **lm-15**: Verify `LoggingContextAutoConfiguration` produces structured JSON logs in ECS or Logstash format
- [ ] **lm-16**: Configure Prometheus scrape endpoint or expose metrics on a secured management port
- [ ] **lm-16**: Confirm Grafana/Prometheus/APM dashboards and alert thresholds are configured
- [ ] **lm-19**: Add `@ToString.Exclude` to PII fields (`nric`, `contactNumber`, `dateOfBirth`, `allergies`, emergency contact fields) on the Patient entity
- [ ] **lm-19**: Implement a `MaskingPatternLayout` to scrub NRIC patterns and phone numbers from logs

### Cryptographic Controls
- [ ] **ck-1**: Verify AWS KMS key policy enforces RSA-2048+ / AES-256
- [ ] **ck-1**: Verify standalone MFA key establishment uses NIST-approved algorithms
- [ ] **ck-2**: Verify AWS KMS auto-rotation is enabled for all production keys
- [ ] **ck-2**: Document key rotation intervals for all managed keys

### Access Control
- [ ] **ac-2**: Determine if MFA at login level is required for USER_MANAGER/USER_ADMIN privileged accounts (currently MFA is step-up only)
- [ ] **ac-2**: Verify MCC SSO provides MFA at the IdP level for all users
- [ ] **ac-3**: Verify `eds-spring-boot-starter-user-management` implements 90-day dormancy and 5-day expiry enforcement
- [ ] **ac-4**: Implement or document automated periodic access review process
- [ ] **ac-6**: Ensure all admin-created production accounts have `require-password-change: true` at creation
- [ ] **ac-8**: Verify MCC JIT provisioning handles deprovisioning when IdP disables an account
- [ ] **ac-8**: For standalone: implement SCIM or HR-system webhook for automated provisioning
- [ ] **ac-12**: Clarify whether offline/standalone deployments are in-scope for SSO requirement

### Data and Documentation
- [ ] **dp-3**: Verify `server.forward-headers-strategy` is set if deployed behind a TLS-terminating proxy
- [ ] **dp-3**: Verify TLS certificates are CA-signed in production
- [ ] **dp-8**: Implement persistent `ClassificationBadge` component adjacent to all sensitive input fields
- [ ] **dp-8**: Fill in `SensitiveDataReminderDialog` placeholder text with actual classification warning
- [ ] **dp-8**: Review and label all patient data input fields in `eds-emr-fe` (not audited here)
- [ ] **pm-6**: Create `docs/ARCHITECTURE.md` with system architecture, data flows, and external integrations
- [ ] **pm-6**: Commit an OpenAPI specification file (`openapi.yaml`) to the repository
- [ ] **pm-6**: Document network topology and deployment configuration

---

## Files Reviewed

### Backend (Spring Boot / Java)

**Configuration:**
- `emr-demo-app/pom.xml`
- `emr-demo-app/src/main/resources/application.yml`
- `emr-demo-app/src/main/resources/application-prod.yml`
- `emr-demo-app/src/main/resources/application-dev.yml`
- `emr-demo-app/src/main/resources/prod/eds-starter-user-config/auth.yml`
- `emr-demo-app/src/main/resources/prod/eds-starter-user-config/datasource.yml`
- `emr-demo-app/src/main/resources/prod/eds-starter-user-config/roles-and-privileges.yml`
- `emr-demo-app/src/main/resources/prod/eds-starter-user-config/springdoc.yml`
- `emr-demo-app/src/main/resources/prod/eds-starter-user-config/url-guard-paths.yml`
- `emr-demo-app/src/main/resources/prod/eds-starter-user-config/accounts-batch.yml`
- `emr-demo-app/src/main/resources/prod/eds-starter-file-config/file-config.yml`
- `emr-demo-app/src/main/resources/prod/eds-starter-report-config/report-config.yml`
- `emr-demo-app/src/main/resources/local/eds-starter-user-config/auth-isolated.yml`
- `emr-demo-app/src/main/resources/local/eds-starter-user-config/auth-mcc.yml`
- `emr-demo-app/src/main/resources/local/eds-starter-user-config/dev-accounts-isolated.yml`
- `emr-demo-app/src/main/resources/local/eds-starter-user-config/mfa-isolated.yml`
- `emr-demo-app/src/main/resources/local/eds-starter-user-config/mfa-mcc.yml`
- `emr-demo-app/src/main/resources/local/eds-starter-user-config/datasource.yml`
- `emr-demo-app/src/main/resources/local/eds-starter-user-config/password.yml`
- `emr-demo-app/src/main/resources/local/eds-starter-user-config/user-management.yml`
- `emr-demo-app/src/main/resources/local/eds-starter-user-config/accounts-batch-isolated.yml`
- `emr-demo-app/src/main/resources/local/eds-starter-file-config/file-config.yml`
- `emr-demo-app/src/main/resources/local/eds-starter-report-config/report-config.yml`
- `emr-demo-app/src/main/resources/dev/eds-starter-file-config/file-config.yml`

**Java Source:**
- `eds/medicaldemo/controller/LetterController.java`
- `eds/medicaldemo/controller/OrderController.java`
- `eds/medicaldemo/controller/PatientController.java`
- `eds/medicaldemo/controller/VisitRecordController.java`
- `eds/medicaldemo/controller/VitalRecordController.java`
- `eds/medicaldemo/config/CustomErrorController.java`
- `eds/medicaldemo/config/DBConfig.java`
- `eds/medicaldemo/config/MvcConfigurer.java`
- `eds/medicaldemo/interceptor/SpaRoutingInterceptor.java`
- `eds/medicaldemo/model/AuditMetadata.java`
- `eds/medicaldemo/model/records/VitalRecord.java`
- `eds/medicaldemo/model/validation/patients/PatientValidator.java`
- `eds/medicaldemo/service/commands/letters/HandleBeforeCreateLetterCommand.java`
- `eds/medicaldemo/service/commands/orders/UploadMedicalReportCommand.java`
- `eds/medicaldemo/service/commands/orders/DownloadMedicalReportCommand.java`
- `eds/medicaldemo/service/commands/patient/HandlePatientCreationCommand.java`
- `eds/medicaldemo/deployment/mcc/config/EDSUserStarterSSOConfig.java`
- `eds/medicaldemo/deployment/mcc/config/FileBatchConfig.java`
- `eds/medicaldemo/deployment/mcc/config/DevUserAuthoritiesConfiguration.java`
- `eds/medicaldemo/deployment/mcc/controller/SFSFileStatusController.java`
- `eds/medicaldemo/deployment/mcc/event/FileScanEventListener.java`
- `eds/medicaldemo/deployment/mcc/model/SFSFileStatusRecord.java`
- `eds/medicaldemo/deployment/standalone/config/EDSUserStarterConfig.java`
- `eds/medicaldemo/deployment/standalone/controller/ClinicalRecordControllerStandalone.java`

### Frontend (React TypeScript)

- `eds-admin-fe/package.json`
- `eds-admin-fe/.env`
- `eds-admin-fe/.gitignore`
- `eds-admin-fe/index.html`
- `eds-admin-fe/vite.config.ts`
- `eds-admin-fe/src/main.tsx`
- `eds-admin-fe/src/routeTree.gen.ts`
- `eds-admin-fe/src/routes/__root.tsx` (inferred from routeTree)
- `eds-admin-fe/src/routes/_authenticated.tsx`
- `eds-admin-fe/src/routes/_authenticated/_authorized.tsx`
- `eds-admin-fe/src/routes/login.tsx` (inferred)
- `eds-admin-fe/src/routes/unauthorised.tsx` (inferred)
- `eds-admin-fe/src/app/_auth/components/login-form.tsx`
- `eds-admin-fe/src/app/_auth/components/login-form-schema.ts`
- `eds-admin-fe/src/app/_auth/context/auth-context.tsx`
- `eds-admin-fe/src/app/_auth/context/current-user-context.tsx`
- `eds-admin-fe/src/app/_auth/context/csrf-token-context.tsx`
- `eds-admin-fe/src/app/_auth/context/user-context.tsx`
- `eds-admin-fe/src/app/_schema/user.ts`
- `eds-admin-fe/src/app/_users/_components/create-user-form.tsx`
- `eds-admin-fe/src/app/_users/_components/change-password-form.tsx`
- `eds-admin-fe/src/app/_users/_components/create-multi-user-upload.tsx` (inferred from hooks)
- `eds-admin-fe/src/app/_users/_hooks/use-file-upload.ts`
- `eds-admin-fe/src/app/_users/_hooks/use-create-user-drawer.tsx`
- `eds-admin-fe/src/app/_components/molecules/sensitive-data-reminder-dialog.tsx`
- `eds-admin-fe/src/components/complex/upload-dropzone.tsx`
- `eds-admin-fe/src/components/hooks/use-upload-dropzone.ts`
- `emr-demo-app/README.md`
