# 04: Account lockout + IP throttling

**What to build:** Brute-force resistance on top of login. N consecutive failed login attempts against one account within a window locks that account for a cooldown period by setting `locked_until`; a locked account rejects even correct credentials until the cooldown expires, after which login succeeds and the failure counter resets. Independently, repeated failed attempts from one IP address across multiple usernames trigger IP-level throttling, so an attacker can't lock out a legitimate user merely by failing that user's password from one source.

**Blocked by:** 03 (extends the login endpoint)

**Status:** done

- [x] N consecutive failed login attempts against one account within a window (e.g. 5 attempts) → account locked for a cooldown period (e.g. 15 minutes) via `locked_until`.
- [x] Locked account + correct password submitted before cooldown expires → still rejected.
- [x] Locked account + correct password submitted after cooldown expires → login succeeds, `failed_login_attempts` resets.
- [x] Repeated failed attempts from one IP across multiple usernames exceeding a threshold → further attempts from that IP are throttled, independent of any single account's lockout state.
- [x] Structured audit log lines for lockout triggered (never logging passwords).

## Implementation notes

**Policy**

- `LockoutPolicy`: 5 consecutive failures (`MAX_FAILED_ATTEMPTS`) locks the account for 15 minutes (`LOCKOUT_DURATION`), matching the spec's example numbers.
- `IpLoginThrottle`: 10 failed attempts (`MAX_FAILED_ATTEMPTS_PER_IP`) from one IP within a 15-minute sliding window (`WINDOW`) throttles further login attempts from that IP with `429 Too Many Requests`. In-memory and process-local (a `ConcurrentHashMap`), which is adequate for a single-instance reference app; a multi-instance deployment would need a shared store behind the same interface.

**How lockout actually rejects a correct password**

- `UserPrincipal.isAccountNonLocked()` now compares `User.lockedUntil` against `Instant.now()`. Spring Security's `DaoAuthenticationProvider` checks this via `AccountStatusUserDetailsChecker` *before* comparing the password, so a locked account throws `LockedException` and never reaches password matching at all — correct or not, it's rejected identically.
- `LoginFailureHandler` catches both "wrong password" and "account is locked" (`LockedException`) and returns the exact same generic message either way, so lockout state isn't an observable side-channel on top of the existing username-enumeration resistance from ticket 03. It explicitly does *not* re-increment the counter or extend the lockout when the failure is a `LockedException` — repeatedly hitting a locked account doesn't push `locked_until` further out.
- `LoginSuccessHandler` clears both `failedLoginAttempts` and `lockedUntil` on success — this can only happen once the cooldown has actually elapsed, since `isAccountNonLocked()` blocks authentication entirely until then.

**IP throttling**

- `IpThrottleFilter` (a plain servlet filter, registered via `HttpSecurity.addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`) checks `IpLoginThrottle.isThrottled(ip)` before the request reaches Spring Security's login processing at all, matched on `POST` + exact request URI (`getRequestURI()`, not `getServletPath()` — the latter didn't match reliably in this project's dispatcher setup, discovered via a failing test).
- Throttle state is keyed purely by IP, never by username, so it can't be used to lock out one specific victim by failing their password repeatedly from a single IP (that only pushes the *account* lockout, which is keyed by username and unaffected by other IPs) — and symmetrically, spraying many different usernames from one IP still trips the IP throttle even though no single account ever reaches its own lockout threshold.

**Tests**

- `LockoutAndThrottlingTest`: account locks after the threshold; locked account rejects the correct password until cooldown; a simulated already-expired `lockedUntil` (set directly rather than waiting 15 real minutes) lets login succeed and resets both fields; IP throttle engages after spraying failures across many nonexistent usernames from the same IP, confirmed independent of the per-account counter/lockout (which both stayed at zero/null for the untouched account).
- `mvn clean verify`: 15/15 tests pass, BUILD SUCCESS.
- Verified live against the running server (not just MockMvc): registered an account, drove 5 real wrong-password `fetch` calls through the actual CORS+CSRF+cookie path, then confirmed a 6th attempt with the *correct* password still got the generic 401. Separately drove 11 failed attempts across distinct nonexistent usernames from the same origin and confirmed the IP throttle's 429 response and message.
