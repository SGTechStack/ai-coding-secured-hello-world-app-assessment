# Context — Hello World Auth App

A secured username/password login application: React SPA plus Spring Boot API,
session-cookie authentication, built to the PRD in
[`prd/assessment-prd.md`](prd/assessment-prd.md).

Single context. Decisions live in [`docs/adr/`](docs/adr/); the threat model is
[`docs/threat-model/hello-world-auth-app-threat-model.md`](docs/threat-model/hello-world-auth-app-threat-model.md).

## Glossary

Terms the code uses deliberately. Where a synonym is listed as *avoid*, it is
because the two were confused at some point and the distinction turned out to
matter.

**Account** — a row in `users`. Holds a username, an email address, a BCrypt
password hash, a role, an enabled flag and sign-in activity.

**Principal** — the authenticated identity attached to a request
(`UserPrincipal`). Derived from an account at login and then *cached in the
session*, which is why narrowing an account's capability has to revoke its
sessions rather than only writing the row. Avoid using "user" for both.

**Role** — `USER` or `ADMIN`. Coarse, and never checked directly in the filter
chain.

**Authority** — a fine-grained permission (`HELLO_READ`, `ADMIN_USER_READ`,
`ADMIN_USER_EMAIL_READ`, `ADMIN_USER_WRITE`, `ACCOUNT_SELF_MANAGE`). Roles map to
authorities in `app.security.role-mappings`; endpoints are guarded by authorities,
not roles. The distinction is load-bearing: it is what lets a future read-only
role hold the user listing without the personal data behind it.

**Guard matrix** — `app.security.url-guards`: authority to HTTP method plus path.
The single source of truth for who can call what. The chain ends in `denyAll()`,
so an endpoint that is neither whitelisted nor guarded is unreachable by everyone.

**Enabled admin** — an account with role `ADMIN` *and* `enabled = true`. The only
kind that can authenticate, and therefore the only kind that counts when asking
whether an administrator exists. Counting `ADMIN` rows instead was a real bug in
two places (`LastAdminGuard`, `AdminBootstrapRunner`).

**Lockout** — per-account, after 5 failures inside a 15-minute window. Rejects
even the correct password until the cooldown lapses.

**Throttle** — per-IP, counting *failed* logins. Separate counter from lockout, so
it engages independently of any one account's state.

**Rate limit** — per-IP, counting *requests* on unauthenticated write endpoints
(registration, password reset). Distinct from the throttle because those endpoints
have no notion of failure: they succeed from the caller's point of view every time,
and what needs defending is the work each call performs anyway.

**Step-up** — re-proving the password behind an existing session, immediately
before an irreversible action (`StepUpAuthenticator`). A session proves somebody
authenticated hours ago, not that this request came from them.

**User reference** (`userRef`) — the keyed pseudonym that appears in logs and audit
rows in place of a username. See ADR 0006. Avoid "user id" for this: the account's
UUID is a different thing and appears in URLs.

**Audit event** — an append-only row in `admin_audit_log` recording an
irreversible, privilege-changing or personal-data-revealing action, written in the
same transaction as the action. See ADR 0007. Avoid calling the log lines "the
audit trail": the table is the record, the lines are a mirror of it.

**Masked email** — `s****@example.com`. What the admin listing returns. The full
address requires a separate, purpose-stated, audited lookup.

## Shape

```
backend/src/main/java/com/sgtechstack/helloworldauthapp/
├── account/        self-service export and erasure (data-subject rights)
├── admin/          admin user management, and the guards around it
├── audit/          append-only audit trail
├── auth/           login, registration, sessions, throttling, step-up
├── config/         security filter chain, CORS, configuration-owned RBAC
├── health/         unauthenticated health endpoint
├── hello/          the protected greeting the PRD is nominally about
├── logging/        log sanitising, pseudonyms, request correlation
├── passwordreset/  token issue, confirm, retention sweep
├── privacy/        the privacy notice, as configuration
└── user/           the account entity, its repository, admin bootstrap
```

Authorization is config-owned: `SecurityConfig` builds its rules entirely from
`app.security` in `application.yml`. Changing who can call what is a YAML change.

## Things that surprise people

- **`setRole` has exactly one caller in production code**, enforced by
  `RoleMutationInvariantTest` scanning source text. Adding a second is a build
  failure, not a review comment.
- **The admin API cannot sequentially reach zero administrators** — authorization
  requires the actor to be an enabled admin and the self-check requires the actor
  not to be the target, so the two conditions contradict. Self-service erasure
  *can*, which is why `LastAdminGuard` shipped in the same change as that
  endpoint. Concurrency can too; that is unfixed and recorded.
- **Flyway is off in dev**, so migrations are never exercised locally. See ADR
  0004 and `FlywayMigrationTest`.
- **`spring-security-test`'s `csrf()` post-processor substitutes the CSRF token
  repository** for the whole shared test context, so MockMvc cannot observe the
  `XSRF-TOKEN` cookie once any test has used it. Cookie attributes are asserted
  over a real container in `SessionCookieAttributesTest`.
- **The reset token travels in the URL fragment**, not the query string, because a
  browser never transmits a fragment to any server.
