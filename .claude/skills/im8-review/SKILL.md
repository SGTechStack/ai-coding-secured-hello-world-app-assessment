---
name: im8-review
description: Run IM8 Application Technical Controls compliance checks against a React (TypeScript) + Spring Boot codebase. Automates verifiable checks and flags manual review items.
---

# IM8 Application Technical Controls Audit

Run this skill against a React 19 (TypeScript) + Spring Boot 3.4 (Spring Security 6.4) codebase to verify compliance with IM8 Application Technical Controls.

**Frontend stack:** React 19 with TypeScript (`.ts`/`.tsx` files only — this project does not use JavaScript/`.js`/`.jsx`). UI components are built with **shadcn/ui** — expect shadcn primitives (`Input`, `Textarea`, `Select`, `FormField`, `FormItem`, `FormControl`, `FormLabel`, etc.) throughout the codebase rather than raw HTML elements.

## Instructions

When the user invokes this skill, perform the following steps:

1. **Identify the target codebase** — ask the user for the path if not obvious from context.
2. **Run all automated checks** below in sequence, collecting PASS/FAIL/WARN/N/A results. **Before running the automated checks for each control, evaluate its applicability first.** Many controls have an applicability section that specifies conditions under which the control is N/A (e.g., internal-only apps, apps without GenAI features, apps that delegate crypto to KMS). If a control is not applicable, mark it as **N/A** immediately and skip its checks — do not run the checks and then retroactively mark it N/A.

3. **Derive finding severity** from the control's catalogued risk level and the finding status. Each control below carries a `**Risk Level:** LR: X | MR: X` annotation sourced from the IM8 Application Controls catalog, where `LR` is the level for a Low Risk system and `MR` for a Medium Risk system (0 = baseline, 1 = standard, 2 = elevated). Use the column that matches the assessed system's risk classification.

   | Risk Level | FAIL | WARN |
   |------------|------|------|
   | 2 | **Critical** | **High** |
   | 1 | **High** | **Medium** |
   | 0 | **Medium** | **Low** |
   | N/A | — | — |

   Controls without a catalogued risk level (e.g., ac, dp, pm, st controls) default to Level 1.

### Evidence standard — framework delegation rule

These rules apply across ALL controls in this audit:

1. **Evidence must come from the application's own code and declared dependencies.** Only count what the application's `pom.xml`/`build.gradle`/`package.json` actually imports and what its own source code configures. Sibling modules in a parent framework that the application does not import are irrelevant — a framework *having* a capability does not mean the application *uses* that capability.
2. **"Delegated to framework" is WARN, not PASS** — unless you can confirm the specific framework module is (a) imported as a dependency by the application AND (b) configured/wired up in the application's own configuration. If you cannot see the implementation because it lives inside a framework jar, mark the control as **WARN** with a manual review flag to verify the framework provides the required behaviour. Never mark a control PASS based on an assumption that an opaque framework "probably handles it."
3. **Verify the import chain.** If the application imports one module from a framework but the feature you are checking lives in a different module that the application does NOT import, that feature is absent — do not credit it.
4. **Produce a single unified compliance report** in markdown covering both backend and frontend together. Do NOT produce separate reports for backend and frontend. The report must include:
   - A summary table (control ID, status, finding) combining BE and FE findings per control
   - Detailed findings per control with file paths and line numbers for both BE and FE
   - Manual review items that could not be automated

---

## Automated Checks

### as-1: Input Validation

**Risk Level:** LR: 1 | MR: 0

**Backend automated checks:**

