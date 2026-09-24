# Wayfinder Map: Secured Hello World Auth App

Label: `wayfinder:map`

## Destination

A decided, compliance-reviewed **plan** for the auth app described in `prd/assessment-prd.md` —
every security-load-bearing and hard-to-reverse decision resolved and written down, ready to hand
to `/to-spec` → `/to-tickets` → `/do-work`.

This map produces **no application code**. It is done when nothing is left to decide before
someone goes and builds it.

## Notes

**Domain:** React SPA (own origin) + Spring Boot REST API (own origin), cookie-based server-side
sessions, standalone username/password auth with account lockout, rate limiting, password reset,
and admin user management. Source of truth for scope: `prd/assessment-prd.md`.

**Governing standards (binding, read before deciding anything):**

- `App-Standards/Appfw-User-Standards/User_Standalone/Standalone_User_Access_Control_Application_Standard.md`
  — governs almost every decision on this map.
- `App-Standards/Appfw-Logging-Standards/` — audit log schema and structured logging.
- `App-Standards/Appfw-User-Standards/Shared_Recipes/` and
  `.../User_Standalone/Standalone_User_Access_Control_Recipes/` — prescribed implementation recipes.
- IM8 + ARC via the `policies` skill. **This plan is formally assessed against IM8/ARC**, so those
  controls are early design constraints, not a late review.

**Conflict rule (settled):** where the PRD and the App Standard disagree, the **standard wins on
how a control behaves**; the **PRD wins on what features exist**. Every deviation from the PRD is
recorded as an ADR naming what the PRD said and why we departed.

**Currency rule (settled):** the standard is authoritative but may be dated. Before adopting any
control from it, verify it is still current practice against primary sources (NIST SP 800-63B-4,
OWASP 2025 cheat sheets). Suspected stale items are listed in "Verify the App Standard's controls
are still current practice".

**Stack baseline (settled, no ticket):**

- Spring Boot 4.1.x on Java 21 (Temurin 21.0.9 installed), Maven 3.9.13. Java 17 is the Boot 4
  baseline; 21 is the installed LTS.
- Spring Session **JDBC** (not Redis, not plain Tomcat sessions) — required because password reset
  must invalidate all of a user's sessions, which needs principal-indexed lookup via
  `SpringSessionBackedSessionRegistry`.
- Flyway versioned SQL against **H2 only**, written vendor-neutral so it ports to Postgres/MySQL
  later. `ddl-auto: validate`. No Postgres, no Testcontainers.
- **BCrypt** password hashing (user decision; the standard permits it, the PRD mandates it).
  Work factor and BCrypt's 72-byte input truncation still to be decided.
- React 19.3 + Vite + TypeScript (strict) + shadcn/ui on Base UI + React Hook Form + Zod +
  TanStack Query. Base UI chosen for real focus management and ARIA rather than hand-rolled a11y;
  accepted cost is that vendored shadcn components do not receive upstream fixes automatically.
- **Dual rate limiting**: per-account lockout (standard) *and* per-IP throttling (PRD Story 3).
  Both, as independent limiters — they defend different attacks.
- OWASP Dependency-Check bound to the Maven `verify` phase with a CVSS failure threshold, since
  there is no CI pipeline in scope.

**Skills every session should consult:** `grilling` and `domain-modeling` by default; `research`
for the research tickets; `policies` for anything IM8/ARC; `prototype` for the frontend ticket.

**Working agreements:** claim a ticket (`Status: claimed`) before any work. Resolve with an
`## Answer` section, set `Status: resolved`, then add a one-line pointer here under Decisions so
far. One ticket per session, except research tickets which may run in parallel.

## Decisions so far

<!-- one line per resolved ticket: gist + link. Detail lives in the ticket, never here. -->

- [Extract the IM8 and ARC controls that bind this app](issues/01-im8-arc-applicable-controls.md):
  **IM8 ac-2 mandates MFA for privileged access and has no N/A branch — the PRD's MFA exclusion is a
  genuine conflict, now [ticket 19](issues/19-mfa-scope-conflict.md).** IM8 does *not* require a durable
  audit store, so log-lines-only survives. Two further unacknowledged FAILs: **as-9** (no CSP anywhere
  in the PRD) and **lm-16** (no monitoring). ARC is N/A as a product requirement; its `op` controls
  apply to our AI-assisted workflow. `im8-review` is a code audit and cannot read this plan; it also
  targets Boot 3.4 / Security 6.4 config spellings, so our 4.1 config may read as absent.
