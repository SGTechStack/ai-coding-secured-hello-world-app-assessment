# IM8 Application Controls Compliance Report

**Target:** `/home/jun_sheng/projects/ai-coding-secured-hello-world-app-assessment` (branch `seahjunsheng`, commit `6e6c9b9`)
**Date:** 2026-09-29
**Stack:** React 19 (TypeScript, Vite, no shadcn/ui) + Spring Boot 4.1 / Spring Security 7.1, Spring Session JDBC
**Risk classification assumed:** Low Risk (LR). The PRD describes a reference/demo application. Severities use the LR column; controls without a catalogued level default to Level 1.
**Audience assumed:** Public-facing, because visitors can self-register. No internal-only user population exists (see ac-8, ac-12).

## Summary

| Control | Status | Severity | Finding |
|---------|--------|----------|---------|
| as-1 | WARN | Medium | BE: every `@RequestBody` is `@Valid` with constrained DTOs. FE: no schema library, and API responses are not validated at runtime (`as T` casts). |
| as-2 | PASS | — | JPQL with named parameters only. No raw SQL, native queries, `JdbcTemplate` or process execution. FE encodes path parameters. |
| as-3 | PASS | — | JSON and `text/plain` only, API CSP `default-src 'none'`. FE has no HTML sinks, `eval` or dynamic `href`. |
| as-4 | WARN | Medium | Account lockout and per-IP throttling are implemented and tested, but as custom code, not the prescribed Bucket4j config. The throttle counts failures, not all requests. |
| as-5 | PASS | — | At least 12 characters (and at most 72 bytes) on BE and FE. Requirements shown before input, confirm-password check, `type="password"`. |
| as-6 | WARN | Medium | BCrypt (cost 12), no client-side hashing, no web storage. FE API base URL defaults to `http://localhost:8080`, and nothing enforces HTTPS in production builds. |
| as-7 | WARN | Medium | BE: URL rules, `denyAll()` default and `@PreAuthorize` on admin. FE: no central 401 handling and no access-denied page. |
| as-8 | WARN | Medium | No hardcoded production secrets (env vars). No approved secret store integrated. FE `.gitignore` does not exclude `.env` or `.env.production`. |
| as-9 | PASS | — | API: `default-src 'none'; frame-ancestors 'none'`. SPA build: strict CSP meta tag without `unsafe-inline` or `unsafe-eval`. |
| as-10 | PASS | — | Spring Security HSTS default not disabled (1 year, includeSubDomains). Sent on HTTPS behind the prod proxy. |
| as-11 | FAIL | High | Idle timeout is 30 minutes, above the 15-minute threshold. No FE idle logout and no concurrent-session limit. Session fixation protection is present. |
| as-12 | N/A | — | No file upload functionality. |
| as-13 | WARN | High | BE: error details suppressed, generic 500, only actuator `health` exposed. FE: no Error Boundary, a `console.error` ships in the production bundle, and server 4xx `detail` is shown verbatim. |
| as-14 | PASS | — | `SecureRandom` for reset tokens, SHA-256 for token hashing (not passwords). No weak algorithms. FE does no cryptography. |
| as-15 | FAIL | — | No forced-password-change flag, and time-based unlock does not force a change. Risk level N/A at LR/MR. |
| lm-4 | WARN | Medium | Structured audit events for authentication, lockout, reset and admin actions (actor and target). 401/403 denials and admin user listing are not audited. No request correlation ID. |
| lm-15 | WARN | High | Prod profile emits ECS JSON to the **console** (`logging.structured.format.console: ecs`), not `format.file`. Dev logs are plain text. |
| lm-16 | WARN | High | Actuator gives the four key signals automatically, but no metrics endpoint or exporter is configured. FE has no RUM or web-vitals instrumentation. |
| lm-18 | FAIL | — | Public-facing, but the WOGAA script snippet is absent. Risk level N/A at LR/MR. |
| lm-19 | WARN | High | Audit values are sanitised and password DTOs redact `toString()`. No global masking layer. The dev email stub logs the reset link, and unhandled exceptions are logged with full messages. |
| ck-1 | N/A | — | The application generates or manages no cryptographic keys. |
| ck-2 | N/A | — | The application generates or manages no cryptographic keys. |
| ck-4 | N/A | — | No key material or key files in the source tree. |
| ga-8 | N/A | — | No GenAI features. |
| ac-1 | WARN | Medium | BE least privilege is enforced by URL rules and method security, and they agree. FE 401/403 handling is per page, not central, and there is no forbidden page. |
| ac-2 | FAIL | High | No MFA for ADMIN accounts or privileged actions. The PRD explicitly puts MFA out of scope. |
| ac-3 | FAIL | High | No 90-day dormancy or expiry job, and no `lastLoginAt`. Admin disable does revoke sessions. |
| ac-4 | FAIL | High | No periodic access-review or revocation mechanism. |
| ac-6 | FAIL | High | No `forcePasswordChange` flag. The bootstrapped admin's configured password is never forced to change. |
| ac-7 | N/A | — | Public users exist but there are no high-impact or high-risk transactions (Parts A and B). |
| ac-8 | N/A | — | No internal-user population; see manual review. |
| ac-12 | N/A | — | No internal-user population; see manual review. |
| dp-3 | WARN | Medium | TLS is delegated to a TLS-terminating proxy (`forward-headers-strategy: native`). Base config and FE default to `http://localhost` URLs. No `upgrade-insecure-requests`. |
| dp-8 | N/A | — | Public-facing application, not an internal officers' application. |
| pm-6 | WARN | Medium | `docs/design.md` documents architecture, data flow, API and security design. No committed OpenAPI spec, ADR directory, deployment topology diagram or IaC. |
| st-3 | FAIL | High | Public-facing, but there is no `/.well-known/security.txt` and no "Report Vulnerability" link. |

