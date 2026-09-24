# 08: Account lockout

**What to build:** A user who fails login 5 times in a row is locked out for 15 minutes. They see the same generic "Invalid username or password" message the whole time, so an attacker learns nothing. The lock lifts on its own and can't be extended by further failures. See spec §Backend modules › Login and lockout, and Acceptance scenarios › Story 5.

**Blocked by:** 02, 05, 07

**Status:** ready-for-agent

- [ ] 5 consecutive failures (`app.security.lockout.max-failures`) set `locked_until = now + 15m` (`app.security.lockout.duration`). The counter update runs in its own transaction, so it survives the rejected login.
- [ ] A locked account with the correct password, a disabled account, an unknown username and a wrong password all get the identical `401 INVALID_CREDENTIALS` body.
- [ ] The password comparison always runs before the lock and enabled checks, so timing stays uniform.
- [ ] Failures while locked neither increment the counter nor extend the lock.
- [ ] Once the clock moves past 15 minutes, the correct password succeeds and resets the counter: 4 more failures don't lock.
- [ ] A successful login resets `failed_login_attempts` and clears `locked_until`.
- [ ] An `ACCOUNT_LOCKED` audit event is emitted when the lock is set.
- [ ] API-seam tests use the controllable clock.
- [ ] e2e Story 5 scenario 1 uses a freshly registered, uniquely named user. Scenario 2 (after the cooldown) is covered at the API seam only.
