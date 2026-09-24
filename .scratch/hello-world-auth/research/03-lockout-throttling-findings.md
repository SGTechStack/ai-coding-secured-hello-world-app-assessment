# Findings 03 — Account lockout and IP throttling mechanism

Ticket: `issues/03-lockout-and-ip-throttling.md`
Spec: `assessment-prd.md` Story 3 + NFR "Enumeration resistance" / "Audit logging".

## TL;DR recommendation

Enforce both layers **inside the login service**, in a fixed order:
**IP-throttle check → account-lock check → credential verification**.
Store the IP throttle in an **in-memory Caffeine cache** keyed by client IP holding a
sliding-window failure counter; source the IP from `request.getRemoteAddr()` only
(no `X-Forwarded-For` trust in the no-proxy demo); inject a `Clock` bean for all
expiry comparisons; a successful login resets the account counter but **not** the
IP bucket (window decay only). Keep Spring Security authentication events for
*audit logging*, not for driving lockout state.

---

## 1. Enforcement layer

### Options surveyed

| Layer | What it gives | Verdict |
|---|---|---|
| `OncePerRequestFilter` on `/api/auth/login` before Spring Security | Rejects throttled IPs before any DB/auth work; cheapest rejection; works even if auth plumbing changes | Valid optimization; extra plumbing for path matching + manual 429 response; ordering inside/before the security chain must be pinned |
| `AuthenticationEventPublisher` + `@EventListener` | `DefaultAuthenticationEventPublisher` maps exceptions to events: `BadCredentialsException` **and** `UsernameNotFoundException` → `AuthenticationFailureBadCredentialsEvent`; `LockedException` → `AuthenticationFailureLockedEvent`; success → `AuthenticationSuccessEvent`. Listeners are servlet-API-independent. | Good for *observing* auth outcomes (audit log). Awkward for *driving* state: events only fire when authentication is actually attempted (a pre-blocked request emits nothing), the exception→event match is exact-match on the exception class, and a bad-credentials listener must re-load the user to skip nonexistent accounts (since `UsernameNotFoundException` collapses into the same event). |
| Service-level inside the login call | Explicit, sequential ordering; one transactional unit with the `users` row update; trivially unit-testable; no dependence on which filter/provider produced the failure | **Recommended core.** The security-critical ordering is written down in code, not emergent from wiring. |

Sources: Spring Security 6.5 "Authentication Events" reference — event types, `DefaultAuthenticationEventPublisher` bean setup, exact-match caveat, `setDefaultAuthenticationFailureEvent` catch-all:
https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/events.html
`DefaultAuthenticationEventPublisher` javadoc (additional exception mappings, default failure event):
https://www.springframework.org/spring-security/reference/7.0/api/java/org/springframework/security/authentication/DefaultAuthenticationEventPublisher.html

### Why this ordering composes correctly

The spec's third AC of Story 3 is the anti-DoS invariant: an attacker on one IP must
not be able to lock a victim's account by spamming bad passwords. That only holds if
the IP throttle is consulted **before** the account counter can move:

```
LoginService.login(username, rawPassword, request):
  ip = request.getRemoteAddr()
  if ipThrottle.isThrottled(ip):            → reject 429 (do NOT increment anything further)
  user = userRepository.findByUsername(username)   // may be absent
  if user != null && user.lockedUntil > clock.now(): → reject 401 generic (locked)
  try:
    auth = authenticationManager.authenticate(UsernamePasswordAuthenticationToken(username, rawPassword))
    // success: user.failedLoginAttempts = 0; user.lockedUntil = null; create session
  catch BadCredentials / UsernameNotFound:
    if user != null:                        // only real accounts accumulate counters
      user.failedLoginAttempts++
      if user.failedLoginAttempts >= N:     // (within observation window — see §5)
        user.lockedUntil = clock.now() + cooldown
    ipThrottle.recordFailure(ip)
    → reject 401 generic
```

Key invariants this produces:

