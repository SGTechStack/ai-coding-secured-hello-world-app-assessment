# 21 — Decide the observability signals and monitoring surface

Type: grilling
Status: resolved
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

---

## Amendment from ticket 09 (lockout and dual rate limiting)

**One detection signal in this ticket is now load-bearing for a compliance deviation, which changes what
"deferring monitoring" costs.**

Ticket 09 declined NIST SP 800-63B-4 §3.2.2's cumulative cap — the `SHALL` that consecutive failed attempts
per account be capped at no more than 100 and enforced by **disabling** the authenticator. Our scheme has no
ceiling at all: 5 failures, 20-minute auto-lift, repeat, which is an unbounded ~12 guesses/hour against one
known username. The deviation is justified on calibration and failure mode, but its **compensating control is
detection, and that control lives here**:

> **Alert when a single account exceeds 50 failed logins in 24 hours.** Four times the paced-attack rate
> (4 failures per 20-minute window = ~288/day is the theoretical maximum; 50 fires on a real campaign and not
> on a forgetful user).

The uncomfortable part, recorded deliberately so ticket 18 does not have to find it: **ticket 01 already
graded lm-16 (no monitoring) an unacknowledged IM8 FAIL.** So as it stands, ticket 09's deviation from a NIST
`SHALL` is compensated by a control that is itself a known gap on this map. If this ticket does not land the
signal, **the deviation has no compensating control at all** and ticket 18 should be told that plainly rather
than shown a chain of two deferrals that cancel.

Two further signals ticket 09 owes this ticket, both from §6 of the standard's runbook (L536-538):

- **`429` rate by route**, which is now actionable because ticket 09 decided the breach WARN carries
  `source.ip.hash` (a domain-prefixed keyed HMAC, `ip:` — not a raw address, per ticket 03's unresolved
  `source.ip` contradiction) plus `url.path`, `trace.id` and `session.hash`. The per-account axis logs **no
  key at all**: it is a raw submitted username, which §3.4 L331 bans outright.
- **Lockout transitions, locked and lifted, and the ratio of failed to successful logins per account** —
  §6 L537-538. The lift is detectable without a scheduler because expiry is derived lazily, so "lifted" is
  observed at the next successful authentication rather than emitted at the moment the clock passes.

---

## Inherited from ticket 13 (audit event catalogue)

[Build the audit event catalogue](13-audit-event-catalogue.md) is resolved, so this ticket is unblocked.
What it hands over:

**Forty rows with `event.severity` and `error_follow_up_action` already assigned**, which is the routing
input this ticket needs.

> **Correction (found while deriving the alert taxonomy).** The `error_follow_up_action` half of that
> sentence is **false**. Ticket 13 assigns `event.severity` on every row, but its catalogue columns are
> `#`, Row, `event.action`, `event.type`, Level, Sev, Identity, Reason vocabulary — there is **no
> follow-up column**, and the field appears exactly once in the whole ticket, at line 583, on the
> audit-write-failure ERROR. So the field is unassigned for 39 of 40 rows. This matters because
> `Logging_AuthN_And_AuthZ_Events.md:85` sets `error_follow_up_action: true` on the locked-account row —
> which is row 3 — and ticket 13 reversed that row's **level** (ERROR → WARN) without addressing the
> flag. Any routing rule built on the field is therefore not derivable from this map. See §15 of the
> [verification asset](../research/boot-4.1-actuator-observability-and-nist-throttling-verification.md). `event.severity` is deliberately independent of `log.level` (the schema says it
"can be used for routing alerts independently of `log.level`"), so the severity column is the alerting
axis and the level column is the retention/filtering axis. Three rows are graded above their level for
exactly this reason: TOTP enrolment confirmed (INFO / **medium**, alert-worthy), factor tier-2 disable
(ERROR / **critical**), TOTP decrypt context mismatch (ERROR / **critical**).

**Four named detection commitments, three of which are compensating controls for declined requirements
and therefore not optional:**

1. **50 failed logins against one account in 24 hours** — ticket 09's sole compensating control for its
   NIST SP 800-63B-4 §3.2.2 deviation. It was **not computable** from the stream ticket 09 specified;
   ticket 13 made it computable *verbatim* by putting `user.id` on resolved failure rows. If this alert
   does not land, the deviation has no compensating control at all — and ticket 01 already recorded
   **lm-16, no monitoring, as an unacknowledged IM8 FAIL**, so this ticket is where that FAIL is either
   closed or confirmed.
2. **A high ratio of identity-absent to identity-present failures from one `source.ip_hash`** — a
   **username-enumeration signature**, and a signal no ticket on this map could produce before. Ticket 09
   §4 argued the submitted-string bucket was the only account-axis control on discovery traffic against
   accounts that do not exist; that traffic is now visible as well as bounded.
3. **TOTP enrolment confirmed** — ticket 23's compensating record for the failed NIST §4.1.2.1
   notification `SHALL`, paired with the 409 `FACTOR_ALREADY_ENROLLED` that a legitimate admin receives if
   someone enrolled ahead of them. Alert-worthy, not merely recorded.
4. **Factor tier-2 disable and TOTP decrypt context mismatch** — the break-glass trigger feeding ticket
   25's runbook, and the only detector of moved or swapped TOTP rows respectively.

**Audit-logging failure is one control with three halves**, and this ticket owns the escalation for all
three: the degraded row the emitter writes when a mandatory field is missing (fail soft at runtime, hard
at test time), the application-logger ERROR with `error_follow_up_action: true` when an audit write fails
after commit, and a Logback `<statusListener>` for appender-level failures the application never sees.
§3.4 requires alerting on all three; nothing in the application can escalate them without this ticket.

**Two ASVS L2 failures are yours to argue against, not to close.** 16.4.2 (logs protected from
modification) and 16.4.3 (shipped to a logically separate system) are recorded as **F** with deployer *Consolidated into the register (ticket 33): R-AUD-012, R-AUD-013. Amend the table by ID, not this list.*
obligations, because no application-side control closes them honestly. Note that V16 contains **no L1
requirement at all**, so neither bears on the declared L1 claim — but both are the kind of finding a
reviewer raises, and the inventory column set (destination, retention, who-can-read) exists so the answer
is on the page. *Consolidated into the register (ticket 33): R-AUD-013. Amend the table by ID, not this list.*

**Do not build alerting on `url.path` literals.** The Standalone standard externalises the API base path
via application properties, so path-keyed saved queries break on redeployment. Key on the static
`message` string and `event.*` fields; `url.path` carries the matched route pattern as context only.

**One thing that is explicitly not yours:** ASVS 16.3 scopes alerting and correlation out of the
application, and §3.3 of the logging standard says the same. This ticket owns the *signals and the
commitments*, not a SIEM.

---

## Verified external facts (asset)

A first draft of this ticket's round 1 was reviewed and found to contain four errors, all of the same kind:
arguments built on external facts that had not been checked. The facts are now verified against primary
sources and recorded once, so they are not re-derived:

[Boot 4.1 Actuator/Micrometer surface, actuator CVE preconditions, NIST SP 800-63B-4 §3.2.2](../research/boot-4.1-actuator-observability-and-nist-throttling-verification.md)