**Totals:** 6 PASS · 13 WARN · 8 FAIL · 9 N/A

## Detailed Findings

### as-1: Input Validation

**Status:** WARN

**Severity:** Medium

**Checks performed:**
- `@Valid` on every `@RequestBody`, constraints on DTOs, and validation of path and query parameters.
- FE: schema libraries, form validation before submit, runtime validation of API responses, and `as` casts.

**Evidence:**
- `backend/.../auth/AuthController.java:52,58`, `passwordreset/PasswordResetController.java:32,39`, `admin/AdminUserController.java:52,58`: `@Valid @RequestBody` on every body.
- `auth/AuthDtos.java`: `@NotBlank`, `@Pattern`, `@Email`, `@Size` and the custom `@ValidPassword` (`security/ValidPassword.java`).
- `admin/AdminUserController.java:45-46`: `@Min`/`@Max` on `page`/`size`. `@PathVariable UUID` is rejected with 400 on a malformed ID.
- `frontend/src/pages/RegisterPage.tsx:26`, `ResetPasswordPage.tsx`: password policy and confirm-match checked before the API call; native `required`/`pattern`/`type=email`.
- `frontend/package.json`: no `zod`/`yup`/etc.
- `frontend/src/api/client.ts:57,79`: response JSON cast `as Problem` / `as T` without runtime validation.
- `pages/AdminUsersPage.tsx:124`: `event.target.value as Role` from a closed `<select>`.

**Issues:**
- FE relies on TypeScript types for API responses, which are erased at runtime.
- There is no shared schema between FE forms and API DTOs.

**Manual review required:**
- Verify FE and BE validation rules stay aligned. They currently mirror `PasswordPolicy` by hand (`frontend/src/passwordPolicy.ts`).

---

### as-2: Parameterised Interfaces

**Status:** PASS

**Severity:** —

