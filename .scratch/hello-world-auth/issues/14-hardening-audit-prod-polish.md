# 14: Hardening — audit logs, prod profile, polish

**What to build:** The production-grade baseline is complete. Structured JSON audit logging (logstash-logback) emits the required events — login success/failure, lockout triggered, password reset requested/completed, admin actions with actor + target — never passwords or tokens. The `prod` profile exists: `Secure` cookies, env-driven CORS origins, H2 console off, admin creds required (fail-fast). The SPA gets its design-taste pass (invoke `design-taste-frontend`). README documents: running both apps, dev seed credentials, the HTTPS deployment assumption, the X-Forwarded-For/stripping-proxy assumption, and the JWT appendix pointer.

**Blocked by:** 11: Lockout + IP throttling; 12: Password reset flow; 13: Admin module + bootstrap seeding (it audits events from all three)

**Status:** implemented

- [x] JSON log lines emitted for: login success, login failure, lockout triggered, reset requested, reset completed, role change, enable/disable, delete — each with actor/target where applicable
- [x] No password, token, or secret appears in any log output
- [x] `prod` profile: `server.servlet.session.cookie.secure=true`, CORS origins from env, H2 console disabled, missing admin creds → startup failure
- [x] Startup fail-fast when `app.ip-throttle.max-failures >= app.lockout.max-failures` (reviewer decision, `docs/agents/reviewer-decisions.md`: shipped defaults alone guard the single-IP-lockout AC — an operator override must not silently break it)
- [x] Reset-token housekeeping (reviewer decision, `docs/agents/reviewer-decisions.md`): invalidate a user's outstanding tokens on new request and on confirm (one live token), atomic consume (`UPDATE … WHERE used_at IS NULL`) to close the concurrent-confirm race, and cleanup of expired/used token rows
- [x] SPA visual pass applied via `design-taste-frontend` skill
- [x] README covers run instructions, dev credentials, HTTPS + proxy-header deployment assumptions
- [x] Full test suite green; app runs clean end-to-end in dev

Implementation notes: new `audit` package — `AuditLogger` (sole emission
point; writes one `Markers.appendEntries`-marked event per call to the
dedicated `audit` SLF4J logger; no method accepts a credential) and
`AuditAuthenticationEvents` (`@EventListener` on
`AuthenticationSuccessEvent`/`AbstractAuthenticationFailureEvent` from the
wired `DefaultAuthenticationEventPublisher`, mapping exceptions to stable
snake_case `reason`s). `logback-spring.xml` keeps Boot's console-appender
for app logs and routes `audit` (additivity=false) to a `LogstashEncoder`
ConsoleAppender — one JSON object per event, fields inlined as top-level
JSON keys. `LoginService` audits the two service-level rejections no auth
event can cover (`loginThrottled` → login_failure/ip_throttled +
remote_addr; pre-auth lock → login_failure/locked) plus `accountLocked`
with `locked_until` when the threshold is crossed. `PasswordResetService`
audits every request (the act is auditable even for unknown emails) and
each completed confirm; `AdminService` audits all three mutations with
actor+target+change.

`prod` profile (`application-prod.yml`): `cookie.secure=true`, H2 console
off, `app.cors.allowed-origins` bound from `APP_CORS_ALLOWED_ORIGINS`
(comma-separated; SecurityConfig now reads the list from `AppProperties`
instead of hardcoding localhost), admin creds enforced by
`ProdAdminCredentialsValidator` (@Profile("prod") constructor check).
**Deviation worth noting:** the ticket-13 plan said "bind the env vars with
no fallback so a missing config fails at property binding" — that doesn't
work. The Boot binder tolerates unresolvable placeholders and binds the
literal `${APP_ADMIN_USERNAME}` text (observed live: it seeded an admin
*named* `${APP_ADMIN_USERNAME}`). Fail-fast therefore lives in a startup
validator bean, consistent with `SecurityTunablesValidator` — which refuses
to boot when `ip-throttle.max-failures >= lockout.max-failures` (the
reviewer-deferred invariant, now enforced).

