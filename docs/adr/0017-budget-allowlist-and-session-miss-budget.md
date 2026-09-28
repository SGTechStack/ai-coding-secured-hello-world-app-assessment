---
status: accepted
---

# ADR-017: The budget table stays a request-rate allowlist; unlisted routes are metered on session-store misses

The per-source budget table lists the routes it throttles on request rate. A route that is not listed is not
throttled on request rate by this application. Every request, listed or not, is instead subject to a second
per-source budget: session-store lookups that did not resolve. Default-deny, with a catch-all budget for unlisted
routes, is the reflex a maintainer would apply. It would put a limiter in front of `/api/admin/**` that cannot tell
an admin from an attacker.

## Context

- **The cost the table never saw is the database, not the route.** `SessionManagementFilter` is in the chain because
  an invalid-session strategy is configured. It first calls `containsContext`, which calls `getSession(false)`. Under
  Spring Session JDBC that is `JdbcIndexedSessionRepository.findById`: a join across the two session tables, with no
  validation of the id's format, in its own `PROPAGATION_REQUIRES_NEW` transaction. So any request carrying a
  Base64-decodable `SESSION` cookie costs one pooled connection and one SELECT, on any path and any method,
  unauthenticated. That includes `/actuator/health`.
- **Admin routes are deliberately unthrottled.** They are authenticated, factor-gated, and rate-limited by the human
  using them. The early limiter runs before `SecurityContextHolderFilter`, so it has no principal. Any catch-all
  budget it applied would let an attacker who cannot authenticate use up an admin's budget, including for the unlock
  and recovery tools.
- **A catch-all budget buys only a constant factor.** Sized generously enough not to trouble a human, it cuts a
  10,000-a-minute flood by perhaps 16 times. Distinct sources remain the attacker's choice, and a budget cannot
  change that.
- **The number of ids per request had to be fixed first.** The cookie resolver returns every cookie named `SESSION`,
  and a total miss tries every one. So one request could cost up to about 130 lookups, and a per-request token could
  not be priced. The build honours at most one resolved session id per request, with a paired `CookieSerializer` bean
  (REJ-083). That makes one miss one lookup.

## Decision

- **Two mechanisms for two costs.** The budget table is an allowlist for raw request rate. A route missing from it is
  not throttled on rate by this application. Raw request rate on unlisted routes is bounded at the infrastructure
  layer, as a declared deployer obligation. **Every** request is subject to the miss budget. So no route is
  unmetered. What varies is which cost is metered where.
- **The miss budget:**
  - axis: source key (ADR-020) → session-store lookups that did not resolve;
  - capacity 300 per 15 minutes, `app.security.rate-limit.session-miss.capacity` and `.window`;
  - call site: the existing early filter. It is a third call site of the same limiter component, not a new filter
    and not a new envelope producer.
- **Check on the way in, record on the way out.** A `try/finally` around `chain.doFilter` records a miss when
  `getRequestedSessionId() != null && !isRequestedSessionIdValid()`. Neither call costs a second lookup, because the
  session repository caches the requested session after `containsContext`. That caching is a dependency, so it is
  pinned by a test. If it ever broke, measuring the cost would double it.
- **Refusal comes before the lookup.** The response is `429` in the standard envelope with an integer
  `Retry-After`. The refusal must not create a session. It emits the rate-limit audit row with reason
  `RATE_LIMITED_SOURCE_MISSES`, and no session-end row.
- **Sizing follows a named cause.** On load the SPA makes one call, a self-read, and it fetches the CSRF token only
  when first needed. The fan-out comes from browser session restore and multiple tabs, each presenting the same stale
  cookie. So capacity is concurrent returning tabs per source × 2. Fifty staff behind one egress with three tabs each
  gives 300. That is 500 times below a 10,000-a-minute flood, and an order of magnitude above the worst legitimate
  morning.

## Consequences

- An admin with a live session pays nothing. An admin returning to an expired session pays about two misses. The
  session cookie has no `Max-Age`, and Spring Session does not clear a cookie that fails to resolve. The next
  CSRF-token fetch creates a session and rewrites the cookie, which heals it.
- The budget is keyed on a source the attacker chooses, so rotating sources restores throughput (R-RL-008). ASVS
  15.3.4 (L2) states the same caveat about client addresses. The emitter-side bound (ADR-019) is what caps the audit
  volume, and it does not depend on this budget.
- The deployer owns raw request rate on unlisted routes. The edge policy must name a per-source ceiling, and must
  aggregate IPv6 at a prefix the deployer chooses (R-OPS-006).
- A rejected alternative, declining to route unmatched paths early, is ADR-018.
- Tests: T-RL-016 (the miss budget on every route, including unlisted ones), T-RL-021 (no second lookup), T-RL-022
  (window binding), T-RL-023 (refusal emits the rate-limit row only and creates no session).

## Sources

- Spring Security 7.1.x: `SessionManagementFilter.doFilter`, `HttpSessionSecurityContextRepository.containsContext`,
  `SecurityContextHolderFilter` ordering.
- Spring Session 4.1.x: `JdbcIndexedSessionRepository.findById`, `JdbcHttpSessionConfiguration`
  (`PROPAGATION_REQUIRES_NEW`), `CookieHttpSessionIdResolver`.
- OWASP ASVS 5.0, 15.1.3 (L2) and 15.2.2 (L2) (document, then implement, the resource-limiting strategy); 15.3.4 (L2)
  (client IP for security decisions).
- RFC 9110 (HTTP Semantics) §10.2.3 `Retry-After`; RFC 6585 §4 `429 Too Many Requests`.