Three of its findings reach beyond this ticket and are flagged here so they are not lost if this ticket
resolves narrowly:

- **Ticket 03's OTel starter already enables OTLP metrics export, to `localhost:4318`, every 60 seconds,
  with a WARN per failed publish.** Micrometer's `OtlpConfig.url()` supplies a hardcoded default that Boot's
  null property falls through to. Unreviewed live behaviour affecting ticket 13's log stream and ticket 24's
  egress surface.
- **Ticket 09's hand-built `Caffeine<String, Bucket>` receives no cache meters** — auto-instrumentation only
  reaches caches behind a `CacheManager` — and needs `.recordStats()` on its builder, which is an amendment
  to ticket 09 rather than a choice available here.
- **NIST SP 800-63B-4 §3.2.2's cap is consecutive, not cumulative**, reset-on-success is a SHOULD, and
  disabling is exited by rebinding. Ticket 09 declined the `SHALL` on a cumulative reading; ticket 23 already
  recorded the correct one. This ticket cannot settle what it is compensating for until that is reconciled.

---

## Answer

**Actuator for the meters and nothing for the reader: `health` is the only endpoint exposed, metrics are
pushed over OTLP or not collected at all, and the alerting half of lm-16 is confirmed as a High rather than
closed.** The three things worth more than the decisions they support are that ticket 03 has been exporting
metrics to `localhost:4318` every 60 seconds since it chose the OpenTelemetry starter, that the alert
taxonomy cannot be routed off `error_follow_up_action` because ticket 13 never assigned it, and that rows 5
and 6 as catalogued were a log-amplification path aimed at the one file whose failure is §3.4's audit-failure
condition.

External facts are in the
[verification asset](../research/boot-4.1-actuator-observability-and-nist-throttling-verification.md),
seventeen sections. Of eleven external facts checked across this ticket, **five were wrong in the direction
that favoured the conclusion being argued** — which is the argument for the asset existing.

### 1. The metrics foundation and the exposed surface

`spring-boot-starter-actuator`, **plus** custom meters, so both of lm-16's alternative paths are satisfied
rather than one. The starter rather than hand-picked modules on **module completeness** — it resolves
`spring-boot-health`, `spring-boot-starter-micrometer-metrics`, `spring-boot-actuator-autoconfigure` and
`micrometer-observation`, and Boot 4 split the monolith so a missing module is easy to miss. **Not** on CVE
avoidance: CVE-2026-40976 required an application with no Spring Security configuration of its own relying on
the default chain, which is the opposite of our four security-config tickets, and 4.1.x is outside its range.
Citing it as a risk avoided would have been inconsistent on its face.

```yaml
management:
  endpoints:
    access: { default: none, max-permitted: read-only }
    web:
      exposure: { include: health }
      discovery: { enabled: false }
    jmx:
      exposure: { include: health }        # already the default since Boot 3.0; written so the grep sees it
  endpoint:
    health:
      access: read-only
      show-details: never                  # show-components deliberately unset, so it inherits
      probes: { enabled: false }
  health:
    db: { enabled: false }
    diskspace: { path: ${app.audit.log-dir}, threshold: <§8> }
  cloudfoundry: { enabled: false }
  otlp:
    metrics:
      export: { enabled: false }           # §3
server:
  tomcat:
    mbeanregistry: { enabled: true }       # §5
```

Every value written explicitly even where it equals the default, because `im8-review` greps the literal
`management.endpoints.web.exposure.include` and, unlike the `server.error.*` bullets, states no verdict for
absence. `max-permitted: read-only` is the entry that matters most: `access.default: none` is only a
*default*, and **every** built-in endpoint except `heapdump` and `shutdown` carries an individual default of
`unrestricted`, so a future `management.endpoint.env.access=unrestricted` would beat it. `max-permitted` caps
the default and per-endpoint values both.

**`info` is not exposed, on a no-benefit reason rather than a disclosure reason.** An earlier draft claimed
Boot 4.1's new `process.workingDirectory` field made as-13's own `health,info` example unsafe. That is wrong:
`management.info.process.enabled` defaults `false` and the reference states the `env`, `java`, `os` and
`process` contributors are disabled absent prerequisites. What remains is thinner — `build` and `git` are on
by default but **conditional on `git.properties` / `build-info.properties` existing**, and this project
generates neither, so a default `info` endpoint is empty. Nothing to expose, so nothing is exposed; if a
version string is ever wanted the cost is an abbreviated commit id and the build-info file, which is mild.

**Probes disabled, and the test asserts the outcome.** Boot 4 turned liveness/readiness on by default (they
were platform-conditional in 3.x) and nothing in scope consumes a probe. The property is
`management.endpoint.health.probes.enabled`; `management.health.probes.enabled` exists but is deprecated at
`level: error` and is unbound. `management.health.livenessstate.enabled` is **not** a second lever on this —
it only controls whether the contributor joins the primary composite, and probes-off plus
`livenessstate.enabled=true` still yields 404. But a real second lever does exist: **an explicit
`management.endpoint.health.group.liveness.*` declaration** builds a group independently of `probes.enabled`.
So the assertion is on the outcome — both probe paths return **404** — not on the property. Two smaller
findings recorded: Boot 3.x's `ProbesCondition`, which OR-ed in Kubernetes/CloudFoundry detection, is **gone
in 4.1**, so no platform path can re-enable them; and probes-off plus a leftover `liveness` group referencing
`livenessState` **fails context refresh** via `HealthContributorMembershipValidator`. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-OBS-008. Amend the table by ID, not this list.*

`management.cloudfoundry.enabled: false` is defence in depth only. It cannot carry a compliance predicate,
because CVE-2026-22733 lists neither CloudFoundry deployment nor that property among its preconditions — see
§4 below.

**Actuator's health body is a seventh envelope producer** against tickets 06 and 08's six: a 503 health
response is `{"status":"DOWN"}`, not `application/problem+json`. Documented exemption, asserted by a test
pinned to `/actuator/**` and nothing else, on the grounds that actuator is infrastructure rather than the API
surface the enumeration contract protects. Decision A removed the `metrics` endpoint, not this one, so the
exemption survives A untouched. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUTH-017. Amend the table by ID, not this list.*

### 2. Authorization: two rules, and `show-details` is what makes the first one safe

`ManagementWebSecurityAutoConfiguration` backs off entirely once we declare a `SecurityFilterChain`, which we
do in four tickets. So the "health public, everything else secured" default is not there, and `/actuator/**`
matches whatever our chain says — which under ticket 11's default-deny-plus-whitelist is neither an `/api/**`
path nor a whitelisted one. This repo's own precedent grades the resulting permit-all as an ac-1 WARN.

```java
.requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
.requestMatchers(EndpointRequest.toAnyEndpoint()).denyAll()
```

`EndpointRequest` moved package in Boot 4 to
`org.springframework.boot.security.autoconfigure.actuate.web.servlet`. `to(...)` matches the endpoint root
**and all its subpaths**, so the first rule covers `/actuator/health/**` — which is safe only because
`show-details: never` makes `HealthEndpointSupport` return `null` for every non-root path, giving 404 on
`/actuator/health/db` and friends. The two settings are one interlock, and the test says so.

