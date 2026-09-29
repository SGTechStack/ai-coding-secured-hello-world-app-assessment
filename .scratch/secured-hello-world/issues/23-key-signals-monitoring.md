# 23: Key signals monitoring

**What to build:** Instrumentation for the four signals IM8 `lm-16` names — latency, traffic,
errors and saturation — exposed on a secured endpoint, plus health probes.

This control is covered by nothing in the PRD. The out-of-scope list excludes CI/CD and hosting
infrastructure, which would rule out dashboards and alert routing, but says nothing about the
application emitting metrics in the first place. So this is a genuine omission rather than a scope
decision, and at risk Level 2 it carries the highest severity of any gap in this build.

Scope discipline: this ticket produces **metrics the application exposes**, not a monitoring
stack. No Prometheus server, no Grafana, no alert manager — those are the hosting infrastructure
the PRD excludes. The deliverable is a scrapeable endpoint and a documented signal inventory, so
that a deployment can wire them up without changing application code.

Covers IM8 `lm-16`, which no PRD story reaches.

**Blocked by:** 01.

**Status:** ready-for-agent

**IM8 controls:** `lm-16` Key Signals Monitoring (primary); `as-7` Access Control Check
Enforcement; `as-13` Exposure of Internal System Details. *ASVS: V7 Logging and Monitoring,
V14 Configuration.*

- [ ] `spring-boot-starter-actuator` and Micrometer are added as declared dependencies — not
      assumed to be transitively present
- [ ] **Latency:** HTTP server request timing is recorded per endpoint and outcome, with
      percentiles published, and URI templates used as the tag so path variables do not explode
      the metric cardinality
- [ ] **Traffic:** request counts per endpoint and outcome
- [ ] **Errors:** 4xx and 5xx counted separately, since a spike in 401/403 is a security signal
      while a spike in 5xx is an availability one
- [ ] **Saturation:** JVM heap and non-heap memory, thread counts, and HikariCP connection-pool
      utilisation — the pool is the first thing to saturate in an app of this shape
- [ ] Authentication-specific counters: login success, login failure, lockout triggered, throttle
      engaged, and authorization denial — the signals that make a credential-stuffing attempt
      visible as a metric rather than only as log lines
- [ ] `/actuator/health` exposes liveness and readiness, with `readiness` reflecting database
      reachability
- [ ] Health detail is **not** exposed anonymously: `management.endpoint.health.show-details` is
      restricted, so an unauthenticated caller cannot enumerate component status
- [ ] Only `health`, `info` and the metrics scrape endpoint are exposed. `env`, `beans`,
      `configprops`, `heapdump` and `threaddump` are **not** — they leak internal detail and would
      be an `as-13` finding
- [ ] The actuator base path is covered by an explicit rule in the security filter chain rather
      than falling through to a permissive default
- [ ] The metrics endpoint requires authentication, or is bound to a separate management port not
      exposed alongside the application — state which choice was made and why
- [ ] No metric tag carries a username, email, session id or IP address; high-cardinality
      user-identifying tags are both a privacy problem and a cardinality problem
- [ ] A short signal inventory is documented — what each signal is, where it is exposed, and what
      a deployment would alert on — so the absent alerting stack is a wiring task, not a
      discovery task
- [ ] Test: the metrics endpoint rejects an unauthenticated request
- [ ] Test: `env` and `heapdump` return 404 or 401, not a payload
- [ ] Test: driving a login failure increments the login-failure counter
- [ ] Test: `/actuator/health` returns no component detail to an anonymous caller
