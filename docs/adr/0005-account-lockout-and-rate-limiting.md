# ADR-0005: Layered login throttling that one source cannot use to lock out a user

## Status

Accepted

## Context

PRD Story 3 requires account lockout after repeated failures, and also that one source cannot
lock out another user. A lockout alone doesn't meet the second requirement: if the account locks
after 5 failures, any single source can lock out any user. Other threats to cover:
- password spraying across many usernames from one IP
- extending a lock indefinitely by retrying while it is locked
- lost-update races on the failure counter
- unbounded in-memory throttle state
- spoofed `X-Forwarded-For` headers
- timing differences that reveal whether an account is locked or disabled
- abuse of registration and password reset

## Decision

Values follow App-Standards UAC §3.5. The PRD gave only examples.

| Limiter (`app.security.rate-limit.*`) | Limit | On exceed |
|---|---|---|
| `login-ip` | 20 failures / 15 min | 429 for 15 min |
| `login-ip-username` | 3 failures / 15 min | 429 until window ends. Trips **before** the account lock. |
| `login-username` | 10 attempts / 1 min | 429 |
| `register-ip` | 10 / h | 429 |
| `reset-request-ip` | 5 / 15 min | 429 |
| `reset-request-email` | 3 / 15 min | **silent** 200, no email (no enumeration) |
| `reset-confirm-ip` | 10 / 15 min | 429 |

- Every 429 is `{code:"RATE_LIMITED"}` with `Retry-After` (seconds, rounded up) and a
  `ATTEMPTS_EXCEEDED` audit event.
- `FixedWindowRateLimiter` is in-memory with a bounded key map (`max-keys`) and an expiry sweep.
- Account lockout: 5 failed passwords → locked for 20 min.
  - The row is locked with `SELECT … FOR UPDATE` while counting.
  - Failures while locked neither count nor extend the lock.
  - When a lock has expired, the counter starts fresh.
  - A successful password reset clears the lockout.
- Password-first checks: `DaoAuthenticationProvider` verifies the password **before** the
  locked/disabled status. Every failure path costs one BCrypt, and all of them return the same
  `401 INVALID_CREDENTIALS`.
- Client IP is `request.getRemoteAddr()`. In prod, `server.forward-headers-strategy=native` with
  Tomcat's `RemoteIpValve` trusts `X-Forwarded-For` only from `${TRUSTED_PROXIES}` (a regex).

## Consequences

- To lock an account an attacker needs at least 2 source IPs within 15 minutes, and the per-account
  10/min limit still caps total guessing.
- Limits are per instance and reset on restart. A multi-instance deployment needs a shared store
  (e.g. Redis/Bucket4j) first. 
- `TRUSTED_PROXIES` must be set correctly in prod. If it's too broad, clients can spoof their IP;
  if it's too narrow, every user shares the proxy's IP and its limits.
