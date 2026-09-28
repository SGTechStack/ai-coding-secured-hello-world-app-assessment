---
status: accepted
---

# ADR-061: Actuator exposes `health` only, read-only, and metrics are pushed over OTLP

The only Actuator endpoint on the web is `health`, every endpoint is capped at read-only, and every other endpoint is
denied. Metrics leave the process only by OTLP push, and that push ships switched off. A maintainer who wants
monitoring will reach for `/actuator/prometheus` or `/actuator/metrics`, which is the pattern the Boot reference
documents for Prometheus. That would add a permanent endpoint for a collector this plan does not have.

## Context

- Spring Boot 4.1 exposes only `health` over HTTP and JMX by default. It also gives every endpoint except `shutdown`
  and `heapdump` unrestricted access by default. So `management.endpoints.access.default: none` is only a default,
  and a later `management.endpoint.env.access: unrestricted` would win over it.
- `management.endpoints.access.max-permitted` takes precedence over both the default and each endpoint's own access
  level. Set to `read-only`, it limits every endpoint to its read operations.
- Boot's `ManagementWebSecurityAutoConfiguration` (the "health public, the rest authenticated" chain) backs off
  completely once the application declares its own `SecurityFilterChain`, which this application does. The actuator
  rules are therefore ours to write.
- IM8 lm-16 asks for latency, traffic, error and saturation signals. It is met by meters, whichever way they leave
  the process. IM8 as-13 is about exposure, and the `im8-review` as-13 check flags any exposure beyond `health` and
  `info` that has no stated reason.
- **Export was already running.** `spring-boot-starter-opentelemetry` brings `micrometer-registry-otlp`, so Boot's
  `OtlpMetricsExportAutoConfiguration` is active with no configuration. With no URL set, Micrometer's `OtlpConfig`
  falls back to `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT`, then `OTEL_EXPORTER_OTLP_ENDPOINT`, then
  `http://localhost:4318/v1/metrics`, every minute. The Boot reference says as much: by default metrics go "to a
  consumer running on your local machine". Traces need a property before they go anywhere. Metrics need nothing.
- The `info` endpoint would be empty here. The `build` and `git` contributors are on by default but need
  `META-INF/build-info.properties` and `git.properties`, and the build produces neither. The `env`, `java`, `os` and
  `process` contributors are off by default.

## Considered options

- **Expose `/actuator/prometheus` or `/actuator/metrics` behind authentication.** Observability is not the
  difference, because both options stay inert until a deployer supplies a collector. Surface is the difference. An
  unconfigured exporter is dormant code. An authenticated endpoint is live in every deployment, needs its own
  authorization rule, and is exactly what as-13 governs.
- **A separate management port, with network isolation instead of authentication.** Rejected. The 4.1 reference
  does not document how the application's security filter chain applies on a separate management port, and this
  design does not rest on undocumented framework behaviour.
- **Expose `info` as well, as as-13's own example does.** Rejected for lack of benefit, not for disclosure. There is
  nothing in it to expose.
- **`health` only, read-only, with OTLP push switched off until a deployer enables it (chosen).**

## Decision

- `management.endpoints.web.exposure.include: health`, `management.endpoints.jmx.exposure.include: health`,
  `management.endpoints.access.default: none`, `management.endpoints.access.max-permitted: read-only` and
  `management.endpoint.health.access: read-only`. Values that equal Boot's defaults are still written, because
  `im8-review` greps for the literal exposure property.
- `management.endpoint.health.show-details: never`.
- Two security rules: `EndpointRequest.to(HealthEndpoint.class)` is `permitAll()`, and
  `EndpointRequest.toAnyEndpoint()` is `denyAll()`. The deny is explicit, so an endpoint switched on by accident is
  refused rather than left to the catch-all rule.
- `management.otlp.metrics.export.enabled: false` in the base configuration. That property removes the whole OTLP
  auto-configuration, so no registry, config or connection-details bean exists.
- A profile that turns export on must also set the URL, and must make it required with
  `ConfigurablePropertyResolver#setRequiredProperties` from an `EnvironmentPostProcessor`. A plain
  `${OTLP_METRICS_URL}` placeholder is not enough. The configuration-properties binder keeps an unresolved
  placeholder as a literal string (ADR-062).

## Consequences

- **`show-details: never` and the `permitAll` rule are one interlock.** `EndpointRequest.to(...)` matches the
  endpoint's subpaths too. `/actuator/health/db` and the other subpaths are safe only because they return 404 when
  details are never shown. T-OBS-009 asserts both halves. T-OBS-010 asserts the deny rule, and T-OBS-011 asserts that
  Boot's management chain really did back off.
- **Only bean absence proves the export is off.** T-OBS-012 asserts that `OtlpMeterRegistry`, `OtlpConfig` and
  `OtlpMetricsConnectionDetails` are absent. It never checks the resolved URL, which is never null. A Docker Compose
  or Testcontainers service connection supplies its own connection-details bean, and the adapter never reads the
  URL property. So required-property validation covers the human-configured path, and bean absence covers the
  service-connection path. Each covers a failure the other cannot see (R-OBS-008).
- The deployer who wants metrics enables export, supplies the URL as a required property, and treats
  `management.otlp.metrics.export.headers.*` as a secret (R-OBS-007, R-CFG-020).
- Unauthenticated `/actuator/health` fails as-13's literal base-path check. It is recorded once, under ac-1, as
  R-OBS-005.
- The empty-`info` argument rests on two files being absent. Adding the plugin that writes either file reopens this
  decision. T-BLD-004 fails first, because it checks the jar for both files (R-BLD-015).
- The health body is not an RFC 9457 envelope (REJ-066). The probes and the `db` indicator are off while
  `diskspace` stays (REJ-063). The test harness disables OTLP export specifically, not all registries (REJ-068).
  Tomcat's MBean registry is on so thread meters exist (REJ-067).

## Reopening triggers

- A deployer supplies a collector that can only scrape, not receive a push.
- The build starts writing `git.properties` or `META-INF/build-info.properties` (R-BLD-015).
- A Boot upgrade gates OTLP metrics export behind a property, or changes the default URL.

## Sources

- Spring Boot 4.1 reference, Actuator, Endpoints: "Controlling Access to Endpoints", "Limiting Access", "Exposing
  Endpoints", "Auto-configured InfoContributors". Actuator, Metrics: "Getting Started", "OTLP", "Prometheus".
- Spring Boot 4.1.x source: `OtlpMetricsExportAutoConfiguration`, `OnMetricsExportEnabledCondition`,
  `OtlpMetricsPropertiesConfigAdapter#url()`, `ManagementWebSecurityAutoConfiguration`, and the
  `spring-boot-starter-opentelemetry` build file.
- Micrometer `micrometer-registry-otlp` source, `OtlpConfig#url()` (main branch; the exact version Boot 4.1 manages
  was not checked separately).
- IM8 as-13 (Exposure of Internal System Details), ac-1 and lm-16 (Key Signals Monitoring), with the `im8-review`
  as-13 check text.
- OWASP ASVS 5.0: 13.4.5 (L2) monitoring endpoints not exposed unless intended; 13.4.6 (L3) no detailed backend
  version information.
