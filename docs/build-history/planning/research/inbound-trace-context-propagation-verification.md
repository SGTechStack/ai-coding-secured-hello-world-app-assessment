# Verification asset — ticket 27 (inbound trace context)

Produced under the map's **verification rule** to discharge ticket 27's research obligation: "Boot 4.1's
default propagator and whether extraction can be disabled without losing outbound propagation are **not
verified**." Primary sources only: Spring Boot 4.1 reference documentation and the `v4.1.1` source tag,
Spring Framework `v7.0.9` (the version `v4.1.1` pins in `gradle.properties`), Micrometer Tracing `v1.7.1`,
OpenTelemetry Java `v1.62.0` (both pinned in `platform/spring-boot-dependencies/build.gradle` at `v4.1.1`),
the OpenTelemetry specification, and the W3C Trace Context Recommendation. Every claim is **VERIFIED**,
**PARTIALLY VERIFIED** (unverified half named) or **UNVERIFIED** (not to be used to carry an argument).

Source links below use these prefixes:

- `SB` = `https://github.com/spring-projects/spring-boot/blob/v4.1.1/`
- `SF` = `https://github.com/spring-projects/spring-framework/blob/v7.0.9/`
- `MT` = `https://github.com/micrometer-metrics/tracing/blob/v1.7.1/`
- `OT` = `https://github.com/open-telemetry/opentelemetry-java/blob/v1.62.0/`
- `OTEL-BOOT` = `SB` + `module/spring-boot-micrometer-tracing-opentelemetry/src/main/java/org/springframework/boot/micrometer/tracing/opentelemetry/autoconfigure/`