- **Throttled attempts never reach credential verification**, so they cannot
  increment `failed_login_attempts` — the attacker loses the lever that locks
  accounts. This is what makes the two layers independent.
- **Lock rejections don't extend the lock**: a locked account check happens before
  credential verification, so correct-password-while-locked is rejected (spec AC)
  without re-arming `locked_until` (unless deliberately desired — recommend not).
- **Unknown usernames never create counters** (enumeration resistance: the response
  is the same generic error; no phantom rows).

This matches OWASP Bot Management guidance that a login endpoint needs **two
independent buckets** — per-username (here: the per-account lockout columns) and
per-IP — both under threshold for a request to proceed, and specifically warns
against keying one bucket on `ip+username` pairs (one IP could then try the full
threshold against unlimited usernames — exactly the spec's attack):
https://cheatsheetseries.owasp.org/cheatsheets/Bot_Management_and_Anti_Automation_Cheat_Sheet.html

OWASP Authentication Cheat Sheet: the failure counter "should be associated with
the account itself, rather than the source IP address"; policy parameters =
threshold + observation window + lockout duration; warns explicitly that lockout can
be abused for DoS (the attack our IP layer blunts):
https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html

### Where events still fit

Register a `DefaultAuthenticationEventPublisher` bean anyway — Spring Security
publishes `AuthenticationSuccessEvent` / `AbstractAuthenticationFailureEvent`
subtypes from `ProviderManager` — and let **audit logging** (ticket 06) consume them.
That keeps security-state transitions synchronous and testable in the service, while
log lines ride the standard event bus. Note: only attempts that reach
`AuthenticationManager` produce events; throttled/locked rejections must be logged
by the service itself.

Alternative (documented, not chosen): the classic listener pattern —
`ApplicationListener<AuthenticationFailureBadCredentialsEvent>` + a
`LoginAttemptService` — is the Baeldung reference implementation
(https://www.baeldung.com/spring-security-block-brute-force-authentication-attempts).
It works, but splits the ordering across listeners and still requires a lock check
inside `UserDetailsService` (throwing `LockedException`); more moving parts for the
same guarantees.

## 2. IP-throttle storage

**Recommendation: in-memory Caffeine cache keyed by IP, value = sliding-window
counter.** Single-instance demo; restart clears throttle state — acceptable and
documented (fail-open on restart; the *account* lockout is what the spec persists,
in `users.locked_until`).

| Approach | Deps | License | Fit |
|---|---|---|---|
| **Caffeine** `Cache<String, AttemptWindow>` | `com.github.ben-manes.caffeine:caffeine` (3.x, Java 11+) | Apache 2.0 | Recommended. `expireAfterWrite/Access` or per-entry `Expiry` for memory hygiene; `maximumSize` bounds memory vs. spoofed-IP key explosion. The *semantics* live in the value (`windowStart`/`Deque<Instant>` of failure times via injected `Clock`), so cache expiry is only cleanup — that keeps the `Clock` the single source of truth for tests. Eviction is periodic, not instant — fine, since correctness comes from the value. |
| **Bucket4j** `bucket4j_jdk17-core` 8.x | one extra dep, works on Java 21 | Apache 2.0 | Purpose-built token bucket; `ConsumptionProbe` yields nanos-until-refill → easy `Retry-After`. Real rate limiter rather than failure counter; distributed extensions exist (JCache/Hazelcast/Redis/etc.) if clustering ever arrives. Credible alternative; slightly more API surface than needed. |
| **DB table** (`login_attempts`/`ip_throttle`) | JPA entity + migration | — | Survives restart and multi-instance — neither required for the demo. Adds write load to the hot auth path and schema churn. Reject; note as the scale-out upgrade path (or Bucket4j-distributed). |

Sources:
- Caffeine: Apache 2.0 license, `expireAfterWrite`/`expireAfterAccess`/`expireAfter(Expiry)` semantics, periodic eviction maintenance:
  https://github.com/ben-manes/caffeine — https://github.com/ben-manes/caffeine/wiki/Eviction — https://javadoc.io/static/com.github.ben-manes.caffeine/caffeine/2.8.8/com/github/benmanes/caffeine/cache/Caffeine.html
- Bucket4j: Apache 2.0, token-bucket, `bucket4j_jdk17-core` on Maven Central, JCache/Hazelcast/Redis/etc. distributed backends:
  https://github.com/bucket4j/bucket4j — https://bucket4j.com/8.18.0/toc.html

## 3. Client IP sourcing

**Use `HttpServletRequest.getRemoteAddr()` only.** In the demo topology
(browser → `localhost:8080`, no proxy) it is the TCP peer address and cannot be
spoofed at the HTTP layer.

**Do not trust `X-Forwarded-For` unconditionally.** With no proxy stripping it, any
client can set/rotate the header — which would let an attacker *bypass* the IP
throttle entirely (each request claims a new IP). Spring's own `ForwardedHeaderFilter`
javadoc states the application cannot know whether forwarded headers came from a
trusted proxy or a malicious client, and the edge proxy must drop inbound
`Forwarded`/`X-Forwarded-*` headers:
https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/filter/ForwardedHeaderFilter.html

**Documented deployment assumption:** if the app is ever placed behind a reverse
proxy, enable `server.forward-headers-strategy: native` (Tomcat's `RemoteIpValve`
rewrites `getRemoteAddr()`, so service code is unchanged) or `framework`
(`ForwardedHeaderFilter`), *and* configure the proxy to strip/overwrite client-supplied
forwarded headers. Until then, read `getRemoteAddr()` directly.
(Spring issue discussing the two strategies being mutually exclusive:
https://github.com/spring-projects/spring-framework/issues/23260)

Precedent noted, not adopted: Baeldung reads `X-Forwarded-For` but only when it
*contains* `getRemoteAddr()` — a heuristic for proxy-aware deployments; overkill
and still fragile for a no-proxy demo.
https://www.baeldung.com/spring-security-block-brute-force-authentication-attempts

## 4. Testability

- **Injectable `Clock`:** declare `@Bean Clock clock() { return Clock.systemUTC(); }`
  and constructor-inject it into the login/throttle services. All expiry logic is
  `Instant.now(clock)` comparisons against `locked_until` / window timestamps. In
  tests, swap via `@TestConfiguration` (`Clock.fixed(...)` or a small mutable test
  clock) to advance past the 15-minute cooldown without sleeping. (For the Caffeine
  side, keep semantics in the value object as in §2 — no reliance on the cache's own
  ticker.)
- **Distinct source IPs in MockMvc:** `MockHttpServletRequestBuilder.remoteAddress(String)`
  exists since Spring Framework 6.0.10 (gh-30484 / gh-30510), e.g.
  `post("/api/auth/login").remoteAddress("10.0.0.7")`. Pre-6.0.10 equivalent:
  `.with(request -> { request.setRemoteAddr("10.0.0.7"); return request; })`.
  `X-Forwarded-For` headers can be set with `.header(...)`, but are only meaningful
  if forwarded-header handling is enabled — for this design they should be *ignored*
  (a good negative test: hammering with rotating XFF still throttles the real
  `remoteAddr`).
  Sources: https://docs.spring.io/spring-framework/docs/6.0.13/javadoc-api/org/springframework/test/web/servlet/request/MockHttpServletRequestBuilder.html — https://github.com/spring-projects/spring-framework/issues/30510
- **Suggested integration tests** (maps to Story 3 ACs):
  1. N bad logins on one account → `locked_until` set; correct password → 401.
  2. Advance `Clock` past cooldown → correct password → 200, counter 0.
  3. From IP-A, bad logins across ≥K distinct usernames → next request from IP-A →
     429; assert the victim account's `failed_login_attempts` stayed below N (still
     loggable from IP-B) — the anti-DoS AC.
  4. Same username attacked from IP-A but under IP threshold while IP-B keeps
     working → layer independence.

## 5. Counter reset semantics

- **Account counter — reset on success** (spec-mandated: "successful login …
  `failed_login_attempts` resets to 0"). Keycloak's brute-force detector does the
  same (on successful login → reset count) and uses a "failure reset time" measured
  from the *last* failed login as its observation window:
  https://github.com/keycloak/keycloak/blob/main/docs/documentation/server_admin/topics/threat/brute-force.adoc —
  https://docs.redhat.com/en/documentation/red_hat_build_of_keycloak/26.6/html/server_administration_guide/mitigating_security_threats
- **Observation window:** the spec says "N consecutive failures within a window".
  The `users` table has only `failed_login_attempts` + `locked_until` — no failure
  timestamp. Two ways to honor "within a window":
  - (a) add a nullable `last_failed_at`/`first_failure_at` column — failure resets
    the counter when `now - last_failed_at > window`. Small, flag-for-implementer
    deviation; makes the window literal (Keycloak-equivalent).
  - (b) minimal-schema reading: counter counts consecutive failures (reset on
    success and on lockout expiry); "window" is implicit. Simpler, but 4 failures
    yesterday + 1 today = lock, which weakens "within a window".
  - **Recommend (a)** — one extra timestamp column, trivial queries, spec-faithful.
    Flag to ticket 07 / implementer as a deliberate schema note.
- **IP bucket — do NOT reset on success.** The spec asks only that the account
  counter reset; the IP bucket should decay purely by sliding-window expiry.
  Rationale: on a shared egress IP (NAT), a legitimate user's success would
  otherwise launder an attacker's accumulated failures on that same IP — a
  co-tenancy bypass. OWASP treats the two buckets as independent with no
  cross-reset requirement. Counter-precedent: Baeldung's `loginSucceeded(key)`
  *does* invalidate the IP cache entry (friendlier to NAT'd users, weaker against
  shared-IP attackers). For a no-NAT demo either passes the ACs; the no-reset
  choice is strictly more conservative.

## Response codes / enumeration notes

- Locked account → same generic 401 "invalid credentials" as bad credentials —
  Keycloak precedent (locked users see the identical `Invalid username or password`
  message so lockout state isn't enumerable):
  https://docs.redhat.com/en/documentation/red_hat_build_of_keycloak/24.0/html/server_administration_guide/mitigating_security_threats
- IP-throttled → **429** (optionally `Retry-After`); throttling is a rate-limit
  contract and needn't be hidden. 401-generic is defensible but 429 is clearer for
  the SPA.

## Recommended design sketch (for implementer)

```
@Bean Clock clock() → Clock.systemUTC()

IpThrottleService (singleton)
  Caffeine Cache<String /*ip*/, Window> — maxSize ~10k, expireAfterAccess > window
  Window = { Instant windowStart; int failures }  (or Deque<Instant> capped at K)
  isThrottled(ip): failures ≥ K within window(now(clock))
  recordFailure(ip): slide/reset window, failures++

User entity
  + failedLoginAttempts int, lockedUntil Instant, (recommended) lastFailedAt Instant

LoginService.login(username, raw, ip)         // ordered exactly as §1
  ipThrottle.isThrottled → 429
  user?.lockedUntil > now → 401 generic
  authenticationManager.authenticate(...)   // session fixation + cookie handled by Spring Security
    success → reset counter, clear lock, audit-log success
    failure → increment counter (+window check), maybe set lockedUntil,
              ipThrottle.recordFailure(ip), audit-log failure, 401 generic

SecurityConfig
  @Bean AuthenticationEventPublisher → DefaultAuthenticationEventPublisher  // feeds audit logging (ticket 06)
  // no filter needed for throttle; keep it service-level
```

Config knobs (suggest `app.security.*`): `lockout.threshold=5`,
`lockout.cooldown=15m`, `lockout.window=10m`, `ip-throttle.threshold=20`,
`ip-throttle.window=10m`.
