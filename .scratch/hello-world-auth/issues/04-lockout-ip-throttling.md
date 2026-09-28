# 04: Account lockout & IP-level throttling

**What to build:** Repeated failed logins blunt brute-force attacks: an account locks after N consecutive failures within a window, and an IP failing many usernames is throttled independently so an attacker cannot lock out a legitimate user from one source.

**Blocked by:** 03 (builds on the login/failed-attempt path).

**Status:** ready-for-agent

- [ ] N consecutive failed attempts (e.g. 5) against one account within a window locks it for a cooldown (e.g. 15 min) by setting `locked_until`.
- [ ] After cooldown elapses, correct password succeeds and `failed_login_attempts` resets to 0.
- [ ] Repeated failures from one IP across multiple usernames trigger IP-level throttling independent of any single account's lockout state. (IM8 as-4)
- [ ] Lockout-triggered events are audit-logged (structured). (IM8 lm-4)
- [ ] Integration tests: N failures triggers lockout; successful login after cooldown resets the counter; IP throttling engages independently of account lockout. (Testing Requirements, Story 3)
