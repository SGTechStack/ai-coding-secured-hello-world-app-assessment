# 08: Password Change with Password History

**What to build:** A logged-in Account holder opens Password Change from the hello screen, enters their current password and a new one, and is sent to the login page with a message. Every one of their Sessions has ended, and they are notified. A wrong current password, a weak password or a recently used password is refused with a specific error. See the spec's stories 44–47 and 49 (story 48, cancelling Reset Tokens, lands in ticket 09), "Credential policy", "API contract", and `CONTEXT.md`'s Password Change and Password History.

**Blocked by:** 05, 06

**Status:** ready-for-agent

- [ ] `PATCH /api/me/password` with `{currentPassword, newPassword}` returns 200 and ends every Session for the Account, including the current one.
- [ ] A wrong current password returns 400 `current_password_invalid`. A policy failure returns 400 `password_policy` with `violations`. Reusing any of the last 3 passwords (current one included) returns 400 `password_history`.
- [ ] Password History keeps the last 3 hashes per Account (configurable).
- [ ] `EmailService` gains a "password changed" operation, sent on success.
- [ ] Audit events: success is INFO `password-reset` with `event.type: ["change"]`; failure is WARN with a generic reason.
- [ ] SPA: a Password Change screen linked from hello, with "Confidential" labels, showing each error; success goes to the login page with a message.
- [ ] Tests cover: success, which returns 200 and invalidates both the current and any other Session; wrong current password; each policy failure; history rejection of the current and a previous password; the notification is recorded; and the audit events.
