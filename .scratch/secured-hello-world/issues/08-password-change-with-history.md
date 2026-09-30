# 08: Password Change with Password History

**What to build:** A logged-in Account holder opens Password Change from the hello screen, enters their current password and a new one, and is sent to the login page with a message. Every one of their Sessions has ended, and they are notified. A wrong current password, a weak password or a recently used password is refused with a specific error. See the spec's stories 44–47 and 49 (story 48, cancelling Reset Tokens, lands in ticket 09), "Credential policy", "API contract", and `CONTEXT.md`'s Password Change and Password History.

**Blocked by:** 05, 06

**Status:** resolved

- [x] `PATCH /api/me/password` with `{currentPassword, newPassword}` returns 200 and ends every Session for the Account, including the current one.
- [x] A wrong current password returns 400 `current_password_invalid`. A policy failure returns 400 `password_policy` with `violations`. Reusing any of the last 3 passwords (current one included) returns 400 `password_history`.
- [x] Password History keeps the last 3 hashes per Account (configurable).
- [x] `EmailService` gains a "password changed" operation, sent on success.
- [x] Audit events: success is INFO `password-reset` with `event.type: ["change"]`; failure is WARN with a generic reason.
- [x] SPA: a Password Change screen linked from hello, with "Confidential" labels, showing each error; success goes to the login page with a message.
- [x] Tests cover: success, which returns 200 and invalidates both the current and any other Session; wrong current password; each policy failure; history rejection of the current and a previous password; the notification is recorded; and the audit events.

## Comments

### Verification (2026-09-29)

**Implemented:** `PATCH /api/me/password` (`PasswordChangeController` / `PasswordChangeService`) takes `{currentPassword, newPassword}` for the logged-in Account, which is taken from the Session principal and row-locked. Checks run in this order: current password, then the Credential policy, then Password History. A wrong current password returns 400 `current_password_invalid`. A policy failure returns 400 `password_policy` with `violations`. Reusing the current password or any of the last 3 (`app.credential.history-length=3`, `@Min(1)`) returns 400 `password_history`; the current hash is always checked, and older history rows are pruned. On success, every Session of the Account ends (`session-end`, reason `password_change`). The "Password changed" email (`EmailService.notifyPasswordChanged`, recipient masked in the stub) is sent after the transaction commits. The success is audited at INFO as `password-reset` with `event.type: ["change"]` and `user.id`. Each refusal is audited once at WARN with only its code as the reason. The SPA's `/password-change` screen is linked from hello and open only to logged-in holders. It has "Confidential" labels and shows each refusal, listing the broken rules under the new password field. On success it goes to the login page with "Your password has been changed. Please log in again.", even if fetching the new CSRF token fails. The password-rule messages moved to `components/passwordRules.ts`, and the register and Password Change screens both use them.

**Deviations and decisions:** A wrong current password counts toward Account lockout exactly like a failed login: same counter, threshold, lock and Account-locked email (reviewer decision "Wrong current password counts toward lockout", `docs/agents/reviewer-decisions.md`). A Locked Account gets the same `current_password_invalid` body even with the correct password. The attempt that locks the Account is also audited as `access-control` / `account_locked`. The endpoint has no dedicated rate limit. A malformed request is audited by the global handler as `access-control` `validation`, not as `password-reset`. Story 48 (cancelling Reset Tokens) is left to issue 09, and clearing `passwordChangeRequired` to issue 13.

**Known gaps (reviewer notes, non-blocking):** (1) The SPA's "last 3" text is hard-coded and doesn't follow `app.credential.history-length`. (2) Password Change doesn't refuse a disabled Account that still has a live Session; ending Sessions when an Account is disabled is left to the admin issue. (3) Password History rows are ordered by `created_at`, so two changes in the same microsecond would make pruning order ambiguous.

**Verification steps:** `./mvnw verify` passed: 193 tests (25 in `PasswordChangeApiTest`). Frontend: vitest 76 passing; `tsc -b`, oxlint and prettier clean. The reviewer loop took one correction round: a human decision (lockout, option A) plus minor fixes (redirect even when the CSRF refresh fails, a comment corrected, email sent after commit). It then passed with Must-fix 0 and Human decisions 0. KB retrieval and the code-reviewer compliance gates were skipped by request; mutation testing was skipped.

**Checklist:** all acceptance-criteria boxes ticked.

Commit: `bf00a61 feat(auth): Change password with Password History`
