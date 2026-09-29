# App-Standards take precedence over the PRD where they conflict

The PRD is the assessment's source of truth, but the organisation's App-Standards — the Standalone User Access Control standard and the Structured Logging standard (with its Log Schema) — are stricter in several places. We follow the Standards where the two conflict and add Standard-required behaviour the PRD is silent on, except where the PRD deliberately limits scope. Every place where we knowingly depart from a Standard is listed below with its reason.

## Deviations from the PRD

- Password policy requires upper, lower, digit and special character on top of length ≥ 12, rejects common passwords, and caps length at 64 characters and 72 UTF-8 bytes. The PRD's Story 1 criterion (length alone is sufficient) is overridden.
- Account lockout lasts 20 minutes, not the PRD's illustrative 15.
- Admin deletion keeps a tombstone; a deleted username cannot be re-registered (a deleted Account's email can). The PRD implied hard delete.
- A registration conflict returns one combined `user exist` error rather than saying whether the username or the email clashed, and registration is rate-limited per IP. Registration still reveals that *some* matching Account exists; closing that fully needs email verification, which is out of scope.
- In the `dev` profile only, a missing admin configuration falls back to `admin` / `password` / `admin@localhost`, which bypasses the password policy and logs a warning. Every other profile fails at startup instead.
- Logout, Password Change and reset confirmation return 200, as the Standard's flow shows, rather than an unspecified success code.

## Additions the PRD does not mention

- Password History (last 3 passwords cannot be reused).
- At most one Session per Account (a new login ends the old one), a 15-minute idle timeout and an 8-hour absolute timeout. A failed login ends any Session the request carried.
- Self-service Password Change for logged-in Accounts.
- A self-read endpoint returning the caller's own Account, so the frontend knows the caller's role.
- Admin unlock of a Locked Account. An Admin cannot unlock their own Account.
- Owner notifications (password changed, Account locked, reset completed) through the stub `EmailService`.
- Rate limits on registration, reset requests and Reset Token redemption.
- A last-Admin rule: a role change, disable or delete that would leave no enabled Admin is rejected, checked under a row lock in the same transaction.
- The admin user list includes a `locked` flag so the unlock action can be offered.
- An admin role-read endpoint (the admin user list's `role` field) and a role-mutation endpoint (`PATCH /api/admin/users/{id}/role`), covering the Standard's role-management surface even though the app still has only the two fixed roles, User and Admin. `ADMIN` plays the part of the Standard's `USER_MANAGER`; unmatched routes are denied.
- Structured ECS JSON logging with trace and correlation IDs, a dedicated audit log kept for at least 90 days, masking and log-injection protection (Structured Logging standard).
- A required-password-change flag (IM8 as-15, ac-6). It is set on the Bootstrap Admin at creation and by an Admin action when compromise is suspected (which also ends that Account's Sessions). Until the password is changed or reset, only `/me`, Password Change, logout and `/csrf` are allowed.
- Actuator metrics on a non-public management port (IM8 lm-16), `/.well-known/security.txt` (IM8 st-3), and a "Confidential" classification label next to every input field (IM8 dp-8).

## Where the PRD wins over the Standards

- Session cookies are `Secure=false` in the `dev` profile (local HTTP, and Safari rejects `Secure` cookies on `http://localhost`); `Secure=true` in every other profile.
- BCrypt (cost 12) is kept, because the PRD requires it by name and one Standard recipe permits it, rather than the Argon2id the Standard prefers for new systems. BCrypt reads only 72 bytes, which is why passwords are capped at 64 characters and 72 bytes.
- Accounts are created by self-registration (PRD) and are active immediately with the User role. The Standard expects only administrators to create Accounts; admin creation is not built.

## Acknowledged deviations from the Standards

- **Single instance.** Rate limiters and the IP Throttle are in memory, so they are not consistent across instances as the Standard's tests require. The app is documented to run as one instance; running more would need a shared store (for example bucket4j over JDBC). Sessions and lockout state are already in the database.
- **Reset confirmation is rate-limited per IP, not per Account.** An invalid token identifies no Account, so a per-Account key can't apply to the guesses that matter, and a 256-bit token cannot be brute-forced. Reset requests are still limited per email address.
- **No automatic required password change on re-enable.** Re-enabling an Account doesn't set the required-change flag, because suspension isn't always a sign of compromise. When it is, the Admin applies "require password change" explicitly. Disabling already ends every Session.
- **Expired Sessions get 401, not a redirect.** This is a JSON API; the SPA turns the 401 into a redirect to the login page, which is what the Standard's redirect achieves.
- **Tombstone table as soft delete.** A deleted Account's row is removed and a `deleted_users` tombstone keeps its UUID, username, email, deletion time and deleting Admin, indefinitely. That preserves the audit trail the soft-delete rule exists for, without leaving deleted Accounts in the live table.
- **Error codes.** The ProblemDetail `code` is snake_case (`user_exist`, `token_invalid`, `too_many_requests`, `authentication_failed`); `detail` carries the Standard's exact wording where it names one ("user exist", "password reset token expired or invalid", "too many requests").
- **TLS is a deployment requirement,** not enforced by the app, because local HTTPS is out of scope.
- **Business-rule rejections are logged at WARN.** The User Access Control standard puts failed administrative attempts at WARN; the Logging standard's general rule says ERROR. The more specific standard wins.
- **`session.hash` is logged** before authentication, as the User Access Control standard and the Log Schema require, although the Logging questions advise against logging Session IDs in any form. It never goes into MDC.
- **The `EmailService` stub logs the reset link, including the plaintext Reset Token,** to its own logger and file (recipient masked), which stand in for a mailbox. No other log may contain the token. This ends when a real email integration replaces the stub.
- **OWASP Dependency-Check runs locally** through a Maven profile; the CI gate is out of scope with the rest of CI/CD.

## Accepted IM8 deviations

The spec was audited against the IM8 application controls. This is a reference implementation for the assessment, not a service deployed to real government users, and the PRD mandates local username/password accounts. On that basis the following are accepted:

- **ac-2 MFA:** Admins log in with a password only; MFA is out of scope. Mitigations are lockout, per-Account and IP rate limits, one Session per Account, and an audit event for every admin action.
- **ac-3 inactive accounts / ac-4 access review:** there is no inactivity disablement, declared permission baseline or periodic review. The Admin list shows role, enabled status, Locked status and creation date for a manual review.
- **ac-7 Singpass/Corppass, ac-8 automated lifecycle (SCIM/JIT), ac-12 SSO:** local accounts with self-registration, as the PRD requires.
- **lm-18 WOGAA:** not embedded; the app isn't a public government digital service.

Any real deployment must revisit every item in this section.

## Standard features deliberately not built

- Admin-initiated password reset, and grace-period disablement when a required password change isn't completed.
- Admin creation of Accounts.
- Config-driven RBAC matrix and role definitions: the PRD rules out authorization beyond the USER/ADMIN check.
- Scheduled account-hygiene jobs (inactivity disablement, role revocation).
- Remember Me.
- Central log-platform configuration (retention, write-once storage, access control, alerting on audit-logging failure). These are documented as deployment requirements.
