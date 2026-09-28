---
status: accepted
---

# ADR-066: A forward-only mutable clock and clock-built cache adapters are production design

All main code reads time from one injected `java.time.Clock`. The rate-limiter and cache time sources are built
from that same clock. In tests, the clock is a single mutable instance that starts at real time and only moves
forward. The adapters that connect the clock to the rate limiter and the caches are production code, not test
scaffolding. They look like code shaped for tests, and a simplifier would remove them first.

## Context

Lockout windows, the escalating lockout ladder, the NIST failure cap, token expiry, factor freshness, the absolute
session lifetime and the rate-limit buckets all depend on elapsed time. Testing them against real time means either
sleeping for up to hours or not testing them at all.

The obvious fix is to inject a fixed `Instant`. That fails here, because two clock sources exist that we do not
control:

- Spring Session stamps `Instant.now()` directly. `SessionRepositoryFilter` sets the last-accessed time that way
  when it resolves a session.
- Spring Security's `FactorGrantedAuthority.Builder.build()` defaults `issuedAt` to `Instant.now()` when it is not
  set. Its Javadoc says so, and the 7.x source confirms it. That instant is the clock the factor rules'
  `validDuration` is measured against (ADR-021).

A fixed instant pinned in the past would put our clock and the framework's clock hours or years apart. A test clock
that starts at real time and only advances keeps ours at or ahead of theirs. Where the framework's clock must be
moved, the test ages the stored data instead: idle expiry is tested by ageing `SPRING_SESSION` rows, never by
advancing a clock.

## Decision

- **One `Clock` bean.** In production it is `Clock.systemUTC()`. In tests it is one forward-only mutable instance.
  The restart harness shares a single instance across both of its runs.
- **The adapters are production code:**
  - A Caffeine `Ticker` built from the clock.
  - A Bucket4j `TimeMeter` built from the clock, registered through `withCustomTimePrecision`, which is Bucket4j's
    documented extension point for a custom time source.
  - Our own code that builds a `FactorGrantedAuthority` always sets `issuedAt(clock.instant())`, so the builder
    never falls back to `Instant.now()`. T-MFA-008 pins this.
- **Ambient time is banned in main code.** ArchUnit rejects every no-argument `now()` on `Instant`, `LocalDate`,
  `LocalDateTime`, `ZonedDateTime` and `OffsetDateTime`, as well as `new Date()`, `System.currentTimeMillis()` and
  `System.nanoTime()`. `now(Clock)` is allowed (T-ARCH-001). T-RL-018 proves that advancing the clock refills the
  buckets and expires the cache entries.
- **`Thread.sleep` is prohibited in tests.** The one real wait is the H2 lock-timeout test, which runs in its own
  context with a 50 ms timeout. A binding test checks that the production URL carries `LOCK_TIMEOUT=1000`.

## Considered options

- **Fixed `Instant` per test.** Rejected: there are two clock sources.
- **Library defaults for the rate limiter and caches, with time-dependent tests skipped or slept through.**
  Rejected: the lockout ladder and the NIST cap work on scales of hours.
- **A mutable forward-only clock with production adapters (chosen).**

## Consequences

- **Caffeine loses monotonicity, and this is a real regression from its default.** Caffeine's
  `Ticker.systemTicker()` reads `System.nanoTime()`, which is monotonic. A ticker built from a wall clock goes
  backwards if NTP steps the system clock back, which can make cache entries live longer or expire early. We accept
  that and record it: the affected caches hold limiter buckets and the cardinality set, and an error of one NTP
  step in either direction is small against windows measured in minutes.
- **Bucket4j loses nothing it had.** Its default time meter is millisecond wall-clock time
  (`SYSTEM_MILLISECONDS`), which already has the same NTP property.
- If a future dependency reads time on its own, it joins the list of clock sources above. Its tests must age stored
  state, not move a clock.
