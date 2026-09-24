# Threat Model — Hello World Auth App (React + Spring Boot)

| | |
| --- | --- |
| **System** | Secured username/password login application specified in [`prd/assessment-prd.md`](../../prd/assessment-prd.md) |
| **Model file** | [`hello-world-auth-app.json`](./hello-world-auth-app.json) — OWASP Threat Dragon v2 format |
| **Methodology** | STRIDE per element (4 diagrams) + LINDDUN privacy pass (1 diagram) |
| **Code reviewed** | Full `backend/` and `frontend/` source as committed, `pom.xml`, `package.json`, `application.yml`, all 6 backend test classes |
| **Date** | 2026-09-23 |
| **Owner** | Samuel Wong |
| **Status** | Remediated to Low. All High and all Medium findings are closed and test-covered (see §12). 13 Low findings remain open; none has an assigned owner or target date. |

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
| **Open** | — | — | 13 | **13** |
| **Mitigated** | 20 | 23 | 4 | **47** |
| **Not applicable** | 1 | — | — | **1** |

**Every High and every Medium finding is now closed.** Six clusters were worked through in sequence (§12): secret handling on the reset path, revocation consistency, dev-only concessions shipping in every profile, deployment posture, anti-automation, and finally the testing gaps, operational maturity and privacy groundwork that made up the Medium tier.

Read the headline carefully, because it is easy to over-claim. "No Medium findings remain" means nothing in this model is currently *rated* Medium. It does not mean the system is deployable, and three things in particular are worth separating from the rest:

- **The production configuration exists but has never run.** TM-19 is closed: there is a `prod` profile with an external datasource, environment-supplied credentials, `ddl-auto: validate` and Flyway-owned migrations. No PostgreSQL instance exists in this repository, so the migration has never been executed against the engine it was written for. `FlywayMigrationTest` checks that the SQL and the entity model name the same tables and columns — which catches the realistic mistake — and cannot check that the SQL is valid PostgreSQL.
- **Append-only is enforced in code, not in the database.** The audit table has no setters, no delete method on its repository, and no foreign key that could cascade it away. Restricting `UPDATE`/`DELETE` against the application's own database role is a grant the migration cannot make, because the migration runs as the owner. Anyone holding the application's credentials can still rewrite history.
- **The privacy position is drafted, not reviewed.** The notice, lawful basis and retention periods are populated and tested for internal consistency. The PRD declares no compliance regime, so they are input to a review rather than its output. `docs/adr/0005` says so explicitly, because populated fields are easily mistaken for decisions somebody was accountable for.

One residual is worth stating as a genuine gap rather than absent work: the last-admin guard (TM-17) closes the sequential path to zero administrators and narrows the concurrent one to the window between reading the count and committing. Two admins removing each other at the same instant both read a count of two. Closing that needs a constraint the database enforces.

The 13 remaining Low findings are opportunistic: enumeration side channels the PRD accepts, a BCrypt strength that is implicit rather than configured, no dependency scanning, no method-level authorization backstop, and single-instance state. None of them is load-bearing on its own.

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

---

## 5. Open findings — Medium

**None. All 13 Medium findings are closed.**

Recorded here rather than deleted, because the table of what was closed and what each fix left behind is the part a reader needs. Full detail, including the reasoning behind each design choice, is in §12.