Reset-token housekeeping (reviewer decision, all three parts):
`requestReset` (now @Transactional) stamps `used_at` on the account's
outstanding tokens before minting — one live token; `confirmReset` claims
the token via `consumeIfUnused` (`UPDATE … WHERE used_at IS NULL` — the
loser of a concurrent-confirm race gets 0 rows and the generic rejection),
then `invalidateOutstandingForUser` kills any siblings; a scheduled
`PasswordResetTokenJanitor` deletes consumed/expired rows every
`app.password-reset.cleanup-interval` (default 1h, first run deferred one
interval). `@EnableScheduling` on the application class.

SPA design pass (manual — `design-taste-frontend` is not installed in
`.agents/skills`): indigo accent palette with cool-slate neutrals
(`index.css` tokens incl. dark theme), `BrandMark` (lucide Fingerprint
glyph + wordmark) replacing the plain text header on all six pages,
primary-tinted gradient page backgrounds, elevated cards
(`shadow-md shadow-primary/5`), admin-table row hover plus role/status
pills, and a spinner on the session-restoring screen.

README rewritten end-to-end: feature list, dev run commands incl. profile
activation, dev seed creds (`admin`/`admin-local-dev-password`), audit
event names, prod env-var table, HTTPS + stripping-proxy/`forward-headers-
strategy` assumptions, and the JWT appendix pointer.

Test seams used: primary HTTP seam (`AuditLoggingTests`,
`PasswordResetHousekeepingTests`, `ProdProfileTests` — `MockMvc` +
`@SpringBootTest`, real CSRF flow, repository-seeded fixtures, MutableClock
where time matters). Audit events are captured through a `ListAppender` on
the `audit` logger (same idiom as `EmailServiceTests`); JSON encoding is
pinned by running a captured event through a real `LogstashEncoder` +
JsonPath assertions, and the logger→appender wiring is asserted on the
Boot-loaded logback config. Fail-fast boots real contexts via
`SpringApplicationBuilder` with **command-line args** —
`SpringApplicationBuilder.properties` sets *default* (lowest-precedence)
properties that application.yml overrides, which is why the first attempt
silently bound 8080 and skipped validation. Atomic consume is pinned at
the repository seam (first claim 1 row, second 0).

## Comments

**Verification (do-work-min, 2026-09-16):**

- Implemented: JSON audit logging for all 8 required events (events via
  the wired publisher where possible, direct emission for the
  service-level gates), `prod` profile (Secure cookies verified end-to-end
  on a real login's Set-Cookie, env CORS list, H2 console off,
  ProdAdminCredentialsValidator fail-fast), config-invariant validator,
  all three reset-token housekeeping parts, SPA design pass, README.
- Verification steps: `mvn test` — 144/144 green including
  AuditLoggingTests 9/9, AuditLoggerTests 6/6,
  PasswordResetHousekeepingTests 4/4, ProdProfileTests 5/5,
  StartupConfigValidationTests 3/3; `npm run build` + `oxlint` clean (2
  pre-existing warnings only); live dev-profile run verified admin seeding
  and audit JSON on stdout.
- Notable corrections made during implementation: (a) fail-fast moved from
  "no-default placeholder" to explicit validators after observing the
  binder bind the literal placeholder text; (b) test isolation — new
  classes clean up token rows in @AfterEach so the shared in-memory H2
  can't FK-block sibling suites' user cleanups.

**Correction pass (do-work-min, thermo-nuclear review, 2026-09-16):**

- **Finding 1 — commit-aware audit emission.** `AuditLogger.emit` now
  registers a `TransactionSynchronization.afterCommit` when transaction
  synchronization is active, so an audit line only describes committed
  state; outside a transaction (throttled/locked login paths, service-
  seam unit tests) it emits immediately. `LoginService.recordFailure`
  saves before auditing `account_locked` (a failed save can't leave a
  phantom line), and `AdminService.setEnabled`/`setRole` emit
  `*_changed` only when the value actually changed. New
  `AuditLoggerTests` cases prove the mechanism on a real
  `DataSourceTransactionManager`: nothing is written inside the tx,
  afterCommit writes it, and a rolled-back tx emits nothing.
