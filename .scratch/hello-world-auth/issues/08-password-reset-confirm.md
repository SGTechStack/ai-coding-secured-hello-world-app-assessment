# 08: Password reset confirm

**What to build:** A user with a valid reset token sets a new password. On success the password is updated, the token is consumed (single-use), and all existing sessions for that user are invalidated. Expired or already-used tokens are rejected.

**Blocked by:** 07 (needs issued reset tokens), 03 (needs sessions to invalidate).

**Status:** ready-for-agent

- [ ] A valid, unexpired, unused token + a new password meeting the strength policy (≥ 12) updates the password (BCrypt), marks the token used, and invalidates all existing sessions for that user. (IM8 as-5, as-6)
- [ ] An expired token is rejected and the password is not changed.
- [ ] An already-used token is rejected on reuse (single-use enforcement).
- [ ] Reset-completed events are audit-logged (structured, no plaintext). (IM8 lm-4, lm-19)
- [ ] Endpoint is CSRF-protected; new-password input validated. (IM8 as-1)
- [ ] Integration tests: token single-use, token expiry, reset invalidates existing sessions. (Testing Requirements, Stories 6–7)