`denyAll` is explicit rather than left to `anyRequest()`, so an endpoint enabled by accident later is denied
rather than inheriting. A third assertion confirms `ManagementWebSecurityAutoConfiguration` **did** back off,
because the public-health behaviour must come from our rule and not from an auto-configuration we disabled
without noticing. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-OBS-009, T-OBS-010, T-OBS-011. Amend the table by ID, not this list.*

Decision A deleted the middle rule this section used to need. There is no ADMIN-plus-factor actuator rule, no
as-13 justification paragraph, and no "automated collection is impossible by design" concession.

**The two actuator CVE regression guards, aimed correctly.** A test asserting "no application endpoint beneath
`/actuator/**`" detects **neither** CVE-2026-22731 nor CVE-2026-22733, because both paths sit outside
`/actuator`: the first needs a health-group `additional-path`, which takes a mandatory `server:`/`management:`
prefix and a **single path segment** and therefore lives on a server root; the second is
`/cloudfoundryapplication` at the application root. Correct predicates: **no
`management.endpoint.health.group.*.additional-path` is set in any profile or config source** (sufficient
alone, since the preconditions are conjunctive), and **no application route resolves at or beneath
`/cloudfoundryapplication`** including `server.servlet.context-path`. Both fixed at 4.0.4, so on 4.1.x these
are guards against a configuration shape, not vulnerability mitigations — stated, or they read stronger than
they are. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CFG-015, T-CFG-016. Amend the table by ID, not this list.*

### 3. Push, not expose — and the export that was already running

Exposing `/actuator/metrics` and pushing over OTLP are both inert until a deployer supplies something, so
observability is not the discriminator. The discriminator is **surface**: an unconfigured exporter is dormant
code, an authenticated endpoint is a permanent surface in every deployment, and surface is the whole of what
as-13 governs. lm-16 passes identically either way.

**The finding that makes this urgent rather than tidy.** Ticket 03 chose `spring-boot-starter-opentelemetry`
for `trace.id`. That starter brings `micrometer-registry-otlp` and `spring-boot-opentelemetry`, which is
exactly the pair `OtlpMetricsExportAutoConfiguration` is conditional on. Its `ConnectionDetails` bean carries
**no `@ConditionalOnProperty`** — unlike the tracing and logging ones — so it always exists, and
`OtlpMetricsPropertiesConfigAdapter.url()` falls through to `OtlpConfig.super::url`, which hardcodes
`http://localhost:4318/v1/metrics`. Result: a publish attempt every 60 seconds, an INFO line at startup naming
the URL, and a **WARN per failure** into the stream ticket 13 spent a ticket keeping clean — where "spikes in
WARN volume" is itself a §6 signal, so the exporter would alert on itself.

The asymmetry explains why nobody caught it: **traces require a property, logs require a property and a
hand-installed appender, metrics require nothing.** Ticket 03 adopted the starter for the one signal that
needs an explicit endpoint and silently acquired the one that needs none. The Boot properties appendix shows a
blank default, because the default lives one layer down in Micrometer.

**The kill switch is `management.otlp.metrics.export.enabled: false`, and a property with no value does not
work.** Two mechanisms were considered and one fails: `management.otlp.metrics.export.url: ${OTLP_METRICS_URL}`
with the variable unset does **not** fail startup, because `PropertySourcesPlaceholdersResolver` builds its
helper with `ignoreUnresolvablePlaceholders = true`, so the binder stores the literal `${OTLP_METRICS_URL}` —
which `@NotBlank` accepts and which `OtlpConfig.validate()` accepts too, since `checkRequired` is a null check
with no URL syntax validation. That is **worse than loopback**, because it reads as configured. `@Value` and
`Environment.getProperty` do throw; the `@ConfigurationProperties` Binder is the one path that does not, which
is a general caveat on every fail-fast claim on this map that rests on binding.

So: `enabled: false` in the base profile, and on any profile that turns export on,
`ConfigurablePropertyResolver#setRequiredProperties` from an `EnvironmentPostProcessor` — which fires in
`prepareRefresh()` before bean instantiation and catches both absence and an unresolvable placeholder.

**These are two controls on two disjoint failures, not belt and braces**, and saying so is the point:
`setRequiredProperties` validates the `Environment`, and `ConnectionDetails` beans are built from container
metadata and never consult it. The human-config path is covered by required-property validation; the
service-connection path is covered only by bean absence. Neither is redundant. *Consolidated into the register (ticket 33): R-OBS-008. Amend the table by ID, not this list.*

That second path is real: the adapter reads the `ConnectionDetails` bean and **never reads the property**, so
a Docker Compose or Testcontainers connection (images `otel/opentelemetry-collector-contrib`,
`grafana/otel-lgtm`, or an `LgtmStackContainer` matched by type) deletes the only route to a configured URL,
for logs, metrics and traces alike. Assertions are therefore on absent beans —
`OtlpMeterRegistry`, `OtlpConfig`, `OtlpMetricsConnectionDetails` — never on the resolved url, which is never
null and reads as configured even when nothing was configured. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-OBS-012. Amend the table by ID, not this list.*

`management.otlp.metrics.export.headers.*` is the documented carrier for an OTLP backend's authorization
header, with env fallbacks `OTEL_EXPORTER_OTLP_HEADERS` and `OTEL_EXPORTER_OTLP_METRICS_HEADERS`. That makes it
a **fourth secret** for ticket 24, conditional on a deployer enabling export, and one whose namespace is a map.

### 4. The meter set

One counter, `security.audit.events`, incremented inside ticket 13's single `emit` — one call site, so a new
`AuditEvent` enum member gets its meter automatically and the meter cannot drift from the catalogue, the same
property that made the ASVS 16.1.1 inventory a generated snapshot. Tags: `action`, `outcome`, `reason`,
`severity`, and `route`. All closed enums; **no `user.id`, no `source.ip_hash`, no username** — the
low/high-cardinality rule bars them, which is why every per-account and per-source signal in §6 is log-derived
by construction rather than by preference.

`route` is a **constant the limiter supplies**, not a resolved path pattern, and this reverses an earlier
position. `http.server.requests` cannot carry 429-by-route: its `uri` tag comes from
`ServerRequestObservationContext.getPathPattern()`, which only a `HandlerMapping` inside `DispatcherServlet`
populates, and ticket 09's limiters reject inside the security filter chain. The observation *is* still
recorded — `ServerHttpObservationFilter` sits at `HIGHEST_PRECEDENCE + 1` against the chain's `-100` — but
`uri` falls through to **`UNKNOWN`**. Ticket 06's prohibition on `sendError` makes that the determined outcome
rather than one of two, since `sendError` could re-enter error dispatch and pick up `/error`. So the limiter
tags a stable logical name, which is bounded, available at the rejection point, and survives the externalised
base path that ticket 13 warned against keying queries on.

Where a real handler exists the split runs the other way: `/csrf` access rate comes from
`http.server.requests{uri}` for free, because that request reaches a handler.

