# 07: Account lockout and IP throttling (Story 3)

**What to build:** Repeated failed logins are blunted at two independent levels. After N consecutive failed attempts against one account within a window (e.g. 5), the Nth failure locks the account for a cooldown period (e.g. 15 minutes) by setting `locked_until`. When the cooldown elapses and the correct password is submitted, login succeeds and `failed_login_attempts` resets. Separately, repeated failed attempts from one IP across multiple usernames are throttled once a threshold is exceeded — independently of any single account's lockout — so an attacker cannot lock out a legitimate user just by failing that user's password from one source.

**Blocked by:** 04.

**Status:** done

- [x] N consecutive failures on one account within the window → account locked via `locked_until` for the cooldown
- [x] Correct password after the cooldown → login succeeds and `failed_login_attempts` resets
- [x] IP-level throttling engages on repeated failures across multiple usernames from one IP, independent of per-account lockout
- [x] Audit log line when a lockout is triggered
- [x] Integration tests: lockout after N failures; reset after cooldown; IP throttling independent of account lockout