| ID | STRIDE / LINDDUN | What was wrong | What closed it | Residual |
| --- | --- | --- | --- | --- |
| TM-15 | R | Raw `username` logged with no validation, and `RegistrationRequest` had `@Size` but no `@Pattern` — so CRLF reached the log and the audit trail was writable by its own subject. | `@Pattern` allow-list on the username; `LogSafe` neutralises control characters and caps length at every call site; ECS-structured JSON in `prod`, where the encoder escapes newlines inside values. | Dev still logs plain text. |
| TM-17 | D | The only guard was "not yourself", and a disabled `ADMIN` row satisfied the bootstrap check — so a system with no usable administrator looked bootstrapped. | `LastAdminGuard` refuses any mutation leaving zero **enabled** admins; `AdminBootstrapRunner` seeds on `existsByRoleAndEnabledTrue` and revives a disabled admin row. | Two admins removing each other concurrently both read a count of two. Needs a database constraint. |
| TM-18 | T | A new reset token invalidated nothing, so N requests meant N live paths into an account; spent rows were never removed. | Issuing retires every outstanding token; `ExpiredTokenPurge` sweeps 7 days past expiry or use. | — |
| TM-19 | T | The only datasource was dev H2 — `sa`, blank password, `ddl-auto: update`, no migrations. The app could not start outside dev. | `prod` profile: external datasource, environment-supplied credentials with no defaults, `ddl-auto: validate`, Flyway-owned schema. | The migration has never run against a real PostgreSQL. No such instance exists here. |
| TM-27 | R | Every required audit event was logged, as ad-hoc key=value text with no correlation id, no client IP and no retention. | ECS-structured JSON, per-request correlation id and resolved client address, 90-day bounded rotation; the audit table is now the authoritative record. | Structured output is `prod` only. |
| TM-30 | LINDDUN (Unawareness) | An email address collected and stored indefinitely with no notice, purpose, retention or recorded basis. | Notice as configuration (`app.privacy`), served from `GET /api/privacy-notice`, rendered at the point of collection; basis in ADR 0005. | Periods are drafted against general expectations, not a declared regime. |
| TM-31 | LINDDUN (Non-compliance) | No retention policy and no self-service erasure, export or rectification — deletion was admin-only. | Periods in `app.retention` and in the notice; the token sweep enforces its own; `GET /api/account/export` and `DELETE /api/account` (password re-entered). | Account retention is "until erased". Automated dormant-account deletion deliberately not implemented — the weakest part of the position. |
| TM-34 | LINDDUN (Disclosure) | One `GET /api/admin/users` returned every registered email address to any admin, with no minimisation, purpose limitation or read logging. | Listing returns `maskedEmail`; the full value needs `GET /api/admin/users/{id}/email` with a mandatory purpose, a separate authority, and an audit row. | The mask keeps the domain, which on a small domain still narrows an account. |
| TM-35 | Linkability | `users` ties username to email, and the logs tied the same username to sign-in times and failure patterns, with no retention bound. | Logs and audit rows carry a keyed HMAC pseudonym; retention bounded at 90 days. ADR 0006. | The `users` table linkage is inherent to the product. |
| TM-36 | LINDDUN (Disclosure) | Usernames across the audit lines, stdout only, no retention, access control or shipping policy. | Pseudonymous references instead of usernames; bounded rotation with size caps. | Access control on the log destination is a deployment grant. The pseudonyms are what make a wider-than-intended grant less damaging. |
| TM-37 | Non-repudiation | Authentication events kept per username forever — accountability outliving its security purpose. | Bounded by the 90-day retention, and pseudonymous. | — |
| TM-38 | T | CSRF, cookie attributes, CORS rejection and session rotation were all configured and none was asserted. | Four new test classes, one per property. | MockMvc cannot observe the CSRF cookie; asserted over a live container instead. |
| TM-39 | R | Destructive admin actions recorded only as an unstructured INFO line written outside the transaction — no client IP, no correlation id, no immutable store, no step-up. | Append-only `AuditEvent` row in the same transaction (`Propagation.MANDATORY`); step-up password on delete. | Append-only is code-level only; the database grant is a deployment step (ADR 0007). |

---

## 6. Open findings — Low

| ID | STRIDE / LINDDUN | Finding | Evidence |
| --- | --- | --- | --- |
| TM-13 | T | **Downgraded from Medium.** The API now sends CSP, `Referrer-Policy: no-referrer`, `Permissions-Policy` and retains `X-Frame-Options: DENY`. Residual: those govern this service's JSON responses, not the SPA — which is a different origin and needs a CSP from whatever serves it. The XSS-defeats-CSRF chain lives on the frontend origin, and there is still no XSS sink there. | `config/SecurityConfig.java` headers DSL; no CSP at the SPA host |
| TM-14 | E | **Downgraded from Medium** by the config-owned RBAC matrix: authorization is now fine-grained authorities against explicit method+path guards in YAML, terminating in `denyAll()`, so forgetting to guard a new endpoint yields a 403 rather than silent exposure. Residual: enforcement is still URL-layer only — no `@EnableMethodSecurity`, so a service method reached from a scheduled job or listener carries no authorization of its own. | `config/SecurityConfig.java`; `application.yml` `app.security` |
| TM-19b | I | **Narrowed by the TM-19 fix but still open.** Credential handling is now settled: the `prod` datasource takes username and password from unresolvable environment placeholders, so a deployment cannot boot with a default. TLS is documented rather than enforced — the parameters are engine-specific and live in the JDBC URL, so `application.yml` and ADR 0004 state the required form (`sslmode=verify-full` with a pinned root certificate for PostgreSQL, since `require` encrypts without verifying the peer) and nothing validates that a deployment used it. Still nothing on the wire in dev, where H2 is in-process. | `application.yml` (prod document); ADR 0004 |
| TM-20 | I | `new BCryptPasswordEncoder()` takes the implicit default strength 10 with no configuration hook; `PasswordPolicy` sets a 12-char minimum and no maximum, so BCrypt silently truncates past 72 bytes. | `SecurityConfig.java:49-51`; `PasswordPolicy.java:13-17` |
| TM-21 | T | `CsrfTokenRequestAttributeHandler` is wired instead of the `Xor` variant, giving up per-response token masking against BREACH. Needs response compression plus a network position to exploit. | `SecurityConfig.java:86, 99` |
| TM-22 | I | Registration answers 409 with distinct messages for a taken username vs a taken email, so accounts are enumerable. In-spec per the PRD, which scopes enumeration resistance to login and reset only. | `auth/RegistrationService.java:45-52` |
| TM-23 | I | Reset-request response bodies are identical, but the registered path does RNG + SHA-256 + a transactional write, so timing recovers the fact the uniform response hides. | `PasswordResetService.java:75-94` |
| TM-24 | I | Three package-scoped advices cover auth, admin and reset; anything else (including `/api/hello`, `/api/health`, `/api/csrf`) falls through to Boot's `/error` in a shape the SPA cannot parse. One property change from being a leak. | the three `*ExceptionHandler` classes |
| TM-25 | D | Sessions, `SessionRegistryImpl` and the throttle map are all JVM-local: a second instance breaks session continuity, "invalidate all sessions" and the throttle budget. Now recorded as an accepted constraint in `docs/adr/0003` rather than only in Javadoc — session *revocation* silently failing is the dangerous half, since it is the fix for TM-04. | `IpLoginThrottle.java:19-23`; ADR 0003 |
| TM-26 | I | `VITE_API_BASE_URL ?? "http://localhost:8080"` — a production build without the variable ships a bundle pointing at the visitor's own machine. | `frontend/src/api/client.ts:1` |
| TM-28 | T | No dependency-vulnerability scan, no SBOM, no `npm audit` step — no way to answer "are we affected" against a new CVE. Worth noting this got slightly larger: the TM-19 fix added Flyway and the PostgreSQL driver to the dependency set. | `backend/pom.xml`; `frontend/package.json` |
| TM-32 | Identifiability | Account existence is externally probeable (TM-22), so a list of email addresses reveals which of those people use the system. Accepted in `docs/adr/0002`. | `RegistrationService.java:45-52` |
| TM-33 | Detectability | Timing (TM-23) reveals the presence of a data subject's record without authenticating. | `PasswordResetService.java:75-94` |

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

