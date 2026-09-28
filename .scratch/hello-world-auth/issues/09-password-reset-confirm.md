# 09: Password reset confirm (Story 7)

**What to build:** A user holding a valid reset token can set a new password and regain access. Given a valid, unexpired, unused token and a new password meeting the strength policy, the password is updated, the token is marked used, and all existing sessions for that user are invalidated. An expired token is rejected and the password is not changed. A token already used once is rejected on any further use (single-use enforcement). A React "set new password" form driven by the token completes the flow.

**Blocked by:** 08.

**Status:** ready-for-agent

- [ ] Valid, unexpired, unused token + policy-compliant new password → password updated, token marked used, existing sessions invalidated
- [ ] Expired token → rejected, password unchanged
- [ ] Already-used token → rejected (single-use enforced)
- [ ] New password re-validated against the strength policy (≥ 12)
- [ ] CSRF enforced on the endpoint
- [ ] Audit log line for password reset completed
- [ ] React confirm form submits the token + new password and reflects success/failure
- [ ] Integration tests: single-use, expiry, and existing-session invalidation
