# No Application-Level IP Rate Limiting — Handled by WAF

**Decision:** This application does not implement IP-based rate limiting
or request throttling in its own code. That control is delegated to a
WAF (Web Application Firewall) or equivalent edge/CDN layer deployed in
front of the application.

**Context:**

Two related but distinct controls were considered together and are
addressed differently:

- **Per-account login lockout** — implemented in-app via
  `LoginAttemptService`/`LoginCountService` (see `ARCHITECTURE.md`,
  `auth` module). This stays in-app because it requires domain knowledge
  (which account, how many failed attempts) that an edge layer does not
  have.
- **IP-based rate limiting / request throttling** — not implemented in
  the application. Historically the codebase had an in-memory per-IP
  throttle (`ClientIpResolver` + a `ConcurrentHashMap` window in
  `LoginAttemptService`), which was removed rather than hardened.

**Justification:**

- An application-layer in-memory IP throttle cannot correctly enforce a
  rate limit across multiple app instances (each instance has its own
  map; an attacker distributed across instances evades the aggregate
  limit).
- It cannot see traffic before it reaches the app, so it provides no
  protection against volumetric/L3-L4 DDoS — only a WAF or CDN sitting in
  front of the app has that vantage point.
- It requires trusting a client-supplied `X-Forwarded-For` header to
  identify the "IP" to throttle, which is directly spoofable unless the
  app also verifies the request came through a known, trusted proxy — a
  topology concern that belongs at the edge, not in application code.
- A WAF has access to IP threat-intelligence reputation data the
  application has no visibility into, and can rate-limit before any
  compute cost is incurred by this app.
- Removing the in-app throttle also closes the associated spoofed
  `X-Forwarded-For` and unbounded-in-memory-map risks that existed while
  it was present (see `docs/artifacts/threat-model-secured-hello-world.md`,
  threats S6 and D1) — those risks are eliminated rather than carried
  forward once the responsibility moves to the WAF.

**Consequence:**

- `/api/register` and `/api/password-reset/request` (and any other
  `permitAll` endpoint) have no in-app request-rate protection. Anyone
  deploying this application **must** place a WAF or equivalent edge
  layer in front of it that enforces IP-based rate limiting; running this
  app directly exposed to the internet without one leaves those endpoints
  unprotected against request flooding.
- Per-account lockout (`LoginAttemptService`) remains the only in-app
  defense against credential-stuffing/brute-force on `/api/login`, and is
  unaffected by this decision.
- This is documented as a deployment prerequisite in `ARCHITECTURE.md`
  ("Known architectural limitations" and the security-header notes), not
  as a gap to fix in application code.

**What would change this determination:** if this application is ever
deployed without a WAF/edge rate-limiting layer guaranteed to be present
(e.g. a topology where the app is directly internet-facing), this
decision must be revisited and an in-app rate limiter (e.g. `bucket4j`,
keyed off a trusted proxy-verified client identifier, not a raw
`X-Forwarded-For` value) added before go-live.

**Related:**

- `ARCHITECTURE.md` — "Known architectural limitations" section states
  this same decision; this ADR is the durable record of the reasoning
  behind it.
- `docs/artifacts/threat-model-secured-hello-world.md` — threats S6
  (spoofed `X-Forwarded-For`) and D1 (unbounded in-memory throttle map),
  both closed by this decision rather than hardened.
- `docs/adr/ac-8-not-applicable.md` — sibling control-scope ADR using the
  same determination format.