- Issuing a new token retires every outstanding one, so at most one live path into an account exists at any moment (`ResetTokenSupersessionTest`). Superseded tokens are marked used rather than deleted, so an old link reports "already used" instead of "invalid".
- Spent rows are removed 7 days past expiry or use (`ExpiredTokenPurgeTest`), which also covers the case an expiry-based sweep alone would miss: a token consumed while its expiry is still in the future.

**Authorization**
- Deny-by-default: the filter chain is built from a YAML guard matrix and terminates in `denyAll()`, so an endpoint that is neither whitelisted nor explicitly guarded is unreachable by everyone, including authenticated admins (`SecurityConfigTest.unmappedPathIsDeniedByDefaultEvenWhenAuthenticated`).
- The system cannot be left without an administrator who can sign in: every mutation that removes admin capability, including self-service erasure, refuses when the target is the last **enabled** admin (`LastAdminProtectionTest`), and a restart recovers a system that reached that state some other way (`AdminBootstrapRecoveryTest`).
- Deleting an account requires the acting admin to re-enter their password, read from the database rather than the cached principal, so a session that outlived a password change cannot confirm with the old one (`StepUpAuthenticationTest`).
- Reading a user's email address is a separate authority from listing users, needs a stated purpose, and is recorded (`EmailMinimisationTest`).
- Role hierarchy resolved at authentication time, so `ROLE_ADMIN` reaches `ROLE_USER`'s fine-grained authorities without duplicating them in config (`SecurityConfigTest`, `RoleHierarchyConfigTest`, `AppUserDetailsServiceTest`).
- Role comes from the authenticated principal; the acting admin id comes from `@AuthenticationPrincipal`, never from the request body.
- Self-action guards on all three admin mutations, each test-covered.
- Disable, demote and delete all revoke the target's live sessions as of the TM-04 fix (§12), so a narrowed privilege takes effect immediately rather than at next login (`AdminSessionRevocationTest`).
- Client-side admin gating is cosmetic by design and the server is authoritative; a `USER` gets 403 on every admin mutation and 401 when unauthenticated.

**Injection**
- **SQL injection is not applicable.** Every repository method is a Spring Data derived query — no `@Query`, no native SQL, no string concatenation anywhere. This survived the TM-18 and TM-19 work deliberately: retiring outstanding tokens loops over a derived finder rather than issuing a bulk `@Query` update, and the two-part retention sweep is two derived deletes rather than one statement with an `OR`. Keeping it a structural claim rather than an audit of query strings was worth a small amount of extra code.
- No XSS sink in the frontend: no `dangerouslySetInnerHTML`, `innerHTML` or `eval`; every server value renders as a JSX text node.
- CSRF enabled for all state-changing endpoints with the cookie-plus-header pattern, and now asserted from the refusing side: a mutation without the token is rejected whether the caller is authenticated or not (`CsrfProtectionTest`).
- **Log injection is closed.** An allow-list on the username stops the value existing; `LogSafe` neutralises CR/LF and control characters at each call site and caps length; structured JSON output in `prod` escapes newlines inside values regardless (`LogSafeTest`, `UsernameValidationTest`).

