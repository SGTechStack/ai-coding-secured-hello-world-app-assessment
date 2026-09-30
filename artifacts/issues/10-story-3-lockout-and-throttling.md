# 3: As a security-conscious operator, I want repeated failed logins to trigger account lockout and IP-level throttling, so that brute-force credential guessing is blunted.

`feature` · wave 4

| Effort | Float |
| --- | --- |
| 2.0 days | 3.0 days |

## Acceptance criteria

- Given N consecutive failed login attempts against one account within a window (e.g. 5 attempts), when the Nth failure occurs, then the account is locked for a cooldown period (e.g. 15 minutes) by setting `locked_until`.
- Given a locked account, when the cooldown period elapses and the correct password is submitted, then login succeeds and `failed_login_attempts` resets.
- Given repeated failed login attempts from one IP address across multiple usernames, when a threshold is exceeded, then further attempts from that IP are throttled independently of any single account's lockout state — so an attacker cannot lock out a legitimate user merely by failing that user's password from one source.

## Dev tasks

1. `be_auth_lockout_service` (1 d) — `AccountLockoutService`: increment on failure, lock at threshold, reset on success; `LoginSecurityProperties` (`app.security.login.*`).
2. `be_auth_throttle_middleware` (0.5 d) — `IpLoginThrottle` sliding window keyed by client IP; `429` via `TooManyLoginAttemptsException`.

## Test seams

- `AccountLockoutServiceTest` (unit, injectable `Clock`) — lock on Nth failure; expired lock + correct password resets.
- `IpLoginThrottleTest` (unit) — threshold and window expiry.
- `LockoutIntegrationTest` — N failures → locked; IP throttle returns 429 across different usernames.

## Dependencies

- Blocked by: 2
- Unblocks: —