**Evidence:**
- `user/UserRepository.java:25,29` and `passwordreset/PasswordResetTokenRepository.java:22,26,30,34`: JPQL with `:named` parameters only.
- No `java.sql.Statement`, `nativeQuery`, `JdbcTemplate`, `Runtime.exec` or `ProcessBuilder` in `backend/src/main`.
- `frontend/src/api/client.ts:155` (and the adjacent admin calls) wraps user IDs in `encodeURIComponent`. Query parameters are numeric page and size values.

**Manual review required:**
- None beyond confirming no command execution is added later.

---

### as-3: Output Sanitisation

**Status:** PASS

**Severity:** —

**Evidence:**
- All controllers return JSON except `hello/HelloController.java:11`, which returns `produces = text/plain`. Never `text/html`.
- `config/SecurityConfig.java:73`: API CSP `default-src 'none'`. `X-Content-Type-Options: nosniff` comes from Spring Security defaults (asserted in `WebSecurityIntegrationTests`).
- FE: no `dangerouslySetInnerHTML`, `innerHTML`, `insertAdjacentHTML`, `document.write`, `eval` or `new Function`. Every `href`/`to` is a static route. React escapes all rendered server strings.

---

### as-4: Authentication Mechanism Rate-Limiting

**Status:** WARN

**Severity:** Medium

**Checks performed:**
- Lockout fields and logic.
- Login endpoint rate limiting (prescribed: Bucket4j).
- FE handling of 429.

**Evidence:**
- **Lockout fields:** `user/User.java:40,43,46` has `failed_login_attempts` and `locked_until`. The lock is a timestamp rather than a boolean flag, which is equivalent in effect.
- **Lockout logic:** `auth/AccountLockoutService.java:46,64-65` locks after 5 consecutive failures within 15 minutes, for 15 minutes. Attempts are reserved under a row lock, so parallel guesses cannot exceed 5 (`LockoutIntegrationTests.parallelGuessesCannotExceedTheThreshold`).
- **IP throttle:** `auth/LoginService.java:53,85` and `throttle/AttemptThrottle.java` allow 20 failed logins per IP per 15 minutes, keyed on `getRemoteAddr()`, then return 429 with `Retry-After` (`IpThrottleIntegrationTests`). The reset request is limited to 5 per IP per 15 minutes.
- **FE 429 handling:** `frontend/src/errors.ts:6-7` shows a Retry-After based message; the submit button is disabled while a request is in flight (`LoginPage.tsx:58`).

**Issues:**
- No `bucket4j-spring-boot-starter` or `bucket4j.filters` configuration; the throttle is a custom Caffeine-backed implementation.
- The throttle counts failed attempts, not all requests, so there is no request-rate cap such as the example "5 per minute".
- Registration has no rate limit.
- Throttle state is per instance, in memory.

**Manual review required:**
- Confirm the thresholds (5 per account, 20 per IP per 15 minutes) meet policy.
- Behind a proxy, confirm `server.forward-headers-strategy: native` trusts only the real proxy (Tomcat RemoteIpValve internal-proxies), so `X-Forwarded-For` cannot be spoofed.

---

### as-5: Password Requirements

**Status:** PASS

**Severity:** —

**Evidence:**
- `security/PasswordPolicy.java:10,13`: minimum 12 characters and maximum 72 bytes (BCrypt limit), enforced by `@ValidPassword` on registration and reset.
- `frontend/src/passwordPolicy.ts` enforces the same rules.
- `RegisterPage.tsx` and `ResetPasswordPage.tsx` show a hint before input ("At least 12 characters…"), check confirm-match, and use `type="password"` with `autoComplete="new-password"`.

**Manual review required:**
- There is no strength meter or composition rule, which follows NIST SP 800-63B. Confirm this satisfies organisational policy.

---

### as-6: Password Salting and Hashing

**Status:** WARN

**Severity:** Medium

