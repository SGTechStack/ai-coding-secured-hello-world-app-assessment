# Threat Model — Secured Hello World App

- **Methodology:** STRIDE (per `.kiro/skills/owasp-threat-modeling/SKILL.md`)
- **Scope:** Full codebase — `backend/` (Spring Boot 3.3.4 / Spring Security 6, Java 21) and `frontend/` (React 19 + TypeScript + Vite)
- **Analysis type:** Static, read-only source review. No dynamic scanning, no dependency CVE scan performed in this pass.
- **Date:** 2026-09-28
- **Author:** Automated threat-modeling session (`threat_modeling` orchestration)

> Correction to task framing: `backend/pom.xml` declares `spring-boot-starter-parent` version `3.3.4`, not `3.4` as stated in the task brief. `PRODUCT.md` also states "Spring Boot 3.3". Findings below reference the actual installed version.

## 1. Scope & Assets

**Assets protected:**
- User credentials (passwords — never stored in plaintext; `User.passwordHash`, BCrypt)
- Session state (server-side `HttpSession`, tracked via Spring `SessionRegistry`)
- Password-reset tokens (single-use, hashed at rest)
- User PII (username, email) in the `users` table
- Admin capability (role escalation, account enable/disable/delete)
- Application availability (login endpoint, IP-throttle state)

**External dependencies:**
- H2 in-memory database (dev profile) — no external network dependency
- No real SMTP; `LoggingEmailService` stub logs the reset link (explicit PRD out-of-scope item)

**Compliance requirements:** None formally declared in PRD beyond "production-grade security baseline" reference implementation. No GDPR/PDPA scope stated — LINDDUN supplement not applied (no stated privacy-regulation requirement), though PII handling is noted under Information Disclosure below.

**Trust boundaries:**
1. Internet/browser ↔ Frontend (React SPA, own origin, e.g. `localhost:3000`)
2. Frontend origin ↔ Backend API origin (e.g. `localhost:8080`) — cross-origin, CORS + credentialed cookies
3. Backend process ↔ Data store (H2, same process/trust zone in dev)
4. Unauthenticated zone ↔ Authenticated zone (session boundary)
5. Authenticated USER zone ↔ Authenticated ADMIN zone (`/api/admin/**`)

## 2. Level 0 Data Flow Diagram (textual)

```
[Browser/User] --HTTPS(prod)/HTTP(dev)--> [React SPA :3000]
[React SPA] --fetch(credentials:include, XSRF header)--> [Spring Boot API :8080]
[Spring Boot API] --JDBC--> [H2 Database: users, password_reset_tokens]
[Spring Boot API] --log line only (stub)--> ["Email" sink via LoggingEmailService]
[Admin Actor] --session-authenticated, ROLE_ADMIN--> [/api/admin/** endpoints]
```

Processes: `LoginController/LoginService`, `RegistrationController/RegistrationService`, `PasswordResetController/PasswordResetService`, `AdminUserController/AdminUserService`, `LogoutController`, `CsrfTokenController`, `AdminBootstrapRunner`.

Data stores: `users` table, `password_reset_tokens` table, in-memory `SessionRegistry`, in-memory `LoginAttemptService.ipAttempts` map.

External entities: Visitor, User, Admin (all via browser).

## 3. STRIDE Threat Analysis

Each row: Threat / Element / Severity / Status / Evidence.

### Spoofing (S)

