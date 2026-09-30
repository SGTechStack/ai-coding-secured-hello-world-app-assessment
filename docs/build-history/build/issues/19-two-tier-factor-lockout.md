# 19: Two-tier factor lockout

**What to build:** Guessing TOTP codes is bounded, and every guess costs a password (ADR-027; ADR-016).

- **Tier 1:** 10 failures in 20 minutes give a 20-minute lock that lifts on its own. It is answered with 429 `TOO_MANY_REQUESTS`, the factor member and `reason: LOCKED`, and an integer `Retry-After`.
- **Tier 2:** 100 cumulative failures disable the factor. That sets `force_password_change` and ends the subject's sessions (T-MFA-023). It surfaces as 423 `FACTOR_DISABLED` on the self-read, the admin entry point and verification (R-MFA-006; REJ-049).
- **Audit:** tier-1 lock, tier-2 disable.
- **SPA:** an admin under tier 2 sees the *terminal factor state*, which says another admin must reset the factor, instead of a challenge that can't succeed.

**Blocked by:** 18

**Status:** done

- [x] The 10th failure locks, and a correct code during the lock is refused. After 20 minutes on the `Clock` it works.
- [x] The 100th cumulative failure disables the factor, forces a password change, and ends every session of the subject (checked by replay).
- [x] Verification, the self-read and the admin entry point all report `FACTOR_DISABLED`.
- [x] The SPA renders the terminal state, not the challenge (Vitest).
