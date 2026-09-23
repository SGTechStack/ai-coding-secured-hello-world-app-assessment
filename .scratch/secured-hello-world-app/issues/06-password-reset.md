# 06: Password reset request + confirm (Stories 6-7)

**What to build:** A user who forgot their password can request a reset by
email and get a generic success response regardless of whether the email
is registered; a user with a valid single-use token can set a new password,
which invalidates all of that user's existing sessions.

**Blocked by:** 03 (Login + session + generic errors)

**Status:** ready-for-agent

- [ ] `POST /api/password-reset/request` accepts an email; always returns a
      generic success response, whether or not the email is registered
- [ ] When the email matches a registered user: a single-use reset token is
      generated, only its hash is stored (`token_hash`) with a 30-minute
      expiry, and a stub `EmailService.sendPasswordResetEmail(...)` is
      invoked (logs the reset link instead of sending mail; no plaintext
      token is ever persisted)
- [ ] `POST /api/password-reset/confirm` accepts a token and new password
- [ ] Valid, unexpired, unused token + password meeting the strength
      policy: password updated, token marked used (`usedAt` set), and all
      existing sessions for that user invalidated
- [ ] Expired token: request rejected, password not changed
- [ ] Already-used token: request rejected on the second attempt
      (single-use enforcement)
- [ ] Integration tests: request-reset returns identical generic response
      for registered vs. unregistered email; confirm succeeds with a valid
      token and invalidates a pre-existing session (verify via a
      subsequent authenticated request failing); expired token rejected;
      reused token rejected