**Accountability**
- Consequential actions — enable/disable, role change, deletion, email reveal, self-service export and erasure — write an append-only row to `admin_audit_log` in the same transaction as the action, carrying actor, target, client address and correlation id (`AdminAuditTrailTest`). The record survives the account it describes, which is the case that forced the design: no foreign key to `users`, because a cascade would destroy the evidence of a deletion and a restriction would block it.
- Immutability is structural, not conventional: the entity has no setters, every column is `updatable = false`, and the repository extends the bare `Repository` marker so `delete`, `deleteAll` and `deleteById` are never inherited. Both asserted.

---

## 8. Testing gaps

**245 backend tests across 44 classes, all green.** The four properties this section used to list as configured-but-unasserted are now asserted:

1. A mutating request without `X-XSRF-TOKEN` is rejected with 403 — `CsrfProtectionTest`, from the refusing side, for authenticated mutations, unauthenticated writes and logout.
2. A request bearing a disallowed `Origin` is refused — `CorsOriginRejectionTest`, at preflight, including origins that differ only by prefix or suffix.
3. `Set-Cookie` on login carries `HttpOnly` and `SameSite` — `SessionCookieAttributesTest`, over a real servlet container. `Secure` is asserted absent in dev there and present in the base profile by `ProductionProfileTest`, so the dev concession cannot silently become the default.
4. The session id changes across successful authentication — `SessionFixationTest`, which also asserts the rotated session is the one that works and that a *failed* login does not upgrade the pre-auth session.

Two things learned in the process, both worth recording because they shape what can be tested where:

- **MockMvc cannot see the CSRF cookie.** `spring-security-test`'s `csrf()` post-processor substitutes the `CsrfFilter`'s token repository for the whole shared test context, so once any test has used it no `CookieCsrfTokenRepository` cookie is written for the rest of the run. An assertion on that cookie passed in isolation and failed in the full suite — exactly the kind of order-dependent test that gets dismissed as a flake. It lives in the live-container class now.
- **MockMvc cannot see session cookie attributes at all.** They are applied by the servlet container, and MockMvc has none. A test written against MockMvc would have had to assert the configuration properties instead, which is a test that the YAML says what the YAML says.

Remaining gaps, stated rather than implied:

- **The Flyway migration is never executed.** Dev uses H2 with `ddl-auto: update` and Flyway off; the migration is written for PostgreSQL and there is no PostgreSQL here. `FlywayMigrationTest` derives the expected tables and columns from the entity classes by reflection and asserts the SQL names each one — which catches adding a field and forgetting the migration — and makes no claim about types, constraints or dialect validity.
- **The concurrent last-admin race is untested** because it is unfixed. See §5.
- **The frontend still has no test framework** (`package.json` scripts are dev/build/preview/typecheck only). The new UI surface — the reveal-purpose and confirm-password challenges, the export and erasure controls — is covered only by `tsc -b` and by the backend tests behind it.

---

## 9. PRD security-requirement conformance

Against the non-functional requirements in [`prd/assessment-prd.md:112-123`](../../prd/assessment-prd.md):

| Requirement | Status | Note |
| --- | --- | --- |
| Password storage — BCrypt, no custom hashing | **Met** | Strength implicit (TM-20) |
| Session security — HttpOnly / Secure / SameSite, fixation protection, invalidation on logout and reset | **Met** | Explicit 30m idle timeout plus an 8h absolute lifetime cap; revocation also covers admin disable/demote/delete |
| CSRF enabled for all state-changing endpoints | **Met** | Now asserted from the refusing side (`CsrfProtectionTest`); no BREACH masking (TM-21) |
| CORS — explicit allow-list with credentials | **Met** | Validated at startup, and runtime rejection now asserted (`CorsOriginRejectionTest`) |
| Enumeration resistance on login and password reset | **Met** | Timing side-channel (TM-23); registration is out of the requirement's scope but enumerable (TM-22) |
| Transport — HTTPS behind any real deployment | **Met** | `requiresSecure` + HSTS, gated on `app.security.require-https` (false only in dev). `forward-headers-strategy` left off by default and documented, since enabling it on a directly-reachable app allows header forgery |
| Audit logging — structured lines for all named events, never passwords | **Met** | ECS-structured JSON in `prod` with a per-request correlation id and client address; consequential actions additionally written to an append-only table in the same transaction. Accounts identified by pseudonym rather than username. Dev remains plain text |
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
| ~~6~~ | ~~TM-38 + the remaining three testing gaps~~ | **Done** (§12). Locked in the controls that were correct but unasserted. | Samuel Wong | 2026-09-24 |
| ~~7~~ | ~~TM-15, TM-17, TM-18, TM-19, TM-27, TM-39~~ | **Done** (§12). Log integrity, last-admin protection, reset-token supersession and retention, a real datasource with migrations, and an append-only audit trail. TM-14 reassessed and left as Low: it is a method-level backstop, which is genuinely opportunistic once authorization is deny-by-default. | Samuel Wong | 2026-09-24 |
| ~~8~~ | ~~TM-30, TM-31, TM-34, TM-35, TM-36, TM-37~~ | **Done** (§12). Notice at the point of collection, retention periods as configuration, data minimisation in the admin listing, pseudonymous identifiers in logs, and self-service access and erasure. The compliance-regime decision this was waiting on is still outstanding — the work was scoped against general expectations and ADR 0005 records that distinction rather than papering over it. | Samuel Wong | 2026-09-24 |
| 9 | The concurrent last-admin race (§5, TM-17 residual) | The one residual that is a defect rather than absent work. Needs a database-enforced constraint. **Next up.** | _TBD_ | _TBD_ |
| 10 | Exercise the migration against a real PostgreSQL; make the audit-table grant | Both are TM-19 and TM-39 residuals that this repository cannot close because it has no deployment. They gate believing either fix. | _TBD_ | _TBD_ |
| 11 | Confirm the compliance regime, then re-scope the privacy position | ADR 0005's periods are drafted, not reviewed. | _TBD_ | _TBD_ |
| 12 | Remaining 13 Low findings | Opportunistic. TM-28 (dependency scanning) is the one with the best return, and it grew slightly: the TM-19 fix added Flyway and the PostgreSQL driver. | _TBD_ | _TBD_ |

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

