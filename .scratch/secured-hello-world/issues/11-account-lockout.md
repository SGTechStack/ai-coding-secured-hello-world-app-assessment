# 11: Account lockout after repeated failures

**What to build:** Repeated failed logins against one account lock it for a cooldown period, so
guessing a single account's password by brute force stops being viable. After the cooldown
elapses the correct password works again and the counter resets — a lockout is a speed bump, not
a permanent denial of service against the legitimate owner.

Covers PRD Story 3, account-lockout half. The IP-throttling half is ticket 12 and is
deliberately separate, because the two controls must work **independently** of each other.

**Blocked by:** 08.

**Status:** ready-for-agent

**IM8 controls:** `as-4` Authentication Mechanism Rate-Limiting; `as-13` Exposure of Internal
System Details; `lm-4` Audit Logging; `pm-6` System Documentation. *ASVS: V2.2 General
Authenticator, V11 Business Logic, V7 Logging.*

- [ ] After N consecutive failed attempts against one account within the window (PRD suggests 5),
      the Nth failure sets the lock expiry to a cooldown period ahead (PRD suggests 15 minutes)
- [ ] Both the threshold and the cooldown duration come from configuration, not hardcoded
      literals, and the chosen values are documented
- [ ] While locked, the account is refused **even with the correct password** — this behaviour
      already exists from ticket 08 and must not regress
- [ ] The lockout response remains generic and does not disclose that the account is locked, or
      how many attempts remain
- [ ] Once the cooldown elapses, submitting the correct password succeeds and
      `failed_login_attempts` resets to zero
- [ ] The counter counts **consecutive** failures — a success partway through resets it, so a
      user who mistypes twice then succeeds is not one mistake from a lockout tomorrow
- [ ] A lockout-triggered audit event is emitted naming the affected account
- [ ] Test: N failed attempts sets the lock expiry and the (N+1)th attempt with the correct
      password is refused
- [ ] Test: after the cooldown passes, the correct password succeeds and the counter is zero
- [ ] Test: failures interrupted by a success do not accumulate toward a lockout