**Evidence:**
- `config/SecurityConfig.java:93`: `BCryptPasswordEncoder(strength)` with cost 12 (`application.yml`).
- No `NoOpPasswordEncoder`. `MessageDigest` SHA-256 (`passwordreset/ResetTokens.java:31`) hashes 256-bit random reset tokens, not passwords.
- FE: no client-side hashing, no `localStorage`/`sessionStorage`, and `autoComplete` is `current-password`/`new-password`.
- `frontend/src/api/client.ts:9` and `.env.example:2` default the API base URL to `http://localhost:8080`.

**Issues:**
- Nothing prevents a production build from targeting an `http://` API. Local dev over HTTP is the PRD's accepted gap.

**Manual review required:**
- Verify `VITE_API_BASE_URL` is `https://` in every non-local build.

---

### as-7: Access Control Check Enforcement

**Status:** WARN

**Severity:** Medium

**Evidence:**
- **URL rules:** `config/SecurityConfig.java:38` has `@EnableMethodSecurity`. Lines 57-65 give explicit URL rules ending in `.anyRequest().denyAll()`.
- **Method security:** `admin/AdminUserController.java:35` adds `@PreAuthorize("hasRole('ADMIN')")` as defence in depth. Every controller method is covered by a URL rule.
- **FE route guards:** `frontend/src/auth/guards.tsx:21,35` has `RequireAuth` with an optional `role`, and `components/Layout.tsx:36` shows the admin link only to ADMIN.
- **FE credentials:** the session cookie is sent centrally (`credentials: 'include'` in `client.ts`).

**Issues:**
- FE 401 handling is per page (`HomePage.tsx:21`, `AdminUsersPage.tsx:21`, `AuthProvider.tsx:17`) rather than one interceptor. A new page could forget to handle it.
- API 403 shows an inline message only, and a role mismatch redirects to `/`. There is no dedicated access-denied page.

**Manual review required:**
- Frontend guards are UX only. The backend enforces every rule independently (`AdminIntegrationTests.userRoleGets403OnEveryAdminEndpoint`).

---

### as-8: Secrets Management

**Status:** WARN

**Severity:** Medium

**Evidence:**
- `backend/src/main/resources/application-prod.yml:7-9`: datasource credentials from `${DATABASE_*}`.
- The admin password has no default and comes from `APP_ADMIN_PASSWORD` (`application.yml:56`).
- `application-dev.yml:5-6`: empty password for the embedded dev H2 file database (not a secret).
- `src/test/resources/application-test.yml:14`: fixture admin password, test scope only.
- No hardcoded secrets in Java or TS sources.
- No `spring-cloud-vault` or AWS Secrets Manager dependency.
- `frontend/.gitignore:13` only ignores `*.local`.

**Issues:**
- `.env` and `.env.production` are not gitignored in `frontend/`, and the repo has no root `.gitignore`.
- Production secrets rely on environment variables with no approved secret-store integration.

**Manual review required:**
- Verify production injects secrets from an approved store (AWS Secrets Manager, Vault) into the environment variables.
- `VITE_API_BASE_URL` is the only FE variable, and it is public by design.

---

### as-9: Content Security Policy

**Status:** PASS

**Severity:** —

**Evidence:**
- `config/SecurityConfig.java:73`: `default-src 'none'; frame-ancestors 'none'`.
- `frontend/vite.config.ts:27,42` injects into the built `index.html`: `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self' <api-origin>; object-src 'none'; base-uri 'none'; form-action 'self'`.
- `vite.config.ts:51` adds the same policy plus `frame-ancestors 'none'` as `vite preview` headers.
- No CDN assets, dynamic script injection or inline styles.

**Manual review required:**
- The production web server must send the header form, because `frame-ancestors` is ignored in meta tags.
- Confirm `connect-src` points to the https API origin.

---

### as-10: HTTP Strict Transport Security

**Status:** PASS

**Severity:** —

