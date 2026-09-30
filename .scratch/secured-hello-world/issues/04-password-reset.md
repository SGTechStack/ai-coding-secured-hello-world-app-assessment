# 04: Password reset (request & confirm)

**What to build:** A user who forgot their password can request a reset via their registered email and, using the resulting single-use token, set a new password — without ever being able to learn from the response whether that email is registered.

**Blocked by:** 01, 02

**Status:** done

- [x] The password-reset-request endpoint always returns the same generic success response regardless of whether the submitted email is registered (enumeration resistance; IM8 `as-3`)
- [x] When the email matches a registered user, a single-use reset token is generated, only its hash (never the plaintext token) is stored, with a short expiry (15–30 min), and the stub `EmailService.sendPasswordResetEmail(...)` is called (logs instead of sending) (IM8 `as-15`)
- [x] The password-reset-confirm endpoint, given a valid, unexpired, unused token and a policy-compliant new password, updates the password, marks the token used, and invalidates all existing sessions for that user (also clears `failed_login_attempts`/`locked_until`, since a user resetting their password to recover from lockout must not still be locked out afterward)
- [x] An expired token is rejected and the password is not changed
- [x] A token that has already been used once is rejected on reuse (single-use enforcement)
- [x] Plaintext reset tokens are never written to the audit log stream; any dev-only stub logging of the reset link is fenced to a non-audit, DEBUG-only logger, active only under the `dev` profile (IM8 `lm-19`)
- [x] Structured audit log lines for reset requested and reset completed
- [x] Integration tests: token single-use, token expiry, reset invalidates existing sessions

## Comments

Implemented in an isolated worktree/branch and merged (commits `366c2100` + merge `8a28bff3`).
7 integration tests (PasswordResetIntegrationTest) pass; live smoke test confirmed the dev-only
DEBUG-level reset-link logging, single-use/expiry rejection, and session invalidation end to end.