**ADRs now exist.** `CONTEXT.md` and `docs/adr/` were written as part of the Medium remediation, so the accepted risks and the load-bearing decisions no longer live only in this document and in class Javadoc:

| ADR | Decision |
| --- | --- |
| [0001](../adr/0001-plain-http-in-local-development.md) | Plain HTTP in local development, and why `forward-headers-strategy` has no safe default |
| [0002](../adr/0002-registration-reveals-whether-an-account-exists.md) | Registration reveals whether an account exists (TM-22, TM-32) |
| [0003](../adr/0003-single-instance-session-and-throttle-state.md) | Session, revocation and throttle state are single-instance (TM-25) |
| [0004](../adr/0004-h2-in-development-postgresql-in-production.md) | H2 in dev, PostgreSQL in production, Flyway owns the schema (TM-19) |
| [0005](../adr/0005-personal-data-lawful-basis-and-retention.md) | Lawful basis and retention for the personal data held (TM-30, TM-31) |
| [0006](../adr/0006-pseudonymous-identifiers-in-logs.md) | Logs identify accounts by a keyed pseudonym (TM-35, TM-36) |
| [0007](../adr/0007-append-only-admin-audit-log.md) | Irreversible actions recorded in an append-only table (TM-39) |

`CONTEXT.md` carries the glossary. Two terms in it are worth knowing before reading this model: **enabled admin** (role `ADMIN` *and* `enabled` — counting `ADMIN` rows instead was the bug behind TM-17, in two separate places), and **user reference** (the keyed pseudonym in logs, distinct from the account UUID that appears in URLs).

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

### 2026-09-24 (final) — the Medium tier closed

Steps 6, 7 and 8 of §10 in one pass: TM-38, TM-15, TM-17, TM-18, TM-19, TM-27, TM-30, TM-31, TM-34, TM-35, TM-36, TM-37, TM-39. Thirteen findings, which is every remaining Medium plus three Lows that the same work closed as a side effect.

**Verification.** 245 tests across 44 classes, all green, `mvn test` exit 0; frontend `tsc -b` and `vite build` clean. Baseline was 120 tests across 26 classes.

#### TM-38 — the four unasserted controls

Four new classes, one property each: `CsrfProtectionTest`, `CorsOriginRejectionTest`, `SessionCookieAttributesTest`, `SessionFixationTest`. Each asserts from the refusing side, since every existing test obtained a CSRF token through `.with(csrf())` and would therefore have kept passing if the protection had been removed.

Two findings about the tooling rather than the application, both of which changed where assertions had to live:

`SessionCookieAttributesTest` pays for a second application context on a real port, because MockMvc has no servlet container and therefore never emits `Set-Cookie` for a session. A MockMvc version would have had to assert the configuration properties instead — a test that the YAML says what the YAML says, passing equally happily if the container ignored it.

More interesting: an assertion that the CSRF cookie is JavaScript-readable **passed in isolation and failed in the full suite**. `spring-security-test`'s `csrf()` post-processor substitutes the `CsrfFilter`'s token repository for the whole shared context, so once any test has used it, no `CookieCsrfTokenRepository` cookie is written for the rest of the run. It reaches the token's own `headerName` too. Worth recording because the first instinct on a failure like that is to retry it and call it flaky; the assertion moved to the live-container class, where nothing is substituted.

#### TM-15, TM-35, TM-36, TM-37 — what appears in a log line

Three problems that turned out to share one answer.