**Evidence:**
- HSTS is not disabled in `SecurityConfig`. The Spring Security default is `max-age=31536000; includeSubDomains`, sent on secure requests.
- The prod profile sets `server.forward-headers-strategy: native` (`application-prod.yml:14`), so requests arriving over HTTPS at the proxy are recognised as secure.

**Manual review required:**
- Verify production is only reachable over HTTPS. No integration test asserts the HSTS header on a secure request.

---

### as-11: Session Management

**Status:** FAIL

**Severity:** High

**Evidence:**
- `application.yml:13`: `spring.session.timeout: 30m`.
- Session fixation is handled by `security/SessionLogin.java:37` (`ChangeSessionIdAuthenticationStrategy`).
- Logout invalidates the session and expires the cookie (`SecurityConfig.java:69`). Sessions are revoked on password reset, disable, role change and delete (`AdminUserService.java:59,76,93`).
- FE: no idle-timer library or activity tracking. Logout clears the in-memory CSRF token and auth state (`client.ts:45,129`), and nothing is persisted in web storage.

**Issues:**
- The 30-minute idle timeout exceeds the 15-minute threshold.
- No `maximumSessions` (concurrent session control).
- No absolute session lifetime.
- No FE inactivity logout.

**Manual review required:**
- Confirm the timeout against organisational policy. The PRD does not specify one.

---

### as-12: Malware Scanning of Uploaded Files

**Status:** N/A

**Severity:** —

- No multipart or upload endpoints and no `<input type="file">`.

---

### as-13: Exposure of Internal System Details

**Status:** WARN

**Severity:** High

**Evidence:**
- **Error details:** `application.yml:20-21` sets `include-message: never` and `include-stacktrace: never`; binding errors use the default `never`.
- **Error handler:** `web/ApiExceptionHandler.java:29` is a `@RestControllerAdvice` returning RFC 9457 problems. Unexpected exceptions return a generic 500. `ApiException` messages are curated client-safe strings. Validation errors never echo rejected values.
- **Actuator:** exposure is `health` only with `show-details: never`, and every other actuator path is `denyAll` (`WebSecurityIntegrationTests.unmappedAndInternalPathsAreDenied`).
- **FE source maps:** not enabled (Vite default).

**Issues:**
- FE has no React Error Boundary; a render error blanks the app.
- `frontend/src/auth/AuthProvider.tsx:18` calls `console.error(..., error)`, which ships in production because there is no `drop_console`.
- `frontend/src/errors.ts` shows server `detail` text verbatim for 4xx. These are curated messages, but the FE doesn't map codes to its own text.

---

### as-14: Secure Cryptographic Libraries

**Status:** PASS

**Severity:** —

**Evidence:**
- `passwordreset/ResetTokens.java:16`: `SecureRandom`, 32-byte tokens.
- SHA-256 is used for token lookup hashes.
- No DES, 3DES, RC4, MD5, SHA-1 or `java.util.Random` in main code.
- FE: no cryptography and no `Math.random`.

---

### as-15: Password Change

**Status:** FAIL

**Severity:** — (catalogued risk level N/A at LR/MR)

**Evidence:**
- `user/User.java` has no forced-password-change field.
- Lockout expires automatically after 15 minutes (`AccountLockoutService`) without forcing a change.
- There is no authenticated change-password endpoint; only the token-based reset exists.

**Manual review required:**
- Confirm whether this control applies at the system's risk classification.

---

### lm-4: Audit Logging

**Status:** WARN

**Severity:** Medium