- Scan all `@RestController` / `@Controller` classes. For every `@RequestBody` parameter, verify it has `@Valid` or `@Validated`.
- Verify DTO classes used in request bodies have Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Size`, `@Pattern`, `@Min`, `@Max`, `@Email`, etc.).
- Check `@PathVariable` and `@RequestParam` parameters have validation constraints or are validated within the method body.
- If `@Valid`/`@Validated` is missing at the controller layer, check whether validation is performed elsewhere before business logic executes:
  - Service/command layer: look for explicit `validate()` calls using `jakarta.validation.Validator` or custom validator classes invoked before the main logic (e.g., command pattern where `execute()` calls `validate()` first).
  - Framework-enforced validation: check if the application uses a framework or base class that guarantees validation is called (e.g., an abstract command class whose `execute()` template method calls `validate()` before `doExecute()`).
  - If validation exists at the service/command layer but not at the controller boundary, mark as **WARN** (validation is present but not enforced at the earliest entry point — defense-in-depth gap).
  - If no validation exists at any layer (neither controller nor service/command), mark as **FAIL**.

**Frontend automated checks (React TypeScript):**

- Check for a schema validation library in `package.json`: `zod`, `yup`, `joi`, `superstruct`, `valibot`. Prefer Zod for TypeScript projects (provides runtime validation + type inference).
- For each form component, verify validation is applied before submission:
  - React Hook Form: check for `resolver` prop (e.g., `zodResolver`, `yupResolver`).
  - Formik: check for `validationSchema` or `validate` prop.
  - Native forms: check for validation in `onSubmit` handlers before API calls.
- Check API client layer (axios/fetch wrappers) for runtime type validation:
  - Verify API response data is validated at runtime (TypeScript types alone are erased at runtime and don't prevent malformed data). Look for Zod `.parse()`, `io-ts` decode, or equivalent on API responses.
  - Verify request payloads are validated before sending.
- Check that URL params consumed via `useParams()` / `useSearchParams()` are validated/parsed before use — TypeScript types on these are `string | undefined`, not validated data.
- Check for `as` type assertions or `any` casts on user input that bypass TypeScript's type safety without runtime validation.
- Flag any `fetch`/`axios` call that sends user input without prior validation in the call chain.

**Manual review flag:**

- "Verify frontend and backend validation rules are aligned (frontend is defense-in-depth, backend is authoritative)."
- "TypeScript types provide compile-time safety but NOT runtime validation — verify runtime checks exist at system boundaries."

---

### as-2: Parameterised Interfaces

**Risk Level:** LR: 1 | MR: 1

**Backend automated checks:**

- Search for raw `java.sql.Statement` usage — flag any instance (should use `PreparedStatement` or JPA).
- Search for `@Query` annotations with `nativeQuery = true` — verify they use `?` positional or `:named` parameters, not string concatenation.
- Search for string concatenation in SQL-like contexts: patterns like `"SELECT" + variable`, `"INSERT" + variable`, `String.format` with SQL keywords.
- Check for `JdbcTemplate` usage — verify only `query(sql, args)` overloads are used, not `query(sql)` with concatenated strings.

**Frontend automated checks (React TypeScript):**

- Check that API calls use parameterised URL construction (e.g., template literals with encoded params, `URLSearchParams`, or library like `qs`) rather than raw string concatenation of user input into URLs.
- Flag any pattern where user input is interpolated into API URLs without `encodeURIComponent()` or equivalent.
- If the app uses GraphQL: verify queries use variables (`$var`) rather than string interpolation of user input into query strings.
- Check for type-safe API route definitions (e.g., typed route builders) that prevent malformed URL construction.

**Manual review flag:**

- "Verify no system command execution uses unsanitised input (Runtime.exec, ProcessBuilder with user-supplied args)."

---

### as-3: Output Sanitisation

**Risk Level:** LR: 1 | MR: 0

**Backend automated checks:**

- Verify `@RestController` endpoints return proper `Content-Type` headers (not `text/html` for API endpoints).
- Check for any direct HTML string construction in Java code (e.g., string concatenation producing HTML).
- Flag any endpoint that returns user-supplied content with `text/html` content type without sanitisation.

**Frontend automated checks (React TypeScript):**

- Search for `dangerouslySetInnerHTML` — flag every usage for review.
- For each `dangerouslySetInnerHTML` usage, verify `DOMPurify.sanitize()` or equivalent is called on the input.
- Search for direct DOM manipulation that bypasses React's escaping:
  - `ref.current.innerHTML = ...`
  - `document.getElementById(...).innerHTML = ...`
  - `insertAdjacentHTML()`
  - `document.write()` / `document.writeln()`
- Check for `<iframe>` elements with dynamic `src` or `srcdoc` attributes derived from user input.
- Check for `javascript:` protocol in dynamic `href` attributes: `<a href={userInput}>` — React 19 warns but doesn't fully block this.
- Flag usage of `eval()`, `new Function()`, or `setTimeout`/`setInterval` with string arguments (code injection vectors).
- Check if markdown rendering libraries (e.g., `react-markdown`, `marked`) are configured with sanitisation enabled (e.g., `remarkPlugins` that strip HTML, or `DOMPurify` post-processing).

**Result note:**

- React auto-escapes string values in TSX by default — XSS risk exists only through explicit escape hatches listed above.

---

### as-4: Authentication Mechanism Rate-Limiting

**Risk Level:** LR: 1 | MR: 1

#### Basic Auth Flow

**Backend automated checks:**

- Verify the User entity/model has fields for tracking failed login attempts and account lockout:
  - A `retryAttempt` / `failedAttempts` (or equivalent) integer field.
  - A `locked` / `isLocked` (or equivalent) boolean field.
- Verify the security filter chain or an authentication event listener contains account-lockout logic:
  - On failed login: increments the `retryAttempt` field (auto-increment pattern).
  - When a threshold is reached: sets `locked = true` and blocks further attempts.
  - Check for `AuthenticationFailureBadCredentialsEvent` listener, custom `AuthenticationFailureHandler`, or filter that performs this logic.

#### Authorization Code Flow (Login Endpoint Rate Limiting)

**Backend automated checks:**

- Check `pom.xml` or `build.gradle` for `bucket4j-spring-boot-starter` dependency.
- Verify `application.yml` contains a `bucket4j.filters` block that:
  - Targets the login endpoint via `url` regex pattern (e.g. `^(/login|/api/auth/login).*` or `/api/auth/**`).
  - Keys the rate limit on the client's remote IP using `cache-key: "getRemoteAddr()"` or `"getHeader('X-Forwarded-For') ?: getRemoteAddr()"` (for proxied environments).
  - Defines `bandwidths` with a `capacity` and `time`/`unit` window (e.g. 5 requests per minute).
- Example of compliant config:
  ```yaml
  bucket4j:
    enabled: true
    filters:
      - cache-name: buckets
        url: ^(/login|/api/auth/login).*
        rate-limits:
          - cache-key: "getHeader('X-Forwarded-For') ?: getRemoteAddr()"
            bandwidths:
              - capacity: 5
                time: 1
                unit: minutes
  ```

**Frontend automated checks (React TypeScript):**

- Check login/authentication forms for client-side rate-limit feedback handling:
  - Verify the UI handles 429 (Too Many Requests) responses gracefully (displays user-facing message, disables submit button, shows countdown).
  - Check for client-side submission throttling/debouncing on login forms (e.g., disabling the submit button after click, debounce on the handler).
- Flag login forms that allow unlimited rapid submissions without any UI-level throttle (even though backend enforces it, UX should reflect it).

**Manual review flag:**

- "Verify rate-limit thresholds are appropriate (e.g., 5 attempts per minute per IP)."
- "If behind a load balancer or proxy, verify `X-Forwarded-For` is trusted and not spoofable."

---

### as-5: Password Requirements

**Risk Level:** LR: 1 | MR: 1

**Backend automated checks:**

- Search for password validation logic: custom `PasswordValidator` classes, Bean Validation annotations on password fields (`@Size(min=...)`, `@Pattern`).
- Check for Spring Security `PasswordPolicy` or custom password strength checking beans.
- Verify minimum length constraint exists (recommended: >= 8 characters).

**Frontend automated checks (React TypeScript):**

- Check password input fields in registration/change-password forms for validation:
  - Minimum length enforcement (check for `minLength` attribute, validation schema rules, or custom validation logic).
  - Complexity feedback: verify presence of a password strength indicator or inline validation messages (e.g., `zxcvbn` library, custom regex checks).
- Verify password requirements are displayed to the user before/during input (not just on submit failure).
- Check that password confirmation field (if present) validates match before submission.
- Verify the password field uses `type="password"` (not `type="text"`).

**Manual review flag:**

- "If SSO/passwordless is the only auth method, this control is N/A — confirm with team."
- "Verify frontend and backend password rules are consistent."

---

### as-6: Password Salting and Hashing

**Risk Level:** LR: 1 | MR: 1

**Backend automated checks:**

- Find `@Bean` returning `PasswordEncoder` — verify it is one of: `BCryptPasswordEncoder`, `Argon2PasswordEncoder`, `SCryptPasswordEncoder`, or `DelegatingPasswordEncoder` (which defaults to BCrypt).
- Flag any usage of: `NoOpPasswordEncoder`, `MD5`, `SHA-1`, `SHA-256` (without proper KDF), `MessageDigest` for passwords.
- Check for `PasswordEncoderFactories.createDelegatingPasswordEncoder()` (acceptable).
- Search for any raw password storage: fields named `password` persisted without encoder call path.

**Frontend automated checks (React TypeScript):**

- Verify passwords are NEVER hashed client-side before sending to the backend (client-side hashing is an anti-pattern — if found, it means the hash becomes the de-facto password).
- Verify passwords are transmitted over HTTPS only — check API base URLs in config/env files are `https://` (not `http://`).
- Check that password values are not stored in `localStorage` or `sessionStorage` (search for `localStorage.setItem` / `sessionStorage.setItem` with password-related keys).
- Verify password fields do not have `autocomplete="off"` (modern best practice is to allow password managers: `autocomplete="current-password"` or `autocomplete="new-password"`).

**FAIL conditions:**

- `NoOpPasswordEncoder` found anywhere.
- `MD5`/`SHA` used directly for password hashing.
- No `PasswordEncoder` bean found.
- Passwords stored in `localStorage`/`sessionStorage`.

---

### as-7: Access Control Check Enforcement

**Risk Level:** LR: 1 | MR: 0

**Backend automated checks:**

- Check if method-level security is enabled: `@EnableMethodSecurity` or `@EnableGlobalMethodSecurity` in configuration.
- For each `@RestController`/`@Controller` method, verify at least one of:
  - `@PreAuthorize` / `@PostAuthorize` / `@Secured` / `@RolesAllowed` annotation present, OR
  - URL is covered by `SecurityFilterChain` `.authorizeHttpRequests()` rules.
- Flag any controller method that has neither annotation nor URL-rule coverage.
- Check for overly permissive rules: `.anyRequest().permitAll()` without preceding restrictive rules.

**Frontend automated checks (React TypeScript):**

- Check for route-level access control:
  - Verify protected routes are wrapped with an auth guard component (e.g., `ProtectedRoute`, `RequireAuth`, `AuthGuard` or equivalent HOC/wrapper).
  - Check React Router config for routes that should require authentication but lack a guard.
  - If using TanStack Router (`@tanstack/react-router`): check for `beforeLoad` or `loader` guards on protected routes that verify authentication/authorization before rendering, and verify an `unauthorized`/`notFound` component is configured for unauthenticated access.
- Check for role-based UI rendering:
  - Verify the app has a mechanism to conditionally render UI based on user roles/permissions (e.g., `useAuth()` hook, `<RoleGate>` component, permission context).
  - Flag admin-level UI components/routes that don't check user role before rendering.
- Verify the app handles 401/403 API responses correctly:
  - Check for a global axios/fetch interceptor that redirects to login on 401.
  - Check that 403 responses display an "access denied" page rather than crashing or showing partial data.
- Check that auth tokens (JWT/session cookies) are included in API requests via interceptor or wrapper (not manually per-call, which risks omission).

**Manual review flag:**

- "Verify authorization logic is correct (right roles for right endpoints) — automation only checks presence, not correctness."
- "Frontend guards are UX only — confirm backend enforces all access control independently."

---

### as-8: Secrets Management

**Risk Level:** LR: 1 | MR: 0

**Backend automated checks:**

- Scan `application.yml` / `application.properties` (all profiles) for hardcoded secrets: patterns matching passwords, API keys, tokens, connection strings with credentials.
- Check for secret management dependencies: `spring-cloud-vault`, `spring-cloud-aws-secrets-manager`, `aws-secretsmanager-jdbc`.
- Run pattern-based scan for hardcoded secrets in Java/Kotlin files: `private.*key = "`, `password = "`, `secret = "`, `token = "`.
- Look for `@Value("${...}")` annotations pulling from environment/external config (good) vs hardcoded values (bad).

**Frontend automated checks (React TypeScript):**

- Check `.env` files are in `.gitignore` — verify `.env`, `.env.local`, `.env.production` are all listed.
- Scan for hardcoded secrets in frontend source: API keys, tokens, or credentials in `.ts`/`.tsx` files.
- Check that environment variables use the `VITE_` / `NEXT_PUBLIC_` / `REACT_APP_` prefix only for truly public config — flag any that look like secrets (containing `SECRET`, `PRIVATE`, `KEY` in the name).
- Verify no sensitive tokens (auth tokens, refresh tokens) are stored in `localStorage` (vulnerable to XSS). Prefer `httpOnly` cookies or in-memory storage.
  - Search for `localStorage.setItem` with keys like `token`, `accessToken`, `refreshToken`, `jwt`, `auth`.
- Check that API keys embedded in the frontend bundle are intended to be public (e.g., Google Maps API key with referrer restrictions vs a private backend API key).
- Flag any `.env` file committed to the repository (search git tracked files).

**Manual review flag:**

- "Verify secrets are sourced from an approved store (AWS Secrets Manager, HashiCorp Vault, etc.) in production configuration."
- "Verify all frontend-exposed environment variables are safe to be public."

---

### as-9: Content Security Policy (CSP)

**Risk Level:** LR: 1 | MR: 1

**Backend automated checks:**

- Search Spring Security config for `.headers(h -> h.contentSecurityPolicy(...))` or `.contentSecurityPolicy(csp -> ...)`.
- Also check `application.yml` for CSP configuration via Spring Security properties (e.g. `spring.security.headers.content-security-policy` or equivalent custom properties used to populate the CSP header).
- If found, extract the policy string and verify it is not overly permissive:
  - Flag `unsafe-inline` in `script-src`.
  - Flag `unsafe-eval` in `script-src`.
  - Flag wildcard `*` in any directive.

**Frontend automated checks (React TypeScript):**

- Check `index.html` for CSP `<meta http-equiv="Content-Security-Policy">` tag as an alternative delivery mechanism.
- If CSP uses `nonce`-based script loading, verify the React build pipeline supports nonce injection (e.g., `__webpack_nonce__`, Vite HTML plugin, or server-rendered nonce).
- Check if the React app loads scripts/styles from CDNs — these origins must be explicitly allowed in CSP. Flag any dynamic script loading (`document.createElement('script')`) that might violate CSP.
- Check for inline styles that would conflict with `style-src` CSP:
  - Flag heavy use of inline `style={{}}` attributes if CSP disallows `unsafe-inline` for `style-src` (CSS-in-JS libraries like styled-components need `nonce` or hash support).
- If no CSP found in either backend headers or frontend meta tag, FAIL.

**Manual review flag:**

- "Review CSP directives against actual resource origins used by the application."
- "If using CSS-in-JS (styled-components, Emotion), verify CSP compatibility via nonce injection."

---

### as-10: HTTP Strict Transport Security (HSTS)

**Risk Level:** LR: 2 | MR: 2

**Automated checks:**

- Check Spring Security config — HSTS is enabled by default for HTTPS in Spring Security 6.x. Look for explicit disabling: `.headers(h -> h.httpStrictTransportSecurity(hsts -> hsts.disable()))`.
- If custom config exists, verify `maxAgeInSeconds >= 31536000` (1 year).
- Check for `includeSubDomains` (recommended).
- If running integration tests, assert `Strict-Transport-Security` header in responses.

**PASS conditions (any):**

- Spring Security default (not explicitly disabled) + app serves over HTTPS.
- Explicit HSTS config with max-age >= 31536000.

---

### as-11: Session Management

**Risk Level:** LR: 1 | MR: 1

**Backend automated checks:**

- Check `application.yml`/`application.properties` for `server.servlet.session.timeout` — verify it is set and <= 15 minutes (or per org policy).
- Check Spring Security config for `.sessionManagement()` configuration.
- Look for `maximumSessions` setting (concurrent session control).
- Check for session fixation protection: `.sessionFixation().migrateSession()` or `.newSession()` (Spring Security defaults to migrateSession).

**Frontend automated checks (React TypeScript):**

- Check for idle/inactivity timeout implementation:
  - Look for idle detection libraries (`react-idle-timer`, `idle-js`) or custom event listeners tracking `mousemove`/`keydown`/`click` activity.
  - Verify that on idle timeout, the app logs the user out or prompts for re-authentication.
- Check for token/session expiry handling:
  - Verify the app checks token expiry (e.g., decoding JWT `exp` claim) and redirects to login when expired.
  - If using refresh tokens, verify refresh logic exists and handles refresh failure (redirect to login).
- Check that closing the browser/tab clears sensitive session data:
  - If tokens are in `sessionStorage` (cleared on tab close): acceptable.
  - If tokens are in `localStorage` (persists across sessions): flag for review — may conflict with session timeout intent.
- Check for "remember me" functionality — if present, verify it has its own (longer but still bounded) expiry.
- Verify that logout action clears all client-side auth state (`localStorage`, `sessionStorage`, in-memory stores, cookies).

**Manual review flag:**

- "Confirm session timeout value aligns with organisational policy (default threshold is 15 minutes)."
- "Verify frontend idle timeout matches or is shorter than backend session timeout."

---

### as-12: Malware Scanning of Uploaded Files

**Risk Level:** LR: 2 | MR: 2

This application uses the **Secure File Scanner (SFS)** — the company's proprietary async malware scanning service. The checks below are organised by deployment profile. Determine which profile is active first, then run the corresponding checks.

#### Profile detection

- Check `application.yml` / `application.properties` for `spring.profiles.active` or `spring.profiles.include`.
  - Profile `aws` (or `sfs`) → run **AWS/SFS profile checks**.
  - Profile `standalone` (or no scanner profile) → run **Standalone profile checks**.
- Also check for `file.scanner.enabled` property: `false` means **scanner bypass mode** is active (acceptable only in local dev — FAIL in production).

---

#### AWS/SFS profile — backend automated checks

**SFS client integration:**

- Check `pom.xml`/`build.gradle` for the SFS client dependency (e.g., `SfsScannerClient`, MCC outbound client, or the team's internal SFS starter).
- Verify `SfsScannerClient` (or equivalent) is declared as a Spring bean and injected into the file processing service/scheduler.
- Check for the SFS endpoint configured via properties (e.g., `file.scanner.sfs-url`, `sfs.endpoint`) — flag if hardcoded rather than externalised.
- Verify MCC authentication is used for SFS calls (shared authenticated outbound client, background mode for scheduled jobs) — flag any unauthenticated SFS calls.

**Async scan orchestration (Phase 0–3):**

- Verify a scheduled orchestrator (`@Scheduled` + `ShedLock`) runs the four processing phases:
  - **Phase 0** — zombie/timeout/retry-exhausted cleanup: check for logic that transitions files stuck in non-terminal states beyond a configured timeout to `BAD_RESULT` or a terminal error state.
  - **Phase 1** — SFS submission: verify a 10-file (or configurable) in-flight cap is enforced before submitting new files; check the SFS `/put` call uses a presigned upload URL.
  - **Phase 2** — verdict polling: verify the SFS `/get` (poll) call is made for files in `PENDING_SCAN_RESPONSE` state; check that all three verdict branches are handled: `allowed` → Phase 3, `blocked` → `BAD_RESULT`, `error` → retry/exhaustion logic.
  - **Phase 3** — clean file download and hash verification: verify SHA-256 of the downloaded clean file is compared against the hash computed at upload time; verify `Sanitized` verdict always transitions to `DOWNLOADED_FILE_MISMATCH` (not `DOWNLOADED`) regardless of hash match.
- Verify `jobAttempts` is incremented in a `finally` block (not only on success) so retry counts are persisted even on unexpected exceptions.
- Verify a maximum retry limit is enforced; files exceeding it transition to a terminal failure state (not retried indefinitely).

**State machine (8 states):**

- Verify the `FILE_METADATA` table (or entity) has all required states: `PENDING_SCAN`, `PENDING_SCAN_RESPONSE`, `PENDING_DOWNLOAD`, `DOWNLOADED`, `BAD_RESULT`, `DOWNLOADED_FILE_MISMATCH`, and any timeout/error states defined by the standard.
- Verify state transitions are guarded with `PESSIMISTIC_WRITE` locks to prevent concurrent processing of the same file.
- Verify only files in `DOWNLOADED` state are accessible for download — check the download endpoint/service for a state guard.

**Dirty/clean blob separation:**

- Verify uploaded (dirty) file content is stored in a separate store (`FILE_CONTENT_DIRTY`) and never served directly to users.
- Verify dirty blobs are deleted after the file reaches a terminal state (clean promotion, `BAD_RESULT`, or `DOWNLOADED_FILE_MISMATCH`) — no dirty blob should survive past terminal state.
- Verify `deleteDirty` is idempotent (safe to call multiple times).

**Upload-time validation (mandatory before SFS submission):**

- Verify all of the following are checked before the file is stored dirty and queued for scanning:
  - **File size**: `spring.servlet.multipart.max-file-size` and `spring.servlet.multipart.max-request-size` are configured; and a per-file size limit is enforced in application logic.
  - **Display name**: validated against regex `^[a-zA-Z0-9_\- ]+$` — flag absence as FAIL (prevents path traversal).
  - **MIME type**: checked against an explicit allowlist (not extension alone).
  - **Magic bytes**: file content signature verified in code (not trusting client-supplied MIME type):
    - PDF: `25 50 44 46 2D`
    - PNG: `89 50 4E 47 0D 0A 1A 0A`
    - JPEG: any of the five recognised JPEG signatures
    - ZIP: `50 4B 03 04`
    - (and any other types the application accepts)
  - **Encrypted PDF detection**: `PDFBox Loader.loadPDF(RandomAccessReadBuffer)` (or equivalent) used to reject password-protected/encrypted PDFs before storage.
  - **SHA-256 hash**: computed at upload time from raw bytes (after size check) and stored for later Phase 3 verification.
  - **UUID**: a new UUID is generated for each file at upload time.

**Exception typing:**

- Verify SFS integration uses typed exceptions for each failure mode: `SfsUploadException`, `SfsPollingException`, `SfsDownloadException` — each must capture `fileId`, HTTP status code, and a truncated response body (≤ 1 KB). Flag any catch block that swallows exceptions without persisting error state.

**Distributed scheduling:**

- Verify `ShedLock` (or equivalent distributed lock) is configured for the scanner scheduler to prevent duplicate processing across nodes.

**Scanner bypass mode (dev only):**

- Check for `file.scanner.enabled=false` conditional bean: verify that when bypass is active, files are promoted immediately (like Standalone) — this path must not be reachable in production config. Flag if the bypass property is set to `false` in any non-`local`/`dev` profile configuration.

---

#### Standalone profile — backend automated checks

- Verify no SFS client dependency or SFS calls are present (Standalone does not use an external scanner).
- Verify the local promotion step transitions `PENDING_SCAN` → `DOWNLOADED` directly after upload-time validation.
- Verify upload-time validation is identical to the AWS profile (size, display name regex, MIME type, magic bytes, encrypted PDF detection, SHA-256, UUID generation).
- Verify storage quota enforcement: `file.storage.quota` (default 5 GB) is checked before accepting the upload; `StorageQuotaExceededException` → HTTP 507.
- Verify dirty blob is deleted immediately after promotion (no async window).
- Verify the state machine has at minimum: `PENDING_SCAN` → `DOWNLOADED` transitions.

---

#### Frontend automated checks (React TypeScript) — both profiles

**Client-side pre-validation (defense-in-depth):**

- File type restriction: verify `accept` attribute on `<input type="file">` limits to expected MIME types using MIME type values (e.g. `accept="image/png,image/jpeg"`) — extension-only `accept` (e.g. `accept=".png"`) is insufficient, flag as WARN.
- File size: check for client-side file size validation before upload (`file.size > MAX_SIZE` check), with a user-facing error message.
- Zero-byte rejection: verify the handler rejects files with `file.size === 0` before calling the upload API.
- MIME type check in handler: verify `file.type` is checked against an allowlist in the upload handler (not only via the `accept` attribute, which can be bypassed).
- Display name / filename validation: verify a regex or character-allowlist check is applied to the display name before submission — must align with backend `^[a-zA-Z0-9_\- ]+$` rule.
- Drag-and-drop upload areas: verify they apply the same type/size/MIME restrictions as the file input element.

**Status polling and scan-state UI (AWS/SFS profile):**

- Verify the file list UI polls for status (e.g., every 3 seconds) while any file is in a non-terminal state.
- Verify polling pauses when the browser tab is hidden (Page Visibility API — `document.visibilityState`).
- Verify polling stops when all visible files reach a terminal state.
- Check for user-facing stage labels that map internal states to friendly strings:
  - `PENDING_SCAN` / `PENDING_SCAN_RESPONSE` → "Queued" / "Scanning"
  - `PENDING_DOWNLOAD` → "Finalizing"
  - `DOWNLOADED` → "Ready"
  - `BAD_RESULT` → "Failed" (blocked message, see below)
  - `DOWNLOADED_FILE_MISMATCH` → "Failed" (mismatch message, see below)
- Verify a "Taking longer than usual" message appears after ~30 seconds of scan-in-progress state.

**Terminal failure state handling (AWS/SFS profile):**

- `BAD_RESULT` (blocked by scanner): verify the UI shows a distinct message such as "This file was blocked during security scanning and cannot be downloaded." — and that no download link is shown.
- `DOWNLOADED_FILE_MISMATCH` (sanitized/hash mismatch): verify the UI shows a distinct message such as "This file was modified during security scanning — re-upload the original file." — and that no download link is shown.
- Verify the UI does **not** offer an auto-retry button for blocked/mismatched files (system retries are handled by `jobAttempts`; user action is to re-upload).

**Download gate:**

- Verify download links/buttons are only rendered for files in `DOWNLOADED` state.
- Flag any code path that calls the download API for a file not in `DOWNLOADED` state.
- Verify previewing/rendering file content (e.g., `URL.createObjectURL()` for images) only occurs after the file reaches `DOWNLOADED` state — previewing dirty or scan-pending content is a FAIL.

**Standalone profile UI:**

- Verify a single post-upload refetch (e.g., 1 second after upload) is used to confirm `DOWNLOADED` state — continuous polling is not required for Standalone.
- Verify a storage quota indicator is present and warns the user when approaching the limit.

**Admin diagnostics:**

- If an admin view exists, verify it exposes lifecycle state, error category, `jobAttempts` (AWS only), scanner job UUID (AWS only), event timestamps, and file metadata — without exposing raw scanner response bodies or presigned URLs.

---

**FAIL conditions:**

- File upload endpoints exist but no SFS integration found (AWS profile) and no local promotion logic found (Standalone profile).
- Scanner bypass mode (`file.scanner.enabled=false`) active in a non-dev profile.
- `Sanitized` verdict transitions to `DOWNLOADED` instead of `DOWNLOADED_FILE_MISMATCH`.
- Dirty blobs reachable via the download endpoint.
- Files in `BAD_RESULT` or `DOWNLOADED_FILE_MISMATCH` state are downloadable via the UI.
- Upload-time validation missing any of: magic bytes check, display name regex, encrypted PDF detection, SHA-256 hash computation.
- Frontend previews file content before `DOWNLOADED` state is confirmed.
- `jobAttempts` not persisted in a `finally` block (AWS profile).

**Manual review flags:**

- "Verify SFS service endpoint and MCC authentication credentials are correctly configured per environment."
- "Verify scanner bypass mode is disabled in all non-local environments."
- "Verify zombie detection timeout values are tuned for expected scan durations."
- "Verify the `jobAttempts` retry limit is appropriate and aligns with SFS SLA."
- "Verify dirty blob deletion is confirmed by checking storage after terminal state transitions in a test environment."
- "Verify the magic bytes allowlist covers all MIME types the application accepts."
- "For Standalone: verify storage quota is set correctly per deployment sizing."

---

### as-13: Exposure of Internal System Details

**Risk Level:** LR: 2 | MR: 2

**Backend automated checks:**

- Check `application.yml`/`application.properties`:
  - `server.error.include-stacktrace` should be `never` (Spring Boot default is `never` — absence is acceptable as PASS; explicit `always` or `on_param` in production is a FAIL).
  - `server.error.include-message` should be `never` (Spring Boot default is `never` — absence is acceptable as PASS; explicit `always` is a FAIL).
  - `server.error.include-binding-errors` should be `never` (Spring Boot default is `never` — absence is acceptable as PASS; explicit `always` is a FAIL).
- Verify a `@ControllerAdvice` or `@RestControllerAdvice` exists with `@ExceptionHandler` that returns generic error responses.
- Check that exception handlers do not expose `e.getMessage()` or `e.getStackTrace()` in response bodies.
- Verify Spring Boot Actuator endpoints (if present) are secured:
  - `management.endpoints.web.exposure.include` must list exact endpoint IDs (e.g. `health,info`) — flag wildcard `*` as a FAIL.
  - Verify the actuator base path is protected by authentication (separate security filter or role restriction).
  - Flag any actuator endpoint exposure beyond `health` and `info` without explicit justification.

**Frontend automated checks (React TypeScript):**

- Check for React Error Boundaries:
  - Verify at least one Error Boundary component exists (class component with `componentDidCatch` / `getDerivedStateFromError`, or a library like `react-error-boundary`).
  - Verify Error Boundary renders a generic fallback UI, NOT the raw `error.message` or `error.stack`.
- Check that API error responses are not displayed verbatim to users:
  - Search for patterns like `{error.message}` or `{err.response.data}` rendered in TSX — these may leak backend details.
  - Verify a generic error message is shown instead of raw server responses.
- Check for `console.log`/`console.error` of sensitive data that remains in production builds:
  - Verify build config strips console statements in production (e.g., `babel-plugin-transform-remove-console`, Terser `drop_console` option, or ESLint `no-console` rule).
  - Flag any `console.log` that outputs tokens, user data, or full API responses.
- Verify `source-map` generation is disabled for production builds:
  - Check Vite config: `build.sourcemap` should be `false` or `'hidden'` (not `true`).
  - Check webpack/CRA: `GENERATE_SOURCEMAP=false` in production env.
  - Source maps expose original source code to anyone with browser devtools.
- Check that the React app does not expose internal API URLs, service names, or infrastructure details in client-visible error messages or HTML comments.

---

### as-14: Secure Cryptographic Libraries

**Risk Level:** LR: 2 | MR: 2

**Backend automated checks:**

- Check `pom.xml`/`build.gradle` for crypto dependencies: `org.bouncycastle`, `javax.crypto`, `java.security`.
- Flag deprecated/weak algorithms in code: `DES`, `DESede` (3DES), `RC4`, `RC2`, `MD5` (for crypto, not checksums), `SHA-1` (for signatures).
- Verify `SecureRandom` is used (not `Random`) for security-sensitive randomness.
- Check for hardcoded IVs or keys in source code.
- Verify TLS configuration if custom: `TLSv1.2` minimum, no `SSLv3`.

**Frontend automated checks (React TypeScript):**

- If the frontend performs any cryptographic operations (e.g., Web Crypto API, `crypto-js`, `tweetnacl`):
  - Verify it uses the native Web Crypto API (`window.crypto.subtle`) where possible (hardware-backed, non-blocking) rather than pure-TypeScript polyfills.
  - Flag usage of `crypto-js` (pure JS implementation, slow, less audited) — prefer `Web Crypto API` or `libsodium-wrappers` (which have TypeScript type definitions).
  - Check for weak algorithms: `MD5`, `SHA-1`, `DES`, `RC4` in frontend crypto code.
  - Verify `crypto.getRandomValues()` is used for secure random generation (not `Math.random()`).
- Flag `Math.random()` usage for anything security-sensitive: token generation, nonce creation, CSRF tokens, OTP generation.
- Check `package.json` for known vulnerable crypto packages (e.g., deprecated versions of `node-forge`, `sjcl`).
- If the app generates client-side tokens or identifiers, verify they use `crypto.randomUUID()` or `crypto.getRandomValues()`.

**FAIL conditions:**

- `DES`, `RC4`, or `SSLv3` usage found.
- `java.util.Random` used for tokens/keys/nonces.
- `Math.random()` used for security-sensitive values in frontend.

---

### as-15: Password Change

**Risk Level:** LR: N/A | MR: N/A

**Applicability check:** Only applies to basic authentication flows. If SSO/passwordless is the only auth method, mark as N/A.

**Backend automated checks:**

- Verify the User entity/model has a field indicating a forced password reset is required (e.g., `forcePasswordChange`, `mustResetPassword`, `passwordResetRequired` boolean field).
- Verify the account unlock flow sets this flag to `true` when the account transitions from locked to unlocked:
  - Check admin unlock endpoints, scheduled unlock tasks, or self-service unlock handlers for logic that sets the forced password change flag.
- Check for logic in the authentication filter chain, login handler, or `AuthenticationSuccessHandler` that intercepts login attempts where the forced password change flag is `true` and:
  - Prevents the normal authentication flow from completing (blocks access to the application).
  - Returns a response indicating password reset is required (e.g., a specific HTTP status code, error code, or redirect).
- Verify a password change endpoint exists (e.g., `/api/auth/reset-password`, `/api/auth/change-password`) that:
  - Requires the current (old) password or a valid reset token.
  - Sets the forced password change flag to `false` after successful password change.
- Check that the forced password change cannot be bypassed by calling other API endpoints while the flag is `true`:
  - Look for a servlet filter or Spring Security filter that intercepts all authenticated requests and blocks non-password-change requests when the flag is set.
  - Verify only the password change endpoint and logout endpoint are accessible in this state.

**Frontend automated checks (React TypeScript):**

- Check for handling of "password reset required" response from the backend after login:
  - Verify the login flow detects the forced password change response (e.g., specific HTTP status code, response body flag, or redirect URL).
  - Verify the app redirects to a dedicated forced password change page or modal.
- Check that the forced password change UI:
  - Requires the user to enter a new password (and confirm it).
  - Prevents navigation away from the password change page without completing the reset (e.g., route guard, modal that cannot be dismissed).
  - Submits to the backend password change endpoint and only then proceeds to the main application.
- Verify the app does not allow the user to access any protected routes while in the "must change password" state (e.g., a route guard or global interceptor that redirects back to the password change page).

**FAIL conditions:**

- No forced password change flag found on the User entity/model.
- Account unlock flow does not set the forced password change flag.
- No filter or interceptor enforcing the forced password change on subsequent requests.

**Manual review flag:**

- "If SSO/passwordless is the only auth method, this control is N/A — confirm with team."
- "Verify that the forced password reset flow is triggered after every account unlock from lockout."
- "Verify the user cannot reuse the previous (compromised) password during forced reset."

---

### lm-4: Audit Logging

**Risk Level:** LR: 1 | MR: 0

**Backend automated checks:**

- Check for audit logging framework: Spring Boot Actuator audit events, `@EventListener(AuditApplicationEvent.class)`, custom audit interceptors/aspects.
- Look for audit log calls on key actions: login success/failure, permission changes, data modifications.
- Check for `AuditAware` or `@CreatedBy`/`@LastModifiedBy` JPA auditing annotations.
- Verify logging of: authentication events, authorization failures, data access.

**Frontend automated checks (React TypeScript):**

> **Note:** Frontend audit logging checks are WARN (not FAIL) — backend audit logging is authoritative. Frontend tracking is defense-in-depth.

- Check for frontend analytics/event tracking that supports audit trails:
  - If present, verify key user actions are tracked and sent to backend: login attempts, permission-sensitive actions, data exports, admin operations.
  - Check for an analytics or event-logging service integration (e.g., custom audit API calls, analytics SDK).
- Check that audit-relevant context is included in API requests:
  - Verify correlation IDs or request IDs are propagated (e.g., `X-Request-ID` header in API client) — absence is WARN, not FAIL.
  - Check if user-agent and client timestamp are sent with audit-worthy requests — absence is WARN, not FAIL.
- Flag (as WARN) critical user actions (delete, bulk operations, role changes) that don't trigger a corresponding API call that the backend can audit.

**Manual review flag:**

- "Verify audit events cover all management and security-relevant actions per business requirements."

---

### lm-15: Structured Log Formatting

**Risk Level:** LR: 2 | MR: 2

**Backend automated checks:**

- Check `application.yml` for `logging.structured.format.file` (Spring Boot 3.4+ native structured logging) — verify the value is `ecs` (Elastic Common Schema). Absence is acceptable if Logback encoder is configured instead.
- Alternatively, check `logback-spring.xml` / `logback.xml` for JSON encoder: `EcsEncoder` (preferred), `LogstashEncoder`, or `JsonEncoder`.
- Verify log output format is JSON-based with consistent schema.

**Frontend automated checks (React TypeScript):**

> **Note:** Frontend structured logging checks only apply if a frontend logging utility or error reporting service is detected in the codebase. Skip this section if no frontend logger is present.

- If using an error reporting/logging service (Sentry, Datadog RUM, custom logger):
  - Verify structured context is attached to events (user ID, session ID, page/route, action).
  - Check for consistent event naming conventions (e.g., typed event name enums or string literal union types).
- If a custom frontend logging utility exists, verify it produces structured output (e.g., JSON objects with consistent fields like `{ level, message, context, timestamp }`).
- Check that the frontend logger type interface enforces structured fields (leveraging TypeScript to prevent unstructured freeform logging).

**PASS conditions (any):**

- Spring Boot 3.4+ `logging.structured.format.file: ecs` configured.
- Logback ECS/JSON encoder configured in `logback-spring.xml`.

---

### lm-16: Key Signals Monitoring

**Risk Level:** LR: 2 | MR: 2

**Backend automated checks:**
- **Metrics foundation: pass if either path is present:**
  - **Actuator path:** `spring-boot-starter-actuator` is present.
  - **Custom path:** custom Micrometer meters (`@Timed`/`Timer`, `Counter`, `Gauge`) plus a `MeterRegistry` bean. Custom meters without a registry collect nothing.
  - Absence of both paths is FAIL.
- **Per-signal coverage**: each must be satisfied by either Actuator (auto) and/or an explicit meter:
  - **Latency:** Actuator's `http.server.requests` timer (auto, all HTTP endpoints) and/or `@Timed`/`Timer` on business methods.
  - **Traffic:** the `http.server.requests` count (auto) and/or a custom `Counter` for business events.
  - **Errors:** request metrics tagged by `status`/`outcome` (auto) and/or a custom error `Counter`.
  - **Saturation:** JVM/CPU metrics (auto with Actuator) and connection-pool metrics (HikariCP, auto-bound if present), or a custom `Gauge`/`ExecutorServiceMetrics` for bespoke resources (e.g. custom queues, thread pools, in-flight counters).
- **`@Timed` aspect check:** if `@Timed` is used on non-controller methods, verify a `TimedAspect` bean is registered. (`@Timed` on controllers and meters built directly via `MeterRegistry` do not need the aspect.)

**Frontend automated checks (React TypeScript):**
- Check for client-side performance/error instrumentation (e.g. `web-vitals`, Sentry/Datadog browser SDK, an RUM/analytics SDK) capturing page-load latency and JS errors.
- Verify client errors and API failures are reported to a backend-consumable channel, not only `console.error`.

**Manual review flags:**

- "Confirm dashboards and alert thresholds are actually configured on these metrics; alerting config typically lives in Grafana/Prometheus/APM outside the application repo."
- "Confirm saturation metrics cover the service's real bottlenecks (DB pool, thread pools, queues, external rate limits)."

---

### lm-18: Whole of Government Application Analytics (WOGAA)

**Risk Level:** LR: N/A | MR: N/A

**Applicability:** Applies only to **public-facing** government digital services. Internal applications (e.g., intranet tools, internal admin portals, apps serving only internal users/officers) are **not** subject to WOGAA. If the application is internal-only, mark as **N/A** and skip all checks below.

To determine applicability, check the application's purpose and target audience. Look for indicators of an internal app:
- Login restricted to internal users (SSO with corporate IdP, no public registration).
- URL patterns suggesting intranet deployment (e.g., `.internal`, `.intranet`, non-public domains).
- Application description, README, or naming indicating internal use (e.g., "admin portal", "internal dashboard", "staff app", "EMR", "case management").
- No public-facing routes or pages accessible without authentication.

**Frontend automated checks (React TypeScript):**
- Search the codebase for the exact WOGAA script snippet: `<script src="https://assets.wogaa.sg/scripts/wogaa.js"></script>`.

**Backend automated checks:**
No backend checks required for this control.

**FAIL conditions:**
- The application is public-facing AND the exact snippet `<script src="https://assets.wogaa.sg/scripts/wogaa.js"></script>` is not found in the codebase.

---

### lm-19: Log Sanitisation

**Risk Level:** LR: 2 | MR: 1

**Backend automated checks:**

- Search for custom Logback layout/filter: `MaskingPatternLayout`, pattern-based masking converter, or `TurboFilter` that scrubs sensitive data.
- Check for log sanitisation utilities: regex-based masking of emails, credit cards, tokens, IPs.
- Scan log statements for potentially sensitive data being logged directly: `.password`, `.token`, `.secret`, `.ssn`, `.creditCard` fields without masking.
- Check if `@ToString.Exclude` (Lombok) or custom `toString()` methods exclude sensitive fields from objects that may be logged.
- Check whether sensitive data is actually logged vs merely exposed via `toString()`:
  - **FAIL:** Log statements directly log sensitive data (e.g., `log.error(patient.toString())`, `log.error(exception.getMessage())` where the exception message contains PII such as validation violation details with field values, or `log.info("User: " + user)` where `user.toString()` includes PII).
  - **WARN:** Entities with PII have `@Data`/`@ToString` without `@ToString.Exclude` on sensitive fields, but no log statements in the codebase actually log those entities. This is a defense-in-depth gap (a future developer could accidentally log the entity), not an active leak.
  - **WARN:** Framework exception handlers (e.g., `@ControllerAdvice`) log exception messages that could contain PII from validation errors (e.g., `ConstraintViolationException.getMessage()` includes the invalid field value), but only on error paths.

**Frontend automated checks (React TypeScript):**

> **Note:** Frontend log sanitisation checks only apply if a frontend logging utility or error reporting service is detected in the codebase. Skip this section if no frontend logger is present.

- Check for sensitive data in frontend logging/error reporting:
  - If using an error reporting service (Sentry, Datadog RUM, LogRocket), verify it is configured to scrub sensitive data:
    - Sentry: check for `beforeSend` hook that strips PII, or `denyUrls`/`ignoreErrors` config.
    - Check for `scrubFields` or equivalent configuration redacting passwords, tokens, etc.
  - Search for `console.log`/`console.error`/`console.warn` statements that output user PII, tokens, or credentials.
- Check that API request/response interceptors (axios interceptors, fetch wrappers) do not log full request bodies containing sensitive data (passwords, tokens) without masking.
- If the app has a custom frontend logger utility, verify it has a sanitisation layer for known sensitive fields.

**Manual review flag:**

- "Verify masking patterns cover all sensitive data types relevant to this application."
- "Verify error reporting SDK (Sentry etc.) is configured to exclude PII in production."

---

### ck-1: Cryptographic Key Establishment

**Risk Level:** LR: 2 | MR: 1

**Applicability check:** Only applies if the application manages its own keys (not delegated to KMS).

**Automated checks:**

- Search for key generation code: `KeyGenerator`, `KeyPairGenerator`, `KeyAgreement`, `SecretKeyFactory`.
- Verify key sizes meet minimum standards:
  - RSA: >= 2048 bits
  - AES: >= 128 bits (256 preferred)
  - EC: >= 256 bits (P-256 or higher)
- Check for NIST-approved algorithms in key establishment: `ECDH`, `DH` with appropriate parameters.
- Flag any custom key derivation that doesn't use standard KDF (`HKDF`, `PBKDF2` with sufficient iterations).

**Skip condition:**

- If all crypto key operations use AWS KMS / GCP KMS / Azure Key Vault SDK, mark as "Delegated to KMS — verify KMS policy instead."

---

### ck-2: Cryptographic Key Rotation

**Risk Level:** LR: 2 | MR: 1

**Applicability check:** Only applies if the application manages its own keys (not delegated to KMS).

**Automated checks:**

- Search for key versioning logic: key IDs with version numbers, key metadata with creation/expiry dates.
- Check for key rotation configuration: scheduled tasks (`@Scheduled`) related to key rotation, key TTL settings.
- Look for key identifier in encrypted payloads (necessary for rotation — decrypt with old, encrypt with new).
- Check application config for key rotation intervals.

**Skip condition:**

- If all keys are KMS-managed, mark as "Delegated to KMS — verify KMS auto-rotation policy instead."

**Manual review flag:**

- "Verify rotation interval meets organisational policy (typically annually or more frequently)."

---

### ck-4: Cryptographic Key Storage

**Risk Level:** LR: N/A | MR: N/A

**Applicability check:** Only applies if the application manages its own cryptographic keys (not fully delegated to KMS or infrastructure-level keystores).

**Skip condition:**

- If all cryptographic key operations use AWS KMS / GCP KMS / Azure Key Vault SDK exclusively, mark as "Delegated to KMS — verify KMS access policy instead."
- If TLS is configured via an infrastructure-level keystore (e.g., `server.ssl.key-store` in Spring Boot) and no application-level cryptographic key management exists (no `KeyGenerator`, `KeyPairGenerator`, `SecretKeySpec`, etc. in application code), mark as **N/A**. Keystore password management is covered by **as-8 (Secrets Management)**, not this control. This control is about cryptographic key *material* being stored insecurely, not about passwords used to access key stores.

**Backend automated checks:**

- Search for hardcoded cryptographic keys in source code:
  - Scan for patterns: `SecretKeySpec`, `new SecretKey`, `PrivateKey`, `KeyFactory` instantiated with inline byte arrays, Base64-encoded strings, or hex strings directly in source.
  - Flag any `private static final` or `private static` fields that hold key material as literal strings or byte arrays (e.g., `private static final String SECRET_KEY = "..."`, `private static final byte[] KEY = { ... }`).
- Search for key files stored within the project directory:
  - Look for `.pem`, `.key`, `.p12`, `.pfx`, `.jks`, `.keystore` files in the project tree.
  - Flag any key files found in the source tree (should be loaded from an external secure store, not bundled with the application).
- Check for key material in configuration files:
  - Scan `application.yml` / `application.properties` (all profiles) for properties containing key material: patterns like `key`, `secret-key`, `private-key`, `signing-key` with inline values (not references to environment variables or external stores).
  - Flag any configuration property that contains what appears to be raw key material (Base64 strings, hex strings, PEM-formatted blocks).
- Check for key loading from environment variables or external stores (good patterns):
  - `@Value("${...}")` referencing an environment variable or config server property for key material.
  - Usage of `KeyStore.load()` with a path from config (not hardcoded).
  - Usage of `ClassPathResource` or `FileSystemResource` pointing to a key file path sourced from config (acceptable if the key file itself is not in the source tree).
- Check for key access controls in code:
  - Verify `KeyStore` instances are loaded with a password (not `null`).
  - Verify key retrieval from `KeyStore` uses a password-protected alias.
- Check for key material in environment/deployment files:
  - Flag `.env` files, Docker Compose files, or Kubernetes manifests in the repo that contain inline key material.

**Frontend automated checks (React TypeScript):**

- Scan for cryptographic key material in frontend source:
  - Search for patterns indicating embedded keys: `-----BEGIN`, `-----BEGIN RSA`, `-----BEGIN PRIVATE`, long Base64 strings (>40 chars) assigned to variables with names containing `key`, `secret`, `cert`, `private`.
  - Flag any hardcoded signing keys, encryption keys, or private keys in `.ts`/`.tsx` files.
- Check `.env` files for key material:
  - Flag any environment variable with names containing `KEY`, `SECRET`, `PRIVATE`, `SIGNING` that has an inline value (not a reference to a runtime-injected variable).
- Verify the frontend does not store or handle private cryptographic keys:
  - Frontend should only use public keys (e.g., for JWT verification, encryption to backend). Flag any reference to private keys in the frontend codebase.
- If Web Crypto API is used (`crypto.subtle.importKey`):
  - Verify key material is not hardcoded in the call — it should be fetched from a secure backend endpoint or derived at runtime.
  - Check that `extractable` is set to `false` where possible to prevent key export.

**FAIL conditions:**

- Hardcoded cryptographic key material found in source code.
- `.pem`, `.key`, `.p12`, `.jks`, or `.keystore` files found in the source tree.
- Raw key material found in `application.yml`/`application.properties`.
- Private keys found in frontend source code.

**Manual review flag:**

- "Verify cryptographic keys are stored in an approved secure store (AWS KMS, HashiCorp Vault, Azure Key Vault, GCP KMS) in production."
- "Verify access controls on key storage follow the principle of least privilege."
- "If key files are loaded from disk at runtime, verify the file system permissions restrict access to the application service account only."

---

### ga-8: Inform Users about GenAI Risks and Limitations

**Risk Level:** LR: N/A | MR: N/A

**Applicability check:** Only applies if the application includes Generative AI features. If no GenAI functionality is present, mark as N/A.

This control can be satisfied by **either** of the two approaches below. Check for both and PASS if at least one is fully implemented.

#### Approach A: Backend-persisted acknowledgement (one-time)

**Backend automated checks:**

- Check for a user acknowledgement persistence mechanism:
  - Search for a field on the User entity/model or a separate acknowledgement record that tracks whether the user has accepted the GenAI risks disclaimer (e.g., `genAiDisclaimerAccepted`, `aiTermsAcknowledged` boolean field, or a dedicated `UserAcknowledgement` table/entity).
- Check for an API endpoint that records the user's acknowledgement:
  - Verify an endpoint exists (e.g., `POST /api/user/acknowledge-genai`, `PUT /api/user/preferences`) that persists the user's acceptance.
- Check for an access-control filter or interceptor that blocks access to GenAI features unless the user has acknowledged the disclaimer:
  - Look for a servlet filter, Spring Security filter, or `@PreAuthorize` condition on GenAI-related endpoints that verifies the acknowledgement flag.
  - Verify GenAI endpoints return an appropriate error (e.g., 403 with a message indicating acknowledgement is required) if the flag is not set.

**Frontend automated checks (React TypeScript):**

- Check for a GenAI risk acknowledgement UI component:
  - Search for components that present a disclaimer, terms, or consent dialog related to AI/GenAI (e.g., keywords: `disclaimer`, `acknowledge`, `hallucination`, `inaccurate`, `fabricated`, `I Agree`, `GenAI`, `AI terms`).
  - Verify the component includes an explicit user action to acknowledge (checkbox, "I Agree" button, or equivalent — not just a dismissible info banner).
- Check that the acknowledgement is required before access:
  - Verify a route guard or conditional render blocks access to GenAI features until the acknowledgement is completed.
  - Verify the acknowledgement state is checked on app load / route navigation (not just shown once and forgotten).
- Check that the acknowledgement is persisted via an API call:
  - Verify the UI calls the backend acknowledgement endpoint upon user acceptance.
  - Verify the acknowledgement state is not stored only in `localStorage` or `sessionStorage` (must be backend-persisted to survive across devices/sessions).

#### Approach B: Frontend-only acknowledgement (shown every session)

In this approach, no backend state or frontend persistent state is required. The acknowledgement must be shown **every time** the user accesses GenAI features (e.g., on every page load, app launch, or navigation to the GenAI section). The frontend may use in-memory state (React state, context) to track acknowledgement within a session for UX purposes, but this is not required.

**Backend automated checks:**

- No backend checks required for this approach.

**Frontend automated checks (React TypeScript):**

- Check for a GenAI risk acknowledgement UI component:
  - Search for components that present a disclaimer, terms, or consent dialog related to AI/GenAI (e.g., keywords: `disclaimer`, `acknowledge`, `hallucination`, `inaccurate`, `fabricated`, `I Agree`, `GenAI`, `AI terms`).
  - Verify the component includes an explicit user action to acknowledge (checkbox, "I Agree" button, or equivalent — not just a dismissible info banner).
- Verify the acknowledgement is shown every time:
  - Verify the acknowledgement dialog/gate is shown on every fresh page load or navigation to GenAI features — it must always appear, with no mechanism to skip it across sessions.
  - The acknowledgement must NOT be persisted in `localStorage`, cookies, or backend state in a way that allows future sessions to bypass the acknowledgement.
- Check that the acknowledgement gates access to GenAI features:
  - Verify a route guard, conditional render, or modal blocks access to GenAI features until the user explicitly acknowledges.
  - Verify the acknowledgement cannot be bypassed by navigating directly to a GenAI route (the guard must trigger on every entry to GenAI features, not just from a specific page).

#### Common checks (both approaches)

- Check the content of the disclaimer (WARN if not verifiable):
  - Flag for manual review if the disclaimer text cannot be verified to mention: risk of inaccurate or fabricated outputs (hallucinations).

**FAIL conditions:**

- GenAI features are present but no acknowledgement/disclaimer mechanism found (neither Approach A nor B).
- Approach A: no backend enforcement preventing access to GenAI features without acknowledgement.
- Approach B: acknowledgement is persisted across sessions (e.g., `localStorage`, cookies, backend state), allowing users to bypass re-acknowledgement on subsequent visits.

**Manual review flag:**

- "Verify the disclaimer text explicitly mentions the risk of inaccurate or fabricated outputs (hallucinations)."
- "For Approach A: verify the acknowledgement is required on every new account or after significant changes to GenAI capabilities."
- "For Approach B: verify the acknowledgement is shown on every session/visit and cannot be bypassed."
- "If no GenAI features exist in the application, confirm this control is N/A."

---


### ac-1: Principle of Least Privilege

**Backend automated checks:**
- Check for URL-role mapping:
  - Verify a `SecurityFilterChain` bean exists with explicit `requestMatchers(...)` role rules mapping URLs to roles.
  - Verify a default-deny catch-all is present (e.g. `anyRequest().denyAll()` or .`anyRequest().authenticated()`).
  - Check rule ordering: specific matchers must come before broad ones (Spring evaluates top-to-bottom, first match wins). Ensure there are no overly broad rules that shadow specific rules below. 
- Check for method-level security:
  - Check for the method-security switch: `@EnableMethodSecurity` (or legacy @EnableGlobalMethodSecurity) in a config class.
  - Check for method-level annotations (`@PreAuthorize`, `@PostAuthorize`, `@Secured`, `@RolesAllowed`) on privileged controller and service methods.
  - Verify ownership/tenancy checks on data access: `#id == authentication.principal.id` style expressions, or equivalent principal checks in the service layer.
- Verify filter-chain role mapping and method-level annotations don't disagree (e.g. route restricted to ADMIN but method allows USER)

**Frontend automated checks:**
- Verify 401/403 responses are handled centrally and trigger a redirect. For example:
  - 401 (unauthenticated / expired token) → redirect to login.
  - 403 (authenticated but not permitted) → redirect to a "forbidden"/access-denied page.
- Verify privilege-dependent UI derives from backend-sourced permissions, not hardcoded client-side role logic.
- Check for a centralised permission/role utility (e.g. usePermission, hasRole) and route guards (e.g. ProtectedRoute) on role-restricted routes 

**Manual review flag:**

- Verify frontend and backend authorization rules are aligned (frontend's UI gating should reflect the backend's privilege rules, backend is authoritative).

---

### ac-2: Multi-Factor Authentication (MFA) Enforcement

**Applicability:** Applies to privileged account logins and privileged actions (and optionally all accounts, per policy).

**Privileged accounts** are accounts holding elevated roles beyond normal user functions, such as `ROLE_ADMIN`, `ROLE_SUPERADMIN`, `ROLE_SYSTEM`, service accounts with write access, or any role that can manage users, modify system configuration, or access sensitive data at scale. Identify these by scanning `SecurityFilterChain` role rules, `@PreAuthorize` expressions, and role enumerations.

**Privileged actions** are high-risk operations that warrant re-verification regardless of how recently the user authenticated, such as: changing account credentials or MFA settings, approving financial transactions, bulk-exporting sensitive data, or deleting records.

---

**Backend automated checks:**

- Check for MFA state fields on the User entity:
  - A flag indicating MFA is enrolled/enabled for this user (e.g., `mfaEnabled`, `totpEnabled`, `mfaEnrolled`).
  - A stored MFA secret or reference to an external MFA credential (e.g., `totpSecret`, `mfaSecretRef`). Verify the stored secret is encrypted at rest (not plaintext).
- Check for a dedicated endpoint accepting the second factor (e.g., `POST /api/auth/mfa/verify`, `POST /api/auth/totp`).
- Verify the application supports at least one additional factor in any of these categories:
  - **Something you know:**
    - **PIN**: check for PIN registration, hashed storage (must not be plaintext), and constant-time validation logic.
  - **Something you have:**
    - **TOTP** (Time-based One-Time Password): check for TOTP secret generation, QR code provisioning endpoint, and TOTP code validation logic using a TOTP library (e.g., `dev.samstevens.totp`, `com.warrenstrange:googleauth`).
    - **HOTP** (HMAC-based / counter-based OTP): check for counter storage and HOTP validation.
    - **OTP via SMS or email**: check for OTP generation (cryptographically random numeric code), storage with expiry, and validation endpoint. Verify OTPs have a short TTL (≤ 10 minutes) and are single-use (invalidated after successful verification or expiry).
    - **Hardware token / FIDO2 / WebAuthn**: check for `webauthn4j`, `yubico-webauthn`, or Spring Security WebAuthn support.
  - **Something you are** (device/IdP-layer only — no application-layer checks expected; flag for manual review if the app claims to implement biometric verification itself).
- For MFA enforcement on privileged account login:
  - Verify a second-factor challenge is issued after primary (password) authentication succeeds — Look for a two-step login flow: first step returns a partial-auth token or session state; second step validates the second factor and issues the full session/token.
  - Verify the partial-auth state cannot be used to access protected resources (check for a filter or guard that requires both factors to be satisfied before granting access).
- For MFA enforcement on privileged actions (step-up authentication):
  - Look for a Spring Security filter, `@PreAuthorize` expression (e.g., `@PreAuthorize("@mfaService.isStepUpVerified(authentication)")`), or custom annotation processor that gates the endpoint on a valid step-up token/flag.

**Frontend automated checks:**

- For MFA enforcement on privileged account login:
  - Verify a separate UI challenge dialog/screen/modal appears after primary credential entry.
  - Verify the step-up UI presents the user's enrolled second-factor options (TOTP, OTP, etc.).
  - Verify the primary credential form and MFA form are separate steps and not a single form submission.
  - Verify that on second factor authentication failure or cancellation, the login fails and no partial state changes occur.
- For MFA step-up on privileged actions:
  - Verify that triggering a privileged action launches a step-up challenge dialog/screen/modal before the action is submitted.
  - Verify the step-up UI presents the user's enrolled second-factor options (TOTP, OTP, etc.).
  - Verify that on step-up failure or cancellation, the privileged action is aborted and no partial state changes occur.
  - Check that the privileged action API call is only made after a successful step-up response from the backend..

**Manual review flags:**
- "Verify MFA policy scope: confirm whether MFA is required for all users or privileged accounts only, per organisational policy."
- "Verify supported factor types meet the minimum bar (at least one 'something you have' factor — TOTP or hardware token)."

---

### ac-3: Inactive and Expired Accounts

**Applicability:** Applies to all user accounts. Accounts must be disabled within **5 calendar days** of the last authorised day of use (e.g., contract end date, role expiry), or automatically after **90 days of inactivity** (no login recorded). Automation can be via SCIM provisioning from an IdP, or the application's own detection and disabling logic.

---

**Backend automated checks:**

- Verify the User entity has an `enabled` / `active` (or equivalent) boolean field that can be set to `false` to disable the account without deletion.
- Application-managed detection: Check for a scheduled job (`@Scheduled` + `ShedLock`, or a batch job) that queries for and disables accounts matching either condition:
  - **Dormancy rule:** `lastLoginAt` older than 90 days AND account is still enabled → disable the account.
  - **Expiry rule:** `accountExpiresAt` is in the past (or within the 5-day grace window) AND account is still enabled → disable the account.
  - Verify the scheduled job runs at least daily (cron expression should be `@daily` or equivalent as a weekly job cannot guarantee the 5-day SLA).
  - Check that disabling an account also invalidates active sessions/tokens.
- For detection via SCIM:
  - Check `pom.xml`/`build.gradle` for a SCIM server library (e.g., `de.captaingoldfish:scim-sdk-server`, or a custom SCIM endpoint implementation).
  - Verify an endpoint exists that accepts `PATCH` or `PUT` requests with `"active": false` or other equivalent parameters to disable accounts.
  - Verify that processing a SCIM `active: false` patch sets the application's `enabled` field to `false` and invalidates active sessions/tokens.

**Frontend automated checks (React TypeScript):**
There are no frontend automated checks for this control. 


**Manual review flags:**
- "Verify the dormancy threshold (90 days) and expiry grace period (5 days) match organisational policy and are configurable per environment."

---

### ac-4: Access Review 

**Applicability**: Only applies for systems with application accounts. Most applications have application accounts — any system where users log in with credentials managed by the application (local username/password, or SSO-provisioned accounts stored in the application's own user table) qualifies. Do NOT confuse this with ac-3 (inactive/expired accounts): ac-3 covers **inactivity-based disabling**, while ac-4 covers **periodic review of granted privileges against a declared baseline**. Batch jobs that only disable inactive accounts or revoke roles after prolonged inactivity satisfy ac-3, NOT ac-4. Mark N/A only if the application has zero application-managed user accounts (e.g., a stateless API that delegates all identity and authorization to an external gateway with no local user table).

**Backend automated checks**
- Detect a self-contained scheduled review-and-revoke pipeline:
  - A scheduled job (`@Scheduled` + ShedLock, or a batch job) that queries accounts for:
    - **Expiry** — past `accountExpiresAt`.
    - **Excessive privilege** — grants the account holds beyond its declared baseline:
      - Detect whether a declared per-account permission baseline exists as a source of truth (e.g. an allow-list config (`service-accounts.yml`, an entitlements file), role definitions in code/config that bound each account's authorities etc). 
      - For each account, compare actually-granted authorities/scopes (e.g. in DB, static code, separate config, runtime JIT mapping, etc) against the declared baseline and flag the difference.
  - The job disables the account for expired accounts (set `enabled`/`active` to false) and invalidates active sessions/tokens; Revokes grants for accounts with excessive privilege.
  - The job runs frequently enough to remove unauthorised or unnecessary access rights within 5 days (for Low, Medium and High Risk Levels).

**Frontend automated checks (React TypeScript):**
There are no frontend automated checks for this control.

**Manual review flags:**
- "If an application-managed workflow is not detected, confirm if the system uses cloud service provider accounts and uses tools such as AWS IAM Access Advisor or Azure AD Access Review to facilitate and manage access reviews."


---

### ac-6: Default Credentials

**Applicability:** Only applies to applications that manage their own credentials. If the application delegates authentication entirely to an external IdP via SSO (e.g. SAML/OIDC), this control is N/A.

**Scope clarification:** This control is about ensuring users are **forced to change default/temporary passwords** before accessing the application. It checks for the `forcePasswordChange` flag, that it is set on account creation/reset paths, and that a filter blocks access until the password is changed. Do NOT check for hardcoded passwords in configuration files here — that is covered by **as-8 (Secrets Management)**. The presence of dev account passwords in config files (e.g., dev-accounts.yml) is an as-8 finding, not an ac-6 finding.

---

**Backend automated checks:**

- Verify the User entity has a boolean field requiring a password change on next login (e.g., `forcePasswordChange`, `mustResetPassword`).
- Verify this flag is set to a `true` state (or `false` for an inverse boolean flag) in all paths that issue a default or temporary credential:
  - Account creation by an admin.
  - Admin-initiated password reset.
  - Self-service "forgot password" flow that sends a temporary credential (check the reset token handler).
  - Any account unlock flow that resets the credential.
- Verify a servlet filter or Spring Security filter checks the flag on every authenticated request and redirects (or returns HTTP 403 with a `PASSWORD_CHANGE_REQUIRED` error code) for all endpoints except the password change and logout endpoints.
- Verify the password change endpoint:
  - Requires the active session (user is already authenticated) plus the current temporary password as confirmation.
  - Sets the flag to `false` only on successful password change.
  - Enforces the new password must meet complexity requirements and must not be the same as the temporary credential.

**Frontend automated checks (React TypeScript):**

- Verify the login flow handles the `PASSWORD_CHANGE_REQUIRED` response (or equivalent HTTP status/error code) from the backend:
  - Check the login error handler for this specific response and verify it redirects to a dedicated forced password change page or modal — not a generic error screen.
- Verify the forced password change UI:
  - Requires entry of the current (temporary) credential or consumes a one-time token from the URL. The form must not allow setting a new password without first validating the current one.
  - Prevents navigation away from the password change page (route guard or undismissable modal) until the change is completed.

**FAIL conditions:**
- No `forcePasswordChange` flag (or equivalent) on the User entity.
- Account creation or admin reset flow does not set the flag.
- Application does not block access to protected endpoints while the flag is set.

---

### ac-7: Singpass / Corppass for Public Users

This control has two parts with the same pattern: use a national identity provider (Singpass or Corppass) for identity assurance, either at login or at the point of high-risk transactions. Check both independently.

---

#### Part A — Singpass for External Public Users (Citizens / Residents)

**Applicability:** Applies to public-facing applications that serve external public users (citizens, residents) and require a high level of identity assurance. If the application does not serve external public users or does not contain high-impact or high-risk transactions, mark Part A as **N/A**.

**High-risk transactions** include but are not limited to: financial disbursements or approvals above a defined threshold, irreversible data deletion, submission of legally binding forms, issuance of permits or licences, or any operation explicitly flagged as high-risk in the application's risk classification.

**Backend automated checks:**

The control is satisfied if Singpass authentication is enforced at **either** login level or transaction level:

- **Option 1 — Singpass at login level:** The application requires Singpass authentication as the login mechanism, so all subsequent actions (including high-risk transactions) are performed under a Singpass-verified identity.
  - Check for Spring Security OAuth2/OIDC client registration pointing to the Singpass OIDC endpoint (e.g., `issuer-uri` or `authorization-uri` matching Singpass).
  - Verify the login flow redirects to Singpass for authentication (no local credential login for public users).
  - Verify the ID token from Singpass is validated (signature, `iss`, `aud` claims).

- **Option 2 — Singpass step-up at transaction level:** The application allows initial login via another mechanism but requires a Singpass step-up before executing high-risk transactions.
  - Check for a Spring Security filter, `@PreAuthorize` expression, or interceptor that validates a short-lived step-up token issued by Singpass on high-risk endpoints.
  - Verify there is a dedicated step-up OIDC flow — a separate authorization request to Singpass (with `prompt=login` or equivalent) that re-authenticates the user at the point of the transaction.
  - Verify the step-up token is scoped to the specific transaction or has a short TTL (≤ 5 minutes) and is single-use.

- In both options, verify the identity verification must go through Singpass.

**Frontend automated checks (React TypeScript):**

- **Option 1 (login level):** Verify the login page for public users redirects to Singpass. There should be no local username/password form for external public users.
- **Option 2 (transaction level):** Verify that UI actions triggering high-risk transactions redirect or present a Singpass re-authentication flow before dispatching the API call. The high-risk API call must only be made after a successful Singpass step-up response.

---

#### Part B — Corppass for Internal Corporate Users (Businesses / Organisations)

**Applicability:** Applies to applications that serve corporate or business users (e.g., companies, organisations interacting with government services) and require a high level of identity assurance. If the application does not serve corporate users or does not contain high-impact or high-risk transactions, mark Part B as **N/A**.

**Backend automated checks:**

The control is satisfied if Corppass authentication is enforced at **either** login level or transaction level (same pattern as Part A):

- **Option 1 — Corppass at login level:** The application requires Corppass authentication as the login mechanism for corporate users.
  - Check for Spring Security OAuth2/OIDC client registration pointing to the Corppass OIDC endpoint.
  - Verify the login flow redirects to Corppass for authentication (no local credential login for corporate users).
  - Verify the ID token from Corppass is validated (signature, `iss`, `aud` claims).

- **Option 2 — Corppass step-up at transaction level:** The application allows initial login via another mechanism but requires a Corppass step-up before executing high-risk transactions.
  - Check for a Spring Security filter, `@PreAuthorize` expression, or interceptor that validates a short-lived step-up token issued by Corppass on high-risk endpoints.
  - Verify there is a dedicated step-up OIDC flow — a separate authorization request to Corppass (with `prompt=login` or equivalent) that re-authenticates the corporate user at the point of the transaction.
  - Verify the step-up token is scoped to the specific transaction or has a short TTL (≤ 5 minutes) and is single-use.

- In both options, verify the identity verification does not fall back to a locally enrolled second factor (TOTP, OTP, PIN) — the authentication must go through Corppass.

**Frontend automated checks (React TypeScript):**

- **Option 1 (login level):** Verify the login page for corporate users redirects to Corppass. There should be no local username/password form for corporate users.
- **Option 2 (transaction level):** Verify that UI actions triggering high-risk transactions redirect or present a Corppass re-authentication flow before dispatching the API call. The high-risk API call must only be made after a successful Corppass step-up response.

---

**FAIL conditions:**
- Part A: External public users exist and high-risk transactions exist, but no Singpass authentication is found at either login or transaction level.
- Part B: Corporate users exist and high-risk transactions exist, but no Corppass authentication is found at either login or transaction level.

**Manual review flags:**
- "Verify the list of high-risk transactions is complete and agreed with the business owner. Automation can only detect annotated or pattern-matched endpoints."
- "If step-up is at login level, verify the session lifetime is appropriately short for the risk level of the transactions."
- "If step-up is at transaction level, verify the step-up MFA threshold (e.g., transaction value above which step-up is required) is correctly configured and not bypassable."


---

### ac-8: Automated Account Lifecycle Management

**Applicability:** Applies to **internal user accounts only** — internal users must be provisioned and deprovisioned via an automated mechanism (push provisioning or SSO JIT). External/public user accounts are out of scope for this control. If the application has no internal users, mark this control as **N/A**.


#### Pattern A — Standards-Based Push Provisioning (SCIM or equivalent)

This pattern covers any protocol where an external IdP or directory **pushes** lifecycle events to the application via a dedicated provisioning endpoint. SCIM 2.0 is the preferred standard, but the checks below also accept equivalent protocols.

**Equivalent protocols accepted:**

| Protocol / API | Typical indicator |
|---|---|
| SCIM 2.0 (RFC 7644) | `/scim/v2/Users` routes, SCIM SDK library |
| SCIM 1.1 | `/scim/v1/Users` routes |
| SPML (Service Provisioning Markup Language) | OASIS SPML library or `/spml` endpoint |
| Okta Provisioning API | Okta SCIM-compatible endpoint or Okta SDK (`com.okta`) with provisioning config |
| Azure AD / WOG AAD Graph API push | `com.microsoft.graph` with `User` create/update/delete calls, or Azure AD SCIM connector |
| Google Workspace Admin SDK provisioning | `google-api-services-admin-directory` with user create/update/delete |
| Custom provisioning webhook | A dedicated REST endpoint (e.g., `/api/provisioning/users`) that receives user lifecycle events from an IdP or HR system |

**Backend automated checks:**

- Check `pom.xml`/`build.gradle` for a provisioning library (any of the above).
- Verify provisioning endpoints are exposed:
  - For SCIM: route mappings matching `/scim/v2/Users`, `/scim/v2/Groups`, or a configurable base path (e.g., `spring.scim.base-path` property).
  - For non-SCIM protocols: controller classes or route mappings with names/paths related to provisioning, user sync, or directory events.
  - Verify create, update, and deactivate operations are all handled — not just creation.
- Verify deactivation semantics: the `active: false` payload (SCIM), a `DELETE` call, or an equivalent deactivation event sets the application's `enabled`/`active` field to `false` AND invalidates active sessions/tokens — cross-reference with ac-3.
- Check for attribute mapping configuration (e.g., mapping the IdP's user identifier claim to the application's user identifier, email, and role fields).


#### Pattern B — SSO JIT (Just-In-Time) Provisioning

**Backend automated checks:**

- Check for JIT user creation on first SSO login:
  - OIDC/OAuth2: look for a custom `OAuth2UserService`, `OidcUserService`, or `AuthenticationSuccessHandler` that creates or updates a local user record upon first login.
    - Verify the service calls a user repository `save()` / `findOrCreate()` pattern using a claim from the token (e.g., `sub`, `email`, `preferred_username`) as the unique identifier.
  - SAML2: look for a custom `Saml2AuthenticationTokenConverter`, `OpenSaml4AuthenticationProvider` with a custom authorities mapper, or `ApplicationListener<AuthenticationSuccessEvent>` that provisions the user from SAML attributes.
- Verify JIT provisioning maps IdP attributes to local user fields:
  - Check for claim/attribute extraction: `email`, `name`, `groups`/`roles` from the IdP token/assertion mapped to the application's User entity fields.
  - Verify role/group mapping is present — JIT-created accounts must not default to maximum privilege.
- Verify JIT deprovisioning is handled:
  - JIT provisioning alone does not guarantee deprovisioning. Check whether the application relies on Pattern A for deprovisioning, OR whether there is a token-validation hook that disables accounts if the IdP reports them as inactive.

---

**FAIL conditions:**

- No automated provisioning mechanism detected (no Pattern A and no Pattern B) and accounts are managed entirely by manual admin action.

**Manual review flags:**

- "Verify the IdP or HR system is configured to push deprovisioning events in a timely manner according to policy."
- "Verify provisioning integration credentials (SCIM Bearer token, webhook HMAC secret) are rotated per key rotation policy."

---

### ac-12: Single Sign-On (SSO) for Internal Services and Accounts

**Applicability:** Applies only to internal services and accounts.

---

**Backend automated checks:**

- Verify SSO is configured as the authentication mechanism for internal users:
  - Check `application.yml`/`application.properties` for an OIDC or SAML2 client registration pointing to the organisation's IdP. For example: 
    - OIDC: `spring.security.oauth2.client.registration.*` with an `issuer-uri` or `authorization-uri` matching the corporate IdP.
    - SAML2: `spring.security.saml2.relyingparty.registration.*` with an `asserting-party.metadata-uri` or `asserting-party.single-sign-on.url` matching the corporate IdP.
  - Flag if no SSO configuration is present for any user path that handles internal accounts.
- Verify internal user roles/groups are sourced from the IdP:
  - Check for a `GrantedAuthoritiesMapper`, `OidcUserService`, or SAML2 authorities extractor that maps IdP group claims or SAML attributes to Spring Security roles.
  - Flag if internal users default to a hardcoded role (e.g., `ROLE_USER`) with no mapping from IdP claims — this bypasses the IdP's group-based access control.
- Verify SSO session/token expiry is enforced:
  - Check that the application does not issue its own long-lived sessions that outlast the IdP session. Verify session timeout aligns with SSO token expiry.

**Frontend automated checks (React TypeScript):**

- Verify no local login form exists for internal users:
  - The primary login entry point for internal users must initiate an SSO redirect and not accept credentials locally through username/password input fields. 
- Verify the SSO redirect is initiated correctly:
  - Check that the login action calls a backend SSO initiation endpoint (e.g., `/oauth2/authorization/{registrationId}`, `/saml2/authenticate/{registrationId}`) or redirects directly to the IdP authorization URL.
- Verify post-login redirect handling:
  - Check that the callback route (e.g., `/login/oauth2/code/*`, `/login/saml2/sso/*`) is handled and the user is redirected to the originally requested page (stored pre-auth destination).
- Verify SSO session expiry is surfaced to the user:
  - Check that the app handles a 401 response (expired SSO session) by redirecting to re-authenticate via SSO.

**FAIL conditions:**

- No SSO configuration found for the internal user authentication path.
- Frontend login page accepts username/password for internal users without an SSO redirect.

**Manual review flags:**

- "Verify the SSO registration points to the correct organisational IdP tenant."

---

### dp-3: Data in Transit Encryption

**Backend automated checks:**

- Verify TLS is enabled via configuration, through either mechanism:
  - `server.ssl.enabled=true` with a configured key-store (`server.ssl.key-store`)
  - An SSL Bundle (`spring.ssl.bundle`, Spring Boot 3.1+) referenced by `server.ssl.bundle`.
- Flag any explicitly configured deprecated protocol (`SSLv3`, `TLSv1.0`, `TLSv1.1`) in `enabled-protocols`
- Flag HSTS only if explicitly disabled, as it is on-by-default.
- Flag any hardcoded `http://` (non-TLS) URLs pointing to external or sensitive endpoints in config, REST clients (`RestTemplate`, `WebClient`, Feign) 
- Flag any code that disables certificate or hostname validation, such as a custom `X509TrustManager` that accepts all certs, `TrustAllStrategy`, a `HostnameVerifier` returning `true`, `NoopHostnameVerifier`, or `setDefaultHostnameVerifier(allHostsValid).

**Frontend automated checks (React TypeScript):**
Note: Frontend transport-security checks role is to avoid introducing insecure calls.

- Flag any `http://` (non-HTTPS) references in API clients, config, or as page resources (scripts, styles, media). Treat references to auth, payment, or PII endpoints as higher severity than static assets.
- Check that any `Content-Security-Policy` does not permit insecure (`http:`) sources and uses `upgrade-insecure-requests` where applicable.

**Manual review flags:**
- "Determine deployment topology: if the app sits behind a TLS-terminating proxy / load balancer / ingress, confirm TLS is enforced there and HTTP→HTTPS redirect occurs at that layer."
- "If the app is behind a TLS-terminating proxy, verify `server.forward-headers-strategy` is set (`framework` or `native`) so the app correctly perceives the original HTTPS protocol, otherwise secure-cookie flags and generated URLs may be incorrect."
- "Confirm HTTPS is enforced somewhere - redirect either at the proxy or in the app, and that the app correctly perceives request protocol. Which mechanism applies depends on where TLS terminates."
- "Verify TLS certificates are valid, CA-signed (not self-signed) in production, and rotated before expiry."

---

### dp-8: Data Classification Disclosure

**Applicability:** Applies only to internal applications serving public officers. Not applicable to public-facing applications.

---

**Frontend automated checks (React TypeScript):**

- Identify all input field components where the user enters or uploads data.
    - Free-text entry: e.g. native `<input type="text/number/email/search">`, `<textarea>`, shadcn `<Input>` and `<Textarea>`.
    - File uploads: `<input type="file">` (including wrapped in a shadcn `<FormField>`), custom dropzone/uploader components (names containing `Upload`, `Uploader`, `Dropzone`, `FileInput`).
    - Audio / recording: components using `MediaRecorder` or `getUserMedia`, or with names containing `Audio`, `Record`, `Voice`.
    - Rich-text editors: e.g. `react-quill`, `@tiptap/react`, `draft-js`, `slate`, `lexical`, `tinymce`, `ckeditor`. Check their wrapper components.
- For each identified input field, verify a classification label is rendered in close proximity:
  - Search is case-insensitive. Labels may appear in any casing (e.g., `Official Open`, `official_open`, `OFFICIAL OPEN`, `Restricted`). Match against the canonical values regardless of case or separator (space, underscore, hyphen).
  - Search the same component or its immediate parent for text or a component containing a security classification value: `official open`, `official closed`, `restricted`, `confidential`, `gems secret` (match case-insensitively).
  - Search the same component or its immediate parent for a sensitivity classification value: `non sensitive`, `sensitive normal`, `sensitive high` (match case-insensitively).
  - Acceptable patterns: a `<label>`, `<span>`, `<p>`, tooltip trigger, or a dedicated `ClassificationBadge`/`ClassificationLabel` component rendered alongside or immediately above/below the input. Any other equivalent patterns can be accepted too.
  - **A one-time popup dialog or dismissible banner does NOT satisfy this control.** Classification labels must be persistently visible near each input field while the user is entering data — not shown once and then gone.
- Check for a centralised classification label component or utility:
  - Search for components or constants with names containing `Classification`, `DataClass`, `Sensitivity`, `SecurityLabel`, `ClassificationBadge`.
  - If a shared component exists, verify it is used consistently across all input fields.
- **FAIL if** any input fields lack a classification label. List all non-compliant fields in the findings with file:line references. Do not PASS based on a single warning dialog that covers the whole application — the requirement is per-field labelling.

**Backend automated checks:**
No automated backend checks are required for this control.

**Manual review flags:**

- "Verify the classification level displayed for each field reflects the actual highest classification of data permitted. Automation can only detect presence of a label, not correctness of the level assigned."
- "Verify that fields accepting `RESTRICTED`, `CONFIDENTIAL`, or `GEMS SECRET` data have corresponding backend enforcement (e.g., access control, audit logging) on par with the classification level."
- "Verify that fields accepting `SENSITIVE HIGH` data have appropriate handling controls (e.g., masking on display, restricted export, enhanced audit logging)."

---

### pm-6: System Documentation

---

**Backend automated checks:**

- Check for architecture documentation artifacts in the repository:
  - Look for files such as `ARCHITECTURE.md` or a `docs/architecture/` tree describing system and application structure.
  - A network architecture/topology document or diagram (e.g. `docs/network/`, `*.drawio`, `*.puml`, `*.mmd`, C4/Structurizr files).
  - An Architecture Decision Record directory (`/adr`, `/docs/adr`).
- Check for inventory artifacts:
  - **Software inventory:** a dependency manifest exists and is the source of truth (`pom.xml`/`build.gradle`, `package.json`, lockfiles).
  - **Hardware/infrastructure inventory:** infrastructure-as-code (Terraform, CloudFormation, Helm, k8s manifests) or a documented inventory of hosts/services.
- Check for a machine-readable API specification (`openapi.yaml`/`json`, `swagger.json`) documenting the service's external surface for API-exposing services.
- Check documented configuration files are present (`application.yml`, env templates).

**Agent-assessed checks:**

- **Architecture accuracy:** verify that significant components in code are represented in the docs.
- **Network architecture accuracy:** verify documented network topology (segments, ingress/egress, exposed ports, trust boundaries) corresponds to actual infrastructure-as-code and service configuration.
- **Software inventory completeness:** verify the documented/declared dependencies match what is actually imported and built.
- **Hardware/infra inventory completeness:** verify documented hosts/services/resources match those declared in infrastructure-as-code.
- **Data flow accuracy:** verify documented data flows match actual code. External calls, message queues, database connections, and third-party integrations found in code should each appear in the data-flow documentation.
- **Configuration accuracy:** verify documented configuration parameters match those the application actually reads.

**Frontend automated checks (React TypeScript):**

- Check for frontend documentation: a `README` covering app structure, documented components (`.stories.tsx` / Storybook), and a documented state/data-flow model.
- Agent-assessed: verify the frontend's documented API contract and data flows match the endpoints actually called in code and the shared OpenAPI spec.

**FAIL Condition**
- Lack of evidence of detailed, up-to-date documentation of system information and architecture.
- A basic README with only setup/build instructions is NOT sufficient. The documentation must describe system architecture, data flows, and deployment topology — not just how to run `npm install` or `mvn spring-boot:run`.
- A runtime-only OpenAPI endpoint (e.g., `/v3/api-docs`) without a committed spec file in the repository does not count as documented API specification — the spec must be version-controlled and reviewable without running the application.
- Framework/starter library documentation does NOT count as the application's own system documentation.

---

### st-3: Public Vulnerability Disclosure Programme

---

**Applicability:** Applies only to **public-facing** applications. Internal applications (e.g., intranet tools, internal admin portals, apps serving only internal users/officers) are **not** required to have a public vulnerability disclosure channel. If the application is internal-only, mark as **N/A** and skip all checks below.

To determine applicability, check the application's purpose and target audience. Look for indicators of an internal app:
- Login restricted to internal users (SSO with corporate IdP, no public registration).
- URL patterns suggesting intranet deployment (e.g., `.internal`, `.intranet`, non-public domains).
- Application description, README, or naming indicating internal use (e.g., "admin portal", "internal dashboard", "staff app", "EMR", "case management").
- No public-facing routes or pages accessible without authentication.

---

**Backend automated checks:**

- Check for a `security.txt` file served at `/.well-known/security.txt` (root `/security.txt` accepted as fallback). In a Spring Boot app this typically lives under `src/main/resources/static/.well-known/security.txt`.
  - If present, verify it contains the two fields required by RFC 9116:
    - At least one `Contact` field (e.g. `mailto:`, `https://`, or `tel:`).
    - A valid `Expires` field with a future date (ISO 8601).
  - `Policy`, `Encryption`, and `Canonical` fields are recommended but optional.
- If no `security.txt` file is found, fall through to the frontend footer-link check (the accepted alternative).

**Frontend automated checks (React TypeScript):**

- Check for the presence of this exact code snippet: `<a herf="https://tech.gov.sg/report_vulnerability" target="_blank">Report Vulnerability</a>` on all pages, such as in the footer.

**Pass Conditions:**

- Valid `security.txt` with `Contact` + future `Expires` OR
- Exact code snippet `<a herf="https://tech.gov.sg/report_vulnerability" target="_blank">Report Vulnerability</a>` displayed on all pages

**Manual review flags:**

- "Confirm the reporting channel (the `Contact` target or footer link destination) is monitored by the security team."

---

## Report Format

Output the final report under artifacts/ using the filename `im8-compliance-report-YYYYMMDD-HHmm.md`:

```markdown
# IM8 Application Controls Compliance Report

**Target:** [codebase path]
**Date:** [date]
**Stack:** React 19 (TypeScript) + Spring Boot 3.4 / Spring Security 6.4

## Summary

| Control | Status | Severity | Finding |
|---------|--------|----------|---------|
| as-1    | PASS/FAIL/WARN | Critical/High/Medium/Low/— | Brief finding |
| ...     | ...    | ...      | ...     |

## Detailed Findings

### [control-id]: [control-name]

**Status:** PASS/FAIL/WARN/N/A

**Severity:** Critical/High/Medium/Low/—

**Checks performed:**
- [what was checked]

**Evidence:**
- [file:line — what was found]

**Issues:**
- [any failures or warnings]

**Manual review required:**
- [items that need human verification]

---

## Manual Review Checklist

Items that cannot be fully automated and require human verification:
- [ ] [item]

### Files Reviewed
Backend
- [file1] 
- [file2]
- [file3]
Frontend
- [file1] 
- [file2]
- [file3]
```
