# 21 — Decide the observability signals and monitoring surface

Type: grilling
Status: open
Blocked by: 13

## Question

What metrics does this app expose, and what does the monitoring surface look like?

Graduated out of the map's "Not yet specified" section, because the research tier turned a vague
"observability someday" into a specific, top-severity compliance failure.

## Why this is now a ticket

From "Extract the IM8 and ARC controls that bind this app": **lm-16** (LR: 2 | MR: 2 — the highest
severity band) requires either Spring Boot Actuator or custom Micrometer meters with a
`MeterRegistry`, covering latency, traffic, errors, **and saturation**. Its FAIL condition is the
absence of *both* paths. The PRD never mentions metrics, so it fails lm-16 — and unlike the PRD's
other gaps, this one is not on its declared list, meaning nobody has consciously accepted it.

There is a direct collision to resolve here, which is why it needs a decision rather than a default:
lm-16 pushes us to add Actuator, while **as-13** (also LR: 2 | MR: 2) requires Actuator's
`exposure.include` to name exact endpoint IDs — a wildcard `*` is an explicit FAIL — and requires the
actuator base path to be authenticated. Adding Actuator to satisfy one control while misconfiguring
it fails another.

A second driver: the App Standard §6 "Observable Signals" lists what to watch — login and failed
login events, 429 rate-limit hits, lockout counts and automatic lifts, failed-to-successful login
ratios per account, session invalidation and timeout patterns, 403 authorization failures, CSRF token
endpoint access rates, and alerting on notification delivery failures.

## What to decide

- **Actuator or custom Micrometer meters, or both.** If Actuator: exactly which endpoint IDs are
  exposed (likely `health` and `info` only), the base path, and how it is authenticated. Resolve
  against as-13 in the same breath.
- **The meter set** covering all four lm-16 dimensions. Latency, traffic, and errors come largely
  free from `http.server.requests`; **saturation** is the one that needs thought — connection pool
  usage, thread pool, and the Caffeine cache bounds from the rate limiter are the candidates.
- **Security-specific meters** derived from the audit event catalogue: counters for login
  success/failure, lockouts triggered, 429s by limiter type (per-account versus per-IP), 403s, reset
  tokens issued and redeemed. These are the signals the App Standard §6 asks for, and they should be
  emitted alongside the audit events rather than as a separate mechanism.
- **Alert thresholds**, and explicitly which are alerts versus dashboard-only. The App Standard wants
  alerting on notification delivery failure and on dependency-scan findings above a severity
  threshold; the logging standard additionally requires an alert when **audit logging itself fails**,
  which is easy to overlook and is the one signal that invalidates all the others if it goes unnoticed.
- **How much is in scope.** Deployment and hosting are out of scope, so there is no Prometheus and no
  dashboard. Decide what "done" means here: exposing correct meters and documenting the thresholds,
  with collection left as a deployment obligation. Say so explicitly rather than implying a monitoring
  stack exists.
- Whether health check details leak information — a `health` endpoint showing database connection
  errors is an information-disclosure path under as-13.

## Done when

The Actuator-versus-Micrometer decision is made and reconciled with as-13, the meter set covers all
four lm-16 dimensions including saturation, the security meters trace back to the audit catalogue, and
the in-scope boundary is stated rather than assumed.