**Evidence:**
- `audit/AuditLog.java`: `AUDIT` logger with SLF4J key-value pairs and a logfmt message.
- Events emitted:
  - `USER_REGISTERED`
  - `LOGIN_SUCCEEDED`, `LOGIN_FAILED` (with reason), `LOGIN_THROTTLED`
  - `ACCOUNT_LOCKED`
  - `LOGOUT`
  - `PASSWORD_RESET_REQUESTED`, `PASSWORD_RESET_COMPLETED`, `PASSWORD_RESET_REJECTED`, `PASSWORD_RESET_THROTTLED`
  - `USER_ENABLED`, `USER_DISABLED`, `USER_ROLE_CHANGED`, `USER_DELETED` (actor and target)
  - `ADMIN_ACTION_REJECTED`, `ADMIN_BOOTSTRAPPED`
- Covered by `AuditLoggingIntegrationTests`.

**Issues:**
- **Denials not audited:** `security/ProblemJsonSecurityHandlers.java:35,41` does not audit 401/403 (including CSRF failures).
- **Admin data access not audited:** `GET /api/admin/users`, which returns other users' email addresses, is not audited.
- **No correlation ID:** no `X-Request-ID`, correlation ID or MDC propagation. FE sends no request ID.

**Manual review required:**
- Confirm event coverage and retention against business requirements.

---

### lm-15: Structured Log Formatting

**Status:** WARN

**Severity:** High

**Evidence:**
- `application-prod.yml:16-19`: `logging.structured.format.console: ecs`, which Spring Boot 4 supports natively and which includes the audit key-value pairs.

**Issues:**
- The check expects `logging.structured.format.file: ecs` or a Logback JSON encoder. Prod emits ECS on stdout (container-style) and writes no log file.
- Dev and test logs are plain text.

**Manual review required:**
- Confirm stdout ECS collected by the platform satisfies policy, or add `logging.file.name` with `logging.structured.format.file: ecs`.

---

### lm-16: Key Signals Monitoring

**Status:** WARN

**Severity:** High

**Evidence:**
- `backend/pom.xml` includes `spring-boot-starter-actuator`. `http.server.requests` provides latency, traffic and errors (status/outcome tags); JVM and HikariCP metrics provide saturation.

**Issues:**
- `management.endpoints.web.exposure.include: health` only, and no Micrometer registry exporter (e.g. Prometheus/OTLP). Metrics are collected in memory but not shipped.
- FE has no web-vitals, RUM or error-reporting SDK. Client errors only reach `console.error`.

**Manual review required:**
- Confirm dashboards and alerts are configured once an exporter exists.

---

### lm-18: WOGAA

**Status:** FAIL

**Severity:** — (catalogued risk level N/A at LR/MR)

**Evidence:**
- The app has public self-registration and no internal SSO, so it is public-facing.
- `<script src="https://assets.wogaa.sg/scripts/wogaa.js"></script>` does not appear in `frontend/index.html` or anywhere else.

**Manual review required:**
- If the app is deployed as a public government digital service, add the snippet. The CSP `script-src` would then need `https://assets.wogaa.sg`.

---

### lm-19: Log Sanitisation

**Status:** WARN

**Severity:** High

**Evidence:**
- `audit/AuditLog.java:32` escapes control characters, quotes and backslashes and truncates values, which prevents forged log lines (`AuditLogTests`).
- `auth/AuthDtos.java:32`, `PasswordResetController` and `AdminBootstrapProperties` redact passwords and tokens in `toString()`.
- Failed logins for unknown usernames do not log the attempted name.
- `AuditLoggingIntegrationTests` asserts no password appears in captured output.

**Issues:**
- **No global masking:** there is no masking Logback layout or `TurboFilter`, so application log lines outside `AuditLog` are not scrubbed.
- **Reset link logged in dev:** `passwordreset/LoggingEmailService.java:25` logs the full reset link, a live credential. This is required by the PRD's email stub and is limited to non-prod profiles (`@Profile("!prod")`).
- **Full exception messages logged:** `web/ApiExceptionHandler.java:55` logs unhandled exceptions with full messages, which could contain data values.

---

### ck-1 / ck-2 / ck-4: Cryptographic Keys

**Status:** N/A

**Severity:** —