### 5. Saturation

- **Hikari** — auto-bound; the pool is named so the tag is stable across profiles.
- **Tomcat threads** — `server.tomcat.mbeanregistry.enabled: true`, because `tomcat.*` is **not instrumented
  without it** and thread occupancy is this application's headline saturation story: BCrypt cost 12 holds a
  request thread for 300–400 ms, and ticket 09 declined progressive backoff *on thread-pool exhaustion
  grounds*. The meter that evidences that argument is off by default. ADR'd, because a property switched on
  for a reason living in another ticket is otherwise deleted as cruft.
- **Rate-limiter buckets** — explicit `CaffeineCacheMetrics.monitor(registry, cache, name)` **plus
  `.recordStats()` on ticket 09's builders**. Cache auto-instrumentation is
  `@ConditionalOnBean(CacheManager.class)` and iterates `CacheManager.getCacheNames()`, and
  `CaffeineCacheMeterBinderProvider` is typed to Spring's `CaffeineCache` wrapper — so ticket 09's hand-built
  `Caffeine<String, Bucket>` receives **nothing**. Without `recordStats()` every statistic reads zero and only
  `cache.size` registers. `monitor(...)` cannot retrofit it, so this is an **amendment to ticket 09**, and the
  test asserts `cache.gets` is non-zero after exercising the limiter rather than trusting the bind-time WARN.
- **Lock contention** — no new mechanism: `LOCK_TIMEOUT` is already row 34's reason vocabulary, so it arrives
  as a tag on the counter above. This is the only visibility into the lock convoy ticket 09 named, now that
  three tickets take pessimistic row locks under H2's pinned 1-second `LOCK_TIMEOUT`.
- **Free** — `logback.events`, `jvm.*`, `system.*`, `process.*`, `disk.*`.
- **Declined** — a session-row-count gauge: it needs a scheduled query, and scheduled jobs are the map's
  largest deferral. The bound is already enforced by ticket 09's budgets. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-OBS-001. Amend the table by ID, not this list.*

### 6. The alert taxonomy: three classes, and a discriminator that is not a field

`error_follow_up_action` **cannot carry this**, and the reason is worse than a conflict.
`Logging_AuthN_And_AuthZ_Events.md:85` sets it `true` on the locked-account row — row 3 — so a "follow-up ⇒
alert" rule classifies every lockout as an incident. And ticket 13 **never assigned the field**: it appears
once in the whole ticket, on the audit-write-failure ERROR, and there is no follow-up column, so this ticket's
own handover sentence claiming forty rows carried it was false. It also cannot reach the rows that need it:
`Structured_Logging_Application_Standard.md:186` scopes it to ERROR events and the encoder injects it into the
**sealed `error` object**, and while two recipes do use it on WARN rows — making :186 underspecified rather
than contradicted — the operative boundary is `event.outcome: failure`, and **no recipe applies it to a
success row**. Row 37 is a success row.

The discriminator is **does clearing this state require an action outside the normal flow?** It reads off
artifacts that already exist — row 4's `AUTO_LIFT | ADMIN_UNLOCK | RESET_REDEMPTION` enum and ticket 23's
tier model, where tier 1 auto-lifts and tier 2 clears only by rebinding — so the classification is a
consequence of earlier decisions rather than a column someone maintains. It is also what "alert" means
operationally: a human must do something.

**Per-event** (a single occurrence is already an incident): 15 authentication system failure, 37 TOTP
enrolment confirmed, 41 factor tier-2 disable, 42 TOTP decrypt context mismatch, 45 cap crossing, 46
breach-row truncation, 34 where `reason ∈ {LOCK_TIMEOUT, TRANSACTION_ROLLBACK}`, and the three halves of
audit-logging failure. Row 34 splitting by reason is the one place the class is a property of (row, reason) —
free, because reason is already a tag.

**Rate-above** (only the shape matters): 2, 3, 5, 6, 12, 13, 14, 20, 22, 23, 39, 40, and 34 on its other two
reasons. Every one of these clears itself — a bucket refills, a lock lifts, a user retypes.

**Rate-below**, the class the first draft had no home for: `Structured_Logging_Application_Standard.md:389`
asks for "sudden drops in log throughput", and Q7's residual is an absence of logs. Neither is a spike.

And the rule that explains why both rate-below signals exist and why the statusListener's companion is a
counter: **signals about the logging subsystem must not travel on the logging channel.** A meter survives
log-transport failure because it rides the metrics channel; a log line does not. The corollary is that the
total case cannot be in-process at all. Stated once, covering three things rather than two. *Consolidated into the register (ticket 33): R-OBS-003. Amend the table by ID, not this list.*

One honest limit: a counter falling to zero is observable only as absence-of-increase, which needs an observer
with memory. So what this ticket delivers for rate-below is the **meter and the floor**; the rate computation
sits in §11 with everything else that needs an observer.

### 7. Audit-logging failure: three halves

Halves one and two are ticket 13's — the degraded row when a mandatory field is missing, and the
application-logger ERROR when a write fails after commit. Half three, appender-level failure the application
never sees, has **no prescribed mechanism**: `<statusListener>` appears nowhere in the corpus. Narrowly
stated, because the corpus *does* prescribe delivery resilience — local disk buffering for a forwarding agent,
and monitoring for delivery resumption (`:391`, §3.5 `:275-277`).

A custom Logback `StatusListener` registered in `logback-spring.xml`, writing to stderr **and** incrementing a
dedicated counter, per the channel rule in §6.

The residual is narrower than an earlier draft claimed. Ticket 13 kept **both** a console appender in every
profile and a dedicated rolling file appender with `max-history: 90`, so a stdout failure leaves the file
working and vice versa — the claim that a stdout failure leaves nothing able to report was wrong. What remains
is simultaneous failure of both, or a Logback-internal failure preventing both, plus the disk-full path
ticket 13 named at `:602` — which §8 now detects. *Consolidated into the register (ticket 33): R-OBS-003. Amend the table by ID, not this list.*

### 8. The disk-space detector, and the arithmetic behind its number

`DiskSpaceHealthIndicator` stays enabled while `db` is disabled, and the asymmetry is the point: it is the
**only in-process detector of the condition that silently breaks ticket 13's rolling file appender**, disk-full
is §3.4's named audit-failure condition, and it costs a `getUsableSpace()` call with no pool or network
interaction. `db` goes on consumer-absence — the same argument that disabled the probes — and not on cost, because
`DataSourceHealthIndicator` runs **no SQL by default**: it borrows a connection, reads
`getDatabaseProductName()`, then calls `connection.isValid(0)`. An earlier "uncapped database-touching
endpoint" framing overstated it.

Health is therefore **ping + diskspace**: process liveness plus the one dependency whose exhaustion breaks a
compliance control, which makes a `DOWN` status meaningful rather than absent.

