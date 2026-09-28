# ADR-0005: Account lockout and IP throttling as two independent, differently-durable defenses

## Status

Accepted

## Context

Story 3 requires both per-account lockout and IP-level throttling, with the
throttle acting independently of account lockout state (so an attacker
can't lock out a legitimate user just by failing that user's password from
one source). These are implemented as two separate mechanisms with
deliberately different durability:

**Account lockout (persistent, per-account).** `User.failedLoginAttempts`
/ `User.lockedUntil` are updated by `LoginAttemptListener`, a
`@Transactional` listener reacting to Spring Security's
`AbstractAuthenticationFailureEvent` hierarchy (broad, not just
bad-credentials — a locked account throws `LockedException` *before*
password comparison, a different event type, and must still be handled or
lockout tracking would silently stop once an account is already locked).
Threshold and cooldown are externalized (`max-attempts=5`,
`duration=15m`). This required manually constructing the
`AuthenticationEventPublisher`/`AuthenticationManager` beans, since the
hand-built `ProviderManager` (needed for the custom JSON login filter, see
[[0002-session-cookie-mechanics]]) bypasses Spring Boot's auto-wired event
publishing.

**IP throttling (in-memory, per-instance, non-persistent).**
`IpLoginThrottleService` uses a `ConcurrentHashMap` keyed by IP with a
sliding window (`window=15m`, `max-attempts=20`, `block-duration=15m`),
enforced by `IpThrottleFilter` ahead of `UsernamePasswordAuthenticationFilter`
so a throttled IP never reaches password comparison. Its class-level
comment states the in-memory choice explicitly: state resets on restart,
which is acceptable for a throttle (unlike account lockout, which must
survive restart) and keeps the implementation simple for a single-instance
deployment.

**Known limitations, not defended against today:**
- Account lockout's update path is read-then-write (`findByUsername` →
  mutate → `save()`) with no row lock or optimistic-lock version column.
  Two concurrent failed attempts against the same account could both read
  the same counter value and each write back the same increment,
  undercounting under true concurrency rather than over-locking. Low
  likelihood for this app's expected load, but not actually race-safe.
- The IP throttle is per-instance and resets on restart — it does not
  survive a rolling deploy and does not coordinate across multiple
  instances behind a load balancer. A horizontally-scaled deployment would
  need a shared store (Redis, a DB table) instead.
- The IP throttle returns a distinguishable `429`, while every account-side
  auth failure (bad password, unknown username, locked account) returns
  the same generic `401`. This is a minor asymmetry with the anti-enumeration
  goal elsewhere: a `429` tells a client "you're being rate-limited" rather
  than "your credentials were wrong" — acceptable because it's IP-scoped
  and reveals nothing about any specific username, but worth naming
  explicitly rather than leaving implicit.

## Decision

Keep lockout and throttling as two separate mechanisms with independent
durability guarantees, matched to what each actually needs to survive
(lockout: restarts; throttle: nothing, by design). Accept the current
non-race-safe lockout counter and single-instance-only throttle as
sufficient for this app's current (single-instance, moderate-load) scope.

## Consequences

- If this app is ever deployed with multiple instances behind a load
  balancer, the IP throttle must move to a shared store before that
  deployment is trustworthy — this is a known, accepted gap, not an
  oversight to silently work around.
- If concurrent-login load ever becomes a real concern, the lockout
  counter update needs either a DB-level atomic increment, an optimistic
  lock, or a `SELECT ... FOR UPDATE`-equivalent — the current read-then-write
  is a correctness gap under concurrency, not just a style choice.
- Any future change to the login failure-handling path must preserve the
  manually-wired `AuthenticationEventPublisher`, or `LoginAttemptListener`
  and `IpThrottleListener` will silently stop receiving events.