- No `KeyGenerator`, `KeyPairGenerator`, `SecretKeySpec` or `KeyStore` in application code.
- No `.pem`, `.key`, `.p12`, `.pfx`, `.jks` or `.keystore` files.
- TLS terminates at the proxy.
- BCrypt salts and reset tokens are not managed keys.

---

### ga-8: GenAI Risks

**Status:** N/A

**Severity:** —

- No GenAI features.

---

### ac-1: Principle of Least Privilege

**Status:** WARN

**Severity:** Medium

**Evidence:**
- **BE URL rules:** `SecurityConfig.java:57-65` orders specific matchers before `/api/**`, with `denyAll()` as the catch-all.
- **BE method and ownership checks:** `@PreAuthorize` on the admin controller. Ownership is enforced by principal: `/api/me` returns the caller, and the admin self-action guard compares the principal ID (`AdminUserService`). Filter and method rules agree.
- **FE:** role-dependent UI derives from the backend `/api/me` role (`useAuth`), and routes are guarded by `RequireAuth role="ADMIN"`.

**Issues:**
- No central FE 401/403 handler.
- 403 has no forbidden page (see as-7).

**Manual review required:**
- Verify FE gating matches BE rules as endpoints are added.

---

### ac-2: MFA Enforcement

**Status:** FAIL

**Severity:** High

**Evidence:**
- ADMIN is a privileged role (user management), but there are no MFA fields, second-factor endpoint or step-up checks on the backend or frontend.

**Issues:**
- The PRD explicitly lists "Multi-factor authentication (MFA/2FA)" as **out of scope**.

---

### ac-3: Inactive and Expired Accounts

**Status:** FAIL

**Severity:** High

**Evidence:**
- `User.enabled` exists, and admin disable revokes sessions (`AdminUserService.java:59`).
- There is no `lastLoginAt` or `accountExpiresAt`, no scheduled dormancy or expiry job, and no SCIM.

---

### ac-4: Access Review

**Status:** FAIL

**Severity:** High

**Evidence:**
- The app has application-managed accounts and roles (`users.role`).
- There is no declared per-account baseline and no scheduled review-and-revoke job.

**Manual review required:**
- Confirm whether access reviews are performed outside the application.

---

### ac-6: Default Credentials

**Status:** FAIL

**Severity:** High

**Evidence:**
- No forced-password-change flag on `User`.
- `admin/AdminBootstrap.java` seeds the first admin with the operator-supplied `APP_ADMIN_PASSWORD`, and that credential is never forced to change on first login.
- There are no admin-created accounts or temporary credentials: password reset issues a single-use token, not a temporary password.

---

### ac-7: Singpass / Corppass

**Status:** N/A

**Severity:** —

- Part A: public users exist, but the app has no high-impact or high-risk transactions (the protected content is a greeting).
- Part B: no corporate users.

---

### ac-8 / ac-12: Automated Provisioning / SSO for Internal Accounts

**Status:** N/A

**Severity:** —

- All accounts, including ADMIN, are local username/password accounts, as the PRD mandates. There is no separate internal-officer population.

**Manual review required:**
- If ADMIN accounts will be held by internal public officers, both controls apply and would FAIL: there is no SSO and no SCIM or JIT provisioning.

---

### dp-3: Data in Transit Encryption

**Status:** WARN

**Severity:** Medium

**Evidence:**
- No `server.ssl.*`: TLS is delegated to a TLS-terminating proxy, which is documented in `docs/design.md` and `application-prod.yml`.
- `forward-headers-strategy: native` is set, so the app detects the original HTTPS scheme.
- No deprecated protocols, trust-all managers or disabled hostname verification.

**Issues:**
- **http defaults:** base `application.yml:38,51` defaults `allowed-origins` and `reset-url` to `http://localhost:3000`. Prod overrides both from the environment, but nothing enforces `https://`.
- **FE:** defaults to `http://localhost:8080` (`client.ts:9`), and the CSP has no `upgrade-insecure-requests`.

