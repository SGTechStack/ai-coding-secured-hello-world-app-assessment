# Verification: Boot 4.1 Actuator / Micrometer surface, actuator CVE preconditions, NIST SP 800-63B-4 §3.2.2

Supporting [ticket 21](../issues/21-observability-signals.md). Every claim below was checked against a
primary source after a first draft of ticket 21's round 1 was found to contain four errors of exactly the
kind this file exists to prevent: arguments built on unverified external facts.

Versions in scope: Spring Boot **4.1.x** (pins Spring Framework 7.0.9, Micrometer 1.17.1, Spring Security
7.1.1), Java 21, H2. Verification date **2026-09-27**.

---

## 1. The `info` endpoint discloses nothing by default

| Property | Default |
|---|---|
| `management.info.build.enabled` | `true` |
| `management.info.git.enabled` | `true` |
| `management.info.env.enabled` | `false` |
| `management.info.java.enabled` | `false` |
| `management.info.os.enabled` | `false` |
| `management.info.process.enabled` | **`false`** |
| `management.info.ssl.enabled` | `false` |

Boot 4.1 reference, `actuator/endpoints.adoc`: with no prerequisites indicating they should be enabled, the
`env`, `java`, `os` and `process` contributors are disabled by default.

**Consequence.** The `process.workingDirectory` / `process.uptime` / `process.locale` fields added to the
`info` endpoint in Boot 4.1 **do not ship by default**. Any argument that Boot 4.1 made the `info` endpoint
disclose a filesystem path is wrong.

The two contributors that *are* on default are additionally **conditional on a build artifact existing**:
`GitInfoContributor` needs `git.properties`, `BuildInfoContributor` needs `META-INF/build-info.properties`,
and neither file is generated unless the Maven/Gradle plugin is configured to generate it. In `simple` mode
(`management.info.git.mode` default) the git contributor emits `git.branch`, `git.commit.id` (**abbreviated**,
via `getShortCommitId()`) and `git.commit.time`. `BuildInfoContributor` uses `Mode.FULL` and exposes the whole
properties file.

So for this application, which has made no decision to generate either file, a default `info` endpoint is
**empty**. The honest reason not to expose it is therefore *no benefit*, not *disclosure risk* — and if a
version string is ever wanted (pm-6 documentation pressure, ticket 25), the cost of exposing it is mild
version disclosure, not a path leak.

