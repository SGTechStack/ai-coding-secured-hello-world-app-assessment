# 07: Password reset request

**What to build:** A user who forgot their password submits their email to a reset-request endpoint. The response is always a generic success (so account existence can't be inferred); if the email matches a real user, a single-use token is generated, its hash stored with a short expiry, and the stubbed EmailService logs the reset link.

**Blocked by:** 02 (needs user accounts).

**Status:** ready-for-agent

- [ ] `password_reset_tokens` entity exists: id, user_id (FK), token_hash (never plaintext), expires_at (15–30 min), used_at (nullable).
- [ ] The reset-request endpoint always returns a generic success message regardless of whether the email is registered. (enumeration resistance, IM8 as-13)
- [ ] For a registered email: a single-use token is generated, only its hash is stored, expiry set to 15–30 min, and `EmailService.sendPasswordResetEmail(...)` is called (stub logs the link, never the raw token in a way that leaks it insecurely). (IM8 lm-19)
- [ ] Reset-request events are audit-logged (structured, no token/plaintext). (IM8 lm-4)
- [ ] Endpoint is CSRF-protected. (Security Requirements)