**Manual review required:**
- Confirm TLS and HTTP→HTTPS redirect at the proxy, and CA-signed certificates rotated before expiry.

---

### dp-8: Data Classification Disclosure

**Status:** N/A

**Severity:** —

- Public-facing application.

---

### pm-6: System Documentation

**Status:** WARN

**Severity:** Medium

**Evidence:**
- `docs/design.md` covers:
  - Architecture and request flow
  - API table
  - Security controls mapped to the PRD
  - Design decisions
  - Deployment checklist
- `README.md` covers run and check instructions.
- Software inventory: `backend/pom.xml`, `frontend/package.json`, `frontend/package-lock.json`.
- Configuration is documented in `application*.yml` (commented) and `frontend/.env.example`.
- Spot-checked accuracy: the documented endpoints, CSRF flow and config keys match the code.

**Issues:**
- No committed machine-readable OpenAPI spec.
- No ADR directory; decisions are inline in `design.md`.
- No deployment or network topology diagram beyond a proxy description.
- No IaC or infrastructure inventory. Hosting, containerisation and CI/CD are out of the PRD's scope.

---

### st-3: Public Vulnerability Disclosure

**Status:** FAIL

**Severity:** High

**Evidence:**
- The app is public-facing.
- There is no `backend/src/main/resources/static/.well-known/security.txt`, and no "Report Vulnerability" link in the frontend.

---

## Manual Review Checklist

- [ ] Confirm the system risk classification (LR assumed) and whether ADMIN users are internal officers (changes ac-8 and ac-12).
- [ ] Confirm the session idle timeout policy (currently 30 minutes).
- [ ] Confirm lockout and throttle thresholds (5 per account, 20 per IP per 15 minutes) and proxy trust for client IPs.
- [ ] Verify `VITE_API_BASE_URL`, `APP_CORS_ALLOWED_ORIGINS` and `APP_PASSWORD_RESET_URL` are `https://` in every non-local environment.
- [ ] Verify production secrets come from an approved store.
- [ ] Verify TLS termination, HTTP→HTTPS redirect and certificate management at the proxy; confirm HSTS is observed on responses.
- [ ] Confirm stdout ECS logging meets lm-15, and configure a metrics exporter, dashboards and alerts (lm-16).
- [ ] Confirm the out-of-PRD-scope controls (MFA, dormancy, access review, forced password change, WOGAA, security.txt) with the product owner before deployment.

### Files Reviewed

Backend
- `backend/pom.xml`
- `backend/src/main/resources/application.yml`, `application-dev.yml`, `application-prod.yml`, `db/migration/**`
- `backend/src/test/resources/application-test.yml`
- `backend/src/main/java/com/sgtechstack/helloauth/config/SecurityConfig.java`, `SessionCookieConfig.java`, `CorsProperties.java`, `InfrastructureConfig.java`
- `backend/src/main/java/com/sgtechstack/helloauth/auth/*` (controller, DTOs, login, lockout, registration, logout audit)
- `backend/src/main/java/com/sgtechstack/helloauth/passwordreset/*`
- `backend/src/main/java/com/sgtechstack/helloauth/admin/*`
- `backend/src/main/java/com/sgtechstack/helloauth/security/*`, `audit/*`, `throttle/*`, `user/*`, `web/*`, `hello/*`

Frontend
- `frontend/package.json`, `frontend/vite.config.ts`, `frontend/index.html`, `frontend/.gitignore`, `frontend/.env.example`, `frontend/dist/index.html` (built CSP)
- `frontend/src/api/client.ts`, `frontend/src/errors.ts`, `frontend/src/passwordPolicy.ts`
- `frontend/src/auth/AuthProvider.tsx`, `useAuth.ts`, `guards.tsx`
- `frontend/src/components/*`, `frontend/src/pages/*`
