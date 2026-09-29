# Spec: Secured Hello World Auth App (React + Spring Boot)

_Label to apply on publish: `ready-for-agent`_

## Problem Statement

A user needs to prove who they are before they can reach protected application
content, and an operator needs to manage who has access — without either party
having to trust a hand-rolled, insecure login flow. Visitors need to register
and sign in; forgetful users need to recover access on their own; and
administrators need to review and control accounts. All of this must resist the
common attacks (credential brute-force, account enumeration, session replay,
CSRF) rather than demonstrate a shortcut-everything demo.

## Solution

A username/password authentication application with a React frontend (its own
origin) and a Spring Boot REST backend (its own origin), using a server-side
Session carried by a secure `HttpOnly` cookie (Spring Session). A Visitor can
register (role `USER`) and log in; a logged-in User sees a personalized
greeting and can log out; a User who forgot their password can request a reset
by Email and set a new one via a single-use Reset Token; and an Admin can list,
enable/disable, re-role, and delete other accounts (never themselves). Security
properties — BCrypt password storage, Account Lockout, IP Throttling, CSRF,
CORS with credentials, Enumeration Resistance, and audit logging — are binding
across every story.

## User Stories

1. As a Visitor, I want to register with a Username, Email, and password, so that I get a `USER` account and can access the protected app.
2. As a Visitor, I want registration rejected when my Username is already taken, so that identities stay unique.
3. As a Visitor, I want registration rejected when my Email is already taken, so that reset delivery stays unambiguous.
4. As a Visitor, I want registration rejected when my password is shorter than 12 characters, so that weak passwords never enter the system.
5. As a Visitor, I want my plaintext password never logged or stored, so that a breach of logs or DB does not expose it.
6. As a registered User, I want to log in with my Username and password, so that I get a Session and reach protected content.
7. As a User, I want a successful login to reset my failed-attempt counter to zero, so that past failures don't count against me.
8. As a User, I want a wrong password to return a generic error that doesn't reveal whether my Username exists, so that attackers can't enumerate accounts.
9. As a User, I want each failed login to increment my failed-attempt counter, so that repeated guessing can trigger lockout.
10. As a User, I want login refused while my account is locked even with the correct password, so that an in-progress attack is contained.
11. As a security-conscious operator, I want an account locked for a cooldown after N consecutive failures, so that brute-force guessing against one account is blunted.
12. As a User, I want a correct login after the cooldown to succeed and reset my counter, so that lockout is temporary, not permanent.
13. As a security-conscious operator, I want repeated failures from one IP across many Usernames to be throttled independently of any single account's lockout, so that an attacker cannot lock out a legitimate user from one source.
14. As a logged-in User, I want to log out, so that my Session is invalidated and its cookie cleared.
15. As a User, I want a session cookie replayed after logout to be rejected as unauthenticated, so that a captured cookie is useless post-logout.
16. As a logged-in User, I want `GET /api/hello` to return "Hello, <username>", so that I can confirm my authentication worked.
17. As a Visitor, I want `GET /api/hello` without a valid Session to return 401, so that protected content stays protected.
18. As a User who forgot their password, I want the reset-request endpoint to return a generic success regardless of whether my Email exists, so that account existence cannot be inferred.
19. As a User with a registered Email, I want a single-use Reset Token generated (hash stored, plaintext emailed via the stub) with a short expiry, so that I can safely recover access.
20. As a User with a valid, unexpired, unused Reset Token, I want to set a new password meeting policy, so that I regain access; and I want all my existing Sessions invalidated, so that any attacker session is killed.
21. As a User, I want an expired Reset Token rejected without changing my password, so that stale links are worthless.
22. As a User, I want an already-used Reset Token rejected on reuse, so that single-use is enforced.
23. As an Admin, I want `GET /api/admin/users` to list each user's Username, Email, role, enabled status, and created-at — never password hashes, so that I can review access without exposing secrets.
24. As a non-admin User, I want `GET /api/admin/users` to return 403, so that only Admins see the roster.
25. As an Admin, I want to enable or disable another user's account, so that I can suspend access without deleting data; and a disabled user can no longer log in.
26. As an Admin, I want the status-toggle on my own account rejected, so that I cannot disable myself.
27. As an Admin, I want to change another user's role between `USER` and `ADMIN`, so that I can grant or revoke admin privileges.
28. As an Admin, I want a role-change on my own account rejected, so that I cannot demote myself.
29. As an Admin, I want to delete another user's account, so that I can remove accounts that should no longer exist.
30. As an Admin, I want a delete on my own account rejected, so that I cannot delete myself.
31. As an operator deploying for the first time, I want an initial Admin seeded from configuration when no Admin exists, so that there's a way into the admin module without manual DB edits.
32. As an operator, I want no duplicate Admin seeded on restart when an Admin already exists, so that seeding is idempotent.
33. As a React SPA, I want to obtain a CSRF token before login and send it on state-changing requests, so that cookie-based auth is CSRF-protected across origins.
34. As an operator, I want audit log lines for login success/failure, lockout, reset requested/completed, and role/enable/disable/delete (actor + target, never secrets), so that security-relevant actions are traceable.

## Implementation Decisions

