---
status: accepted
---

# ADR-014: No sleep-based progressive delay

A failed login is not slowed down by holding the request, whether with `Thread.sleep`, a scheduled completion or
any other server-side wait. Progressive delay is a textbook anti-brute-force control, so a maintainer would plausibly
add it. On this stack it would be a denial of service that the application inflicts on itself.

## Context

- NIST SP 800-63B-4 §3.2.2 lists an increasing wait after failed attempts as one of three optional techniques
  (MAY). They are additions to the 100-attempt limit, and they exist to reduce the chance an attacker locks out the
  legitimate subscriber. So the wait is not an alternative to lockout, and comparing its strength with a lock misses
  its purpose (R-LCK-006).
- The API runs on blocking Tomcat, where each request holds a thread. A delay that sleeps holds that thread for the
  whole wait. Around 200 slow-failing logins at once would fill the connector's pool, and every other request would
  queue behind them. The attacker would get a larger outage than any lockout gives them, for free.
- The same technique can be built without holding a thread, by making the lock itself last longer. ADR-011 does
  that, so the technique is adopted in that form.
- The enumeration contract (ADR-033) already rejected a response-time floor on the password axis for similar
  reasons. Timing uniformity comes from the provider doing the same work, not from waiting.

## Decision

- **No server-side wait on any authentication path.** That covers sleeping, async completion delayed by a timer, and
  a per-account delay in `Retry-After`. The last one does not hold a thread, but it puts account state in a header
  (ADR-011).
- **The increasing wait is realised as the escalating lock duration** (ADR-011): 20, then 40, then 60 minutes,
  carried by `locked_until`.
- Of NIST's other two techniques, bot detection is declined on scope: no third-party service, no network egress,
  and a demo application. Risk-based signals need a baseline this application cannot learn. The IETF `RateLimit`
  headers are declined because they are still an Internet-Draft, and on the login route they would reveal limiter
  policy. Both declines are register rows, not part of this decision.

## Consequences

- Throttled clients learn to wait from a `429` with an integer `Retry-After` from the per-source or per-username
  bucket. At greedy refill that is almost always 1 second. No response is ever slowed down.
- Under ASVS 6.1.1 (L1), which names anti-automation, adaptive response and bot detection, this application has two
  of the three classes: limiters and lockout, plus the adaptive ladder. Bot detection stays unbuilt (R-LCK-003,
  R-LCK-006).
- A future move to a non-blocking server would remove the thread cost, but not the header leak. Reopening this
  decision would mean choosing between those two problems again.
- Evidence: by inspection, no authentication path contains a server-side wait. T-LCK-010 and T-LCK-011 assert the
  ladder that replaces it.

## Sources

- NIST SP 800-63B-4 (July 2025) §3.2.2, additional techniques (bot detection, an increasing wait "30 seconds up to an
  hour", risk-based or adaptive techniques).
- OWASP ASVS 5.0, 6.1.1 (L1).
- IETF draft-ietf-httpapi-ratelimit-headers (Internet-Draft, not an RFC).
- RFC 9110 (HTTP Semantics) §10.2.3 `Retry-After`; RFC 6585 §4 `429 Too Many Requests`.
