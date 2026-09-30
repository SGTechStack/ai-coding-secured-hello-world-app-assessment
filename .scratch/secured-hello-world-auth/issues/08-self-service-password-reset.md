# 08: Self-service password reset

**What to build:** A Regular user who forgot their password enters their email on the SPA, receives a reset link through the email stub, opens it, and sets a new password. Afterwards every one of their existing sessions has ended and they are notified that the password changed. The flow reveals nothing about whether the email is registered, tokens work once and expire, and a reset never clears a lock. This ticket adds the Password reset module and its token table. See ADR-0006.

**Blocked by:** 03 (Registration rules and password policy), 06 (Account lockout), 07 (Login and registration throttling)

**Status:** ready-for-agent

- [ ] `password_reset_tokens` matches the spec's schema (UUID id, `user_id` FK, unique SHA-256 hex `token_hash`, `expires_at`, `used_at`, `created_at`).
- [ ] `POST /password-reset/request` (CSRF) always returns 202 with the same generic body, and takes the same time whether or not the email is registered. The token and email are created asynchronously.
- [ ] A token is created only for a registered Account that isn't a Tombstone. It has at least 32 random bytes, is stored only as a SHA-256 hash, and is valid for 30 minutes.
- [ ] Issuing a new token invalidates any pending token for that Account.
- [ ] The reset link is delivered through `EmailService`. The stub writes the link only in the dev profile, and never to the audit log.
- [ ] `POST /password-reset/confirm` (CSRF) applies the Password policy, updates the credential, marks the token used, ends all of the Account's sessions, and returns 200.
- [ ] An expired, already-used or unknown token gets 400 `password reset token expired or invalid`, and the password stays unchanged. Expiry at 30 minutes is tested with the controllable `Clock`.
- [ ] A successful reset leaves `locked_until` and the Failed-login counter untouched.
- [ ] Both reset endpoints are throttled and return 429 with `Retry-After`.
- [ ] After a successful reset, the Account's owner receives a password-changed notification.
- [ ] Password reset requested and completed are audited, and the plaintext token appears in no log (checked with `ListAppender`).
- [ ] The SPA has a forgot-password page and a reset-password page. The reset page reads the token from the query string.

## Comments

**2026-09-29 — asynchronous request.** `POST /password-reset/request` hands its work to Boot's `applicationTaskExecutor` and answers 202 before it even looks the email up. The work is issuing the token and sending the email, and the email goes only after the token is committed, so the link always works. Tests replace the executor with `BackgroundTasks`, which holds work until the test runs it. `PasswordResetTest` uses that to show that at response time nothing has been looked up, stored or sent, for either kind of email.

**Audit: four events, all `event.action=password-reset`.**
- **Requested:** written in the request thread, before anyone knows whether the email is registered, so it carries no `user.id`, like a failed login.
- **Token issued:** written in the background, only for a registered Account, with its `user.id`. It has no request fields, but it carries the request's correlation and trace IDs, because `MdcTaskDecorator` copies the MDC into background work (logging standard §5).
- **Completed:** carries the Account's UUID.
- **Rejected:** a WARN event with no identity, for an unknown, used or expired token.

This follows User Access Control standard §3.3 ("token issuance and redemption").

**Concurrent requests.** Issuing locks the Account's row while it replaces the pending token, so two requests at once still leave only one working link.

**Throttling.** There are three limits, all configuration properties: `password-reset-requests-per-ip` (10/h), `password-reset-confirms-per-ip` (10/h) and `password-reset-requests-per-email` (3/h). The per-email bucket is keyed on the submitted email lowercased, never on a looked-up Account, so its 429 says nothing about registration. It is the part that stops reset emails flooding one inbox (story 49). The per-IP buckets sit in `ThrottleFilter`, so a throttled confirm never reaches the password policy's breach check. The per-email bucket sits in the controller, after the body is read.

**Validation before token.** The confirm body is validated first, including the password policy and its breach check. A new password that breaks the policy therefore never uses up a token. A bad token with a bad password gets `validation failed`.

**Configuration.** `app.password-reset.reset-page-url` (env `APP_RESET_PAGE_URL`) is required, and startup fails without it. The dev profile points it at `http://localhost:5173/reset-password`.

**SPA.** The reset page reads the token once and then removes it from the address bar, so it isn't left in history.