**Two figures from one arithmetic, and they are not the same number.** Sizing is `90 × daily`; the free-space
threshold is a **lead time**, `daily × lead_days`. Deriving the threshold from the retention total would park
health permanently `DOWN` on a correctly sized disk. `lead_days` is 2, recorded as a **judgement, not a
derivation**, with its recomputation trigger in the ADR and an acceptance item in §11 — because the deployer
sizes the disk and therefore owns recomputation, and the number stays arithmetically valid while becoming
semantically wrong if the mount is resized or the logs move. *Consolidated into the register (ticket 33): R-OBS-012. Amend the table by ID, not this list.*

`daily` is bounded only because ticket 13's second amendment bounds it — see §9. The default threshold of
**10 MB** is far below any useful line for a 90-day archive, and the default path is **`new File(".")`**, the
working directory rather than the log directory. Both are set deliberately. `management.health.diskspace.path`
and the appender's `<file>` consume **one externalised property**, asserted equal at resolved values, because
ticket 13 names no `logging.file.name` and an assertion against that property would be vacuous. On a
single-mount deployment the distinction is cosmetic and the property exists to be correct when it is not. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-OBS-013. Amend the table by ID, not this list.*

And `show-details: never` is **load-bearing** here rather than merely tidy: this indicator's `details` carry
`path` as an absolute filesystem path. The path-disclosure worry that was wrong about `info` is right about
`diskspace`.

### 9. Bounding audit volume, which was unbounded

Rows 5 and 6 as catalogued had **no per-transition annotation** while rows 3 and 40 did, so the default
reading was one audit row per rejected request — meaning the limiter bounded the work a request causes but not
the logging a rejection causes, and volume was set by the attacker's request rate. Amended in ticket 13 to
emit once per bucket-exhaustion transition.

That alone does not bound the per-IP axis: `maximumSize(10_000)` caps memory, not transitions, and eviction
only makes a source spend another budget to breach again. Transitions per window are
`min(distinct sources, requests_in_window / per_IP_budget)` with distinct sources attacker-chosen. So a second
amendment supplies the ceiling: **per-source breach rows for up to N distinct sources per window, then one
aggregate row carrying `source.distinct_count` and a truncation flag (row 46), and nothing further that
window.** Ceiling `N × windows/day`, which is §8's bounded input.

This answers ticket 13's own objection at the right layer — the loss is recorded in the stream rather than
performed silently by a file deleter — and it follows two precedents on this map, ticket 11's
`user.target.count` recording a count instead of a set, and the degraded row failing soft while recording the
degradation. One new parameter, no new mechanism. `total-size-cap` remains untouched, and its decline now rests
on a second reason: its arithmetic is reported inconsistently in the field, so it is unreliable as well as
silent, and what it deletes is audit records.

### 10. The §6 coverage matrix

Three outcomes per bullet — covered here, covered elsewhere with a pointer, or not producible with a stated
cause. Full table in the ticket's working notes; the entries that are not straightforward:

- **Session invalidation and timeout patterns, idle versus absolute** — *partial*. Absolute expiry and
  concurrent eviction are rows 9 and 10; **idle expiry is not producible**, because Spring Session JDBC
  publishes no session events and reaps by bulk DELETE. Ticket 13 declined a custom reaper on scope, so this
  is inherited rather than new.
- **Counts of users flagged for mandatory password change, and disabled users** — *not producible as rates*.
  These are state counts, not events; a count needs a scheduled query and scheduled jobs are deferred. The
  transitions are covered by rows 24 and 29.
- **Batch job execution and role revocation failures** — *out of scope*, both: hygiene jobs are the map's
  largest deferral and batch reset was declined in ticket 11.
- **Alert on notification delivery failure** — *subsumed by a declared deferral*, not "not producible". A stub
  that logs what it would send means delivery failure is total and known by construction, so an alert would
  fire on every notification. Ticket 23 already records the NIST §4.1.2.1 notification `SHALL` as a failure
  compensated by row 37; this points there rather than describing the same gap in a second vocabulary.
- **Alert on dependency-scan CVE findings above a severity threshold** — *covered elsewhere*, and stronger
  than §6 asks: `map.md:57-58` binds OWASP Dependency-Check to the Maven `verify` phase with a CVSS failure
  threshold, which **fails the build** rather than alerting, and ticket 01:122 maps it to ARC CTRL-0074 while
  noting it works without CI. Residual: a build-time gate cannot see a CVE published after the last build, and
  CVE-2026-40976 landing on Boot 4.0.x is this project's own example. Cadence and acceptance check in §11. *Consolidated into the register (ticket 33): R-BLD-010. Amend the table by ID, not this list.*

### 11. What closes, what is confirmed, and what the deployer inherits

**lm-16 is confirmed as a narrowed High, not closed.** Both automated paths pass. Its two manual-review flags
— dashboards and alert thresholds actually configured — cannot be discharged by an application with no
deployment. Ticket 01 recorded lm-16 as an *unacknowledged* FAIL; after this ticket it is an acknowledged,
narrowed, partially compensated **WARN at Level 2, i.e. High**, which ticket 18 carries. That is the direct
answer to the question this ticket asked itself: **confirmed and narrowed, not closed.** *Consolidated into the register (ticket 33): R-OBS-004. Amend the table by ID, not this list.*

**as-13 is declared, not silently failed.** `/actuator/health` as `permitAll` fails as-13's literal base-path
check. No grade attaches in the skill and this repo's precedent files unauthenticated `/actuator/**` under
**ac-1** as WARN/Medium — both control IDs cited so a reviewer does not count it twice. A citation correction
ticket 18 needs: lm-16's and as-13's PASS/FAIL conditions and LR/MR severities live in `im8-review/SKILL.md`
(`:710-733` and `:541-573`), **not** in `policies/references/im8-reform-app-policy.md`, which carries one line
per control with no severity and no Actuator text. *Consolidated into the register (ticket 33): R-OBS-005. Amend the table by ID, not this list.*

**One section for ticket 25**, "what this application cannot observe about itself", consolidated because three
of its five items are the same shape — a signal emitted correctly into a void — and four separate lines would
be graded as four gaps rather than one deployment boundary. Each item carries the control it compensates, the
finding left open if skipped, and an **acceptance check**, since an obligations list with no way to prove
discharge is read and skipped. Severity is **not** flattened: one is the High above, the rest are residuals.

1. Alerting and rate computation for both rate-above and rate-below classes. Acceptance: the thresholds in
   §6's table exist as rules in whatever consumes the stream. *Consolidated into the register (ticket 33): R-OBS-004. Amend the table by ID, not this list.*
2. External absence-of-logs detection, the one signal that cannot be in-process. Acceptance: an alert fires
   when the application stops emitting. *Consolidated into the register (ticket 33): R-OBS-003. Amend the table by ID, not this list.*
3. Re-enabling `probes.enabled` **and** `management.health.db.enabled` together, with
   `add-additional-paths` left `false`. Acceptance: the two probe paths return 200. *Consolidated into the register (ticket 33): R-OBS-006. Amend the table by ID, not this list.*
4. The OTLP collector — **two settings, not a URL**: flip `enabled`, supply the url under required-property
   validation, and note that a service-connection bean overrides both. Acceptance: the absent-bean assertions
   inverted. *Consolidated into the register (ticket 33): R-OBS-007. Amend the table by ID, not this list.*