Bias check, as in the ticket-15 asset: the conclusion I expected ("extraction is on by default; turning it off
is one property") came back **true for the first half and only source-inferred for the second**. §3 records
the gap rather than papering over it.

---

## §1 — Default propagation: consume W3C + B3 + B3-multi, produce W3C only

**VERIFIED.** Sources:
[Boot 4.1 application properties appendix](https://docs.spring.io/spring-boot/4.1/appendix/application-properties/index.html)
(Actuator properties, `management.tracing.propagation.*`), and `SB` +
`module/spring-boot-micrometer-tracing/src/main/java/org/springframework/boot/micrometer/tracing/autoconfigure/TracingProperties.java`.

- All three properties exist in 4.1. The appendix lists `management.tracing.propagation.consume` default
  `[W3C, B3, B3_MULTI]`, `management.tracing.propagation.produce` default `[W3C]`, and
  `management.tracing.propagation.type` with no default, described as overriding the fine-grained pair.
- Source agrees: `TracingProperties.Propagation` — `produce = List.of(PropagationType.W3C)` (line 204),
  `consume = List.of(PropagationType.values())` (line 209), `type` is `@Nullable` (line 199). The enum
  `PropagationType` has exactly three values, `W3C`, `B3`, `B3_MULTI` (lines 238–257). **There is no `NONE`
  value.**
- `OTEL-BOOT` `CompositeTextMapPropagator.java` lines 145–151: if `type` is set it is used for *both*
  directions; otherwise `consume` drives extractors and `produce` drives injectors.

**Consequence.** By default the application *accepts* three inbound formats, not one. Any control that
strips or validates inbound trace headers must cover `b3` and the `X-B3-*` family as well as `traceparent` /
`tracestate` (see §3.4 for how to derive the list rather than hard-code it).

## §2 — Inbound extraction is on by default for Spring MVC server observations, and runs before Spring Security

**VERIFIED.** The chain, link by link:

1. **Filter registered by default.** `SB` +
   `module/spring-boot-webmvc/src/main/java/org/springframework/boot/webmvc/autoconfigure/WebMvcObservationAutoConfiguration.java`
   lines 66–79: a `FilterRegistrationBean<ServerHttpObservationFilter>` with
   `@ConditionalOnMissingFilterBean`, `setOrder(Ordered.HIGHEST_PRECEDENCE + 1)` (line 76), dispatcher types
   `REQUEST, ASYNC` (line 77).
2. **The context carries a header getter.** `SF` +
   `spring-web/src/main/java/org/springframework/http/server/observation/ServerRequestObservationContext.java`:
   extends `RequestReplyReceiverContext<HttpServletRequest, HttpServletResponse>` (line 36), passes a
   `HeaderGetter` to `super` (line 43) whose `get` is `carrier.getHeader(key)` (line 59).
3. **A receiver handler is registered by default.** `SB` +
   `module/spring-boot-micrometer-tracing/src/main/java/org/springframework/boot/micrometer/tracing/autoconfigure/MicrometerTracingAutoConfiguration.java`
   lines 101–108: `PropagatingReceiverTracingObservationHandler` bean, `@ConditionalOnMissingBean`,
   `@ConditionalOnBean(Propagator.class)`, `@Order(RECEIVER_TRACING_OBSERVATION_HANDLER_ORDER)` = 1000
   (line 70), ahead of `DefaultTracingObservationHandler` at `LOWEST_PRECEDENCE - 1000` (line 64).
4. **The handler extracts on start.** `MT` +
   `micrometer-tracing/src/main/java/io/micrometer/tracing/handler/PropagatingReceiverTracingObservationHandler.java`:
   `onStart` calls `this.propagator.extract(context.getCarrier(), …)` (line 60) and starts the extracted span
   (line 85); `supportsContext` is `context instanceof ReceiverContext` (lines 128–129).
5. **The propagator is Boot's composite.** `OTEL-BOOT` `OpenTelemetryTracingAutoConfiguration.java`:
   `OtelPropagator` bean over `ContextPropagators` (lines 201–205); `ContextPropagators` is the composite of
   every `TextMapPropagator` bean (lines 130–134); the default `TextMapPropagator` bean is
   `CompositeTextMapPropagator.create(properties.getPropagation(), …)`
   (`OpenTelemetryPropagationConfigurations.java` lines 53–55 / 75–81).

**Ordering against Spring Security.** `spring.security.filter.order` defaults to **-100** (Boot 4.1 appendix;
source `SB` +
`module/spring-boot-security/src/main/java/org/springframework/boot/security/autoconfigure/web/servlet/SecurityFilterProperties.java`
line 49, `OrderedFilter.REQUEST_WRAPPER_FILTER_MAX_ORDER - 100`). `HIGHEST_PRECEDENCE + 1` is
`Integer.MIN_VALUE + 1`, so **`ServerHttpObservationFilter` runs before the entire Spring Security filter
chain**, and the observation (hence extraction, hence MDC `traceId`) is already established when
`CsrfFilter`, authentication and authorization run.

**Consequence for ticket 27.** "Gate continuation on authentication" cannot be done with a Security-chain
filter or an authorization rule: the trace identity is fixed before Security sees the request. It would need
custom code at the observation layer (§3.4, options B–D), which reinforces the ticket's own observation that
the gate does not help the pre-authentication login path.

## §3 — Disabling extraction while keeping injection

### §3.1 The consume/produce split is real and independent

**VERIFIED.** `OTEL-BOOT` `CompositeTextMapPropagator.java` lines 132–143: injectors are built from the
effective *produce* list, extractors from the effective *consume* list, as separate collections. `extract`
(lines 105–124) iterates only `this.extractors`; `inject` (lines 97–102) iterates only `this.injectors`. So
narrowing `consume` does not touch outbound `traceparent` injection, provided `type` is left unset (setting
`type` overrides both, lines 145–151).

### §3.2 An empty `consume` list: works in source, not documented, not tested

**UNVERIFIED (source-inferred).** There is no `NONE`-like enum value (§1). With an empty extractor list,
`extract` returns the incoming context unchanged (loop at lines 113–119 does nothing), which the Micrometer
bridge turns into a new root span. And Boot's comma-delimited list conversion
(`SB` + `core/spring-boot/src/main/java/org/springframework/boot/convert/DelimitedStringToCollectionConverter.java`
lines 67–88, via `StringUtils.delimitedListToStringArray`) appears to yield an empty collection for an empty
string. But:

- the Boot reference documentation does not document an empty `consume` as a way to disable extraction;
- no test at `v4.1.1` exercises it (the only `setConsume` in tests is `CompositeTextMapPropagatorTests`
  line 105, with `B3`);
- whether `management.tracing.propagation.consume=` (properties) or `consume: []` (YAML) actually binds to
  an empty list rather than keeping the default was not run.

This must be a **first-implementation test** (`/do-work`): send a valid `traceparent` and assert the MDC /
log `traceId` differs from the inbound `trace-id`, and that an outbound call still carries `traceparent`.

### §3.3 Baggage still extracts even with an empty `consume`

**VERIFIED.** `CompositeTextMapPropagator.extract` lines 120–122 apply the separate `baggagePropagator`
*after* the extractor loop, regardless of `consume`. That propagator is Micrometer's
`BaggageTextMapPropagator`, which reads only the configured `remote-fields` (`MT` +
`micrometer-tracing-bridges/micrometer-tracing-bridge-otel/src/main/java/io/micrometer/tracing/otel/propagation/BaggageTextMapPropagator.java`
lines 99–101). With the default empty `remote-fields` it reads nothing. The W3C `baggage` header parser
(`W3CBaggagePropagator`) is part of the *W3C extractor* (`CompositeTextMapPropagator.java` lines 192–194), so
it is dropped along with `W3C` from `consume`.

### §3.4 Alternative mechanisms

Each is **VERIFIED as to the hook existing**; the behavioural effect of B–D is **UNVERIFIED** (not run).

- **A. Header-stripping servlet filter.** Must be registered at `Ordered.HIGHEST_PRECEDENCE` (i.e. strictly
  before `HIGHEST_PRECEDENCE + 1`, §2) and wrap the request so `getHeader`/`getHeaders` hide the propagation
  fields — the getter reads the `HttpServletRequest` it is handed (§2 item 2). A Spring Security filter is too
  late (§2). The header list should be taken from the propagator rather than hard-coded:
  `CompositeTextMapPropagator.fields()` is the union of injector, extractor and baggage fields
  (lines 71–77, 93–95), exposed via `ContextPropagators.getTextMapPropagator().fields()`. This is also the
  natural place for the §7 length/content validation of §5.
- **B. Own `ServerHttpObservationFilter` registration.** Boot's is `@ConditionalOnMissingFilterBean`
  (`WebMvcObservationAutoConfiguration.java` line 67), so registering one's own backs Boot's off.
- **C. Replace the `PropagatingReceiverTracingObservationHandler` bean.** It is `@ConditionalOnMissingBean`
  (`MicrometerTracingAutoConfiguration.java` line 102), so a subclass overriding `onStart` to ignore the
  carrier would replace it. It is the only receiver-side hook that could see the observation context and so
  the only one in which an "authenticated or not" gate could even be attempted — but §2 shows authentication
  has not happened yet at that point.
- **D. Replace the Micrometer `Propagator` or OTel `ContextPropagators` bean.** Both are
  `@ConditionalOnMissingBean` (`OpenTelemetryTracingAutoConfiguration.java` lines 131, 202). A replacement must
  keep `inject` working, because `PropagatingSenderTracingObservationHandler` uses the same `Propagator`
  (`MicrometerTracingAutoConfiguration.java` lines 92–99).
- **Not an option: adding another `TextMapPropagator` bean.** The Boot-provided `TextMapPropagator` beans in
  `PropagationWithoutBaggage` / `PropagationWithBaggage` are **not** `@ConditionalOnMissingBean`
  (`OpenTelemetryPropagationConfigurations.java` lines 51–55, 73–81; only `NoPropagation` at line 99 is), and
  `ContextPropagators` composites *all* `TextMapPropagator` beans (`OpenTelemetryTracingAutoConfiguration.java`
  line 133). A custom bean is **added alongside** the default extractor, it does not replace it. Verified from
  source.

## §4 — Baggage: on by default, but nothing reaches MDC unless configured

**VERIFIED.** Boot 4.1 appendix and `TracingProperties.java`:

| Property | Default | Source line |
|---|---|---|
| `management.tracing.baggage.enabled` | `true` | 94 |
| `management.tracing.baggage.correlation.enabled` | `true` | 164 |
| `management.tracing.baggage.correlation.fields` | empty list | 170 |
| `management.tracing.baggage.remote-fields` | empty list | 106 |

- With baggage enabled (default), the W3C extractor is `W3CTraceContextPropagator` **plus**
  `W3CBaggagePropagator` (`CompositeTextMapPropagator.java` lines 192–194), so an inbound `baggage` header is
  parsed into the OTel context by default.
- MDC is written only by `Slf4JBaggageEventListener` (`OpenTelemetryPropagationConfigurations.java`
  lines 83–88), and it puts an entry **only if the baggage key is in `correlation.fields`**, compared
  case-insensitively (`MT` + `…/otel/bridge/Slf4JBaggageEventListener.java` lines 46–50, 72–79). The MDC key
  is the baggage key; arbitrary keys cannot be injected.
- **Consequence.** With the defaults, inbound `baggage` cannot inject MDC keys. If a later ticket adds a name
  to `correlation.fields`, a caller can set that MDC value via `baggage: <name>=<value>` (and via a header of
  that name if it is also in `remote-fields`, §3.3). Setting `management.tracing.baggage.enabled=false`
  removes both the W3C baggage extractor and the listener (`OpenTelemetryPropagationConfigurations.java`
  lines 46–57).
- Boot's own tracing reference says baggage is propagated automatically over W3C and that
  `correlation.fields` is what moves it into MDC
  ([Tracing › Baggage](https://docs.spring.io/spring-boot/4.1/reference/actuator/tracing.html)).

## §5 — Malformed `traceparent` and `tracestate` limits in the OTel W3C propagator

**VERIFIED.** `OT` + `api/all/src/main/java/io/opentelemetry/api/trace/propagation/W3CTraceContextPropagator.java`
and `…/propagation/internal/W3CTraceContextEncoding.java`.

**Malformed `traceparent` → dropped, fresh trace.**

- `extractContextFromTraceParent` (lines 178–215) returns `SpanContext.getInvalid()` on: wrong length or
  delimiter positions (181–191), version not in `00`–`fe` (193–196), version `00` with trailing data
  (197–199), non-hex flags (206–209). Trace-id and span-id are then validated by
  `SpanContext.createFromRemoteParent` → `TraceId.isValid` (32 chars, base16, not all zeros;
  `OT` + `api/all/src/main/java/io/opentelemetry/api/trace/TraceId.java` lines 61–65), same for `SpanId`.
- `extract` (lines 141–146) returns the **unchanged incoming context** when the result is invalid; no
  exception, only a `FINE` log (line 189). The SDK then generates a new trace-id because the parent is invalid
  (`OT` + `sdk/trace/src/main/java/io/opentelemetry/sdk/trace/SdkSpanBuilder.java` lines 192–194).
- This matches W3C §3.2.4 ("restart the trace" when the version prefix cannot be parsed).

**`tracestate`.**

- 32 list-members: **enforced**. `decodeTraceState` checks `listMembers.length <= TRACESTATE_MAX_MEMBERS`
  (32) and throws (`W3CTraceContextEncoding.java` lines 29, 44–45); the propagator catches and keeps the
  `traceparent` **without** state (`W3CTraceContextPropagator.java` lines 165–175). Per-entry key/value are
  validated in `ArrayBasedTraceStateBuilder` (key ≤ 256, value ≤ 256, ≤ 32 entries; lines 24–27, 69); any
  invalid member drops the whole tracestate (`W3CTraceContextEncoding.java` lines 55–58).
- 512 characters: **not enforced on decode.** `TRACESTATE_MAX_SIZE = 512` (line 28) is used only as a
  `StringBuilder` capacity in `encodeTraceState` (line 67). The whole header is regex-split before any member
  count is checked (line 43). The only length bound on an inbound header is the container's
  `server.max-http-request-header-size`, default **8KB**, which for Tomcat applies to the combined request
  line and headers (Boot 4.1 appendix, Server properties).
- The W3C text is softer than the ticket paraphrase: §3.3.1 (the `list` definition) says there *can be a maximum of 32*
  list-members; §3.3.1.5 says vendors *SHOULD propagate at least 512 characters* and, if truncating, MUST
  truncate whole entries. 512 is a propagation floor, not a rejection ceiling.

**Correction to the ticket-15 asset (§3), recorded because it is cited by ticket 27.** W3C §4 "Processing
Model" is marked **non-normative** ("This section is non-normative"). The normative basis for "a valid
inbound `trace-id` is adopted" is §3.2 / §3.4 plus the OTel code above; the conclusion stands, the citation
should not call §4.3 normative. Separately, W3C **§3.4** names **"Restart trace"** as an allowed mutation —
all of `trace-id`, `parent-id`, `trace-flags` regenerated — used by services "defined as a front gate into
secure networks" and described as eliminating a potential denial-of-service attack surface; vendors SHOULD
clean up `tracestate` on restart. This is primary-source support for ticket 27's *restart at the boundary*
option.

## §6 — Sampling: the caller controls the sampled flag, not the `traceId` in logs

**VERIFIED.**

- Default sampler is **`parent-based-trace-id-ratio`** with probability **0.1**
  ([Boot 4.1 Tracing › Sampling](https://docs.spring.io/spring-boot/4.1/reference/actuator/tracing.html);
  appendix `management.opentelemetry.tracing.sampler`, `management.tracing.sampling.probability`; source
  `OpenTelemetryTracingProperties.java` line 45, `TracingProperties.java` line 77,
  `OpenTelemetryTracingAutoConfiguration.java` lines 136–149).
- OTel spec `ParentBased` ([Tracing SDK › ParentBased](https://opentelemetry.io/docs/specs/otel/trace/sdk/#parentbased)):
  `remoteParentSampled` defaults to `AlwaysOn`, `remoteParentNotSampled` to `AlwaysOff`. So with the default
  sampler, **a caller's flag decides**: `-00` → not sampled; `-01` → sampled, bypassing the 10 % ratio.
- Unsampled spans are not exported by default (`management.opentelemetry.tracing.export.include-unsampled`
  default `false`, appendix; `BatchSpanProcessor.setExportUnsampledSpans`,
  `OpenTelemetryTracingAutoConfiguration.java` line 169).
- **`traceId` still reaches MDC when unsampled.** On `DROP` the SDK returns `Span.wrap(spanContext)` with
  the same trace-id (inherited from the parent, `SdkSpanBuilder.java` lines 207–211, 233–250). MDC is written
  by `Slf4JEventListener` whenever the scoped span is non-null, with no sampling check
  (`MT` + `…/otel/bridge/Slf4JEventListener.java` lines 54–60; bean at
  `OpenTelemetryTracingAutoConfiguration.java` lines 219–223).

**Consequence.** A caller cannot make `traceId` disappear from our logs by sending `-00`; it only suppresses
span export. The reverse is the concern: `-01` forces 100 % export for that caller — W3C §7.2's "load the
application with tracing work" / hosted-provider cost limb. Restarting the trace at the boundary also
removes this lever.

**Adjacent fact, for ticket 03's MDC mapping.** The MDC key written by Micrometer is `traceId` (and
`spanId`), not `trace.id` (`Slf4JEventListener.java` lines 32–34). Boot's ECS formatter writes MDC entries
verbatim as context pairs (`SB` +
`core/spring-boot/src/main/java/org/springframework/boot/logging/logback/ElasticCommonSchemaStructuredLogFormatter.java`
lines 77–79). How `traceId` becomes `trace.id` on our rows is therefore a project-side mapping decided in
ticket 03; not re-verified here.

## §7 — No trace headers on responses by default

**VERIFIED within the request path; exhaustive repository search not performed.**

- `ServerHttpObservationFilter` (`SF` +
  `spring-web/src/main/java/org/springframework/web/filter/ServerHttpObservationFilter.java`) contains no
  `setHeader`/`addHeader` call.
- `PropagatingReceiverTracingObservationHandler` contains no `inject` call; injection lives only in
  `PropagatingSenderTracingObservationHandler`, which applies to `SenderContext` (outbound clients).
- The `v4.1.1` modules on this path (`spring-boot-micrometer-tracing`, `…-tracing-opentelemetry`,
  `spring-boot-opentelemetry`, `spring-boot-webmvc`, `spring-boot-micrometer-observation`) contain no
  `traceresponse` or `Server-Timing` string and no response-header writes related to tracing.
- The W3C Trace Context Recommendation itself defines no `traceresponse` header (the string does not occur
  in the Recommendation).

Consistent with the ticket-15 asset's note that W3C §6.3/§7.3 response-side exposure does not apply.

---

## Summary table

| # | Question | Answer | Status |
|---|---|---|---|
| 1 | Default propagation | consume `[W3C, B3, B3_MULTI]`, produce `[W3C]`; properties `…propagation.type/consume/produce` exist; no `NONE` | VERIFIED |
| 2 | Extraction on by default? | Yes; `ServerHttpObservationFilter` at `HIGHEST_PRECEDENCE+1`, before Security at `-100` | VERIFIED |
| 3a | consume/produce independent | Yes, separate extractor/injector lists | VERIFIED |
| 3b | Empty `consume` disables extraction | Inferred from source; undocumented, untested | UNVERIFIED |
| 3c | Alternatives | Stripping filter at `HIGHEST_PRECEDENCE`; replaceable filter, receiver handler, `Propagator`, `ContextPropagators`; extra `TextMapPropagator` bean *adds*, does not replace | Hooks VERIFIED, effect UNVERIFIED |
| 4 | Baggage | Enabled, W3C `baggage` parsed by default; MDC only for `correlation.fields` (default empty) | VERIFIED |
| 5 | Malformed input | Invalid `traceparent` ignored → new trace; `tracestate` >32 members dropped, 512 chars **not** enforced; 8KB header cap | VERIFIED |
| 6 | Sampling | Parent-based, 0.1; caller's flag decides; `traceId` in MDC regardless | VERIFIED |
| 7 | Response headers | None emitted on the request path | VERIFIED (scoped) |