**Forging.** A username could contain newlines, so the audit trail was writable by the person it was meant to hold accountable: register as `alice\nLogin succeeded username=admin` and every line about that account emits a fabricated one underneath it. Fixed at the edge with a `@Pattern` allow-list, which stops the value existing, *and* at each call site with `LogSafe`, because login logs an unvalidated username by necessity — an unknown username is a normal thing to log, and validating it there would reveal which format the system accepts.

`LogSafe` replaces control characters rather than deleting them. Deleting would normalise `ad\nmin` and `admin` to the same logged value, making an attacker's input indistinguishable from a real account's in the very record meant to tell them apart.

**Linkability.** The lines named the account directly, and `users` ties every username to an email address — so the log was a second, unbounded copy of a personal-data linkage, held in the least-governed store in most deployments. Accounts are now identified by `userRef`, a keyed HMAC of the lower-cased username.

Three options were weighed (ADR 0006). Dropping the identifier is the strongest privacy answer and the wrong security one: "five failed logins" is not actionable without knowing whether it was five attempts on one account or one on five. Logging the account UUID is stable but is also the key used in admin URLs, so it is not really a pseudonym. A plain digest would be reversed by hashing a wordlist, making the protection decorative — the key is what makes it one-way in practice.

The default is a **random key per process**, and that is deliberate rather than lazy: a fixed fallback committed to a public repository would make every pseudonym in every deployment reversible by anyone who can read the source, which is worse than logging the username plainly because it would look protected. The cost — references do not survive a restart — is logged at startup and stated in the ADR.

**Structure and retention.** `prod` emits ECS JSON via Boot's native structured logging, with a correlation id and resolved client address from `LoggingContextFilter`, and 90-day bounded rotation. No new dependency was needed, and no `logback-spring.xml`. The correlation id is returned as `X-Request-Id` so a user reporting "it said access denied" can quote it; an inbound one is ignored, since honouring it would let a caller stamp their requests with somebody else's id.

Structured output also makes the forging defences belt-and-braces: in JSON the encoder escapes a newline inside a value, so a forged record cannot be produced by content alone. The validation stays, because something has to hold when a plaintext appender is configured — which dev still is.

#### TM-17 — the last administrator, and a correction to the finding

The finding said two admins can eliminate each other down to one, and that a disabled `ADMIN` row satisfied the bootstrap check. Both true. Working through it to write the tests turned up something the finding implied but did not say, and it is worth correcting rather than quietly fixing:

**the admin API cannot sequentially reach zero.** Authorization requires the actor to be an enabled admin, and the self-check requires the actor not to be the target. So whenever a target is the last enabled admin, the actor would have to be a *second* enabled admin — which contradicts the premise. Two admins genuinely can grind each other down to one, and one is where the sequential path stops.

That does not make `LastAdminGuard` redundant, for three reasons:

1. **Self-service erasure reaches zero directly**, and the self-check offers nothing there because targeting yourself is the entire point of the endpoint. That right was added in this same change (TM-31), so shipping it without the guard would have *introduced* the hole the finding warned about.
2. **Concurrency.** Two admins removing each other at the same instant both read a count of two. The guard narrows the window to the gap between the count and the commit; closing it needs a database-enforced constraint. Recorded as residual and promoted to step 9 of §10 — it is now the only open item that is a defect rather than absent work.
3. A guard that exists and is named is one a future mutation path can be checked against.

Because of the reachability argument, `LastAdminProtectionTest` drives the service rather than the API: the HTTP layer cannot construct "actor authorized, target is the last enabled admin". The branch is still worth covering, since the argument depends on authorization and the self-check continuing to hold.

The counting detail is the load-bearing one, and it is the same mistake in two places: **enabled** admins, not `ADMIN` rows. A disabled admin cannot authenticate, so it cannot re-enable itself or anyone else; counting it would permit removing the last working admin on the strength of a dormant account, and it was exactly why `existsByRole` let the bootstrap runner skip a system with no usable administrator.

Relaxing the bootstrap condition created a new case: seeding can now run while the configured username is already taken. That row is revived — enabled, password reset, lockout state cleared — rather than inserted, since the username is unique. Reviving is confined to rows already holding `ADMIN`: if the configured username belongs to a regular account, startup logs an error and seeds nothing. Promoting it would be admin rights granted by one environment variable, bypassing `RoleMutationGuard` and leaving no audit record — worse than refusing to recover.

#### TM-18 — one live path into an account

Each reset request added a token and invalidated nothing, so N requests meant N concurrent 30-minute windows. That matters most in the situation the reset flow is used in anger: someone who suspects their mailbox has been read clicks "forgot password" again, reasonably believing the earlier link is void. It was not — every link issued in the last half hour still granted a takeover, including whichever one the attacker held. So this was less about token hygiene than about the flow behaving the way the person using it believes it behaves.

Superseded tokens are marked **used**, not deleted, so following an old link answers "this reset token has already been used" — true, and an explanation — rather than "invalid token", which reads like a malfunction.