- **Architecture:** Flat-layered Spring Boot — `controller` → `service` → `repository` → `entity`, single package hierarchy. React frontend on its own origin.
- **Auth mechanism (ADR-0001):** Server-side Session via secure `HttpOnly` cookie (Spring Session). JWT documented in the PRD appendix only, not built.
- **Identity:** `Username` is the sole login identifier; `Email` is reset-only and Admin-visible. Email is never accepted as a login credential.
- **Password storage:** BCrypt (`BCryptPasswordEncoder`). Policy: length ≥ 12, max 128, no composition rules, no breached-password check.
- **Auth failure ordering:** IP Throttle → Account Lockout → Credential Check. All failure modes return the same generic error body to preserve Enumeration Resistance; HTTP status may differ (e.g. 429 for throttling).
- **Account Lockout:** N consecutive failures within a window sets `locked_until` for a cooldown; correct credentials rejected while locked; success after cooldown resets the counter.
- **IP Throttling:** In-memory, per application instance (documented single-instance limitation), independent of Account Lockout.
- **Reset Token (ADR-0003):** 256-bit (32-byte) cryptographically-random, Base64URL-encoded; stored as SHA-256 hash only; 30-minute expiry; single-use; issuing a new token invalidates any existing unexpired token for that user.
- **Session revocation on reset (ADR-0002):** Successful reset revokes all of the user's Sessions via Spring Session's principal-name-indexed repository.
- **Admin Self-Action Guard:** Enforced by authenticated principal id == target account id (id-based). Literal self-check only; "at least one Admin must remain" is out of scope.
- **CSRF/CORS:** `CookieCsrfTokenRepository.withHttpOnlyFalse()`; an unauthenticated way to obtain the first token; CORS allow-list of the frontend origin with `Access-Control-Allow-Credentials: true` and the CSRF header permitted.
- **Session cookie attributes:** `HttpOnly`, `Secure` (prod), `SameSite`; session-fixation protection; invalidation on logout and reset.
- **Email delivery:** `EmailService` is a stub that logs the reset link instead of sending mail.
- **Admin bootstrap:** Seed one `ADMIN` from configuration (`app.admin.username`, `app.admin.password`) only when no Admin exists; password hashed identically to any account; idempotent on restart.
- **Persistence:** Spring Data JPA over H2 (dev profile); schema portable to Postgres/MySQL. Entities: `users`, `password_reset_tokens` per the PRD data model.
- **Time source:** A single injected `Clock` (or equivalent) drives lockout cooldown and token expiry, so time-dependent behavior is deterministic in tests.
- **Least privilege:** Role checks enforced server-side via Spring Security; never trusted from client-supplied state.

## Testing Decisions

- **What makes a good test:** Assert external, observable behavior at the HTTP boundary — status codes, response bodies, cookies, and persisted state — not internal implementation details. Failure-mode tests assert the *generic* error body (no username-existence signal).
- **Primary seam (one):** The HTTP API boundary, exercised with the real Spring Security filter chain, real service/repository, over H2 (`@SpringBootTest` + `MockMvc`, or full web-environment tests). This single seam covers auth ordering, CSRF, Enumeration Resistance, lockout, session lifecycle, admin guards, and role enforcement.
- **Second seam (time):** An injected `Clock` lets tests advance time to cover lockout-cooldown expiry and Reset Token expiry deterministically — no `Thread.sleep`.
- **Modules tested (mapped to PRD required coverage):** Login (success, wrong password, unknown username → identical generic error, account locked); Lockout (N failures locks; success after cooldown resets; IP throttling engages independently); Logout (reused cookie rejected post-logout); Password reset (single-use, expiry, all-session invalidation); Admin self-action guard (cannot disable/delete/demote self); Role enforcement (`USER` → any `/api/admin/**` → 403).
- **Prior art:** None yet — greenfield repo. These integration tests establish the pattern; architecture tests already planned in `artifacts/arch-test-plan.md` (ArchUnit) complement them at the structural level.

## Out of Scope

- JWT implementation (design documented in the PRD appendix only).
- MFA / 2FA.
- Real SMTP / email delivery (stubbed `EmailService`).
- Containerization / CI-CD / hosting infra.
- Local HTTPS setup (documented deployment assumption; local dev over HTTP).
- Granular per-resource authorization beyond the `USER`/`ADMIN` check on admin endpoints.
- Breached-password checks and password composition rules beyond minimum length.
- The "at least one Admin must remain" invariant (only the literal self-action guard is in scope).
- Shared/distributed store for IP throttling (in-memory per-instance only).

## Further Notes

- Domain vocabulary is defined in `CONTEXT.md`; this spec uses those terms
  (Visitor, User, Admin, Username, Email, Session, Account Lockout, IP
  Throttling, Reset Token, Enumeration Resistance, Auth Failure Ordering, Admin
  Self-Action Guard).
- Decisions with lasting consequences are recorded as ADRs: 0001
  (session-cookie over JWT), 0002 (revoke all sessions on reset), 0003
  (SHA-256 for reset tokens).
- Architecture enforcement is planned in `artifacts/arch-test-plan.md`
  (flat-layered Spring Boot rules + frontend cycle check).
