# Threat Model — Hello World Auth App (React + Spring Boot)

| | |
| --- | --- |
| **System** | Secured username/password login application specified in [`prd/assessment-prd.md`](../../prd/assessment-prd.md) |
| **Model file** | [`hello-world-auth-app.json`](./hello-world-auth-app.json) — OWASP Threat Dragon v2 format |
| **Methodology** | STRIDE per element (4 diagrams) + LINDDUN privacy pass (1 diagram) |
| **Code reviewed** | Full `backend/` and `frontend/` source as committed, `pom.xml`, `package.json`, `application.yml`, all 6 backend test classes |
| **Date** | 2026-09-23 |
| **Owner** | Samuel Wong |
| **Status** | Partially remediated — TM-02 and TM-04 fixed and test-covered (see §12). Owners and target dates not yet assigned for the remainder. |

Open the model in [OWASP Threat Dragon](https://github.com/OWASP/threat-dragon/releases) (desktop) or a Docker instance, then use *Open existing threat model* and select the JSON file. To export a PDF report, use Threat Dragon's own *Report* view.

---

## 1. Scope

**Assets being protected**

1. Account credentials — BCrypt password hashes in `users.password_hash`.
2. Session cookies — the `SESSION` cookie is the sole bearer of authenticated identity.
3. Password-reset tokens — a valid token is equivalent to a password.
4. User email addresses — the only personal data the system collects beyond a username.
5. The `ADMIN` privilege itself — it confers the ability to disable, re-role and delete any account.

**Trust boundaries**

| # | Boundary | Crossing guarded by |
| --- | --- | --- |
| TB1 | Browser (untrusted) → backend, across origins, with credentials | CORS allow-list, cookie-based CSRF, `SameSite=Lax` |
| TB2 | Unauthenticated → authenticated | Form-login filter chain: IP throttle → CSRF → `DaoAuthenticationProvider` |
| TB3 | `USER` → `ADMIN` | A single URL matcher: `requestMatchers("/api/admin/**").hasRole("ADMIN")` |
| TB4 | Application → data stores and log sinks | Nothing: in-process H2 with a blank `sa` password, in-memory sessions, stdout logs |

**External dependencies:** none at runtime beyond H2 (dev profile). Email delivery is a stub that logs. No actuator, no springdoc/swagger, no external identity provider.

**Out of scope** (per the PRD): JWT implementation, MFA, real SMTP, containerisation/CI/CD, local HTTPS. Threats arising from those are not modelled; threats arising from the *stub* that replaces SMTP are in scope, and TM-02 is one of them.

**Compliance context:** the system stores email addresses tied to identified individuals, which is why a LINDDUN pass was added. No specific regime is declared in the PRD; the privacy findings are written against general PDPA/GDPR-style expectations and should be re-scoped once a regime is confirmed.

---

## 2. Results at a glance

61 threats enumerated across 5 diagrams and 100 diagram elements.

| Status | High | Medium | Low | Total |
| --- | --- | --- | --- | --- |
| **Open** | 4 | 20 | 14 | **38** |
| **Mitigated** | 16 | 5 | 1 | **22** |
| **Not applicable** | 1 | — | — | **1** |

The headline: this is a genuinely well-built security baseline, and the open findings are concentrated rather than scattered. The authentication core — password storage, session handling, CSRF, enumeration resistance, reset-token cryptography, admin self-action guards — is correct and largely test-covered.

The original model found three clusters of weakness. Two have since been closed (§12): **secret handling on the reset path** (the live token was written to the log) and **revocation consistency** (password reset killed sessions, disable and demote did not). What remains is **deployment posture**: no prod profile, no transport enforcement, and dev-profile rules that ship unconditionally.

All four remaining High findings (TM-01, TM-05, TM-06, TM-07) are deployment-configuration problems rather than code defects. That is worth saying plainly: the application logic is in good shape, but the configuration is not currently deployable outside dev, and the dev configuration is unsafe to expose.

---

## 3. Diagram inventory

| Diagram | Title | What it decomposes |
| --- | --- | --- |
| D0 | Level 0 — System context | Actors, SPA, API, H2 console, database, session store, log sink, email stub. Carries all cross-cutting and deployment-wide threats. |
| D1 | Level 1 — Login, lockout and session establishment | CSRF filter → IP throttle filter → auth provider → success/failure handlers, and the three state stores they touch. |
| D2 | Level 1 — Password reset | Controller → service → token store, session registry and the email stub. |
| D3 | Level 1 — Admin user management and bootstrap | URL authorization → controller → management service, plus `AdminBootstrapRunner`. |
| D4 | Privacy (LINDDUN) — personal data flows | Where username and email are collected, stored, read in bulk, and logged. |

---

## 4. Open findings — High

### TM-01 · Elevation of privilege · H2 console rules are not profile-gated

`SecurityConfig` unconditionally grants `permitAll` to `/h2-console/**`, exempts that path from CSRF, and weakens `frameOptions` to `sameOrigin` **for the whole application**. The inline comments argue this is safe because the servlet only exists when `spring.h2.console.enabled=true`. The servlet claim is true; the conclusion is not. The rules themselves ship in every profile, the H2 jar sits on the runtime classpath of every build, and the datasource credentials are `sa` with a blank password. Anyone who can reach the port on a dev or demo instance gets unauthenticated full SQL read/write — dump every password hash, insert themselves an `ADMIN` row, or drop the schema. The global frame-options relaxation also weakens clickjacking protection on the real API in exchange for nothing.

- **Evidence:** `config/SecurityConfig.java:104, 108-110, 130`; `application.yml:41-50`
- **Fix:** move these rules into a `@Profile("dev")` `SecurityFilterChain` bean; scope `frameOptions().sameOrigin()` to `/h2-console/**` only; set `spring.h2.console.settings.web-allow-others=false`; move the `h2` dependency to `<scope>test</scope>` once a real datasource exists.

> **TM-02 and TM-04 were the other two High findings in this section. Both are now fixed, test-covered, and verified failing-first — see §12.**

### TM-05 · Information disclosure · No transport security is enforced anywhere

The filter chain has no `requiresChannel()` and no HTTPS redirect; HSTS is emitted only by Spring Security's default when a request *already* arrived over HTTPS; the dev profile overrides the session cookie to `secure: false`. Local dev over HTTP is an accepted, documented PRD gap — but nothing in the code or config stops the same build from serving plaintext outside dev, and in that case the session cookie, submitted passwords and reset tokens all cross the network in the clear. There is also no `forward-headers-strategy`, so behind a TLS-terminating proxy the application cannot tell whether the original request was secure.

- **Evidence:** `config/SecurityConfig.java:108-110`; `application.yml:5-15, 52-56`
- **Fix:** outside dev, add `requiresChannel().anyRequest().requiresSecure()` and an explicit HSTS policy (long `max-age`, `includeSubDomains`); set `server.forward-headers-strategy=framework`; keep `secure: false` confined to the dev profile as it already is.

### TM-06 · Elevation of privilege · CORS allow-list is environment-driven with no validation

`allowedOrigins` comes from `APP_CORS_ALLOWED_ORIGINS`, `allowCredentials` is `true`, and `allowedHeaders` is `*`. The strict origin list is the load-bearing control here: set that variable to an attacker-reachable origin — or widen it during debugging and forget — and any page on that origin can make credentialed calls with the victim's `SESSION` cookie and read the responses, which also hands them the CSRF token echo path. Nothing validates the value at startup.

- **Evidence:** `config/CorsConfig.java:29, 37-42`
- **Fix:** validate origins at startup — reject `*`, reject empty, require `https://` outside dev — and fail fast rather than booting permissive. Add an integration test asserting a disallowed `Origin` is refused.

### TM-07 · Spoofing · The dev profile ships a fixed, publicly documented admin password

The dev profile defaults the bootstrap admin to `admin` / `password1234`. The same credentials appear in `README.md` and are rendered in the SPA's dev login form. This matters more than "it's only dev" suggests: the dev profile is the profile every backend test runs under, and it is the *only* profile with a working datasource, so it is the profile anyone actually starts the app with. Any dev or demo instance reachable beyond localhost is a trivial full-admin takeover.

Credit where due: the base profile binds `app.admin.username`/`password` with **no defaults**, so startup fails rather than seeding a guessable account. The control is right; the dev override undermines it.

- **Evidence:** `application.yml:58-61`; `README.md:82`; `frontend/src/LoginForm.tsx:45, 66`
- **Fix:** require `APP_ADMIN_USERNAME`/`APP_ADMIN_PASSWORD` even in dev (via `.env.example`, or generate a random password and log it once at startup), force a password change on first admin login, and bind the dev server to `127.0.0.1`.

---

## 5. Open findings — Medium

| ID | STRIDE | Finding | Evidence | Fix in one line |
| --- | --- | --- | --- | --- |
| TM-08 | D | Failed-attempt counter never decays, so five cheap requests lock any known username indefinitely (repeatable every 15 min). The PRD asks for failures "within a window"; there is no window. | `auth/LoginFailureHandler.java:84-95`; `LockoutPolicy.java:14-18`; PRD:52 | Add `last_failed_login_at` and expire the counter once the window elapses; consider exponential backoff instead of a hard lock. |
| TM-09 | D | IP throttle keys on `getRemoteAddr()` with no forwarded-header handling. Behind any proxy every client collapses to one key: per-client throttling vanishes and the 10th failure from anyone locks out everyone. | `auth/IpLoginThrottle.java:33, 40-70` | Set `server.forward-headers-strategy=framework`, resolve the client IP from a trusted proxy's `X-Forwarded-For` (rightmost untrusted hop), and list trusted proxies. |
| TM-10 | D | Throttle map has no TTL sweep or size cap; entries clear only on a successful login from the same address, so distributed failures grow the heap without bound. | `auth/IpLoginThrottle.java:27-33, 40-70` | Replace with a bounded expiring cache (Caffeine `expireAfterWrite` + `maximumSize`). |
| TM-11 | D | Rate limiting matches only `POST /api/auth/login`. Registration and both reset endpoints are unthrottled and each runs a BCrypt hash. | `auth/IpThrottleFilter.java:44-45` | Apply a shared rate-limit filter to every unauthenticated write endpoint. |
| TM-12 | D | No request-size, multipart or Tomcat thread/connection limits. Boot caps no JSON body by default, so an unauthenticated caller can force buffering and parsing of an arbitrarily large payload. | `application.yml` (absent) | Set `server.tomcat.max-http-form-post-size`/`max-swallow-size`, cap threads, and add `@Size` to every string field in the request DTOs. |
| TM-13 | T | No CSP, `Referrer-Policy` or `Permissions-Policy`; the CSRF token is deliberately script-readable, so any XSS foothold defeats CSRF wholesale. No XSS sink exists today, so this is defence-in-depth. | `config/SecurityConfig.java:85, 108-110` | Add a restrictive CSP and `Referrer-Policy: no-referrer` via the headers DSL. |
| TM-14 | E | `hasRole("ADMIN")` on one URL prefix is the only authorization check; `@EnableMethodSecurity` is absent and the controller deliberately does not re-check. Any future admin handler outside `/api/admin/**` is unguarded. | `config/SecurityConfig.java:122-132` | Enable method security and annotate the admin **service** so authorization travels with the capability, not the URL. |
| TM-15 | R | Raw `username` is logged with no validation on login, and `RegistrationRequest` has `@Size` but no `@Pattern`, so CRLF and control characters reach the log — forged audit entries and broken parsers. | `auth/LoginFailureHandler.java:64, 77`; `RegistrationRequest.java:13-24` | Add `@Pattern` to username, strip CR/LF and cap length before logging, emit JSON logs. |
| TM-16 | S | `server.servlet.session.timeout` is never set, so idle expiry is an implicit 30-minute container default, and there is no absolute lifetime — a stolen cookie stays valid as long as it keeps being used. | `application.yml` (absent) | Set the timeout explicitly and enforce an absolute maximum session age. |
| TM-17 | D | The only admin guard is "not yourself". Two admins can eliminate each other down to one, and a disabled `ADMIN` row still satisfies the bootstrap check — so recovery means direct DB edits, exactly what Story 12 exists to prevent. | `admin/AdminUserManagementService.java:43, 57, 71, 88-92`; `user/AdminBootstrapRunner.java:42-57` | Reject any mutation leaving zero **enabled** admins; seed on "no enabled admin" rather than "no admin". |
| TM-18 | T | Issuing a new reset token does not invalidate outstanding ones, so several live paths into an account can coexist; expired and used rows are never purged. | `passwordreset/PasswordResetService.java:83-86` | Invalidate outstanding tokens on issue; add a scheduled purge past expiry. |
| TM-18b | D | Reset requests are unthrottled: unlimited token rows per user and, once real mail is wired, an inbox-flooding vector that trains users to expect reset mails. | `auth/IpThrottleFilter.java:44-45` | Rate-limit per IP and per target email; refuse a new token while an unexpired one exists. |
| TM-03 | I | The reset token is delivered as a query parameter. **Partially mitigated:** `App.tsx` erases it from the address bar via `history.replaceState`, and the app loads no external subresources, so history, bookmark and third-party `Referer` vectors are closed. Residual: `replaceState` runs after the request was already sent, so the token still reaches the frontend host's access logs and any URL-logging intermediary. | `PasswordResetService.java:90`; mitigating code `frontend/src/App.tsx:18-20, 39-42` | Deliver the token in the URL fragment — never sent to a server, so it closes the access-log vector `replaceState` cannot reach. Add `Referrer-Policy: no-referrer`. |
| TM-19 | T | The only datasource is dev H2: `sa` with a blank password, `ddl-auto: update`, no migrations, no prod profile. The app as committed cannot start outside dev. | `application.yml:35-50` | Add a prod profile with an external datasource, env/secret-manager credentials, `ddl-auto: validate`, and Flyway migrations. |
| TM-34 | LINDDUN (Disclosure) | `GET /api/admin/users` returns every user's full email to any admin in one call, with no minimisation, purpose limitation or read logging — the PRD says email exists solely for password reset. | `admin/UserSummaryResponse.java` | Drop email from the default listing or mask it; expose it only via a purpose-logged per-user lookup. |
| TM-30 | LINDDUN (Unawareness) | Registration collects and indefinitely stores an email address with no privacy notice, no stated purpose or retention, and no recorded lawful basis. | no notice anywhere in the repo | Add a notice at the point of collection; record the lawful basis as an ADR. |
| TM-36 | LINDDUN (Disclosure) | **Downgraded from High** by the TM-02 fix, which removed the reset link, its token and the recipient email from the logs. Residual: usernames still appear across the audit lines, and logs still go to stdout with no retention, access control, redaction or shipping policy. | no `logback-spring.xml`; no retention config | Log a pseudonymous user id instead of the username (TM-35), then define retention and access control. |
| TM-31 | LINDDUN (Non-compliance) | No retention policy for accounts, reset tokens or logs, and no self-service erasure, export or rectification path — deletion is admin-only. | PRD (no retention requirement) | Define retention periods and add self-service deletion/export. |
| TM-38 | T | CSRF is configured correctly but **untested**: no test submits a mutating request without `X-XSRF-TOKEN` and asserts 403. A refactor could silently disable it. Same for cookie attributes, CORS origin rejection and session-ID rotation. | `backend/src/test/java/...` | Add the four missing integration assertions (see §8). |
| TM-39 | R | Destructive admin actions are irreversible but recorded only as an unstructured INFO line — no client IP, no correlation id, no immutable store, no step-up re-authentication. | `admin/AdminUserManagementService.java:49-50, 63-64, 81-85` | Require re-authentication for delete; write to an append-only audit table in the same transaction; consider soft-delete. |

---

## 6. Open findings — Low

| ID | STRIDE / LINDDUN | Finding | Evidence |
| --- | --- | --- | --- |
| TM-19b | I | No TLS or credential-handling pattern exists for the Postgres/MySQL migration the PRD anticipates; nothing on the wire today because H2 is in-process. | `application.yml:38-42` |
| TM-20 | I | `new BCryptPasswordEncoder()` takes the implicit default strength 10 with no configuration hook; `PasswordPolicy` sets a 12-char minimum and no maximum, so BCrypt silently truncates past 72 bytes. | `SecurityConfig.java:49-51`; `PasswordPolicy.java:13-17` |
| TM-21 | T | `CsrfTokenRequestAttributeHandler` is wired instead of the `Xor` variant, giving up per-response token masking against BREACH. Needs response compression plus a network position to exploit. | `SecurityConfig.java:86, 99` |
| TM-22 | I | Registration answers 409 with distinct messages for a taken username vs a taken email, so accounts are enumerable. In-spec per the PRD, which scopes enumeration resistance to login and reset only. | `auth/RegistrationService.java:45-52` |
| TM-23 | I | Reset-request response bodies are identical, but the registered path does RNG + SHA-256 + a transactional write, so timing recovers the fact the uniform response hides. | `PasswordResetService.java:75-94` |
| TM-24 | I | Three package-scoped advices cover auth, admin and reset; anything else (including `/api/hello`, `/api/health`, `/api/csrf`) falls through to Boot's `/error` in a shape the SPA cannot parse. One property change from being a leak. | the three `*ExceptionHandler` classes |
| TM-25 | D | Sessions, `SessionRegistryImpl` and the throttle map are all JVM-local: a second instance breaks session continuity, "invalidate all sessions" and the throttle budget. Documented in `IpLoginThrottle`'s Javadoc. | `IpLoginThrottle.java:19-23` |
| TM-26 | I | `VITE_API_BASE_URL ?? "http://localhost:8080"` — a production build without the variable ships a bundle pointing at the visitor's own machine. | `frontend/src/api/client.ts:1` |
| TM-27 | R | Every PRD-required audit event *is* logged, but as ad-hoc key=value text with no JSON encoder, no correlation id, no client IP on admin actions, and no retention or tamper protection. | no `logback-spring.xml`; PRD:122 |
| TM-28 | T | No dependency-vulnerability scan, no SBOM, no `npm audit` step — no way to answer "are we affected" against a new CVE. | `backend/pom.xml`; `frontend/package.json` |
| TM-32 | Identifiability | Account existence is externally probeable (TM-22), so a list of email addresses reveals which of those people use the system. | `RegistrationService.java:45-52` |
| TM-33 | Detectability | Timing (TM-23) reveals the presence of a data subject's record without authenticating. | `PasswordResetService.java:75-94` |
| TM-35 | Linkability | `users` permanently ties username to email; logs tie the same username to login times and failure patterns, with no context separation and no retention bound. | — |
| TM-37 | Non-repudiation | Authentication events are kept per username forever — accountability is the point, but with no retention limit the record outlives its security purpose. | — |

---

## 7. Verified mitigations

Worth recording explicitly, because a threat model that only lists problems misrepresents the system. Each of these was confirmed by reading the implementation; where a test asserts it, the test is named.

**Credentials and passwords**
- BCrypt only, never plaintext or reversible encoding; the seeded admin password is hashed like any other (`AdminBootstrapRunnerTest`). **Caveat worth recording:** when this model was first written that test was failing, and had been since commit `e0730ee` — it asserted a hardcoded password literal that configuration had since moved away from. The product code was always correct; the assertion was not actually running green. Fixed in `7ff9624` by reading the expected value from `app.admin.password`.
- Passwords never appear in any log statement; exception handlers deliberately log messages without payloads.
- Reset tokens never appear in any log statement either, as of the TM-02 fix (§12), enforced by `EmailServiceTest`.
- Password hashes never leave the API: `/api/admin/users` returns a purpose-built projection, asserted explicitly by `AdminUserControllerTest`.
- Admin credentials are mandatory with no defaults in the base profile — startup fails rather than seeding a guessable account.

**Sessions**
- Session fixation handled by Spring Security's default `changeSessionId()` rotation on authentication.
- Logout invalidates server-side state and deletes the cookie; a replayed cookie is rejected (`LoginLogoutHelloTest`).
- `HttpOnly` + `SameSite=Lax` + `Secure` (outside dev) on the `SESSION` cookie.
- No client-side token at all: the SPA keeps only `{username, role}` in a React `useState`. No `localStorage`, `sessionStorage` or `IndexedDB` anywhere.

**Authentication flow**
- Brute force blunted by per-account lockout (5 / 15 min) and per-IP throttling (10 / 15 min), with residual weaknesses tracked as TM-08/09/10.
- Lock and enabled state are checked *before* password comparison, so locked and disabled accounts cannot authenticate at all (`LockoutAndThrottlingTest`, `AdminUserControllerTest`).
- Login returns one generic 401 for both an unknown username and a wrong password, asserted by a test written specifically to compare the two responses.

**Password reset**
- 256-bit `SecureRandom` token, stored only as a SHA-256 hash in a uniquely-indexed column, 30-minute expiry, single-use via `used_at`.
- All sessions for the user are expired on a successful reset — the behaviour TM-04 shows is missing from the admin path.
- The request endpoint returns an identical 200 whether or not the email resolves.
- Expiry and reuse rejection are both test-covered (`PasswordResetTest`).

**Authorization**
- Role comes from the authenticated principal; the acting admin id comes from `@AuthenticationPrincipal`, never from the request body.
- Self-action guards on all three admin mutations, each test-covered.
- Disable, demote and delete all revoke the target's live sessions as of the TM-04 fix (§12), so a narrowed privilege takes effect immediately rather than at next login (`AdminSessionRevocationTest`).
- Client-side admin gating is cosmetic by design and the server is authoritative; a `USER` gets 403 on every admin mutation and 401 when unauthenticated.

**Injection**
- **SQL injection is not applicable.** Every repository method is a Spring Data derived query — no `@Query`, no native SQL, no string concatenation anywhere.
- No XSS sink in the frontend: no `dangerouslySetInnerHTML`, `innerHTML` or `eval`; every server value renders as a JSX text node.
- CSRF enabled for all state-changing endpoints with the cookie-plus-header pattern (untested — TM-38).

---

## 8. Testing gaps

41 backend tests across 9 classes cover every security path the PRD's testing section requires, and they are well-targeted. Four security properties remain configured but unasserted, which is how a correct control becomes an incorrect one during a refactor:

1. A mutating request without `X-XSRF-TOKEN` is rejected with 403 (TM-38).
2. A request bearing a disallowed `Origin` is refused (TM-06).
3. `Set-Cookie` on login carries `HttpOnly`, `SameSite` and — outside dev — `Secure`.
4. The session id changes across successful authentication (session-fixation rotation).

A fifth gap — that a disabled or demoted user's **existing** session is rejected — was closed by the TM-04 fix and is now asserted by `AdminSessionRevocationTest`.

The frontend has no test framework at all (`package.json` scripts are dev/build/preview/typecheck only).

---

## 9. PRD security-requirement conformance

Against the non-functional requirements in [`prd/assessment-prd.md:112-123`](../../prd/assessment-prd.md):

| Requirement | Status | Note |
| --- | --- | --- |
| Password storage — BCrypt, no custom hashing | **Met** | Strength implicit (TM-20) |
| Session security — HttpOnly / Secure / SameSite, fixation protection, invalidation on logout and reset | **Met** | No timeout configured (TM-16) |
| CSRF enabled for all state-changing endpoints | **Met** | Untested (TM-38); no BREACH masking (TM-21) |
| CORS — explicit allow-list with credentials | **Met** | Unvalidated at startup (TM-06) |
| Enumeration resistance on login and password reset | **Met** | Timing side-channel (TM-23); registration is out of the requirement's scope but enumerable (TM-22) |
| Transport — HTTPS behind any real deployment | **Partial** | Documented as an accepted gap, but nothing enforces it and there is no prod profile (TM-05) |
| Audit logging — structured lines for all named events, never passwords | **Partial** | All events logged, no passwords, and no reset token since the TM-02 fix; still unstructured and uncorrelated (TM-27) |
| Least privilege — role checks server-side, never trusted from the client | **Met** | Single point of enforcement, no method-level backstop (TM-14) |
| Account lockout after N failures *within a window* | **Partial** | Lockout works; there is no window (TM-08) |
| IP throttling independent of account lockout | **Partial** | Works in a direct-connection topology only (TM-09) |

---

## 10. Suggested remediation order

Sequenced by risk reduction per unit of effort, not by severity alone. Owners and dates are deliberately blank — assign them before treating this as a plan.

| Order | Items | Rationale | Owner | Target |
| --- | --- | --- | --- | --- |
| ~~1~~ | ~~TM-02~~ | **Done** (§12). Stop writing a live account-takeover secret to the log. | Samuel Wong | 2026-09-23 |
| ~~2~~ | ~~TM-04~~ | **Done** (§12). The revocation mechanism already existed; wired into the admin path. | Samuel Wong | 2026-09-23 |
| 3 | TM-01, TM-07 | Profile-gate the dev-only rules and remove the fixed admin password. Makes the profile people actually run safe to expose. **Next up.** | _TBD_ | _TBD_ |
| 4 | TM-05, TM-06, TM-13, TM-16, TM-03 | Deployment posture: transport enforcement, CORS validation, security headers (which also completes TM-03), session timeout, and the token-in-fragment change. Mostly configuration. | _TBD_ | _TBD_ |
| 5 | TM-08, TM-09, TM-10, TM-11, TM-12, TM-18b | Make the anti-automation controls actually hold in a real topology, and stop them being DoS vectors themselves. | _TBD_ | _TBD_ |
| 6 | TM-38 + the other four testing gaps | Lock in the controls that are correct today so they stay correct. | _TBD_ | _TBD_ |
| 7 | TM-14, TM-15, TM-17, TM-18, TM-19, TM-39 | Defence in depth, log integrity, last-admin protection, and a real datasource with migrations. | _TBD_ | _TBD_ |
| 8 | TM-30, TM-31, TM-34, TM-36 | Privacy: notice, retention, data minimisation in the admin listing. Needs a compliance-regime decision first. | _TBD_ | _TBD_ |
| 9 | Remaining Low findings | Opportunistic. | _TBD_ | _TBD_ |

---

## 11. Maintenance

This model is a living document and lives in version control beside the code it describes. Revisit it when:

- the deployment topology changes (a proxy or load balancer in front of the app invalidates TM-09's assumptions and activates TM-25),
- a real `EmailService` replaces the stub,
- a persistent datasource replaces H2,
- the JWT alternative in the PRD appendix is ever built (a different threat set: token storage and revocation replace CSRF as the primary concern),
- any new endpoint is added outside `/api/admin/**` that carries admin capability (TM-14).

**Not yet done:** the model has not been validated against the official Threat Dragon v2 JSON schema here — the schema could not be fetched in this environment. To validate locally:

```powershell
npm install -g ajv-cli
npx ajv validate --allow-union-types -s threat-dragon-v2.schema.json --all-errors --verbose `
  -d docs/threat-model/hello-world-auth-app.json
```

Threat Dragon warns on schema mismatches but still loads the model, so a warning is not a blocker. Structural invariants *were* checked on generation: unique cell ids, no dangling data-flow endpoints, contiguous threat numbering, and valid shape/element/threat-type values for both STRIDE and LINDDUN.

**No ADRs exist yet.** `docs/adr/` and `CONTEXT.md` are absent, so every accepted risk in this model — HTTP in dev, registration enumeration, single-instance deployment, H2 persistence — is recorded only here and in class Javadoc. The four accepted risks deserve ADRs so the reasoning survives.

---

## 12. Remediation log

### 2026-09-23 — TM-02 and TM-04 closed

Baseline before starting: **34 tests, one failing.** `AdminBootstrapRunnerTest.seededAdminPasswordIsHashedNotPlaintext` had been red since commit `e0730ee`, because it asserted the literal `change-this-admin-password` while the dev default moved to `password` and then `password1234`. Product code was never at fault — `AdminBootstrapRunner` always hashed via `passwordEncoder.encode`. Fixed by reading the expected value from `app.admin.password` so the assertion tracks configuration instead of duplicating it (`7ff9624`).

This matters for the model's credibility: §7 credits that test as evidence the seeded password is hashed, and it was not actually passing. The claim was true; its stated proof was not running.

**TM-02 — reset token in the log.** `EmailService` no longer logs the link, the token or the recipient address. It records only that a dispatch occurred, which is the part with audit value.

The judgement call: the stub logged the link because that is how a developer walks the reset flow with no mail server. Deleting the line outright breaks local testing, so the link is now behind `app.mail.log-reset-link`, defaulting to **false in every profile including dev**. Enabling it in dev was tempting and would have been wrong — dev is the only profile with a working datasource and therefore the profile the app is actually started with, so a dev-on default would have left the leak in place everywhere it runs and made the fix cosmetic. The opt-in is documented in README.

Covered by `EmailServiceTest` (3 tests), deliberately a plain unit test rather than `@SpringBootTest` so the assertions cannot be satisfied by whatever the active profile happens to set.

**TM-04 — stale authorization after disable or demote.** The session-expiry logic moved out of `PasswordResetService`'s private method into a shared `auth/SessionRevoker`, now called from `setEnabled(false)`, `changeRole` and `deleteUser`. Each call site records `revokedSessions=` in its audit line.

Role changes revoke in **both** directions, not only on demotion: a demotion has to take effect immediately or it can be undone by the very session it failed to cut, a promotion otherwise leaves the new admin with stale `USER` authorities, and one rule is easier to reason about than two.

Covered by `AdminSessionRevocationTest` (4 tests), **verified failing-first**: with the fix reverted, three of the four failed with `expected:<401> but was:<200>`. The delete case was the most striking — a deleted user's session was still served `/api/hello` successfully, authenticated against a principal no longer in the database.

**Residual:** `SessionRegistry` is in-process, so revocation only reaches the instance handling the call. Tracked as TM-25; a multi-instance deployment needs Spring Session before revocation can be relied on.

**After:** 41 tests across 9 classes, all green, `mvn test` exit code 0.

| | Before | After |
| --- | --- | --- |
| Open High | 7 | 4 |
| Open total | 40 | 38 |
| Mitigated | 20 | 22 |
| Tests | 34 (1 failing) | 41 (all green) |