`ExpiredTokenPurge` removes spent rows 7 days past expiry or use. The grace period is the interesting part: purging on expiry would destroy the answer to "was a reset requested on this account last week, and was the link followed" at exactly the moment an account turns out to have been compromised. Two derived deletes rather than one statement with an `OR`, because "expired" and "used" are independent — a token consumed five minutes after issue is spent while its expiry is still in the future, and an expiry-based sweep alone would never touch it.

One implementation correction worth noting: `ExpiredTokenPurge` was initially `@ConditionalOnProperty` on the same switch that gates the scheduler, so disabling the timer in tests also removed the bean under test. The component is now registered unconditionally and only `SchedulingConfig` is gated — which is the better shape anyway, since the behaviour worth asserting is which rows are selected, not whether Spring's scheduler fires.

#### TM-19 — a production configuration that exists

A `prod` profile with an external datasource, environment-supplied credentials, bounded connection pool, `ddl-auto: validate`, and Flyway-owned schema in `db/migration/`. Flyway, the PostgreSQL engine module and the driver are new dependencies.

Nothing in the profile has a usable default. Every value is an unresolvable placeholder, so a deployment that forgets one fails to start — the same fail-fast rule the admin credentials already followed, applied to the datasource. The failure mode being avoided is specific: a defaulted URL could fall back to an in-memory database that accepts writes and loses them, which is the case where everything looks healthy and nothing is persisted.

`validate` rather than `update` is the substantive half. `update` lets the running application alter the schema — an ambient write privilege over the data model, and a silent divergence between environments, since the schema becomes whatever the entity classes happened to say on the day each instance started.

Flyway stays **off in dev**, and that asymmetry is deliberate. The dev database is created from the entity model on every boot and discarded; there is no history to migrate, and the migrations use partial indexes, functional unique indexes and `timestamp with time zone`. Running them against H2 would mean either constraining them to the intersection of two dialects or maintaining two sets.

The cost is real and is the main residual: **dev cannot reveal a broken migration.** `FlywayMigrationTest` mitigates rather than closes it — reflection over the `@Entity` classes, asserting the SQL names every table and column, which catches adding a field and forgetting the migration. It makes no claim about types or dialect validity. `ProductionProfileTest` parses `application.yml` as data and pins the profile's shape, including that no credential is committed and that dev is the only place the session cookie is not `Secure`. Booting the profile was considered and rejected: supplying an H2 datasource to make the context start would test a configuration nobody deploys.

Two non-dev test classes needed `spring.flyway.enabled=false` added, since they run on H2 and inherit the base profile's "migrations on" default. That default is correct — a deployment must not run without migrations — so the opt-out belongs at the test.

#### TM-34 — email out of the bulk listing

One `GET /api/admin/users` returned every registered address: the entire personal-data holding of the system, to any admin, as the default payload of the screen they open to change somebody's role. The PRD says the address exists so a password reset can be delivered.

