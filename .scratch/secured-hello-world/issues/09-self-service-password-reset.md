# 09: Self-service password reset

**What to build:** An Account holder who forgot their password enters their email on the forgot-password screen and always sees the same message, in the same time, whether or not the email is registered. The emailed link opens the reset screen, which reads the Reset Token from the URL fragment and clears it from the address bar. They set a new password, every Session ends, and they are notified. A token works once, expires after 30 minutes, and is cancelled by a newer request or by a Password Change. See the spec's stories 48, 50–62 and 103, "Password reset service", "Rate limiters", "Schema", and the Further Notes on the stub. Use `CONTEXT.md`'s Reset Token.

**Blocked by:** 07, 08, 11 (the Disabled-Account case needs the API to disable an Account)

**Status:** ready-for-agent

- [ ] A Flyway migration creates `password_reset_tokens` (id, Account FK, unique SHA-256 `token_hash`, `expires_at`, nullable `used_at`). Tokens are never deleted.
- [ ] `POST /api/password-reset/request` with `{email}` always returns 202 with a generic message, before any lookup.
- [ ] Token issuance and the email run on a background executor whose `TaskDecorator` copies the trace and correlation IDs into the task and clears them afterwards.
- [ ] Issuance generates at least 32 random bytes from `SecureRandom`, URL-safe encoded, and stores only the SHA-256 hash with a 30-minute expiry (configurable). It cancels any earlier pending token, and issues nothing for unknown or Disabled Accounts.
- [ ] `EmailService` gains a "reset link" operation and a "reset completed" operation. The emailed link is `<frontend-origin>/reset-password#token=<token>`, written in full to the stub's own file (ADR 0001).
- [ ] `POST /api/password-reset/confirm` with `{token, newPassword}` returns 200. It applies the Credential policy and Password History, marks the token used, ends every Session, leaves any active lock in place, and sends the "reset completed" notification.
- [ ] An expired, used or unknown token returns 400 `token_invalid` with `detail` "password reset token expired or invalid". Policy and history failures return the same errors as Password Change.
- [ ] A successful Password Change cancels any pending Reset Token.
- [ ] Reset requests are limited to 3 per hour per email and 10 per hour per IP. Confirmations are limited to 10 per minute per IP (ADR 0001 explains why not per Account). All return 429 in the standard format.
- [ ] No log line (application, audit or anything other than the stub's own file) contains the token or its hash.
- [ ] Audit events: `password-reset` issuance and completion (INFO), and failures and limit breaches (WARN).
- [ ] SPA: a forgot-password screen and a reset-password screen (which reads `#token=` and then calls `history.replaceState`), both with "Confidential" labels. Success goes to the login page with a message.
- [ ] Tests (Clock seam, with the token taken from the recording `EmailService` after a bounded wait) cover: the token works once; it expires after 30 minutes; a new request cancels the old token; a Password Change cancels a pending token; the reset ends existing Sessions; a lock remains after reset; a Disabled Account gets no token and no email with an identical response; the request returns 202 before the email is recorded; `token_invalid` wording; each limiter's 429; the completion notification; and no token or hash in the audit or application logs.