Sources: [4.1 properties appendix](https://docs.spring.io/spring-boot/4.1/appendix/application-properties/index.html),
[endpoints.adoc @ 4.1.x](https://github.com/spring-projects/spring-boot/blob/4.1.x/documentation/spring-boot-docs/src/docs/antora/modules/reference/pages/actuator/endpoints.adoc),
[`GitInfoContributor`](https://github.com/spring-projects/spring-boot/blob/4.1.x/module/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/info/GitInfoContributor.java).

---

## 2. Endpoint access and exposure defaults

- `management.endpoints.web.exposure.include` default **`health`**. `management.endpoints.jmx.exposure.include`
  default **`health`** — changed from `*` in Boot 2.x to `health` in **3.0.0**, so no JMX action is needed and
  one line in the record pre-empts the question.
- Per-endpoint access default is **`unrestricted` for every built-in endpoint except `heapdump` and
  `shutdown`**, which are `none`. Not "several endpoints" — all but two. Verified across all 25
  `management.endpoint.<id>.access` appendix rows, including `threaddump`, `sessions`, `startup`,
  `scheduledtasks`, `env`, `configprops`.
- `management.endpoints.access.max-permitted` exists, default **`unrestricted`**. Generated metadata:
  it caps an endpoint's individual access level **and** the default access. Reference guide: it takes
  precedence over both.

**Consequence.** `access.default: none` plus per-endpoint opt-in is not sufficient as a cap, because any
future `management.endpoint.<id>.access=unrestricted` beats the default. `max-permitted: read-only` is the
property that matches the intent.

Access ≠ exposure: `unrestricted` access on `env` does not make `env` reachable, because exposure defaults
to `health` only.

---

## 3. Probes: property name, and the Boot 4 default change

Correct name is **`management.endpoint.health.probes.enabled`**, default `true`.

`management.health.probes.enabled` also exists in the metadata but carries
`"deprecation": {"level": "error", "replacement": "management.endpoint.health.probes.enabled", "since": "2.3.2"}`.
At `level: error` it is no longer bound and is reported as invalid — so the widely-circulated short form
fails loudly rather than silently. Still worth a binding test, because the doctrine here is "write it
explicitly so the reviewer's grep sees it", and a property the grep sees but Boot ignores is the worst case.

Boot 4.0 Migration Guide, *Liveness and Readiness Probes*: the probes are now enabled by default, so the
health endpoint exposes the `liveness` and `readiness` groups by default; disable via
`management.endpoint.health.probes.enabled`. The prior Kubernetes/CloudFoundry-conditional activation is
implied by the guide but **not quoted verbatim there** — treat that half as unverified.

`management.endpoint.health.probes.add-additional-paths` default **`false`**; when `true` it publishes the
`liveness` group at `/livez` and the `readiness` group at `/readyz` **on the main server port**.

---

## 4. Actuator CVE preconditions — and why the obvious test predicate catches neither

### CVE-2026-40976 — does not apply to this application, twice over

Affected **4.0.0–4.0.5**, fixed **4.0.6**. Four stated preconditions, conjunctive, closing with
"If any of the above does not apply, the application is not vulnerable":

1. servlet-based web application
2. **no Spring Security configuration of its own, relying on the default web security filter chain**
3. depends on `spring-boot-actuator-autoconfigure`
4. does **not** depend on `spring-boot-health`

This application fails precondition 2 (own chain in tickets 05, 08, 11, 23) and is outside the version
range. So it was never in scope, and citing it as a risk avoided by dependency choice is invalid —
especially alongside the claim that we disarm the default chain, which is the same condition.

Note the advisory's wording is "Spring Security configuration of its own", not "`SecurityFilterChain`
bean". The mapping is sound but it is our interpretation.

**The defensible reason to use `spring-boot-starter-actuator` rather than hand-picked modules is module
completeness**, not CVE avoidance. Boot 4 split the actuator monolith; the starter resolves to
`spring-boot-starter`, `spring-boot-starter-micrometer-metrics`, `spring-boot-actuator-autoconfigure`,
`spring-boot-health`, `micrometer-observation`, `micrometer-jakarta9`. CVE-2026-40976 is background
evidence for why a missing module is easy to miss, not a threat we mitigate.

### CVE-2026-22731 — health group additional paths, on the server root

Affected 4.0.0–4.0.3 / 3.5.0–3.5.11 / 3.4.0–3.4.14; fixed 4.0.4 / 3.5.12. Conjunctive preconditions:
actuator on the classpath; a custom health group declared via
`management.endpoint.health.group.<name>.include`; that group exposed under an **additional path on the main
server**, the advisory's example being `management.endpoint.health.group.mygroup.additional-path=server:/healthz`;
and an authenticated application endpoint contributed under a subpath such as `/healthz/admin`.

The additional path takes a mandatory `server:` or `management:` prefix and **must be a single path
segment**, so it sits on the server or management **root** and is structurally incapable of living under
`/actuator`.

Whether `probes.add-additional-paths=true` creates the same exposure is **not addressed by the advisory**.
The mechanism is the same documented one (health groups on the main server port), so keeping it `false` is
prudence, not advisory compliance. Do not record it as CVE coverage.

### CVE-2026-22733 — the CloudFoundry path

Affected 4.0.0–4.0.3 and earlier lines; fixed 4.0.4 / 3.5.12. Preconditions: web application; actuator on
the classpath; **Spring Security on the classpath**; an authenticated application endpoint under a subpath
such as `/cloudfoundryapplication/admin`.

**Deployment to CloudFoundry is not a stated precondition, and `management.cloudfoundry.enabled` is not
mentioned.** "We do not run on CloudFoundry" therefore carries nothing as a compliance statement — the
stated conjunction is satisfiable without any CF deployment. Setting
`management.cloudfoundry.enabled: false` is documented as fully disabling those endpoints and is reasonable
defence in depth, but it cannot carry the predicate. With a context path set, the CF endpoints resolve
beneath it (`/app/cloudfoundryapplication/*`).

### The test predicate

**A test asserting "no application endpoint is mapped beneath `/actuator/**`" detects neither CVE** — both
paths sit outside `/actuator`. Coverage is zero, and the test passes vacuously.

Correct predicates:

1. No property matching `management.endpoint.health.group.*.additional-path` is set in **any** profile or
   config source — sufficient on its own, because the preconditions are conjunctive. A test reading only
   `application.yml` is weaker than the predicate; profile overlays and environment overrides matter.
2. No application handler mapping is equal to or nested beneath `/cloudfoundryapplication`, evaluated
   against the **resolved** path including `server.servlet.context-path`.
3. Optionally `probes.add-additional-paths` is not `true` — prudence, not advisory-grounded.

The real invariant: *no application route is mapped at or beneath any actuator-owned path, wherever
actuator paths actually resolve* — and actuator-owned paths are not confined to `/actuator`.

Both CVEs are fixed at 4.0.4, so on 4.1.x these assertions are **regression guards against a configuration
shape**, not vulnerability mitigations. Say so, or they read as stronger than they are.

As of 2026-09-27 no Spring advisory lists any **4.1.x** version as affected, and no Actuator advisory
appears after these three. That is absence of evidence from a non-exhaustive index sweep, not a clean bill.

---

## 5. OTLP metrics export: available already, and **already exporting by default**

- Property prefix is `management.otlp.metrics.export`, and the key is **`url`**, not `endpoint`
  (`endpoint` belongs to OTLP tracing/logging). `enabled` default `true`, `step` default **`1m`**.
- `resource-attributes` is **not** under this prefix in 4.1; it moved to
  `management.opentelemetry.resource-attributes.*`, shared across signals.
- **The URL has an effective default.** Boot's own property is null and the appendix row is blank, but
  `OtlpMetricsPropertiesConfigAdapter.url()` falls through to `OtlpConfig.super::url`, and Micrometer
  1.17.1's `OtlpConfig.url()` checks `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT`, then
  `OTEL_EXPORTER_OTLP_ENDPOINT`, then hardcodes **`http://localhost:4318/v1/metrics`**.
- Runtime behaviour with nothing listening: `PushMeterRegistry` schedules at fixed rate every `step`, so
  **once a minute, indefinitely**. Startup emits an INFO line naming the URL. Each failed publish is caught
  and logged at **WARN** — `"Failed to publish metrics to OTLP receiver (context: ...)"` — and does not
  propagate.
- Dependency: **`io.micrometer:micrometer-registry-otlp` is required**, and OpenTelemetry *tracing* on the
  classpath does not supply it. `micrometer-tracing-bridge-otel`'s published POM does not bring it.
  `spring-boot-starter-actuator` does not bring it. **`spring-boot-starter-opentelemetry` does**
  (`runtimeOnly`), along with `spring-boot-opentelemetry` — and
  `OtlpMetricsExportAutoConfiguration` is `@ConditionalOnClass({OtlpMeterRegistry, OpenTelemetryProperties})`,
  so it needs **both**. Adding `micrometer-registry-otlp` alone to an actuator app is not enough.
- `SimpleMeterRegistry` is `@ConditionalOnMissingBean(MeterRegistry.class)` and the OTLP auto-configuration
  declares `before = SimpleMetricsExportAutoConfiguration.class`, so Simple backs off when OTLP is present.
  `management.simple.metrics.export.enabled` default `true`.

**Consequence for this map.** [Ticket 03](../issues/03-logging-schema-extraction.md) chose
`spring-boot-starter-opentelemetry` for `trace.id` (§ at lines 275, 307). That starter therefore already
puts the OTLP metrics registry on the classpath, and once `spring-boot-starter-actuator` joins it, **metrics
export is auto-configured and enabled, pointing at `localhost:4318`, attempting a publish every 60 seconds
and emitting a WARN each time it fails**. Nobody decided that.

Two things follow. First, "nothing is exported until a deployer sets a URL" is **false** — the opposite
holds, and any push-based design must neutralise the default explicitly. Second, this is a live finding
against ticket 03 and ticket 24 independent of ticket 21: a once-a-minute WARN lands in the log stream
ticket 13 spent a whole ticket keeping clean, and a periodic outbound connection leaves an application
whose egress ticket 24 never scoped.

Ticket 24's idiom is the fix: a property with **no default anywhere**, so absence fails fast rather than
silently resolving to a loopback address.

---

## 6. The `uri` tag on a request short-circuited inside the security filter chain

- `ServerHttpObservationFilter` is registered by `WebMvcObservationAutoConfiguration` at
  `Ordered.HIGHEST_PRECEDENCE + 1` (`Integer.MIN_VALUE + 1`), for `DispatcherType.REQUEST, ASYNC`.
- The Spring Security filter chain is at **`-100`** (`SecurityFilterProperties.DEFAULT_FILTER_ORDER =
  OrderedFilter.REQUEST_WRAPPER_FILTER_MAX_ORDER - 100`, where that constant is `0`). Note this moved off
  `SecurityProperties` in Boot 4.

So the observation filter sits **outside** the security chain by a wide margin, and the observation **is**
recorded even when a security filter writes the response and stops the chain: `doFilterInternal` starts the
observation before `filterChain.doFilter` and stops it in `finally`. A meter with `status=429`,
`outcome=CLIENT_ERROR` exists.

The `uri` tag, however, does **not** come from the convention reading `BEST_MATCHING_PATTERN_ATTRIBUTE`. In
Framework 7.0.x it comes from `ServerRequestObservationContext.getPathPattern()`, a context field that a
`HandlerMapping` pushes in — `RequestMappingInfoHandlerMapping.handleMatch` calls
`ServerHttpObservationFilter.findObservationContext(request).ifPresent(c -> c.setPathPattern(...))`. The
pattern is therefore populated **only once `DispatcherServlet` has resolved a handler**. The fix is not
"set the attribute".

`DefaultServerRequestObservationConvention.uri()` fallbacks, verbatim in effect: empty pattern → `root`;
null pattern with a 3xx response → `REDIRECTION`; null pattern with 404 → `NOT_FOUND`; otherwise
**`UNKNOWN`**.

**Consequence.** A 429 written by a filter inside the security chain yields `uri=UNKNOWN`, so
`http.server.requests` cannot supply §6's "429 rate by route". This is a composition of verified source
facts rather than a documented behaviour, and it is sensitive to one variable: `response.sendError(429)`
can re-enter Boot's error dispatch and pick up `/error` as the pattern, whereas `setStatus` + return
cannot. Ticket 06 **prohibits `sendError`**, which makes `UNKNOWN` the determined outcome rather than one
of two.

Test assertions that settle it: after one short-circuited request, a meter named `http.server.requests`
exists with `status=429` (proving the observation was recorded at all); its `uri` tag equals exactly
`UNKNOWN`; `outcome=CLIENT_ERROR`; and `management.metrics.web.server.max-uri-tags` (default **100**) has
not been reached, since `MaximumAllowableTagsMeterFilter` starts denying new `uri` values past the cap and
would mask the result.

---

## 7. Cache metrics do not reach a hand-built Caffeine cache

- `CacheMetricsAutoConfiguration` is `@ConditionalOnBean(CacheManager.class)` and
  `@ConditionalOnClass(MeterRegistry.class)`.
- `CacheMetricsRegistrarConfiguration` iterates strictly `CacheManager.getCacheNames()`.
- `CaffeineCacheMeterBinderProvider` is typed to Spring's `org.springframework.cache.caffeine.CaffeineCache`
  wrapper. A raw `com.github.benmanes.caffeine.cache.Cache` is **not** a match.
- Reference guide, *Cache Metrics*: only caches configured on startup are bound; caches created
  programmatically after startup require explicit registration, for which a `CacheMetricsRegistrar` bean is
  provided — and that registrar takes a Spring `Cache`, so it does not help a raw Caffeine cache either.

[Ticket 09](../issues/09-lockout-and-dual-rate-limiting.md) builds
`com.bucket4j:bucket4j_jdk17-core:8.20.0` plus **its own hand-built `Caffeine<String, Bucket>`** (line 417),
explicitly not the `bucket4j_jdk17-caffeine` module and explicitly not behind a `CacheManager`. So cache
auto-instrumentation registers **nothing** for the rate limiter buckets.

Second gate: Caffeine statistics are opt-in at build time via `Caffeine.recordStats()`. Micrometer's
`CaffeineCacheMetrics` javadoc states `recordStats()` is required for non-zero statistics, and the
constructor warns when `cache.policy().isRecordingStats()` is false: *no meters except `cache.size` will be
registered*. `monitor(...)` returns the cache unwrapped and cannot retrofit stats collection, so
`recordStats()` must be on ticket 09's builder — which makes this an **amendment to ticket 09**, not a
ticket 21 implementation detail.

So the working shape is `CaffeineCacheMetrics.monitor(registry, cache, name)` per bucket map **plus**
`.recordStats()` at construction, asserted by a test that `cache.gets` exists and is non-zero after
exercising the limiter. The bind-time WARN is a real detector but a WARN nobody reads is not a control.

---

## 8. NIST SP 800-63B-4 §3.2.2 — the cap is **consecutive**, and disabling is recoverable

Source: **final, July 2025**. [pages.nist.gov/800-63-4/sp800-63b.html](https://pages.nist.gov/800-63-4/sp800-63b.html)
(§3.2.2 anchor `#throttle`), [CSRC record](https://csrc.nist.gov/pubs/sp/800/63/b/4/final). Supersedes the
2020 edition. US federal public domain.

1. **Consecutive, not cumulative. SHALL.** The verifier SHALL limit *consecutive* failed authentication
   attempts using a specific authenticator on a single subscriber account to no more than 100 **by disabling
   that authenticator**. There is **no time window anywhere in §3.2.2** — no rolling-window language of any
   kind. Scoped per (subscriber account, authenticator).
2. §3.2.2 fires where §3.1 says it does, and for passwords it fires: §3.1.1.2 requires a rate-limiting
   mechanism "as described in Sec. 3.2.2".
3. **Reset on success is a SHOULD**, twice ("the verifier SHOULD disregard any previous failed attempts";
   "the verifier SHOULD reset the retry count"). The only SHALLs attached are the AAL constraint on the
   reset and the §4.2 fallback. **No time-based reset is sanctioned anywhere.**
4. **100 is an upper bound and agencies MAY impose lower limits.**
5. The named additional techniques are introduced as ways to *reduce the likelihood that an attacker locks
   the legitimate claimant out* — explicitly **additions to** the cap, all **MAY**: a wait after a failed
   attempt that **increases as the account approaches its maximum** (example given: *30 seconds up to an
   hour*); a **bot detection and mitigation challenge** (the word CAPTCHA never appears); and **risk-based
   or adaptive techniques** naming IP address, geolocation, request-pattern timing, browser metadata.
6. **Disabling is recoverable, not permanent.** Disabled authenticators SHALL rebind per §4.1; if the
   subscriber cannot authenticate at the required AAL, the §4.2 account-recovery procedures SHALL be used.
   §4.2 draws the line: replacing a forgotten password where the subscriber can authenticate with another
   authenticator is **binding a new authenticator under §4.1.2.1, explicitly not account recovery**.
   Recovery-code verification is itself subject to §3.2.2 throttling. Phrase it as an obligation on the
   authenticator's state ("SHALL be required to rebind"), not as a subscriber entitlement.
7. **The rationale names account-recovery burden.** The limit of 100 was chosen to balance the likelihood of
   a correct guess — the example given is *100 attempts against a six-digit decimal OTP output* — against
   the potential need for account recovery when the limit is exceeded. §4.4 confirms the state obligation is
   simply "state information on recent failed authentication attempts".
8. **Nothing on non-disclosure or HTTP signalling.** Zero occurrences of `Retry-After` or `indistinguish*`
   in the whole publication; no requirement that the response conceal that throttling engaged. Signalling
   is left entirely to the implementer.

### Consequences for tickets 09 and 21

- Compliance with the SHALL is **one integer plus a disable flag**, reset on success, no window to roll.
  A 24-hour rolling cumulative counter is **more machinery than the requirement**, so a detection-only
  substitute built that way is both more expensive than compliance and weaker than it.
- Ticket 09's amendment text in ticket 21 is internally inconsistent — it labels §3.2.2 a "cumulative cap"
  and then describes "consecutive failed attempts". [Ticket 23](../issues/23-totp-enrolment-stepup-and-reset-flows.md)
  already recorded the correct reading ("NIST's cap is *consecutive*, so our cumulative reading is stricter
  than the SHALL"). The map holds both readings; the wrong one needs deleting.
- Ticket 09's calibration argument for declining (~12 guesses/hour, ~105,000/year against a 15-character
  zxcvbn-3 credential) answers a question the corrected reading does not ask: you do not need a strength
  argument to decline a control that costs one integer. And ticket 09 independently observed that "100
  guesses is a six-digit-OTP number" — which is NIST's own stated rationale, and the move it licenses is
  *set the limit lower* (MAY), not *decline the cap*.
- Ticket 09's "permanent disable is an unauthenticated DoS" argument does not survive §3.2.2 plus §4.2:
  disabling is exited by rebinding, and [ticket 10](../issues/10-credential-flows.md) already decided that
  reset redemption clears the password lockout — so the rebinding channel NIST requires already exists on
  this map. What remains is friction, which is precisely the account-recovery burden NIST weighed and
  accepted when it chose 100.
- On progressive delay, ticket 09 §11 already conceded that its draft "alternatives, not additions"
  framing was wrong and confined its thread-pool-exhaustion argument to the **sleep-based** form, noting
  that "the `429`-expressed form does not have that problem, which is why only that form was ever on the
  table". Ticket 13 then corrected the parenthetical again, and ticket 23 §6 declined it a third time. The
  residue is a real gap: **the 429-expressed progressive delay is declined with no reason stated anywhere**,
  and the map's summary line compresses the decline to "thread-pool exhaustion", which is an argument
  against a form that was never proposed.

---

## 9. Unresolvable placeholders do **not** fail `@ConfigurationProperties` binding

Two candidate neutralisers for §5's loopback default were proposed and one of them does not work. Recording
the mechanism because the failure is silent and the property *looks* like a control.

Given `management.otlp.metrics.export.url: ${OTLP_METRICS_URL}` with the variable unset:

`PropertySourcesPlaceholdersResolver` (4.1.x) constructs its helper with `ignoreUnresolvablePlaceholders`
= **`true`** (the trailing argument):

```java
new PropertyPlaceholderHelper(SystemPropertyUtils.PLACEHOLDER_PREFIX,
        SystemPropertyUtils.PLACEHOLDER_SUFFIX, SystemPropertyUtils.VALUE_SEPARATOR,
        SystemPropertyUtils.ESCAPE_CHARACTER, true);
```

`PlaceholderParser.PartResolutionContext#handleUnresolvablePlaceholder` then returns
`toPlaceholderText(key)` rather than throwing, so the binder sets the property to the **literal string**
`${OTLP_METRICS_URL}`. `ConfigurationPropertiesBinder#getBinder` wires exactly this lenient resolver, and
`Binder#bindProperty` applies it to the raw value before conversion.

The three property paths differ, and only one is lenient:

| Path | Unset variable | Source |
|---|---|---|
| `@ConfigurationProperties` (Binder) | binds literal `${VAR}`, no exception | `PropertySourcesPlaceholdersResolver` (lenient helper) |
| `@Value("${VAR}")` | throws `PlaceholderResolutionException` (extends `IllegalArgumentException`) | `PlaceholderConfigurerSupport.ignoreUnresolvablePlaceholders = false` |
| `Environment.getProperty(...)` | throws | `AbstractPropertyResolver.ignoreUnresolvableNestedPlaceholders = false` |

Downstream, the literal is **not** caught either: `OtlpConfig.validate()`'s `checkRequired("url", ...)`
resolves to `Validated::required`, which is `invalidateWhen(Objects::isNull, ...)` — a null check with **no
URL syntax validation**. Boot's adapter overrides `url()` and so bypasses `OtlpConfig`'s own
format-validating accessor. The failure therefore surfaces as a per-publish WARN from
`OtlpMeterRegistry#publish()`'s blanket `catch (Exception e)`, once per `step`, plus a startup INFO line
naming the unresolved literal.

**`@Validated` + `@NotBlank` does not close it either**, because the literal `${VAR}` is a non-blank String.
This is the trap for ticket 24's idiom: that idiom works for secrets because absence binds null and the
constraint fires, whereas here absence binds null and **Micrometer substitutes loopback**. The property
cannot be the control at any strength.

**Two mechanisms that do fail startup:**

- `management.otlp.metrics.export.enabled: false` (or `management.defaults.metrics.export.enabled: false`)
  removes the auto-configuration via `@ConditionalOnEnabledMetricsExport("otlp")` before any adapter or
  registry exists. This is the only lever that reaches behind Micrometer's default.
- `ConfigurablePropertyResolver#setRequiredProperties(...)` from an `EnvironmentPostProcessor` or
  `ApplicationContextInitializer`. `AbstractApplicationContext#prepareRefresh()` calls
  `validateRequiredProperties()` before the bean factory is touched, and it catches **both** shapes:
  absence → `MissingRequiredPropertiesException`; unresolvable placeholder → `PlaceholderResolutionException`,
  because `validateRequiredProperties` reads through the strict `getProperty` path.

`spring.config.import` of a non-`optional:` location also fails, but the throw site was not read — treat as
high confidence, not source-proven.

## 10. OTLP metrics `ConnectionDetails` replaces the property rather than outranking it

`OtlpMetricsPropertiesConfigAdapter#url()`:

```java
return obtain((properties) -> this.connectionDetails.getUrl(), OtlpConfig.super::url);
```

The adapter reads the **`OtlpMetricsConnectionDetails` bean** and never reads `OtlpMetricsProperties.url`
directly; the property is reachable only through the fallback `PropertiesOtlpMetricsConnectionDetails`,
declared with a bare `@ConditionalOnMissingBean` (type deduced from the return type — the explicit
`@ConditionalOnMissingBean(OtlpMetricsConnectionDetails.class)` form is used by the *logging* module, so
grepping for the explicit spelling will miss it).

Resolution order: any `OtlpMetricsConnectionDetails` bean wins outright → else the property → else
`OtlpConfig.super.url()` → `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT`, `OTEL_EXPORTER_OTLP_ENDPOINT`, then
hardcoded `http://localhost:4318/v1/metrics`. Boot's own test
(`OtlpMetricsExportAutoConfigurationTests#testConnectionFactoryWithOverridesWhenUsingCustomConnectionDetails`)
asserts the inversion and asserts `doesNotHaveBean(PropertiesOtlpMetricsConnectionDetails.class)`.

Contributors, all packaged in `spring-boot-micrometer-metrics` and inert unless
`OtlpMetricsExportAutoConfiguration` is on the classpath:

| Factory | Trigger |
|---|---|
| `OpenTelemetryMetricsDockerComposeConnectionDetailsFactory` | Compose service with image `otel/opentelemetry-collector-contrib` or `grafana/otel-lgtm` |
| `OpenTelemetryMetricsContainerConnectionDetailsFactory` | Testcontainers `@ServiceConnection` on image `otel/opentelemetry-collector-contrib` |
| `GrafanaOpenTelemetryMetricsContainerConnectionDetailsFactory` | Testcontainers `@ServiceConnection` on `LgtmStackContainer`, matched by **container type**, not image |

**Assertion target.** `OtlpConfig#url()` is the wrong thing to assert on — it is never null and never empty,
and unconfigured it returns loopback, which reads as configured. Assert absent beans, per Boot's own tests:

```java
assertThat(context).doesNotHaveBean(OtlpMeterRegistry.class);
assertThat(context).doesNotHaveBean(OtlpConfig.class);
assertThat(context).doesNotHaveBean(OtlpMetricsConnectionDetails.class);
```

## 11. The signal asymmetry is structural

| Signal | Property | Default | Active with no config? |
|---|---|---|---|
| Traces | `management.opentelemetry.tracing.export.otlp.endpoint` | none | **No** — `ConnectionDetails` bean is `@ConditionalOnProperty(endpoint)`, exporters `@ConditionalOnBean(ConnectionDetails)` |
| Logs | `management.opentelemetry.logging.export.otlp.endpoint` | none | **No** — same gating, **plus** the OTel Logback appender is not shipped by Boot: it must be declared in `logback-spring.xml` and given an `OpenTelemetry` instance programmatically at startup |
| Metrics | `management.otlp.metrics.export.url` | none *in Boot* | **Yes** — the `ConnectionDetails` bean carries **no `@ConditionalOnProperty`**, so it always exists and falls through to Micrometer's loopback default |

So ticket 03 adopted `spring-boot-starter-opentelemetry` for the one signal that requires an explicit
property, and silently acquired the one that requires nothing. That is the sentence that explains why nobody
caught it. The Boot metrics reference states the default outright; the properties appendix shows a blank
default column, which is how it hides.

`management.otlp.metrics.export.headers.*` is the documented mechanism for supplying an OTLP backend's
authorization header, with env-var fallbacks `OTEL_EXPORTER_OTLP_HEADERS` and
`OTEL_EXPORTER_OTLP_METRICS_HEADERS` in `key=value` form. Treat the namespace and both env vars as
secret-bearing for any redaction or scanning rule.

With `spring-boot-docker-compose` present, Boot contributes `ConnectionDetails` for logs, metrics **and**
traces against those same two images — so under Compose a configured URL is silently ignored for all three.

## 12. NIST §3.1.4.2 — the TOTP axis is a second instance of the same `SHALL`

Verbatim, Single-Factor OTP Verifiers:

> "The verifier SHOULD implement or, if the authenticator output is less than 64 bits in length, SHALL
> implement a rate-limiting mechanism that effectively limits the number of failed authentication attempts
> that can be made on the subscriber account, as described in Sec. 3.2.2."

A six-digit output lands on the `SHALL` branch, and the argument closes inside the text: §3.1.4.1 permits
truncation "to as few as six decimal digits", and §3.2.2's rationale note cites "100 attempts against a
six-digit decimal OTP authenticator output" as its own worked example. Note this clause says "failed", not
"consecutive failed" — §3.2.2, which it delegates the mechanism to, supplies "consecutive".

Related clauses, which are **not** interchangeable:

- **Multi-factor OTP verifiers (§3.1.5.2)** — unconditional `SHALL`, and it cross-references **§3.2.10**,
  not §3.2.2. Our authenticator is a single-factor OTP device used as a second factor, so §3.1.4.2 → §3.2.2
  is the correct chain — and it stays correct only because `MFA_Core`'s PIN factor is out of scope. A
  TOTP-plus-activation-secret authenticator would move the citation to §3.2.10.
- **Look-up secret verifiers (§3.1.2.2)** — unconditional `SHALL` → §3.2.2.
- **Out-of-band verifiers (§3.1.3.2)** — conditional on the same 64-bit threshold, plus "Generating a new
  authentication secret SHALL NOT reset the failed authentication count."

**Ticket 23 already implements this**: tier 2 is 100 cumulative failures disabling the factor, cleared only
by `DELETE /api/admin/users/{uuid}/totp`, explicitly not reset on success, with `.lockout.tier2-threshold`
annotated "NIST §3.2.2's cap" and line 520 recording that the cap is verbatim *consecutive* and that the
cumulative reading is deliberately stricter. So the reopening of ticket 09 is **one** reopening, and it has
a working precedent in the same repository on the axis ticket 09 never touched.

**Replay interaction: not addressed.** Neither §3.1.4.2 nor §3.2.2 says whether rejecting a replayed OTP
increments the failed-attempt count. §3.1.4.2 only says a duplicate-use denial MAY warn the claimant. The
only counter rule in the neighbourhood is the out-of-band secret-regeneration sentence above. Whether a
burned timestep counts toward 100 is a documented decision, not a citation — and it bears on ticket 23's
replay rule, which burns `counter-1` and `counter` on first success.

## 13. §3.2.2's multi-authenticator sentence is an interpretation, not a citation

Verbatim, in context:

> "Unless otherwise specified in the description of a given authenticator, the verifier SHALL limit
> consecutive failed authentication attempts using a specific authenticator on a single subscriber account
> to no more than 100 by disabling that authenticator. If more than one authenticator is involved with an
> excessive number of authentication attempts (e.g., single-factor cryptographic authenticator and centrally
> verified password), both authenticators SHALL be disabled. Authenticators that have been disabled SHALL be
> required to rebind to the subscriber account, as described in Sec. 4.1, to be usable in the future."

The trigger is an **episode** — "more than one authenticator is involved with an excessive number of
attempts" — not independent exhaustion of two counters. The parenthetical describes a single ceremony
consuming two authenticators.

Whether it binds a **staged** flow (password stage, then OTP stage reachable only after a correct password)
is **not resolved by the text**. Two readings survive:

- **Wide:** an attempt reaching the OTP stage involved both, so tier 2 firing forces disabling the password
  too — which escalates directly into §4.2 account recovery rather than a timed lockout.
- **Narrow:** stage-1 failures never involve the OTP, and stage-2 failures involved a password that
  *succeeded*, so the password authenticator did not fail and was not "involved with" the failures. Two
  independent counters, per-authenticator disabling.

The narrow reading is the stronger one on the text's own words ("failed authentication attempts using a
specific authenticator"), but it is an ADR, not a quote.

## 14. Spring Security 7.1.x: the disable-path timing oracle is already closed

`AbstractUserDetailsAuthenticationProvider` declares
`private boolean alwaysPerformAdditionalChecksOnUser = true;` (`@since 5.7.23`). On 7.1.x the pre-check and
password comparison live in a private helper:

```java
private void performPreCheck(UserDetails user, UsernamePasswordAuthenticationToken authentication) {
    try {
        this.preAuthenticationChecks.check(user);
    }
    catch (AuthenticationException ex) {
        if (!this.alwaysPerformAdditionalChecksOnUser) {
            throw ex;
        }
        try {
            additionalAuthenticationChecks(user, authentication);
        }
        catch (AuthenticationException ignored) {
            // preserve the original failed check
        }
        throw ex;
    }
    additionalAuthenticationChecks(user, authentication);
}
```

So with the flag `true` and pre-checks failing, `PasswordEncoder.matches` **still runs against the account's
real stored hash**; its exception is swallowed and the pre-check exception is rethrown. A disabled account
therefore pays one BCrypt cost-12 verify, the same as a live account with a wrong password. The flag *is*
CVE-2026-22746's fix (Spring Security advisory, 2026-04-20, affected 7.0.0–7.0.4 / 6.5.0–6.5.9 and earlier
lines, fixed 7.0.5 / 6.5.10; setting it `false` is the documented escape hatch that reintroduces the
discrepancy). 7.1.x is outside the affected range and ships the flag defaulting to `true`.

`DaoAuthenticationProvider`'s unknown-user mitigation also survives in 7.1.x: `prepareTimingAttackProtection()`
lazily encodes a cached `"userNotFoundPassword"`, and `mitigateAgainstTimingAttack(...)` runs in the
`catch (UsernameNotFoundException ex)` branch — **only** on that exception and **only** when credentials are
non-null.

**Three residuals this leaves, in the opposite direction to the one expected:**

1. **`UserCache`.** If a non-null `UserCache` is configured and the user came from the cache, `authenticate`
   re-runs `retrieveUser` **and** `performPreCheck`, paying **two** BCrypt verifies on the pre-check-failure
   path — so a disabled account answers measurably *slower*. The default `NullUserCache` makes
   `cacheWasUsed` false and the exception rethrows after one comparison. This must become a negative
   assertion: no `UserCache` bean. Nothing on this map has said so.
2. **Exception identity still differs** (`DisabledException` vs `BadCredentialsException`). Ticket 06
   collapses these on the wire; the audit reason vocabulary distinguishes them deliberately.
3. **Anything short-circuiting before the provider** has a different envelope entirely — which is ticket
   09's per-IP filter and per-account converter, both of which reject before `AuthenticationManager`.

**Modelling consequence for the reopened ticket 09:** the §3.2.2 cap must not be expressed as
`enabled = false`. That collides with ticket 11's admin enable/disable — an admin re-enable would clear a
NIST cap, and the user list could not distinguish "disabled by admin" from "disabled by cap". Ticket 23's
tier 2 is the precedent: its own counter, cleared only by rebinding.

**And the cap cannot share ticket 09's lockout integer.** That counter resets on window expiry, so a cap
built on it could never reach 100 — unreachable dead code, which is the third occurrence on this map of the
exact defect ticket 02 found in the standard (locking at 5 makes the 10/min per-account limit unreachable).
NIST independently sanctions no time-based reset. So one new integer, success as its only reset, is forced
rather than chosen.

---

## 15. `error_follow_up_action` cannot carry an alert-routing rule

Checked because a proposed discriminator for ticket 21's alert taxonomy rested on this field. It fails at
both ends, and the second failure is an absence rather than a conflict.

**It is set `true` on the lockout row by the recipe we deviate from.**
`Appfw-Logging-Standards/Recipes/Logging_AuthN_And_AuthZ_Events.md:75-87` handles
`AuthenticationFailureLockedEvent` with `log.atError()`, `event.severity: high`, `error_code: 423`,
`error_category: "cert/auth"`, **`error_follow_up_action: true`**, message `"Account locked."` That is
ticket 13's row 3. A rule of the form "follow-up true ⇒ per-event alert" therefore classifies every lockout
as an incident, which is the outcome such a rule would exist to avoid.

**Ticket 13 never assigned the field.** Its catalogue columns are `#`, Row, `event.action`, `event.type`,
Level, Sev, Identity, Reason vocabulary. `error_follow_up_action` appears exactly once in the entire ticket,
at line 583, on the audit-write-failure ERROR. Ticket 13 reversed row 3's **level** (recipe `atError` → WARN,
on §3.4 and §3.3 authority) and recorded that reversal, but said nothing about the flag. So the field is
unassigned for 39 of 40 rows and the ticket 21 handover sentence claiming otherwise is wrong — corrected in
place.

**It cannot reach the success rows that need alerting.** `Structured_Logging_Application_Standard.md:186`
scopes the instruction to `ERROR` events: "For `ERROR` events, set `error_code` and `error_category` from the
defined enum values, set `error_follow_up_action` to indicate whether the error requires follow-up…". The
field is also mapped **into the sealed `error` object** — `Custom_Structured_Log_Encoder.md:120` lists
`error.follow_up_action` ← `error_follow_up_action`, "Inject into the `error` object", and
`Centralising_Audit_Logging_With_A_Typed_Module.md:322` explains the underscore spelling exists precisely
because "the ECS formatter pre-seals the `error.*` nested object".

A nuance worth getting right rather than overstating: two recipes **do** use the field on `atWarn()` rows —
`Centralising_Audit_Logging_With_A_Typed_Module.md:130` and `:147`, both `error_follow_up_action: false`. So
:186's ERROR scoping is **underspecified rather than contradicted**, and the real boundary in practice is
`event.outcome: failure`, which both of those rows are. That makes the argument against the field *stronger*
for ticket 21's purposes, not weaker: **no recipe anywhere applies it to a success row**, and ticket 13's
row 37 (TOTP enrolment confirmed, INFO, `event.outcome: success`, alert-worthy) would have to carry an
`error` object to express it.

**Conclusion.** The predicate must be over all forty rows and independent of the error schema. The workable
one is *does clearing this state require an action outside the normal flow* — derivable from artifacts that
already exist: row 4's reason enum (`AUTO_LIFT` | `ADMIN_UNLOCK` | `RESET_REDEMPTION`) and ticket 23's tier
model, where tier 1 auto-lifts and tier 2 clears only by rebinding. It also matches what "alert" means
operationally: a human must do something.

One consequence of adopting it: **row 34 splits by reason.** "Administrative attempt failed" carries
`SELF_ACTION` | `TWO_ADMIN_INVARIANT` | `LOCK_TIMEOUT` | `TRANSACTION_ROLLBACK`. The first two are caller
error and clear themselves; the last two are infrastructure and clear by investigation. So the class is a
property of (row, reason) for at least one row — which costs nothing, because reason is already a tag on the
audit counter.

## 16. Health contributors: keys, and what `db` actually does

Property pattern is `management.health.<key>.enabled`, with a blanket `management.health.defaults.enabled`
(default `true`). `OnEnabledHealthIndicatorCondition` builds `"management.health." + name + ".enabled"` and
that property, when present, wins over the blanket — so a per-indicator `true` re-enables an indicator even
under `defaults.enabled: false`.

Keys verified from the 4.1 reference table: `db` → `DataSourceHealthIndicator`, `diskspace` →
`DiskSpaceHealthIndicator`, `ping` → `PingHealthIndicator`. **Three distinct namespaces exist and they are
not interchangeable:** the property key is flat lowercase `diskspace`; the JSON/health-group name is
camelCase `diskSpace` (bean name `diskSpaceHealthIndicator` minus the stripped suffix, via
`HealthContributorNameGenerator.withoutStandardSuffixes()`); the class is `DiskSpaceHealthIndicator`. Health
group `include`/`exclude` and `/actuator/health/{component}` use the **JSON** name. `db` and `ping` happen to
coincide across namespaces; `diskspace`/`diskSpace` does not.

**`DataSourceHealthIndicator` does not run SQL by default.** It borrows a connection, calls
`connection.getMetaData().getDatabaseProductName()` (reported as the `database` detail), then calls
`connection.isValid(0)`, reporting `validationQuery: "isValid()"`. A SQL statement runs only when the
**connection pool** has a validation query configured — `getValidationQuery()` reads
`DataSourcePoolMetadata`, and HikariCP has none by default. So the anonymous cost is a pool borrow plus a
metadata call, not a `SELECT 1`. Any argument for disabling it on **cost** grounds is therefore weak; the
honest grounds are consumer-absence (the same argument that disabled the probes) and removing the
availability oracle.

Auto-configuration conditions: `@ConditionalOnBean(DataSource.class)`,
`@ConditionalOnEnabledHealthIndicator("db")`, and bean-level
`@ConditionalOnMissingBean(name = {"dbHealthIndicator", "dbHealthContributor"})`.

**`DiskSpaceHealthIndicator` is the cheap detector, with two calibration traps.** Auto-configured on
`@ConditionalOnClass(Health.class)` plus the toggle — no other bean required. It reads
`path.getUsableSpace()` and compares against a threshold; no database, socket or pool interaction.

1. **Default threshold is `DataSize.ofMegabytes(10)`.** Ten megabytes is far below any useful warning line
   for a rolling appender with `max-history: 90` and **no `total-size-cap`** — by the time usable space is
   under 10 MB the appender has already been failing. So adopting it as the audit-failure detector *requires*
   a threshold decision, and ticket 13's deliberate omission of the size cap is exactly what makes sizing it
   hard.
2. **Default `path` is `new File(".")`** — the working directory, not the log directory. If the audit file
   lives on a different mount the indicator watches the wrong filesystem and reports `UP` while the appender
   fails. `management.health.diskspace.path` must be pinned to the log directory.

And a disclosure consequence that inverts an earlier error on this ticket: `details` carries five keys —
`total`, `free`, `threshold`, `path`, `exists` — and `path` is an **absolute filesystem path**. So
`management.endpoint.health.show-details: never` becomes load-bearing for path non-disclosure. The
path-disclosure worry that was wrong about the `info` endpoint is right about this contributor.

## 17. Reading meters in tests, and the one setting that makes it work

With `management.otlp.metrics.export.enabled: false` and no other registry dependency,
`SimpleMetricsExportAutoConfiguration` (`@ConditionalOnMissingBean(MeterRegistry.class)`,
`@ConditionalOnEnabledMetricsExport("simple")`) registers a `SimpleMeterRegistry`, and
`CompositeMeterRegistryConfiguration`'s `MultipleNonPrimaryMeterRegistriesCondition` does **not** match with
a single registry present — so injecting `MeterRegistry` yields the `SimpleMeterRegistry` itself and
`registry.get(name).counter().count()` reads real values. That makes every meter claim on this ticket a unit
assertion rather than an argument.

**Two traps.**

- **Do not use the blanket switch.** `management.defaults.metrics.export.enabled: false` suppresses
  `SimpleMeterRegistry` too, and `NoOpMeterRegistryConfiguration` then contributes an **empty
  `CompositeMeterRegistry`** — so every assertion silently fails to find its meter. Disable **otlp
  specifically**. This is the inert-control shape again, in the test harness this time.
- **Adding any second registry** flips the single-candidate condition and injects a `@Primary` composite,
  which does not expose child meter values the same way.

`management.metrics.use-global-registry` defaults **`true`**, and the guidance to disable it in tests is
real but comes from the **property's own description** (4.1 application-properties appendix, generated from
the `MetricsProperties.useGlobalRegistry` javadoc): for testing, set it to `false` to maximise test
independence. The narrative Metrics chapter does not mention it — it only warns against registering meters
through the static `Metrics` methods because that registry is not Spring-managed. Cite the appendix, not a
testing chapter.

`MeterRegistryPostProcessor.addToGlobalRegistryIfNecessary` is the mechanism, and `MeterRegistryCloser`
removes registries from the global holder on `ContextClosedEvent` — but Spring caches test contexts, so a
cached context's registry stays attached to `Metrics.globalRegistry` for the run. Whether the global
composite retains meter IDs after `removeRegistry` is **UNVERIFIED** (Micrometer-internal); do not assert it
either way. Read meters off the injected bean, never off `Metrics.globalRegistry`.

---

## 18. Where a status check runs decides whether its audit reason is a password oracle

Added while resolving the ticket 09 reopening. Source:
[`AbstractUserDetailsAuthenticationProvider`](https://raw.githubusercontent.com/spring-projects/spring-security/main/core/src/main/java/org/springframework/security/authentication/dao/AbstractUserDetailsAuthenticationProvider.java)
and
[`DefaultAuthenticationEventPublisher`](https://raw.githubusercontent.com/spring-projects/spring-security/main/core/src/main/java/org/springframework/security/authentication/DefaultAuthenticationEventPublisher.java).
Apache 2.0.

### The two checker slots are not interchangeable, and §14 only inspected one of them

`authenticate` runs, in order: `retrieveUser` → `performPreCheck` → `postAuthenticationChecks.check(user)` →
`createSuccessAuthentication`. §14 quoted `performPreCheck` and stopped there. The line after it is the one
that matters:

```java
try {
    performPreCheck(user, (UsernamePasswordAuthenticationToken) authentication);
}
catch (AuthenticationException ex) {
    if (!cacheWasUsed) { throw ex; }
    cacheWasUsed = false;
    user = retrieveUser(username, (UsernamePasswordAuthenticationToken) authentication);
    performPreCheck(user, (UsernamePasswordAuthenticationToken) authentication);
}
this.postAuthenticationChecks.check(user);
```

Contents of each slot, verbatim from the two private inner classes:

| Slot | `UserDetails` predicate | Exception | Runs when the password was wrong? |
|---|---|---|---|
| `DefaultPreAuthenticationChecks` | `isAccountNonLocked()` | `LockedException` | **Yes** |
| | `isEnabled()` | `DisabledException` | **Yes** |
| | `isAccountNonExpired()` | `AccountExpiredException` | **Yes** |
| `DefaultPostAuthenticationChecks` | `isCredentialsNonExpired()` | `CredentialsExpiredException` | **No** |

The pre-check slot throws *before* `additionalAuthenticationChecks`, so it fires regardless of the submitted
password, and `alwaysPerformAdditionalChecksOnUser = true` is what makes it pay the same BCrypt cost anyway
(§14). The post-check slot is reached **only** when `performPreCheck` returned without throwing — i.e. only
when `additionalAuthenticationChecks` matched. So:

> **A post-authentication status check fires if and only if the submitted password was correct.**

### Consequence: a live finding against ticket 11's 30-day expiry, not merely an input to ticket 09

Ticket 09 §6 records that ticket 11's `credentialIssuedAt` expiry is "implemented as
`isCredentialsNonExpired()`" and that it "increments nothing". Both halves are correct — and the untaken
consequence is that its audit reason is emitted **only on a correct password**. Ticket 06 §3 routes the
internal failure reason to the audit log, ticket 13 confirmed that routing and put `user.id` on resolved
failure rows, and ticket 10 established that the log leak is **total for ordinary users**. A log reader
therefore gets a clean password-confirmation oracle on any account whose credential has aged past 30 days:
reason `credential-expired` means the guess was right, reason `wrong password` means it was not. Ticket 06's
wire-uniformity rule does not reach it, because the distinguishing channel is the audit stream, not the
response.

This does **not** follow from exception identity, which §14 residual 2 already noted and ticket 06 already
collapses on the wire. It follows from **reachability**: the pre-check reasons are emitted on every attempt
and carry no information about the credential; the post-check reason is conditioned on the credential being
correct.

**Rule this generalises to, for any new account-state refusal:** a status check whose audit reason must not
confirm a guessed password belongs in `preAuthenticationChecks`. The post-check slot is only safe for a
reason that is already implied by a *successful* authentication.

### Unmapped `AuthenticationException` subclasses publish no event at all

`publishAuthenticationFailure` resolves a constructor via `exceptionMappings.get(exception.getClass().getName())`,
falling back to `defaultAuthenticationFailureEventConstructor` — **null unless explicitly set** — and when the
resolved constructor is null it publishes nothing and logs at DEBUG:

```java
this.logger.debug("No event was found for the exception " + exception.getClass().getName());
```

Two facts for the counting design:

1. `CredentialsExpiredException` **is** mapped, to `AuthenticationFailureCredentialsExpiredEvent`, so ticket 09
   §6's "increments nothing" holds by the mapping, not by absence.
2. A **custom** `AuthenticationException` subclass is unmapped by default, so it publishes **no** event and a
   listener keyed on `AuthenticationFailureBadCredentialsEvent` cannot see it. This is a silent property of the
   lookup — DEBUG-only — so any design that wants such an exception counted must either register a mapping or
   count somewhere other than the listener. Note the exact-class lookup: **subclassing a mapped exception does
   not inherit its mapping**, because the key is `getClass().getName()`.

### `UserCache` retry path confirmed

The `catch` block above re-runs `retrieveUser` **and** `performPreCheck` when `cacheWasUsed` was true, which is
§14 residual 1 read off the source rather than inferred: a non-null `UserCache` makes a pre-check-failure path
pay **two** BCrypt verifies. `private UserCache userCache = new NullUserCache();` is the field initialiser, so
the default is safe and the negative assertion is against a bean someone adds later.

---

## 19. Spring Session JDBC writes in their own transaction, so "kill sessions atomically" is unavailable

Added while resolving the ticket 09 reopening. Verified on the **pinned 4.1.x line**, not only `main`.
Sources: [`JdbcHttpSessionConfiguration` (4.1.x)](https://raw.githubusercontent.com/spring-projects/spring-session/4.1.x/spring-session-jdbc/src/main/java/org/springframework/session/jdbc/config/annotation/web/http/JdbcHttpSessionConfiguration.java),
[`JdbcIndexedSessionRepository`](https://raw.githubusercontent.com/spring-projects/spring-session/main/spring-session-jdbc/src/main/java/org/springframework/session/jdbc/JdbcIndexedSessionRepository.java),
[Spring Session Boot-JDBC guide](https://docs.spring.io/spring-session/reference/guides/boot-jdbc.html). Apache 2.0.

```java
private TransactionTemplate createTransactionTemplate(PlatformTransactionManager transactionManager) {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transactionTemplate.afterPropertiesSet();
    return transactionTemplate;
}
```

`JdbcIndexedSessionRepository` routes `findById`, `deleteById` and `findByIndexNameAndIndexValue` through that
`TransactionOperations`. Boot applies configuration equivalent to `@EnableJdbcHttpSession`, so this class is on
our path. Therefore:

1. **Every session write suspends the caller's transaction and commits independently.** No ambient
   `@Transactional` block can roll a session deletion back, so a state change and its session kill cannot be
   made atomic.
2. **Called inside a transaction, it needs a second pooled connection while the first is still held.** Where the
   caller also holds a pessimistic row lock, the connection pool joins the lock graph, and H2's pinned
   1-second `LOCK_TIMEOUT` does not bound a thread waiting on the pool — that wait is the pool's own timeout.
   Under a many-accounts-in-flight attack this is thread occupancy, which is the failure mode ticket 09 §11
   used to decline sleep-based backoff.
3. **A row lock cannot be released early to avoid (2).** Locks are released by commit or rollback only;
   savepoints do not release them. So "lock, write, release, then kill sessions" has no implementation, and
   the only choice is *before commit, inside the lock* versus *after commit, outside it*.

**Consequence for the map:** session rows must be touched **after commit**, never inside the lock, which also
preserves the after-commit dispatch rule tickets 13 and 10 established. The residual is a crash between
commit and dispatch, repairable by an idempotent startup reconciliation sweep rather than by transactions.
This applies to **all** of ticket 08's invalidation triggers, not just the one that surfaced it.

---

## 20. With an `AuthenticationConverter`, `authenticationDetailsSource` is never applied

Source: [`AbstractAuthenticationProcessingFilter`](https://raw.githubusercontent.com/spring-projects/spring-security/main/web/src/main/java/org/springframework/security/web/authentication/AbstractAuthenticationProcessingFilter.java)
and [`WebAuthenticationDetails`](https://raw.githubusercontent.com/spring-projects/spring-security/main/web/src/main/java/org/springframework/security/web/authentication/WebAuthenticationDetails.java). Apache 2.0.

**Both halves of this have to be stated, because each one alone reads as the opposite conclusion.**

*Half one — the base method is concrete on 7.1.x, and it sets no details.* `attemptAuthentication` was abstract
in older lines; converter support made it concrete, and the field initialiser proves it ("Please either
configure an `AuthenticationConverter` **or override attemptAuthentication** when extending
`AbstractAuthenticationProcessingFilter`"). The body is three statements:

```java
Authentication authentication = this.authenticationConverter.convert(request);
if (authentication == null) { return null; }
Authentication result = this.authenticationManager.authenticate(authentication);
```

*Half two — populating details is the subclass's job, and the subclass does it.*
`UsernamePasswordAuthenticationFilter` overrides the method and calls a protected hook:

```java
setDetails(request, authRequest);   // -> authRequest.setDetails(this.authenticationDetailsSource.buildDetails(request))
```

So the `authenticationDetailsSource` field lives on the base class but is **read only by subclasses that build
their own token**. Nothing downstream of a converter fills the gap. **Under Route C's JSON converter,
`Authentication.getDetails()` is therefore whatever the converter sets — by default `null`.**

Citing only half one invites "then override it and you're fine"; citing only half two invites "then the base
class must fill it in". Neither is true.

`WebAuthenticationDetails` is the type that would otherwise carry the address, and it reads
`request.getRemoteAddr()`:

```java
public WebAuthenticationDetails(HttpServletRequest request) {
    this(request.getRemoteAddr(), extractSessionId(request));
}
```

**Two readers depend on this, and they fail in opposite directions — which is why the assertion is the
control and not instrumentation.**

1. **Ticket 09 §12 fails *open*, quietly.** It specifies a keyed HMAC of the client IP on the rate-limit breach
   and failed-login rows, and those rows are written from an event listener with no `HttpServletRequest`, so
   `getDetails()` is the only in-band carrier. Unset, every row carries one key: the same
   looks-configured-but-inert shape as §7's unset `forward-headers-strategy`, and it still looks plausible
   because a hash is present.
2. **Ticket 09's per-source distinct-locked-account limiter fails *closed*, loudly.** It enforces in the
   converter, which holds the request, so enforcement needs nothing — but it **records on the tier-1 lockout
   transition**, detected in the listener. Null details attribute every lockout in the system to one key, so
   the cardinality threshold trips immediately and the limiter `429`s everyone. That is a fail-closed
   inversion of a control added to bound a fail-open one, which makes this the more operative of the two
   dependencies.

**Why the record-here / enforce-there split is safe at all:** `WebAuthenticationDetails` reads
`getRemoteAddr()`, the same call the early filter resolves through, so both sides pass through Tomcat's
`RemoteIpValve` when ticket 09 §7's `source=proxy` is configured and the two keys cannot diverge. That
property holds **only** while the converter sets the details.

**One representation trap on the same value:** the limiter's cache key is the resolved raw address, and §12's
audit field is a domain-prefixed keyed HMAC of it. One resolution path, two representations — so neither HMAC
the cache key nor log the raw one.
