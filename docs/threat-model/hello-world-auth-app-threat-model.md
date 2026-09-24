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
| **Open** | — | 10 | 16 | **26** |
| **Mitigated** | 20 | 13 | 1 | **34** |
| **Not applicable** | 1 | — | — | **1** |

The headline: this is a genuinely well-built security baseline, and the open findings are concentrated rather than scattered. The authentication core — password storage, session handling, CSRF, enumeration resistance, reset-token cryptography, admin self-action guards — is correct and largely test-covered.

All four original clusters of weakness are now closed (§12): **secret handling on the reset path** (the live token was written to the log and carried in a query string), **revocation consistency** (password reset killed sessions, disable and demote did not), **dev-only concessions shipping in every profile** (the H2 console's rules, the fixed admin password), and **deployment posture** (nothing enforced HTTPS, the CORS allow-list was unvalidated input, no security headers, no session lifetime cap).

Authorization was separately strengthened by work outside this model's original scope — a configuration-owned RBAC matrix terminating in `denyAll()` — which is why TM-14 dropped from Medium to Low.

**No High findings remain.** That deserves a caveat rather than a victory lap: it means nothing in this model is currently rated High, not that the system is deployable. The largest outstanding item is TM-19 — there is still **no production configuration at all**: no non-dev datasource, no migrations, H2 only. It sits at Medium because it is absent work rather than a defect, but it is the reason two of the fixes just made (TM-05, TM-06) protect a deployment that does not yet exist.

The anti-automation cluster (TM-08 through TM-12 and TM-18b) is also now closed. What remains is operational maturity — structured logs, an audit trail, dependency scanning, a real datasource — and privacy groundwork that needs a compliance decision before it can be scoped.

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

**None. All six High findings are closed.**

TM-01, TM-02, TM-04, TM-05, TM-06 and TM-07 have each been fixed, test-covered, and — where the behaviour was observable — verified failing-first against the original code. §12 records what changed and what evidence was taken.

This is worth stating carefully rather than triumphantly. It means no finding in this model is currently rated High; it does not mean the system is ready to deploy. The largest outstanding item, TM-19, is that **no production configuration exists at all** — no non-dev datasource, no migrations, H2 only — and that is rated Medium because it is an absence of deployment work rather than a defect in the code. Two of the High fixes just made (TM-05, TM-06) protect a deployment that does not yet exist.

---

## 5. Open findings — Medium

| ID | STRIDE | Finding | Evidence | Fix in one line |
| --- | --- | --- | --- | --- |

| TM-15 | R | Raw `username` is logged with no validation on login, and `RegistrationRequest` has `@Size` but no `@Pattern`, so CRLF and control characters reach the log — forged audit entries and broken parsers. | `auth/LoginFailureHandler.java:64, 77`; `RegistrationRequest.java:13-24` | Add `@Pattern` to username, strip CR/LF and cap length before logging, emit JSON logs. |
| TM-17 | D | The only admin guard is "not yourself". Two admins can eliminate each other down to one, and a disabled `ADMIN` row still satisfies the bootstrap check — so recovery means direct DB edits, exactly what Story 12 exists to prevent. | `admin/AdminUserManagementService.java:43, 57, 71, 88-92`; `user/AdminBootstrapRunner.java:42-57` | Reject any mutation leaving zero **enabled** admins; seed on "no enabled admin" rather than "no admin". |
| TM-18 | T | Issuing a new reset token does not invalidate outstanding ones, so several live paths into an account can coexist; expired and used rows are never purged. | `passwordreset/PasswordResetService.java:83-86` | Invalidate outstanding tokens on issue; add a scheduled purge past expiry. |
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
| TM-13 | T | **Downgraded from Medium.** The API now sends CSP, `Referrer-Policy: no-referrer`, `Permissions-Policy` and retains `X-Frame-Options: DENY`. Residual: those govern this service's JSON responses, not the SPA — which is a different origin and needs a CSP from whatever serves it. The XSS-defeats-CSRF chain lives on the frontend origin, and there is still no XSS sink there. | `config/SecurityConfig.java` headers DSL; no CSP at the SPA host |
| TM-14 | E | **Downgraded from Medium** by the config-owned RBAC matrix: authorization is now fine-grained authorities against explicit method+path guards in YAML, terminating in `denyAll()`, so forgetting to guard a new endpoint yields a 403 rather than silent exposure. Residual: enforcement is still URL-layer only — no `@EnableMethodSecurity`, so a service method reached from a scheduled job or listener carries no authorization of its own. | `config/SecurityConfig.java`; `application.yml` `app.security` |
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
- Deny-by-default: the filter chain is built from a YAML guard matrix and terminates in `denyAll()`, so an endpoint that is neither whitelisted nor explicitly guarded is unreachable by everyone, including authenticated admins (`SecurityConfigTest.unmappedPathIsDeniedByDefaultEvenWhenAuthenticated`).
- Role hierarchy resolved at authentication time, so `ROLE_ADMIN` reaches `ROLE_USER`'s fine-grained authorities without duplicating them in config (`SecurityConfigTest`, `RoleHierarchyConfigTest`, `AppUserDetailsServiceTest`).
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
| Session security — HttpOnly / Secure / SameSite, fixation protection, invalidation on logout and reset | **Met** | Explicit 30m idle timeout plus an 8h absolute lifetime cap; revocation also covers admin disable/demote/delete |
| CSRF enabled for all state-changing endpoints | **Met** | Untested (TM-38); no BREACH masking (TM-21) |
| CORS — explicit allow-list with credentials | **Met** | Unvalidated at startup (TM-06) |
| Enumeration resistance on login and password reset | **Met** | Timing side-channel (TM-23); registration is out of the requirement's scope but enumerable (TM-22) |
| Transport — HTTPS behind any real deployment | **Met** | `requiresSecure` + HSTS, gated on `app.security.require-https` (false only in dev). `forward-headers-strategy` left off by default and documented, since enabling it on a directly-reachable app allows header forgery |
| Audit logging — structured lines for all named events, never passwords | **Partial** | All events logged, no passwords, and no reset token since the TM-02 fix; still unstructured and uncorrelated (TM-27) |
| Least privilege — role checks server-side, never trusted from the client | **Met** | Config-owned guard matrix with a deny-by-default terminal rule; no method-level backstop (TM-14, now Low) |
| Account lockout after N failures *within a window* | **Partial** | Lockout works; there is no window (TM-08) |
| IP throttling independent of account lockout | **Partial** | Works in a direct-connection topology only (TM-09) |

---

## 10. Suggested remediation order

Sequenced by risk reduction per unit of effort, not by severity alone. Owners and dates are deliberately blank — assign them before treating this as a plan.

| Order | Items | Rationale | Owner | Target |
| --- | --- | --- | --- | --- |
| ~~1~~ | ~~TM-02~~ | **Done** (§12). Stop writing a live account-takeover secret to the log. | Samuel Wong | 2026-09-23 |
| ~~2~~ | ~~TM-04~~ | **Done** (§12). The revocation mechanism already existed; wired into the admin path. | Samuel Wong | 2026-09-23 |
| ~~3~~ | ~~TM-01, TM-07~~ | **Done** (§12). Profile-gated the dev-only rules and removed the fixed admin password. | Samuel Wong | 2026-09-24 |
| ~~4~~ | ~~TM-05, TM-06, TM-16, TM-03~~ | **Done** (§12). Transport enforcement, CORS validation, security headers, session lifetime caps, token-in-fragment. TM-13 partly done and downgraded to Low: the SPA's own CSP needs a frontend host that does not exist yet. | Samuel Wong | 2026-09-24 |
| ~~5~~ | ~~TM-08, TM-09, TM-10, TM-11, TM-12, TM-18b~~ | **Done** (§12). Lockout decay, proxy-aware and bounded throttling, request-rate limits on unauthenticated writes, request-size caps. | Samuel Wong | 2026-09-24 |
| 6 | TM-38 + the remaining three testing gaps | Lock in the controls that are correct today so they stay correct. **Next up.** | _TBD_ | _TBD_ |
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

### 2026-09-24 — TM-01 and TM-07 closed; TM-14 reassessed

**Context that changed underneath this model.** Two commits landed between the previous entry and this one, written outside this remediation effort: `9020940` replaced the hardcoded authorization rules with a configuration-owned RBAC matrix (fine-grained authorities bound from `app.security` in YAML, a role hierarchy, and a terminal `denyAll()` instead of `authenticated()`), and `e128438` added `RoleMutationGuard` as a single checkpoint for the sanctioned role-mutation path. Baseline moved from 41 tests to 59, all green.

That work required reassessing **TM-14**, which is why it is now **Low** rather than Medium. The original finding was that a single URL matcher stood between a `USER` and every admin capability, so a future admin-capable handler mounted outside `/api/admin/**` would be unguarded. Deny-by-default inverts precisely that failure mode: an endpoint nobody remembered to guard is now unreachable by everyone rather than quietly open to any authenticated user. What remains is narrower — enforcement is still URL-layer only, so a service method reached from a scheduled job or a message listener carries no authorization of its own.

**TM-01 and TM-07 shared one shape**, which is why they were fixed together: a concession made for local development was granted by a rule that was not gated on the same switch as the thing needing it.

For **TM-01**, the H2 console's three concessions — unauthenticated access, CSRF exemption, and `frameOptions=sameOrigin` — all moved onto a `@Profile("dev")` chain in `H2ConsoleSecurityConfig` matching `/h2-console/**` only, and the path came out of the whitelist. Outside dev it now matches neither the whitelist nor a url-guard and hits `denyAll()`. The API chain reverts to `frameOptions: DENY`, so clickjacking protection is no longer traded away application-wide for a dev tool.

The failing-first check was unusually informative here. With the old configuration restored, `/h2-console/` returned **404, not 401** — meaning the request sailed through the security chain and only failed because the servlet happened to be unregistered. That is exactly the difference the finding was about: the console was protected by accident of packaging rather than by policy.

For **TM-07**, dev now defaults the admin password to blank and `AdminBootstrapRunner` generates a random 24-byte password per boot, logged once at `WARN`. Clone-and-run still works with no setup, but there is no published credential and the value rotates every restart. The README and the dev login panel no longer print a password — the panel prefills the username only. The generate path is structurally confined to dev: the base profile's placeholder has no default, so an unset password outside dev fails startup rather than generating anything, preserving the original fail-fast control.

**One residual accepted deliberately:** the generated password is written to the log, which is the category of problem TM-02 was about. The distinction is that this is a per-boot, rotating, dev-only bootstrap credential with no other delivery channel, where TM-02 was a user's account-takeover token logged in every profile including production. Recorded here rather than left implicit.

**Tests added:** `H2ConsoleNotExposedOutsideDevTest` (3) — the only class in the suite that boots without the dev profile, since that is the condition under test; `AdminBootstrapPasswordGenerationTest` (3), targeting the real risk in the change, which is hashing the empty string and shipping an account with a blank password; and `SecurityPropertiesTest.whitelistDoesNotExposeTheH2Console`, guarding the YAML entry directly because it looks harmless sitting next to the others.

**After:** 66 tests across 17 classes, all green, `mvn test` exit code 0; frontend `tsc -b` clean.

| | Session start | After §12 entry 1 | Now |
| --- | --- | --- | --- |
| Open High | 8 | 4 | **2** |
| Open total | 40 | 38 | **36** |
| Mitigated | 20 | 22 | **24** |
| Tests | 34 (1 failing) | 41 | **66** |

Both remaining High findings are TM-05 (nothing enforces HTTPS) and TM-06 (CORS allow-list is unvalidated environment input) — step 4 in §10.

### 2026-09-24 (later) — TM-05, TM-06, TM-16, TM-03 closed; TM-13 partly closed

The deployment-posture cluster, and with it the last two High findings.

**TM-05 — transport.** `app.security.require-https` (default true, false only in dev) now drives `requiresChannel().anyRequest().requiresSecure()` plus an explicit HSTS policy. Plaintext is refused rather than merely discouraged.

`server.forward-headers-strategy` was the interesting decision. Honouring `X-Forwarded-*` is *required* behind a TLS-terminating proxy — without it the app cannot tell what scheme the client used, and `requiresSecure`, HSTS and `Secure` cookies all misfire. But enabling it while the app is directly reachable lets any client forge `X-Forwarded-Proto: https` and defeat exactly those controls. So the property is present and documented with the trade-off stated, but left at `none`. Turning it on is a deployment decision that depends on a topology this repo does not yet have; switching it on by default would have been a fix that introduced its own hole.

**TM-06 — CORS.** `CorsConfig` now refuses to start on an empty list, a blank entry, any wildcard, a non-absolute origin, or a plaintext origin when HTTPS is required. Failing to boot is proportionate: with `allowCredentials: true`, any origin on this list can make authenticated requests with a visitor's session and read the responses, so it is the load-bearing control for the whole cookie-auth design. A warning in a log nobody reads would not do.

**TM-16 — session lifetime, in two halves.** The idle timeout is now explicit at 30m, replacing an implicit container default nobody had chosen. The substantive half is `AbsoluteSessionTimeoutFilter`, capping total lifetime at 8h regardless of activity — an idle timeout alone renews a periodically-used session forever, so a stolen cookie stayed valid for as long as the attacker kept exercising it. There was previously no point at which a session simply ended. On expiry the filter clears the context and lets the request continue as anonymous, so clients get the normal 401 shape and whitelisted routes keep working.

**TM-03 — reset token in the fragment.** Now `/#token=…` rather than `?token=…`. This is what the client-side strip could not achieve: `replaceState` removed the token from the address bar, but only *after* the request carrying it had been sent and logged wherever the SPA is served from. A fragment is never transmitted at all. The strip is retained as defence in depth.

**TM-13 — partly closed, downgraded to Low.** The API now sends CSP, `Referrer-Policy: no-referrer` and `Permissions-Policy`. But those govern this service's own JSON responses, and the finding was about the SPA: it is served from a different origin, so a CSP from the backend does not constrain the pages where script actually runs. Closing it properly needs a CSP from whatever serves the SPA — deployment configuration that does not exist yet (TM-19). Marked Low rather than closed, because claiming otherwise would misrepresent it. The exploitable precondition is still absent anyway: there is no XSS sink in the frontend.

**Verification.** 87 tests across 20 classes, all green, `mvn test` exit 0; frontend `tsc -b` clean. New: `CorsOriginValidationTest` (9), `SecurityHeadersTest` (6), `HttpsEnforcementTest` (3), `AbsoluteSessionTimeoutTest` (2), and a `PasswordResetTest` case pinning the fragment form so a regression to the query string is caught.

Also confirmed against the running application rather than only in tests — the live `/api/health` response carries all four new headers, and `Strict-Transport-Security` is correctly withheld over plaintext:

```
Content-Security-Policy   default-src 'none'; frame-ancestors 'none'; base-uri 'none'
Referrer-Policy           no-referrer
X-Frame-Options           DENY
X-Content-Type-Options    nosniff
Permissions-Policy        geolocation=(), camera=(), microphone=(), payment=(), usb=()
Strict-Transport-Security (absent)
```

Two existing non-dev tests needed adjusting: `H2ConsoleNotExposedOutsideDevTest` now sets `require-https=false`, because inheriting the new default would have redirected every request to HTTPS and rejected the default `http://localhost` CORS origin at startup — neither of which is what that class is testing.

| | Session start | After entry 1 | After entry 2 | Now |
| --- | --- | --- | --- | --- |
| Open High | 8 | 4 | 2 | **0** |
| Open total | 40 | 38 | 36 | **32** |
| Mitigated | 20 | 22 | 24 | **28** |
| Tests | 34 (1 failing) | 41 | 66 | **87** |

Next is step 5 in §10: the anti-automation cluster (TM-08 lockout decay, TM-09 proxy-blind throttling, TM-10 unbounded throttle map, TM-11/TM-18b unthrottled endpoints, TM-12 request limits). Six findings, all Medium, and the largest remaining group.

### 2026-09-24 (later still) — the anti-automation cluster closed

TM-08, TM-09, TM-10, TM-11, TM-12 and TM-18b. Split into two commits because the findings divide cleanly: the login throttle and lockout core (`e1c2da6`), then rate limiting and request caps (`0f3717d`).

**TM-08 — the counter now decays.** `last_failed_login_at` plus `LockoutPolicy.continuesStreak` mean only failures inside a 15-minute window accumulate. This closed a cheap, repeatable denial of service: five requests denied any known username access, repeatable whenever the cooldown lapsed, and five stayed under the ten-attempt per-IP threshold so IP throttling never engaged. The PRD's "N consecutive failed attempts within a window" is now actually what the code does.

Verified failing-first by making `continuesStreak` always return true, reproducing the old lifetime tally — it failed exactly the two window cases and left the other three passing, which is the right blast radius.

This also exposed something worth recording: `LoginLogoutHelloTest` seeded `failedLoginAttempts = 3` with no timestamp. That is a state the application can no longer produce, because every increment now writes one. The fixture was made consistent rather than the assertion relaxed — the alternative would have been to weaken a test to accommodate an impossible fixture.

**TM-09 — the topology is now declared, not assumed.** `ClientIpResolver` is driven by `app.security.trusted-proxy-hops`. This one deserved care because both obvious fixes are exploitable in opposite directions: ignoring `X-Forwarded-For` behind a proxy collapses every client into one bucket, so the tenth failed login from anyone locks out everyone; trusting it while directly reachable lets a caller rotate a forged value and never be throttled at all. Neither could be adopted as a default, so the deployment has to say which world it is in. Zero — the default — ignores the header entirely.

Counting back from the *right* of the chain is the load-bearing detail. The rightmost entries were appended by infrastructure we control; the leftmost is whatever the original caller claimed.

**TM-10 — the map is bounded**, at 10,000 addresses, sweeping expired windows first and dropping the oldest if that is not enough. The trade is explicit: forgetting old throttle state is recoverable, exhausting the heap is not. Note the specific reason the old code leaked — entries were pruned only on a *successful* login from the same address, and no successful login ever arrives to clean up after a spray.

**TM-11 and TM-18b — request limits on the endpoints that had none.** `RequestRateLimiter` and `RateLimitFilter`, with rules in YAML beside the guard matrix.

Deliberately *not* a generalisation of `IpLoginThrottle`, because the two measure different things. Login counts failures, which is correct there — a legitimate user logging in repeatedly should never be limited. Registration and password reset have no notion of failure: they succeed from the caller's point of view every time, and the cost to defend is the work performed regardless of outcome. Each runs a BCrypt hash, roughly 100ms of server CPU for a rounding error of client effort.

**TM-12 — bodies capped before they are read.** Tomcat caps on header size, form size, threads, connections and backlog, plus `MaxRequestSizeFilter` refusing oversized bodies from `Content-Length` ahead of the rest of the chain. A filter was necessary rather than `@Size`: bean validation runs *after* Jackson has buffered and parsed the whole payload, so an oversized body was fully materialised before anything rejected it, and Tomcat's `max-http-form-post-size` does not apply to JSON.

Stated as a floor, not a guarantee. A chunked request sends no `Content-Length`, so a hard ceiling belongs at an ingress that can enforce one while streaming — deployment capability this repo still lacks (TM-19).

**Verification.** 120 tests across 26 classes, all green, `mvn test` exit 0. New: `ClientIpResolverTest` (8), `IpLoginThrottleEvictionTest` (4), `LockoutWindowTest` (5), `RequestRateLimiterTest` (6), `RateLimitFilterTest` (4), `MaxRequestSizeFilterTest` (3), plus three `SecurityPropertiesTest` cases pinning the configured limits.

Also confirmed live: five password-reset requests served, the sixth and seventh refused with 429. The restart additionally showed the TM-07 fix working in situ — `passwordSource=generated` with a fresh random admin password.

Two test-quality notes. Three existing classes needed the shared rate-limit or throttle singleton cleared between methods, since every MockMvc request arrives from the same address and the beans live in a cached context. And `RequestRateLimiterTest`'s window-expiry case had to be rewritten: the first version used a zero-length window and so depended on clock resolution rather than on the behaviour under test, which is exactly the kind of test that passes or fails for the wrong reason.

| | Session start | Entry 1 | Entry 2 | Entry 3 | Now |
| --- | --- | --- | --- | --- | --- |
| Open High | 8 | 4 | 2 | 0 | **0** |
| Open Medium | 18 | 20 | 19 | 16 | **10** |
| Open total | 40 | 38 | 36 | 32 | **26** |
| Mitigated | 20 | 22 | 24 | 28 | **34** |
| Tests | 34 (1 failing) | 41 | 66 | 87 | **120** |

Next is step 6 in §10: the testing gaps (TM-38 — CSRF rejection, CORS origin rejection, cookie attributes, session-ID rotation), which lock in controls that are correct today but unasserted.
