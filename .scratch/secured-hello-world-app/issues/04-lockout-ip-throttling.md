# 04: Lockout + IP throttling (Story 3)

**What to build:** Repeated failed logins against one account trigger a
timed lockout; the lockout clears automatically after cooldown; repeated
failures from one IP across multiple usernames are throttled independently
of any single account's lockout state.

**Blocked by:** 03 (Login + session + generic errors)

**Status:** ready-for-agent

- [ ] 5 consecutive failed login attempts against one account within the
      tracking window sets `lockedUntil` to 15 minutes from the triggering
      failure
- [ ] After the cooldown elapses, a correct-password login succeeds and
      `failedLoginAttempts` resets to 0
- [ ] IP-level throttling tracked independently of per-account lockout,
      keyed by client IP across usernames, with a documented threshold/
      window (e.g. 20 failed attempts per IP per 15-minute window); once
      exceeded, further attempts from that IP are throttled even against
      usernames that are not individually locked
- [ ] Audit log line emitted when a lockout triggers (actor = the
      account/IP, no passwords logged)
- [ ] Integration tests: Nth failure triggers lockout; login after cooldown
      succeeds and resets the counter; IP throttling engages using
      different usernames from the same IP, independent of any one
      account's lockout state
