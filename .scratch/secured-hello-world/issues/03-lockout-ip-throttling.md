# 03: Brute-force protection: account lockout & IP throttling

**What to build:** Repeated failed logins against one account trigger a cooldown lockout, and repeated failures across multiple accounts from one IP address are throttled independently — so an attacker can't force a legitimate user's account into lockout merely by failing that user's password from a single source. This is also the primary compensating control for the PRD's decision to exclude MFA.

**Blocked by:** 02

**Status:** ready-for-agent

- [ ] N consecutive failed login attempts against one account within a window (e.g. 5) locks that account for a cooldown period (e.g. 15 minutes) by setting `locked_until` (IM8 `as-4`)
- [ ] A login attempt against a locked account is rejected — even with the correct password — until the cooldown expires
- [ ] Once the cooldown elapses, a correct-password login succeeds and `failed_login_attempts` resets to 0
- [ ] Repeated failed logins from one IP address across multiple usernames are throttled once a threshold is exceeded, independently of any single account's lockout state
- [ ] A structured audit log line is emitted when a lockout is triggered
- [ ] Integration tests: N failed attempts triggers lockout; successful login after cooldown resets the counter; IP throttling engages independently of account lockout