| # | Threat | Element | Severity | Status | Evidence |
|---|--------|---------|----------|--------|----------|
| S1 | Credential stuffing / brute force against `/api/login` | LoginService (process) | Medium | **Mitigated** | Per-account lockout after `max-attempts: 5` for 15 min (`application.yml:22-24`) enforced in `LoginAttemptService.recordFailedAttempt` (`LoginAttemptService.java:47-54`) and checked in `LoginService.login` (`LoginService.java:71-74`); independent per-IP throttle at 20/15min (`LoginAttemptService.java:75-92`). |
| S2 | Username enumeration via differing error responses | LoginController / LoginService (process) | Medium | **Mitigated** | Unknown-username and bad-password paths both throw the same `AuthenticationFailedException` with a single `GENERIC_MESSAGE` (`LoginService.java:64-79`, `ApiExceptionHandler.java:24-28`). Password-reset request also returns an identical response regardless of email existence (`PasswordResetController.java:19-22`, `PasswordResetService.java:52-68`). |
| S3 | Session fixation (attacker pre-sets a session ID before login) | LoginService (process) | Low | **Mitigated** | `ChangeSessionIdAuthenticationStrategy` is invoked on every successful authentication (`LoginService.java:39,85-90`), rotating the session ID post-login. |
| S4 | Session hijacking via stolen session cookie (XSS or network sniffing) | Data Flow (Browser↔API) | Medium | **Mitigated (partial — see I3)** | Cookie is `http-only: true` (blocks JS/XSS theft) and `secure: true` by default in `application.yml:12-15`; dev profile explicitly relaxes `secure: false` only for local HTTP (`application-dev.yml:16-19`, documented rationale in comment). Residual risk: `same-site: lax` (not `strict`) — see T4. |
| S5 | Admin bootstrap credential spoofing / predictable default admin password | AdminBootstrapRunner (process) | Medium | **Open** | Default seeded admin credentials `admin` / `ChangeMe123456!` are checked into `application.yml:19-21` in version control. If this exact config reaches any non-dev/non-throwaway environment without the operator rotating it, it's a well-known default credential. No forced-rotation or "must change on first login" mechanism exists (`AdminBootstrapRunner.java` has no expiry/force-change flag). |
| S6 | Forged `X-Forwarded-For` header to bypass or misattribute IP throttling | ClientIpResolver (process) | Low | **Open (accepted for dev-only deployment)** | `ClientIpResolver.resolve` trusts the first `X-Forwarded-For` entry unconditionally (`ClientIpResolver.java:17-21`) with no validation that the request actually came through a trusted proxy. An attacker can spoof this header directly (no proxy in front) to (a) evade their own IP's throttle by rotating the header value, or (b) frame another IP by setting it to a victim's address, causing that IP to be throttled (DoS against a third party). PRD documents this as a "real deployment would sit behind a reverse proxy" assumption but the code has no proxy-count / trusted-hop configuration (e.g. Spring's `ForwardedHeaderFilter` allow-list) to enforce that assumption. |

### Tampering (T)

| # | Threat | Element | Severity | Status | Evidence |
|---|--------|---------|----------|--------|----------|
| T1 | CSRF on state-changing endpoints (login, register, password reset, admin actions) | Data Flow / Process | High if open | **Mitigated** | `CookieCsrfTokenRepository` + `CsrfTokenRequestAttributeHandler` wired in `SecurityConfig.java:60-63`; only `/h2-console/**` is exempted. Frontend echoes the `XSRF-TOKEN` cookie value back as `X-XSRF-TOKEN` header on all mutating requests (`frontend/src/api.ts:33-38`). Verified by `SecurityHardeningIntegrationTest.mutatingEndpointWithoutCsrfTokenIsRejected` (test file present). |
| T2 | SQL injection against user/token lookups | UserRepository / PasswordResetTokenRepository (data store interface) | Low | **Mitigated (N/A by construction)** | All persistence uses Spring Data JPA derived queries (`UserRepository.java`, `PasswordResetTokenRepository.java`) — no raw/native/concatenated SQL anywhere in the codebase (confirmed via source read of all repository interfaces). |
| T3 | Tampering with password-reset token to bypass expiry/single-use check | PasswordResetToken (data store) | Low | **Mitigated** | Token is compared via `BCrypt.checkpw` against a stored hash (`PasswordResetService.java:100-106`); plaintext token is never persisted (`PasswordResetToken.java` comment, only `tokenHash` column). `isUsed()`/`isExpired()` checked before consuming (`PasswordResetService.java:78-84`). |
| T4 | Cross-site request tampering via top-level navigation (CSRF variant not fully covered by SameSite) | Session cookie (data flow) | Low | **Open (defense-in-depth gap)** | `same-site: lax` (`application.yml:14`) allows the session cookie to be sent on top-level cross-site GET navigations. Combined with CSRF-token protection on mutating routes this is low residual risk, but `lax` (vs `strict`) is a deliberate trade-off not documented as accepted risk anywhere in code comments or PRD. |
| T5 | Denial-of-service amplification via `PasswordResetService.confirmReset` full-table scan | PasswordResetService (process) / password_reset_tokens (data store) | Low | **Open** | `confirmReset` calls `tokenRepository.findAll().stream().filter(candidate -> matchesToken(...))` (`PasswordResetService.java:74-77`) — a linear scan performing a BCrypt comparison (intentionally slow, ~10 rounds) against **every** token row for every reset-confirm request. As the `password_reset_tokens` table grows, this endpoint's cost grows linearly and is unauthenticated (`permitAll` on `/api/password-reset/**`), making it an easy unauthenticated CPU-exhaustion vector. Low severity today given demo/reference-app scale, but flagged as a design smell worth an indexed-lookup fix (e.g., derive a lookup key from a non-secret portion of the token, or store a fast HMAC alongside the BCrypt hash for indexed lookup) before any production use. |

### Repudiation (R)

| # | Threat | Element | Severity | Status | Evidence |
|---|--------|---------|----------|--------|----------|
| R1 | User denies performing a login/logout/admin action | LoginService / LogoutController / AdminUserService (process) | Low | **Mitigated** | All security-relevant actions are logged with actor identity: login success/failure (`LoginService.java:66,73,78`), logout (`LogoutController.java:22`), admin actions with `actor=`/`target=` fields (`AdminUserService.java:37-38,48-49,58`), registration (`RegistrationService.java:31`), password reset request/completion (`PasswordResetService.java:65,90`). |
| R2 | No tamper-evidence / centralized audit trail for logs | Logging sink (data store, implicit) | Low | **Open (accepted — reference app)** | Logs are local SLF4J output only; no append-only audit store, no log shipping/SIEM integration. Acceptable for a reference/demo app per `PRODUCT.md` "coding-assessment reference/demo" framing, but would be a gap for a real production audit requirement. Not scored higher because PRD explicitly scopes this as a demo, not a production deployment. |

### Information Disclosure (I)

| # | Threat | Element | Severity | Status | Evidence |
|---|--------|---------|----------|--------|----------|
| I1 | Password hash leakage via admin user-list API | AdminUserController (process) | Medium if open | **Mitigated** | `AdminUserView.from()` explicitly excludes `passwordHash` (`AdminUserView.java:14-24`; record has no such field), confirmed by test `listUsersReturnsExpectedFieldsAndOmitsPasswordHash`. |
| I2 | Password/PII echoed back in validation error responses | ApiExceptionHandler (process) | Medium if open | **Mitigated** | `ApiExceptionHandler.handleValidation` returns only field name → message pairs, never the submitted value (`ApiExceptionHandler.java:52-60`); class-level Javadoc states this is deliberate. |
| I3 | CSRF cookie (`XSRF-TOKEN`) readable by JavaScript | Data Flow (Browser↔API) | Low | **Mitigated (by design — required for double-submit pattern)** | `CookieCsrfTokenRepository.withHttpOnlyFalse()` (`SecurityConfig.java:61`) intentionally makes the CSRF cookie JS-readable so the SPA can echo it as a header (`frontend/src/api.ts:15-18`) — this is the standard/expected double-submit-cookie CSRF pattern, not a vulnerability, since the CSRF token is not a secret credential (session cookie remains HttpOnly). |
| I4 | Verbose Spring Boot default error pages / stack traces leaking internals | Web layer (process) | Low | **Not verified — needs runtime check** | No explicit `server.error.include-stacktrace`/`include-message` configuration found in `application.yml` or `application-dev.yml`. Spring Boot 3.x defaults (`include-message: never`, `include-stacktrace: never`) are safe out of the box, but this was not exercised at runtime in this static review. Recommend confirming with an actual error-triggering request before production sign-off. |
| I5 | H2 web console exposed and permitAll'd | SecurityConfig (process) / H2 console (data store admin UI) | Medium (dev), N/A (prod) | **Mitigated for prod / Open for dev if mis-deployed** | `h2Console` matcher is `permitAll` unconditionally in `SecurityConfig.java:53,75` (not profile-gated in the security filter chain itself), and `h2.console.enabled: true` is set only in `application-dev.yml:12-14` (not in base `application.yml`), so the console is absent unless the `dev` profile is active. Risk is contingent entirely on operational discipline (never activating `dev` profile in a reachable environment) rather than defense-in-depth in code — the security filter chain itself does not restrict `/h2-console/**` to the dev profile, it just happens to be a 404 when the console isn't registered. Recommend making the `permitAll`/CSRF-ignore for `/h2-console/**` itself conditional on `@Profile("dev")` for defense-in-depth. |
| I6 | Admin bootstrap password in cleartext in version-controlled config | AdminBootstrapProperties (data store — config) | Medium | **Open** | Same evidence as S5: `app.admin.password: ChangeMe123456!` in `application.yml:20-21`, committed to source control. Even though it's clearly a placeholder ("ChangeMe"), it's a real credential the app will actually seed and accept for authentication if unrotated. |

### Denial of Service (D)

| # | Threat | Element | Severity | Status | Evidence |
|---|--------|---------|----------|--------|----------|
| D1 | Unbounded growth of in-memory IP-throttle map | LoginAttemptService (process) | Low | **Open** | `ipAttempts` is a `ConcurrentHashMap<String, IpAttemptWindow>` with entries added on every failed login from a new IP (`LoginAttemptService.java:29,68`) and **never evicted**, even after the throttle window expires (the code resets counters in place but never removes stale keys, `LoginAttemptService.java:70-73,85-90`). An attacker rotating source IPs (or spoofing `X-Forwarded-For`, see S6) can grow this map unboundedly, causing heap exhaustion over time. Explicitly documented in code as "sufficient for a single-instance dev-profile deployment" (`LoginAttemptService.java` class Javadoc) — acceptable for the stated reference-app scope but a real gap if ever deployed beyond dev. |
| D2 | Password-reset-confirm CPU exhaustion | see T5 | Low | **Open** | Duplicate of T5 — cross-referenced here under the D category since the effect is availability degradation, not just tampering. |
| D3 | No global request-rate limiting (e.g. registration spam, password-reset-request spam) | RegistrationController / PasswordResetController (process) | Low | **Open (accepted — out of scope per PRD)** | No rate limiting exists on `/api/register` or `/api/password-reset/request` beyond the login-specific IP throttle (which is scoped to `LoginService` only, not reused by these controllers). Registration and reset-request are both `permitAll`. Acceptable for a demo but a real gap for production abuse resistance (e.g., mass account creation, mass reset-email/log spam). |
| D4 | `maximumSessions(-1)` = unlimited concurrent sessions per user | SecurityConfig (process) | Low | **N/A (by design)** | `SecurityConfig.java:68-69` explicitly allows unlimited concurrent sessions per principal. This is a deliberate product choice (no stated single-session-per-user requirement in PRD), not a defect — noted for completeness, not scored as a threat. |

### Elevation of Privilege (E)

| # | Threat | Element | Severity | Status | Evidence |
|---|--------|---------|----------|--------|----------|
| E1 | Non-admin user calling `/api/admin/**` directly | AdminUserController (process) | High if open | **Mitigated** | `SecurityConfig.java:74` — `.requestMatchers("/api/admin/**").hasRole("ADMIN")`. Verified by test `nonAdminReceives403OnAdminEndpoints`. |
| E2 | Admin self-lockout/self-demotion/self-deletion abuse (accidental or malicious, e.g. griefing via shared admin credentials) | AdminUserService (process) | Medium if open | **Mitigated** | `requireNotSelf` guard rejects self-targeted disable/role-change/delete (`AdminUserService.java:64-68`, throwing `SelfActionForbiddenException`), verified by 3 dedicated integration tests (`adminCannotDeleteTheirOwnAccount`, `adminCannotChangeTheirOwnRole`, `adminCannotDisableTheirOwnAccount`). |
| E3 | Privilege escalation via mass-assignment / arbitrary role value in role-change request | AdminUserController (process) | Medium | **Mitigated** | `UpdateRoleRequest.getRole()` is passed through `Role.valueOf(request.getRole())` (`AdminUserController.java:44`), which throws `IllegalArgumentException` for any value outside the `Role` enum (`USER`/`ADMIN`) rather than silently accepting an arbitrary string — no injection of a non-enum role is possible. Note: this throws an unhandled `IllegalArgumentException` rather than a mapped 400 via `ApiExceptionHandler` — a minor robustness gap (likely surfaces as a 500), not a privilege-escalation path. |
| E4 | Locked-out account bypassing lockout via Spring Security's own `UserDetails.isAccountNonLocked()` | AppUserDetails (process) | Low | **Mitigated (by design, verified)** | `AppUserDetails.isAccountNonLocked()` always returns `true` (`AppUserDetails.java:47-50`) — lockout is deliberately *not* delegated to Spring Security's built-in mechanism (which is permanent/binary) and is instead enforced explicitly and time-bounded in `LoginService.login` via `user.isLocked(Instant.now())` (`LoginService.java:71-74`). This is a documented, deliberate design choice (see class Javadoc) and is exercised by `loginRejectedWhileAccountLocked` test — not an oversight. |
| E5 | Registration self-assigning `ADMIN` role | RegistrationRequest / RegistrationService (process) | High if open | **Mitigated (N/A by construction)** | `RegistrationRequest` has no `role` field at all (`RegistrationRequest.java:13-25`) and `RegistrationService.register` hardcodes new users via the `User(username, email, passwordHash)` constructor, which defaults `role = Role.USER` (`User.java:38`) with no service-layer path to set it otherwise. Role can only change via the admin-only `PATCH /api/admin/users/{id}/role` endpoint. |
| E6 | Frontend-only admin-nav gating creating a false sense of access control | AdminUsersPage / HelloPage (frontend process) | Low | **Mitigated (server-enforced; frontend gating is UX-only)** | `HelloPage` always renders the "Manage users" nav button for any authenticated user regardless of role (`frontend/src/HelloPage.tsx` — no role check before rendering `onNavigateToAdmin`), and `AdminUsersPage` only discovers a 403 after calling `api.adminListUsers()` (`AdminUsersPage.tsx:14-23`). This is not a real vulnerability since the backend independently enforces `hasRole("ADMIN")` (E1), but it is a UX/defense-in-depth gap: a non-admin can navigate to the admin screen and see a naked "You do not have permission" state, and the frontend performs no proactive role check (e.g., no `/api/me`-style role endpoint consulted before rendering the nav link). Cosmetic/UX-severity, not a security boundary failure. |

## 4. Summary Table

| STRIDE Category | Open (Medium/High) | Open (Low) | Mitigated | N/A |
|---|---|---|---|---|
| Spoofing | 1 (S5) | 1 (S6) | 4 | 0 |
| Tampering | 0 | 2 (T4, T5) | 3 | 0 |
| Repudiation | 0 | 1 (R2) | 1 | 0 |
| Information Disclosure | 1 (I6) | 1 (I4 unverified), 1 (I5 conditional) | 3 | 0 |
| Denial of Service | 0 | 3 (D1, D2/T5 dup, D3) | 0 | 1 (D4) |
| Elevation of Privilege | 0 | 1 (E6, UX-only) | 5 | 1 (E5) |

**Open Medium-severity threats:** S5 / I6 (default admin credentials committed to `application.yml`, single root cause, counted once for gating purposes).

**Open High/Critical-severity threats:** None identified.

## 5. Recommended Mitigations for Open Items

| Threat | Recommended control | Owner | Priority |
|---|---|---|---|
| S5 / I6 — default admin credential in VCS | Move `app.admin.username`/`app.admin.password` to an environment-variable-sourced secret (no default in `application.yml`), or force a password change/rotation flag on first admin login. Fail startup if the property is unset in non-dev profiles. | Backend | Before any non-dev deployment |
| S6 — spoofable `X-Forwarded-For` | Only trust `X-Forwarded-For` when the immediate peer is a configured, trusted reverse-proxy IP (e.g., use Spring's `ForwardedHeaderFilter` with an explicit trusted-proxy allow-list), else fall back to `getRemoteAddr()`. | Backend | Before production; low urgency for demo |
| T4 — `SameSite=Lax` | Evaluate `SameSite=Strict` for the session cookie given the SPA and API are both first-party-controlled origins; document the trade-off if `Lax` is kept intentionally (e.g., needed for a redirect-based flow). | Backend | Low |
| T5 / D2 — linear BCrypt scan on reset confirm | Add an indexed lookup column (e.g., HMAC-SHA256 of the token with a server secret, stored alongside the BCrypt hash) so `confirmReset` can do an indexed exact-match lookup before the BCrypt compare, eliminating the full-table scan. | Backend | Before production scale |
| D1 — unbounded IP-attempt map | Add periodic eviction of expired `IpAttemptWindow` entries (e.g., a scheduled sweep, or switch to a bounded cache with TTL such as Caffeine). | Backend | Before any multi-instance/production deployment |
| D3 — no rate limiting on register/reset-request | Apply the existing IP-throttle pattern (or a shared rate-limiter) to `/api/register` and `/api/password-reset/request`. | Backend | Before production |
| I4 — unverified error-page verbosity | Add explicit `server.error.include-stacktrace: never` / `include-message: never` (or `on_param` never) to `application.yml` to make the safe default explicit rather than implicit, and verify with a live error-triggering request. | Backend | Low — verify before sign-off |
| I5 — H2 console gating | Make the `/h2-console/**` `permitAll`/CSRF-ignore rule itself conditional on the `dev` profile (e.g., via a `@Profile`-gated bean or conditional matcher) rather than relying solely on `h2.console.enabled` to be unset elsewhere. | Backend | Low — defense-in-depth |
| E6 — frontend admin-nav UX gap | Optionally surface the authenticated user's role from `/api/hello` or a dedicated `/api/me` endpoint so the frontend can conditionally render the "Manage users" link only for admins. Cosmetic; no security fix required. | Frontend | Low |

## 6. Out-of-Scope / Not Evaluated in This Pass

- Dependency CVE scanning (no `mvn dependency-check` / `npm audit` run in this session — recommend running `dependency-vuln-scan` skill separately)
- Runtime/dynamic testing (no live requests made; all findings are static-source-based)
- Infrastructure/deployment security (containerization, TLS termination, network segmentation) — explicitly out of scope per `PRODUCT.md`
- MFA/2FA — explicitly out of scope per PRD