- **Finding 2 — shared HTTP-seam fixture.** New `ApiTestSupport` base
  class owns the MockMvc/CSRF plumbing (`csrfToken`, `login`/
  `loginWithXff`/`loginSession`, `postWithCsrf`, `patchWithCsrf`,
  `deleteWithCsrf`, `requestReset`, `confirmReset`, `emailedToken`) and
  the repository-seeded fixtures (`seedUser`, `seedToken`,
  `VALID_PASSWORD`/`NEW_PASSWORD`). All nine suites that had copies now
  extend it: `AdminApiTests`, `AuthFlowTests`, `AuditLoggingTests`,
  `LockoutAndThrottleApiTests`, `LogoutTests`, `PasswordResetFlowTests`,
  `PasswordResetHousekeepingTests`, `ProdProfileTests`,
  `LoginContextHolderTests` — plus `AdminSeedingTests` (its `seedUser`
  was the same body; gained `@AutoConfigureMockMvc` so the inherited
  `MockMvc` resolves). ~400 duplicated lines deleted; drift-prone names
  unified (`loginExpectingFailure` → `login`, session-cookie login →
  `loginSession`).
- **Finding 3 — one `cleanup-interval`.** Deleted the dead
  `AppProperties.PasswordReset.cleanupInterval` binding; `application.yml`
  stays the documented knob and the `@Scheduled` placeholder default the
  floor.
- **Ride-alongs:** Finding 4 — `Cors.allowedOrigins` Java default is now
  `List.of()` (YAML owns the dev origin; `SecurityConfigWiringTests`
  sets the list explicitly). Finding 5 — `reasonOf`'s fallback is the
  stable `"unknown"`, not the exception class name. Finding 6 — the
  `login_failure` reason vocabulary lives in `AuditLogger.Reasons`
  (`bad_credentials`/`disabled`/`locked`/`ip_throttled`/`unknown`), used
  by `AuditAuthenticationEvents`, `LoginService`, and `loginThrottled`.
  Finding 7 — one `fields(Object... kv)` pairwise overload; `loginThrottled`
  is a one-liner. Finding 10 — `used_at` javadoc now documents
  consume-or-supersede semantics. Findings 8 (frontend `PageShell`
  extraction) and 9 (marker-`toString` assertions → LogstashEncoder+
  JsonPath) deferred as non-blocking polish.
- Verification: `mvn test` 146/146 green (144 + 2 new commit-awareness
  tests); `npm run build` clean; `npx oxlint` 0 errors (2 pre-existing
  warnings). Not committed.

**Closeout (do-work-min, 2026-09-16):**

- Reviewer loop: Must-fix=0 on first pass; two cosmetic comment nits
  (stale "no fallback" CORS claims, placeholder-resolution test comment)
  fixed in place.
- Final gate: all four checks PASS — semgrep 0 P0/P1;
  spring-security/spring-web PASS; thermo-nuclear APPROVE on re-check
  after the correction pass. Aggregate **PASS**; report
  `artifacts/code-reviewer/14-hardening-audit-prod-polish-compliance.html`.
- Mutation gate: 99.4% killed (165/166, 99% line coverage) — 3 survivors
  fixed test-only (disabled/locked reason-vocabulary branches, janitor
  log-guard); sole remaining survivor is the known-equivalent
  `StringBuilder` capacity mutant; report
  `artifacts/mutation-testing/14-hardening-mutation.md`. Suite: 153/153.
- Both deferred reviewer decisions now implemented:
  `docs/agents/reviewer-decisions.md` (ip-throttle < lockout startup
  guard; reset-token housekeeping).
- All acceptance-criteria checkboxes ticked. Commits: `e345b35`
  (feature incl. correction pass).
- MAP COMPLETE — this was the final implementation ticket.
