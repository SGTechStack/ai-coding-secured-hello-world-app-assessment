# 12: Lockout, NIST cap and cardinality axis

**What to build:** Brute force on one account is blunted, and a stranger's failed guesses never sign the owner out (ADR-034).

- **Password lockout.** 5 consecutive failures inside a 20-minute observation window lock the account (ADR-012). The duration escalates 20 → 40 → 60 minutes, 5 cycles per rung, read from the cap counter (ADR-011; R-LCK-007). The lock lifts on its own. A startup floor refuses a ladder that reaches the cap in under 840 minutes or leaves under 580 minutes of warning.
- **NIST cap.** 100 consecutive failures set `password_disabled_at`, with an alert row at 50 (ADR-013). Only rebinding clears it.
- **Pre-authentication check order:** locked/disabled, then the cap, then forced-change expiry (ADR-013; ADR-046). No lockout branch runs before authentication on account existence, and locked and capped accounts still get the uniform 401.
- **Cardinality axis.** One source may drive at most 5 distinct accounts into lockout per hour, with expiry from first insertion (ADR-015).
- **Audit:** lockout triggered at WARN (REJ-043), lock cleared, and the cap alert.

The counter and ladder are pure logic, in PIT scope.

**Blocked by:** 11

**Status:** ready-for-agent

- [ ] 5 failures lock the account. The correct password then gets the same 401, and after 20 minutes on the `Clock` it succeeds.
- [ ] The ladder escalates on schedule, and a misconfigured ladder stops startup.
- [ ] 100 failures disable the password authenticator. The 50th emits the alert row.
- [ ] A failed login leaves the owner's live session intact.
- [ ] A 6th distinct account from one source within the hour is refused for new usernames.
- [ ] The counter and ladder meet 85% mutation score.