Dropping the field was the first option and was rejected. The screen has one genuine use for it — telling two similarly-named accounts apart before acting irreversibly on the wrong one — and removing the only disambiguating field from a screen whose actions cannot be undone trades a privacy problem for a safety one. So the listing carries `maskedEmail` (`s****@example.com`, a fixed-width mask so the local part's length does not leak) and the real address needs `GET /api/admin/users/{id}/email` with a mandatory `purpose`, a separate `ADMIN_USER_EMAIL_READ` authority, and an audit row.

The authority split matters more than it looks: listing accounts and reading somebody's address are different acts with different sensitivity, and keeping them separate is what would let a future support or read-only role hold the listing without the personal data behind it. Both land on `ADMIN` today; the seam is the point.

The purpose is required, not optional. An optional justification is one that is never supplied, and the audit row would then answer "somebody looked" without the part worth keeping — a bulk harvest through this endpoint now shows up afterwards as a run of lookups with identical or thin reasons.

Residual, stated plainly: the mask keeps the domain. On a large public host that reveals almost nothing; on a small or single-tenant domain it narrows an account to one organisation. Masking the domain too would leave the field with no disambiguating value, which is equivalent to removing it.

#### TM-39 — a record that shares the mutation's fate

Destructive actions left one unstructured INFO line, written **outside** the transaction. The mutation could commit and the line be lost, or the line be written and the transaction roll back, with no way afterwards to tell which. A record that can disagree with the thing it describes is not evidence.

`AuditEvent` rows are now written in the same transaction, and `AuditService.record` is `Propagation.MANDATORY` — it does not start a transaction, it refuses to run outside one. A future caller who audits from a non-transactional context fails loudly at that call rather than quietly writing a record that commits independently, which is the failure mode that would reintroduce the original problem while looking like a fix.

Append-only is structural at three levels: no setters and `updatable = false` on the entity; `AuditEventRepository extends Repository`, the bare marker, so `delete`/`deleteAll`/`deleteById` are never inherited from `JpaRepository`; and no foreign key to `users`. The last is the design decision the deletion case forced — a foreign key would have to either cascade the record away with the account, destroying the evidence, or block the deletion. Asserted by `theAuditRowSurvivesTheAccountItDescribes` and, structurally, by a test that the repository exposes no `delete*` method.

The fourth level is **not done and cannot be done here**: restricting `UPDATE`/`DELETE` against the application's own database role needs a grant the migration cannot make, because the migration runs as the owner. The required statements are in the migration comment and ADR 0007. Until then, anyone with the application's credentials can rewrite history.

Step-up re-authentication on delete: the acting admin re-enters their password in `X-Confirm-Password`. A session proves somebody authenticated within the last eight hours, not that this request came from them — an unlocked laptop or a lifted cookie produces a valid session held by the wrong person. Scoped to the one irreversible action on purpose; requiring it everywhere would make the prompt routine, and a routine password prompt is one people type into anything that shows it.

Three details worth recording. The hash is re-read from the database rather than taken from the cached principal, so a session that outlived a password change cannot confirm with the old one. A missing header and a wrong password produce the identical 403, so the response cannot reveal whether confirmation is expected. And it is 403 rather than 401 — a 401 would tell the browser the session was gone and send the SPA back to the login screen, discarding the admin's context over a missing confirmation on one action.

Soft-delete was considered, per the finding's suggestion, and not adopted: the safety it would provide comes instead from the re-proved password, and it would have conflicted with the erasure right added under TM-31.

#### TM-30, TM-31 — a privacy position

The notice lives in configuration (`app.privacy`), is served from `GET /api/privacy-notice` unauthenticated, and is rendered on the registration form above the submit button. Each of those is a decision:

Configuration rather than frontend copy, because copy in a component cannot be configured per deployment, cannot be asserted on, and drifts out of step with what the code does. The lawful basis and the retention periods are exactly the parts that differ by jurisdiction and operator. Two tests hold the notice to the code: `PrivacyNoticeTest` asserts it names a purpose for every item collected, a period for every category, and only rights that have endpoints behind them; `ProductionProfileTest` asserts the 90-day log retention the notice claims matches the configured rotation. A mismatch there would be a false statement to a data subject, and the notice is the half people read.

Unauthenticated, because a notice that requires an account is not a notice at the point of collection — the person deciding whether to hand over an address has not registered yet, so gating it means the only people who can read it are those who no longer need to.

Above the submit button and collapsed behind a `<details>`, because a wall of text above a three-field form is text nobody reads, and a notice nobody reads is the same failure in a different shape.

Rights: `GET /api/account/export` and `DELETE /api/account`, both acting on the authenticated principal only. Neither takes an account id — there is no parameter to tamper with, so the endpoints are structurally incapable of reaching somebody else's account, where an `/api/account/{id}` shape would have needed an ownership check on every method and a forgotten check would be a bypass.

The export includes the unglamorous operational columns — failure counter, lockout time, reset-request history — because an export that omits a field makes a false claim: the reader takes it for the full picture. Two exclusions, both with reasons. The password hash is not the user's data in any useful sense; it is a credential verifier, and handing over a BCrypt hash gives an offline cracking target while telling its owner nothing they do not know. Audit records are excluded because they are about actions taken by administrators, retained for accountability under a different basis, and handing them over on request would make the accountability record extractable by the accountable party.

Erasure is a real delete, not a flag. A soft delete is the safer choice for the *admin* path and is the thing the right exists to prevent here — "erasure" that leaves the data in place with a boolean beside it. The safety comes from the re-proved password instead.

Residual, and the weakest part of the position: **account retention is "until erased", which is not a period.** Automated deletion of dormant accounts has a real cost — deleting somebody's account because they were away — and is deliberately not implemented. ADR 0005 says so, and says that the periods are drafted against general PDPA/GDPR-style expectations rather than a declared regime, because populated fields are easily mistaken for decisions somebody was accountable for. Nobody has signed these off.

#### Supporting work

`CONTEXT.md` and seven ADRs (`docs/adr/0001`–`0007`) now hold the accepted risks and load-bearing decisions that previously lived only in this document and in class Javadoc — the gap §11 flagged. The README gained an environment-variable table for deploying outside dev, including the two things the application cannot do for itself: the audit-table grant, and not running more than one instance.

| | Session start | Entry 1 | Entry 2 | Entry 3 | Entry 4 | Now |
| --- | --- | --- | --- | --- | --- | --- |
| Open High | 8 | 4 | 2 | 0 | 0 | **0** |
| Open Medium | 18 | 20 | 19 | 16 | 10 | **0** |
| Open Low | 14 | 14 | 15 | 16 | 16 | **13** |
| Open total | 40 | 38 | 36 | 32 | 26 | **13** |
| Mitigated | 20 | 22 | 24 | 28 | 34 | **47** |
| Tests | 34 (1 failing) | 41 | 66 | 87 | 120 | **245** |

Next is step 9 in §10: the concurrent last-admin race, which is the only remaining open item that is a defect in the code rather than deployment work this repository cannot perform.