5. Post-deployment dependency re-scan, with a stated cadence (monthly, and on any dependency change) and an
   acceptance check: a scan report dated inside the cadence. *Consolidated into the register (ticket 33): R-BLD-010. Amend the table by ID, not this list.*

Plus the disk item from §8: the deployer sizes the mount, so the deployer owns recomputing `threshold` when *Consolidated into the register (ticket 33): R-AUD-010. Amend the table by ID, not this list.*
the mount or the log location changes. *Consolidated into the register (ticket 33): R-OBS-012. Amend the table by ID, not this list.*

### 12. The seam with ticket 09, recorded as a rule

Ticket 09 is **reopened** and owns both thresholds; this ticket owns the emitter, the routing class and the
catalogue row. The table here is **parameterised with property keys named and covered by the binding test**, so
the hole has a known shape rather than being a TODO, and the forward dependency is a named owed input in the
register rather than a missing value. Ticket 09 also needs to know its **per-IP budget is now load-bearing for
disk sizing**, via §9. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-LCK-015. Amend the table by ID, not this list.*

This is the third instance of one ticket owning a mechanism while another owns its constants — ticket 13's
severity column, ticket 09's thresholds, and now ticket 09's per-IP budget. Two is a convention and three
without a written rule is how an untracked dependency appears on the fourth, so it goes in the map's working
agreements: **when a ticket owns a mechanism whose constants another ticket owns, the owning ticket names the
property keys and their binding test, and the dependency is registered as an owed input.**

### 13. Register, ADRs, glossary

**Eight register entries**: lm-16 WARN/High narrowed; as-13 base-path unauthenticated with the ac-1 precedent; *Consolidated into the register (ticket 33): R-OBS-004, R-OBS-005. Amend the table by ID, not this list.*
post-deployment re-scan residual; thresholds owed by ticket 09; never-executed assertions for the OTLP *Consolidated into the register (ticket 33): R-BLD-010. Amend the table by ID, not this list.*
enablement path per ticket 20's precedent; lm-16's frontend half declined; total log-transport failure *Consolidated into the register (ticket 33): R-OBS-008, R-OBS-009. Amend the table by ID, not this list.*
undetectable in-process; and ASVS 16.4.2/16.4.3 inherited from ticket 13 as F with deployer obligations, *Consolidated into the register (ticket 33): R-OBS-003. Amend the table by ID, not this list.*
recorded so this ticket is seen not to re-grade them. Plus a ninth, restated from the round: **audit volume *Consolidated into the register (ticket 33): R-AUD-012, R-AUD-013. Amend the table by ID, not this list.*
bounded by a stated per-window cap with truncation recorded** — a design note rather than an open residual,
which is what §9 bought. `lead_days` is **not** a register entry; it is a design constant with published
arithmetic, and it goes in the ADR as a recomputation trigger.

**Eight ADRs**: the exposure-and-access posture including `info` non-exposure on a no-benefit reason; *Consolidated into the ADR routing (ticket 34): ADR-061. Amend by ID, not this list.*
push-not-expose with the loopback-default finding as its motivation; probes and `db` disabled while *Consolidated into the ADR routing (ticket 34): ADR-061. Amend by ID, not this list.*
`diskspace` is kept; the three-class taxonomy and its state-clearing discriminator; the frontend-half decline; *Consolidated into the ADR routing (ticket 34): REJ-063 / REJ-064 / REJ-065. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-OBS-009. Amend the table by ID, not this list.*
the actuator envelope exemption from tickets 06 and 08's contract; `server.tomcat.mbeanregistry.enabled: true` *Consolidated into the ADR routing (ticket 34): REJ-066. Amend by ID, not this list.*
with its reason from ticket 09's argument; and the test-harness decision — disable otlp specifically rather *Consolidated into the ADR routing (ticket 34): REJ-067. Amend by ID, not this list.*
than the metrics defaults, plus `use-global-registry: false` — which earns one on the test this map has used
before, a wrong-looking-correct alternative whose failure mode is silent. *Consolidated into the ADR routing (ticket 34): REJ-068. Amend by ID, not this list.*

**Five glossary terms**: *per-event alert*, *rate-above*, *rate-below*, *transition-keyed row*, *observability
boundary*.

### 14. The test surface, which is unusually strong here

Disabling OTLP export brings `SimpleMeterRegistry` back, so **every meter claim is a unit assertion against an
injected `MeterRegistry`** rather than an argument: the Caffeine `recordStats` trap becomes "the gauge exists
and moves", and `uri=UNKNOWN` becomes a value read off a meter rather than a mechanism derived from
`ServerRequestObservationContext`.

Two traps that make this work only if done precisely. **Do not use the blanket switch** —
`management.defaults.metrics.export.enabled: false` suppresses `SimpleMeterRegistry` too, and
`NoOpMeterRegistryConfiguration` contributes an empty `CompositeMeterRegistry`, so every assertion silently
finds no meter. That is the inert-control shape a fourth time, in the harness. And
`management.metrics.use-global-registry: false` in the test profile, which the 4.1 properties appendix
recommends for test independence — cited as the appendix and the `MetricsProperties` javadoc, not as a testing
chapter, because the narrative Metrics chapter never mentions it.