- [Verify the App Standard's controls are still current practice](issues/02-verify-standard-currency.md):
  the standard is **behind on four of seven** items. Breached-password screening is a NIST `SHALL` and
  is absent entirely; lockout-as-primary-control is superseded by throttling; `SameSite=Lax` should be
  `Strict`; composition rules are discouraged. Two defects found in the standard itself: lockout has
  **no observation window**, and locking at 5 failures makes the 10/min per-account rate limit
  unreachable dead code. **NIST requires a 15-character minimum for single-factor auth, so the PRD's 12
  is below the floor** unless MFA lands.
- [Extract the binding structured logging and audit schema](issues/03-logging-schema-extraction.md):
  `event.action` is a **closed enum** and every PRD event maps onto it, though enable/disable, role
  change, and delete all collapse onto `user-administration`, so `event.type` carries the semantics.
  **No target field exists** for the second party in an admin action — `user.target.id` is a documented
  schema extension we must declare. A custom `StructuredLogEncoder` subclass is effectively mandatory
  (the ECS formatter seals `error.*`), on a **non-public Spring extension point**. A dedicated audit
  appender is an Enforced Constraint. Unresolved contradiction in the sources over whether `source.ip`
  may be logged.
- [Inventory the prescribed recipes](issues/04-prescribed-recipe-inventory.md): **every recipe already
  targets Boot 4.x / Security 7.x** — the version risk we feared does not exist. The recipes largely
  settle the CSRF bootstrap and the RBAC config format, and confirm `ImmutableSecurityHandler` guards
  role *definitions* only, so **Story 10's admin role-change endpoint is permitted**. Two gaps found:
  the recipes assume a single origin and so **never address CSP for the SPA origin**, and the RBAC
  recipe registers guards before the whitelist under first-match-wins ordering.
- [Pin down the Spring Security 7 config surface](issues/05-spring-security-7-config-surface.md):
  Security **7.1.1**, Spring Session **4.1.1**, new `spring-boot-starter-session-jdbc`. Own the session
  DDL in Flyway with `initialize-schema=never` (the `EMBEDDED` default silently creates nothing on a
  real database and `continue-on-error=true` masks the failure). **`csrf.spa()` is built on
  `CookieCsrfTokenRepository` and is therefore prohibited for us** — the likeliest wrong turn. **No
  absolute session lifetime exists**; it must be built on an auth-instant attribute, and *not* on
  `getCreationTime()`, which `changeSessionId()` preserves from before login. `__Host-` works but
  forces a per-profile cookie name. **BCrypt is asymmetric: encoding >72 bytes throws, verification
  does not short-circuit** — that asymmetry *is* the CVE-2025-22234 fix, so validate length at
  registration and change, never on the login path.

## Not yet specified

In scope, but not yet sharp enough to ticket. Graduates as the frontier advances.

- **Response-time normalisation.** The standard requires identical response *timing* across auth
  outcomes, not just identical bodies. Partly answered by the research: `DaoAuthenticationProvider`
  already performs a dummy encode and a full `matches()` against a cached dummy hash for unknown
  users, with `hideUserNotFoundExceptions=true` by default — so the primary mitigation is the
  framework's, and our job is mostly not to defeat it (no pre-auth lockout branch that short-circuits
  on username existence, no fast-path `PasswordEncoder` wrapper). What remains unsharpened is the
  lockout and rate-limit paths, which run *before* authentication and can reintroduce a timing signal.
  Graduates once ticket 09 lands.
- **Owner notification mechanics** beyond the stub: channels, retry policy, delivery-failure
  handling. The standard requires notifying owners on password change, lockout, and reset
  completion; only the transport stub is currently decided.
- **Accessibility conformance target** and how it gets verified (keyboard, screen reader). Waits on
  the frontend architecture prototype.
- **Secrets and configuration handling for non-local environments** — admin bootstrap credentials,
  datasource credentials, the `session.hash` HMAC key if we use one, and the TOTP secret encryption key
  if [ticket 19](issues/19-mfa-scope-conflict.md) lands MFA (ac-2 requires that secret encrypted at
  rest, so the key has to live somewhere). Partly an IM8 question (**as-8**: no hardcoded secrets in any
  profile, `@Value` from environment), partly deployment, and deployment is out of scope. This patch has
  grown enough that it may graduate into its own ticket once 19 resolves.
- **Password reset token redemption rate limits** — the standard requires them but the specific
  budgets depend on the lockout and rate limiting decision.
- ~~**Authorization matrix file format.**~~ Resolved: [Inventory the prescribed recipes](issues/04-prescribed-recipe-inventory.md)
  found `Common_Role-Based_Access_Control_Configuration.md` prescribes it, so it collapses into the
  admin module ticket as inherited config rather than becoming its own.
- **HTTPS/HSTS handover notes for whoever deploys this.** Local dev is HTTP by PRD concession; what
  ops must be told is a real deliverable, shape unknown.

## Out of scope

Ruled beyond this destination. Does not graduate.

- **Application code.** This map plans; `/do-work` builds.
- **Testcontainers, Docker-based tests, and any real Postgres/MySQL runtime** — user decision;
  PRD defers portability, so H2 serves dev and test. Accepted gap, stated plainly: H2 in Postgres
  compatibility mode does not prove Postgres portability.
- **Account hygiene scheduled jobs** (90-day inactivity disablement, 180-day role revocation,
  ShedLock serialisation). The standard mandates these; we defer them with written justification as
  operational lifecycle controls orthogonal to the PRD's auth scope. The single largest deferral.
- ~~**MFA.**~~ **No longer out of scope.** The exclusion was conditional on IM8 not forcing it, and
  [Extract the IM8 and ARC controls](issues/01-im8-arc-applicable-controls.md) found that **ac-2 does
  force it for privileged access, with no N/A branch**. Now a live decision:
  [Resolve the MFA scope conflict raised by IM8 ac-2](issues/19-mfa-scope-conflict.md). The
  `Appfw-Mfa-Standards` feature-router reading still holds for the App Standard; IM8 is a separate and
  unconditional source.
- **CI/CD pipelines, containerization, hosting infra** — PRD out of scope.
- **JWT implementation** — PRD appendix only, documented alternative.
- **Local HTTPS setup** — PRD documents this as an accepted gap.
- **SSO / OAuth2 / OIDC** (`App-Standards/.../User_SSO/`) — different standard, not this app.
