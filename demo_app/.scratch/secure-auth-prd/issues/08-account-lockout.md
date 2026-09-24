# 08: Account lockout

**What to build:** A user who fails login 5 times in a row is locked out for 15 minutes. They see the same generic "Invalid username or password" message the whole time, so an attacker learns nothing. The lock lifts on its own and can't be extended by further failures. See spec §Backend modules › Login and lockout, and Acceptance scenarios › Story 5.

**Blocked by:** 02, 05, 07

**Status:** resolved

- [x] 5 consecutive failures (`app.security.lockout.max-failures`) set `locked_until = now + 15m` (`app.security.lockout.duration`). The counter update runs in its own transaction, so it survives the rejected login.
- [x] A locked account with the correct password, a disabled account, an unknown username and a wrong password all get the identical `401 INVALID_CREDENTIALS` body.
- [x] The password comparison always runs before the lock and enabled checks, so timing stays uniform.
- [x] Failures while locked neither increment the counter nor extend the lock.
- [x] Once the clock moves past 15 minutes, the correct password succeeds and resets the counter: 4 more failures don't lock.
- [x] A successful login resets `failed_login_attempts` and clears `locked_until`.
- [x] An `ACCOUNT_LOCKED` audit event is emitted when the lock is set.
- [x] API-seam tests use the controllable clock.
- [x] e2e Story 5 scenario 1 uses a freshly registered, uniquely named user. Scenario 2 (after the cooldown) is covered at the API seam only.

## Comments

- **Ordering.** `SecurityConfig.authenticationManager` sets `DaoAuthenticationProvider`'s pre-authentication checks to a no-op and uses `AccountStatusUserDetailsChecker` as the post-authentication checks, so the BCrypt comparison (or the dummy-hash one for unknown users) always runs first. A wrong password on a locked or disabled account is a `BadCredentialsException`; the correct one is `LockedException`/`DisabledException`. All render as the same `401 INVALID_CREDENTIALS`. `LockoutApiTest.thePasswordIsCheckedBeforeTheLockAndEnabledFlags` proves the order through the real `AuthenticationManager` bean.
- **Lock state.** `AccountUserDetails(UserAccount, Instant now)` maps `accountNonLocked = !account.isLockedAt(now)`; `AccountUserDetailsService` takes the `Clock`. The principal in a session keeps the values from login time.
- **Domain.** `UserAccount.isLockedAt(Instant)`, package-private `registerFailedLogin(now, maxFailures, lockDuration)` (returns whether it set the lock; no-op while locked; an expired lock starts a fresh count, so one typo after a lock doesn't re-lock) and `registerSuccessfulLogin()`, plus **public `clearLockout()`** for ticket 09: call it on the managed `UserAccount` inside the reset-confirm transaction (it zeroes `failed_login_attempts` and nulls `locked_until`).
- **Persistence.** `user.LoginAttempts` (`recordFailure(username)` → `boolean locked`, `recordSuccess(username)`), each `@Transactional(REQUIRES_NEW)`, loading the row with the new `UserAccountRepository.findByUsernameForUpdate` (`PESSIMISTIC_WRITE`), so concurrent failures are all counted. Unknown usernames are ignored. `AuthController` calls `recordFailure` only for `BadCredentialsException` (a correct password on a locked/disabled account isn't a failure), and `recordSuccess` before the session is created, so a failed reset is a `500` with no session (fails closed).
- **Audit.** `ACCOUNT_LOCKED` follows the locking failure's `LOGIN_FAILURE`, with the normalised (lowercase) username as actor.
- **Config.** `LockoutProperties` (`app.security.lockout.max-failures: 5`, `duration: 15m` in `application.yml`), enabled on `LoginAttempts`; missing or non-positive values stop startup.
- **Tests.** `LockoutApiTest` (10 cases, `TestClockConfig`, a registered user and `uniqueIp()` per test; disabling is done with SQL until the admin API exists). Caveat: the `MutableClock` contexts (2026-01-01) and the real-clock contexts share one DB, so a `locked_until` set in one looks expired or far-future in the other. Never lock `johndoe` or any shared user.
- **E2E.** `e2e/story-5-lockout.spec.ts` Scenario 1 (fresh registered user, 5 UI failures, correct password shows "Invalid username or password"). Scenario 2 is API-seam only. The e2e backend now also gets `APP_SECURITY_THROTTLE_LOGIN_MAX_ATTEMPTS=1000`. Suite: 27 passed with `CI=1 BACKEND_PORT=18088 FRONTEND_PORT=13008`; Story 5 passed 4 repeats. `mvn verify`: 169 tests, coverage met. `npm run check` green.