Assertions, all outcome-based per the principle this ticket adopted three times: absent beans for the
exporter; 404 on both probe paths; `uri=UNKNOWN` with `status=429` on a short-circuited request, with
`max-uri-tags` (100) confirmed unreached; `cache.gets` non-zero after exercising the limiter; the resolved
diskspace path equal to the appender's; `ManagementWebSecurityAutoConfiguration` backed off; no
`UserCache` bean (ticket 09's, but surfaced here); and the two CVE configuration predicates. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-OBS-014, T-AUTH-003, T-OBS-008, T-OBS-011, T-CFG-015, T-CFG-016, T-OBS-012, T-OBS-001, T-OBS-013; retired items are recorded in ticket 32's reconciliation. Amend the table by ID, not this list.*

### 15. Declined

**The frontend half of lm-16**, named as a declared partial rather than silently failed. Its only beneficiary
is an automated check pointed at telemetry infrastructure we do not have, and the price is a client
error-ingestion endpoint that reopens four settled decisions at once — ticket 11's default-deny, ticket 09's
rate-limit axes, ticket 06's envelope, and ticket 13's structural closure of content-bearing error messages,
which was closed on purpose by building an emitter that exposes no throwable. Ticket 14 inherits only what
as-13 already requires: an Error Boundary rendering a generic fallback, never `error.message`. *Consolidated into the register (ticket 33): R-OBS-009. Amend the table by ID, not this list.*

**A separate management port.** It would let network isolation substitute for authentication, but the behaviour
of the security filter chain on a separate management port is **not documented** in the 4.1 reference, and this
map does not build on unverified framework behaviour.

**`RateLimit` headers, a session-count gauge, `totalSizeCap`, and integer rate-above thresholds** — the last
deliberately, see the fog patch.

---

## Amendment from ticket 09 (§R) — your reopening is discharged, and what it bought you

**The cap is implemented at 100 consecutive failures, so what you are compensating for has changed: nothing on the
password axis is a declined `SHALL` any more.** ADR 3 flips from declined to implemented, which retires the
deviation whose compensating control was your own lm-16 gap. Your framing was right about the misreading and wrong *Consolidated into the ADR routing (ticket 34): ADR-013. Amend by ID, not this list.*
about the price — compliance was never "one integer and a flag"; it cost a recovery channel, a third limiter axis
and a dispatch-rule change in ticket 08.

### 1. Your alert threshold survives with its window deleted

**50, off `consecutive_failures_since_success`, once per transition.** The 24-hour window is dropped and **not
replaced**. Under reset-on-success it is better calibrated than the windowed version: a forgetful user's successes
clear it, and the paced attacker who never succeeds is unaffected.

**Consequence to state rather than fix:** the alert now carries **no rate information** — 50 failures in 5 hours
and 50 across two years emit identical rows. Ticket 13 already emits a timestamped per-attempt failed-login row, so
**you derive the rate from the stream**. Recorded explicitly because the obvious repair is a
`consecutive_failures_started_at` column, which would make a fourth counter on the user row.

### 2. The cross-account signature needs no new state, which keeps your fog at one patch

Row 45 is per-account and per-event, so ticket 09 §R.2's mass primitive produces **240 rows and no signature**. The
aggregate you need is a **windowed count over row-45 emissions** — *k* crossings within *W*. No distinct-key
tracking, no truncation to record, nothing to size, because cardinality is bounded by the transitions themselves.
So it is **not** row 46's mechanism and not a fourth class; it is a count over rows you already produce, and it
needs no baseline, so it stays out of your one fog patch.

### 3. Two further signals, and one meter that is now a control

- **Zero authenticable admins.** Ticket 11's two-admin invariant counts *enrolment*, not authenticability, and its
  guard is decrement-safe on mutation paths while the cap arrives through the **authentication** path, which no
  guard sees. So this is monitorable only. Consume ticket 11's single `authenticable` predicate — enabled,
  activated, not password-disabled, TOTP row present, not tier-2 disabled — rather than a second copy.
- **Operator rebinding invoked.** The only privileged path with no HTTP authentication in front of it. Either a
  genuine break-glass recovery or someone with shell access; both are worth waking up for.
- Your `.recordStats()` requirement now covers a **third** Caffeine structure, and it is not a bucket cache: the
  per-source bounded **set** backing the cardinality axis. Its saturation and eviction rate matter more than the
  bucket caches', because **eviction there is a bypass rather than a memory saving**.

### 4. Your disk arithmetic gains a bounded contributor and loses a worry

The cardinality axis adds one transition-keyed breach row per source per window — bounded by the same reasoning as
row 46 and small. Against that, the escalating lockout duration **reduces** audit volume per attacked account,
because the same 20 transitions to the cap are spread over 900 minutes instead of 400. `lead_days` is unaffected.

### 5. lm-16's narrowed-High grade is unchanged, but its stakes drop

Your alert is no longer the sole compensating control for a declined NIST `SHALL` — the `SHALL` is implemented. It
is still load-bearing, because reset-on-success makes the cap structurally blind to a compromised-but-active
account, which is the shape ticket 09 §R.9 keeps the per-account failed-to-successful ratio for. Note that ratio is
**different** from your enumeration signature, which is per `source.ip_hash`; only the per-account one is affected *Consolidated into the register (ticket 33): R-OBS-004. Amend the table by ID, not this list.*
by reset-on-success.

---

## Amendment from ticket 25 — the `info` endpoint argument contains its own reopening trigger

**§9's actuator reasoning is sound and then recommends the thing that breaks it, four sentences later.**

The argument as written: `build` and `git` info contributors "are on by default but conditional on
`git.properties` / `build-info.properties` existing, and this project generates neither, so a default `info`
endpoint is empty. Nothing to expose, so nothing is exposed." Correct — and verified externally: `git.properties`
comes from `git-commit-id-maven-plugin` (pre-configured by `spring-boot-starter-parent`, *not* by Boot's own
plugin, which emits `build-info.properties`), lands at `BOOT-INF/classes/git.properties`, and `mode=full`
republishes every key in it including committer name and email, remote origin URL, build host and build user.

The sentence that undoes it: "if a version string is ever wanted the cost is an abbreviated commit id and the
build-info file, **which is mild**."

So this ticket's own "nothing to expose" verdict rests on the absence of two files, while recommending one of them
as cheap. Two consequences:

1. **The absence is now a recorded reopening trigger**, not an incidental fact: adding either plugin reactivates the
   `info` exposure path, and it does so at *build* time, invisibly to any property-based control.
2. **The enforcement surface is not ticket 24's refresh-phase validator.** A `@ConfigurationProperties` validator
   cannot see a Maven plugin addition. Both filenames go onto ticket 24's **13.4.1 jar-content test** instead —
   one line, and it converts this from a declared position into an enforced one. Ticket 25's schema would otherwise
   grade it `procedural` on a row that can be `enforced` for free. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-BLD-004. Amend the table by ID, not this list.* *Consolidated into the register (ticket 33): R-BLD-015. Amend the table by ID, not this list.*

Scoping correction while here: the runtime git-metadata exposure is **ASVS 13.4.6 (L3)** plus 13.4.5 (L2), **not**
13.4.1 (L1) — `git.properties` is derived from `.git` rather than being the folder 13.4.1 names. And `denyAll` on
every endpoint but health already covers `/info` today, so this is defence of an argument rather than a live hole. *Consolidated into the register (ticket 33): R-BLD-015. Amend the table by ID, not this list.*

---

## Amendment from ticket 15 (threat model) — your `daily` has no ceiling, and your admin signal has three blind channels

Two items. The first says an input you sized against is attacker-controlled; the second widens a signal you
were handed.

### 1. `daily` is not a property of the workload — TM-01

Your §8 publishes two figures deliberately as different arithmetic: disk sizing `90 × daily`, and
`management.health.diskspace.threshold` as `daily × lead_days` with `lead_days = 2` recorded as a judgement
rather than a derivation. That framing was right, and so was handing the deployer the recomputation duty. What
neither you nor ticket 13 knew is that **`daily` has no application-side ceiling.**

Ticket 13 found and closed one amplification path — rows 5 and 6, where "volume is then set by the attacker's
request rate, not by the limiter" — with row 46's per-window distinct-source truncation cap. Ticket 15 found the
same shape on rows that row 46 does not cover:

- Spring Security's ordering is verified as exploit protection **before** authentication and authorization
  ([verification asset §1](../research/threat-model-external-fact-verification.md)), so `CsrfFilter` is
  evaluated on **every path**, including paths matching no controller, before any authorization decision. A
  CSRF-less `POST` anywhere therefore emits **row 13**.
- `/api/admin/**` is *deliberately* unthrottled (`09:358-360`), and anonymous or under-privileged traffic there
  emits **rows 12 and 14**.
- Ticket 09's budget table is a **route allowlist** and no ticket states the default for a route absent from it.

Rows 12, 13 and 14 are all in your **rate-above** class — not transition-keyed, and row 46 caps only
`RATE_LIMITED_SOURCE`. So the flood is unbounded, its target is the file ticket 13 gave no `total-size-cap`, and
disk-full is §3.4's own audit-failure condition — the one your `diskspace` indicator exists to catch. The
indicator still works; the *number* it is configured with was computed from an attacker-controlled input.

Owned by **[ticket 26](26-unbudgeted-routes-and-audit-volume.md)**, which also discharges the seam-rule debt your
§14 left: **row 46's `N` has no value, no property key and no named binding test** anywhere in yours or ticket
13's text — `21:574-585` invokes the mechanism/constants rule and then does not name the constants, and
`21:515-517` puts the table in working notes that are not in the file. `N × windows/day` is the ceiling that has
to carry audit volume once the flood is bounded, so the two numbers belong in one session.

Your framing note for the handover stands and gets stronger: the deployer inherits a tuning obligation on
numbers chosen blind, and one of the inputs was not merely uncalibrated but unbounded. *Consolidated into the register (ticket 33): R-OBS-004, R-OBS-012. Amend the table by ID, not this list.*

### 2. The zero-authenticable-admins signal has three blind channels — TM-14

Ticket 11 §R.3 handed you a **zero-authenticable-admins signal** and one `authenticable` predicate with two
readers, spanning five terms across two tables. The count that predicate watches can now be changed by **three**
channels that no mutation path observes: ticket 12's `ON DELETE CASCADE` on `totp_user_details`, ticket 11
§R.3's NIST cap through the authentication path, and — new since both — ticket 09 §R.3's rebinding runner at
scope `totp`/`both` and ticket 25 §6's break-glass path, which clear a tier-2 disable and an enrolment with **no
authenticated session and no transaction the guard could join**.

**Amendment: the signal must observe all three.** The first two are already row-derived, so they arrive for
free. The out-of-band pair does not: both are CLI channels, and whether they emit an audit row your signal can
read is exactly what [ticket 28](28-out-of-band-privileged-channels.md) is deciding — the runner's row exists
(ticket 09 §R.3 gives it its own reason and asks you for an alert) but has **no nameable actor**, and the
break-glass path's audit trail is prose in ticket 25 rather than a catalogued row.

So this is a **named owed input** rather than a change you can make now: the signal is specified against five
terms, and the two channels that can zero it fastest are the two whose rows are not yet in ticket 13's
catalogue. Registered per the map's seam rule rather than left as a missing value.

### 3. One thing checked and found already correct

Your `/actuator/health` `permitAll` plus `show-details: never` interlock was re-examined from the attacker's
side rather than the compliance side, in case the subtree match gave an unbudgeted, unaudited endpoint. It does
not produce an audit row, so it is not a TM-01 carrier, and `EndpointRequest.to(HealthEndpoint.class)` matching
subpaths is safe only because `show-details: never` makes `HealthEndpointSupport` return `null` for every
non-root path — which is exactly what your text says. It **is** an unbudgeted route, so it lands inside ticket
26's default-for-unlisted-routes question, which is the right place for it rather than here.

---

## Amendment from ticket 28 — one per-event alert, and a new detection class with its cost attached

**1. Per-event: enrolled-admin count reaches zero.** The rebinding runner deliberately bypasses `AdminActionGuard`
(TM-14's third exception), so the two-admin invariant is **observable rather than enforced**. Its rows carry pre-
and post-operation enrolled-admin counts, and a transition to zero belongs in your per-event class. So does
**any** runner outcome row, which ticket 09 constraint 4 already asked to wake someone for.

**2. A new class: absence detection. "Intent with no outcome" is not a threshold.** [Ticket 28](28-out-of-band-privileged-channels.md)
§9 emits an intent row before the transaction and an outcome row after it. A run that dies between them leaves
the intent row alone. Detecting that needs **stateful correlation with a timeout**, which is the most
failure-prone alert shape there is, and it is new to this map. Two costs are named, not absorbed:
- Under ticket 28's offline process model the correlation can only complete **after restart**, so the timeout
  must exceed the recovery outage.
- The detector needs somewhere to hold open intents, and this application has no mechanism to do that. That puts
  it on the collector side, as a deployer obligation. *Consolidated into the register (ticket 33): R-RUN-010. Amend the table by ID, not this list.*

If you decline it, the intent row still stands as evidence for a human investigator. What would be lost is only
the automatic page. *Consolidated into the ADR routing (ticket 34): REJ-064 (attached amendment). Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-OBS-010. Amend the table by ID, not this list.*

---

## Amendment from ticket 29 (anonymous session-row growth)

**Your decline of a session-row gauge (`21:401-402`) is reversed, and on verified facts, not a new argument.**
Both of its reasons are false:

- Spring Session JDBC already runs its own `ThreadPoolTaskScheduler` for cleanup
  ([asset](../research/anonymous-session-growth-and-h2-file-verification.md) §A.3).
- A Micrometer gauge is evaluated at publish time, on the OTLP registry's own thread, so it needs no scheduler at
  all (§F.5).
- "The bound is already enforced by ticket 09's budgets" was true per source and false in aggregate.

Changes:

- **Health becomes ping + diskspace + `h2Data`.** `h2Data` is a custom indicator, not a second
  `DiskSpaceHealthIndicator`, because its threshold moves with the live row count. It shares one component with
  ticket 29's shed check and **never runs its own COUNT**, since `/actuator/health` is `permitAll`. Your `diskSpace`
  indicator on the audit directory stays, with its binding test. On a shared mount, `h2Data` goes DOWN first.
- **Two gauges:** anonymous `SPRING_SESSION` rows, and the H2 file's size read through `Files.size`. The second *Consolidated into the ADR routing (ticket 34): REJ-063 (attached amendment). Amend by ID, not this list.*
  survives an H2 panic.
- **"Two figures from different arithmetic" is preserved.** `h2Data`'s alert line sits above ticket 29's shed line
  for every `N`, and gains a count branch at 0.8 × `N_max`.
- **Row 5 `DISK_RESERVE_SHED` is per-event and row 47 is rate-above.** *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-OBS-013. Amend the table by ID, not this list.*

---

## Amendment from ticket 30 — "fewer than two" needs a gauge, and the gauge needs the deployer

The zero-authenticable-admins signal (21:676–679) gains a **gauge of `authenticable` admins**, reading ticket 11's
single predicate (11:816–818), with a deployer-side rule at **< 2**. It extends that signal, not ticket 28's
enrolled-count transition (21:802–805): a fresh install starts at one and never transitions, and two capped admins
still count as two enrolled.

- **No scheduler needed** (21:826–829), **but** export ships disabled (21:228–230) and health is the only exposed
  endpoint (21:187–188), so with export off nothing reads it. The alert is part of what the deployer owes under
  21:563 item 4, not something the application provides. lm-16's alerting half stays a High. *Consolidated into the register (ticket 33): R-OBS-007. Amend the table by ID, not this list.*
- **State object strongly held** or it reads `NaN` (29:235).
- A startup check was considered and dropped: it fires on every fresh install's first boot and never again.
