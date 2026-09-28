---
status: accepted
---

# ADR-063: Inbound trace context is restarted at the application boundary

No trace header a caller sends, valid or not, ever sets `trace.id`. A servlet filter strips every inbound
trace-context header before Boot's observation filter reads them, so each request starts a new trace with a
server-generated id. Continuing an inbound `traceparent` is what Spring Boot does by default, so this looks like a
filter that breaks distributed tracing for no reason. It exists because `trace.id` is the correlation key of the
audit stream, and a caller must not be able to choose it.

## Context

- Spring Boot 4.1 consumes W3C, B3 single-header and B3 multi-header context by default and produces W3C only
  (`management.tracing.propagation.consume` and `produce`). There is no `NONE` value, so extraction cannot be
  switched off by a property.
- Boot registers `ServerHttpObservationFilter` at `Ordered.HIGHEST_PRECEDENCE + 1`, for `REQUEST` and `ASYNC`. The
  trace is fixed there, before Spring Security runs. A rule in the security chain cannot gate it.
- The default OpenTelemetry sampler is parent-based trace-id-ratio at probability 0.1. A caller that sends the
  sampled flag `-01` bypasses the ratio and forces sampling.
- `trace.id` is the correlation ID that the audit stream carries on every row. Within the login request it links the
  rows written before and after the session-id rotation (ADR-038, R-OBS-001). It is also the `traceId` in the
  error envelope an operator is asked to quote (ADR-031).
- Nothing takes part in a trace. There is no proxy in front, no browser tracing, no outbound call, no trace exporter,
  and OTLP metrics export ships off (ADR-061).
- W3C Trace Context §3.4 lists "Restart trace" as an allowed mutation, used by services "defined as a front gate
  into secure networks", and it "eliminates a potential denial-of-service attack surface". §7.2 names forged trace-id
  collisions as an attack on services that naively continue any trace. §4, the processing model, is non-normative.

## Considered options

- **Accept inbound context, and stop treating `trace.id` as a join key.** Rejected on price. The audit rows around
  the rotation would need a new join field. The envelope's `traceId` would become a value an attacker can plant in
  an operator's hands. The `-01` sampling lever would remain. Pinning one `traceparent` across many requests would
  collapse unrelated rows onto one id.
- **Continue context only for authenticated requests**, as §7.2 suggests. Rejected. It needs custom code at the
  observation layer, since the security chain runs too late. It also cannot protect the login path, whose audit rows
  are written before the caller is authenticated.
- **Narrow `consume`.** Rejected. It has no `NONE` value, whether an empty list disables extraction was not
  confirmed, and it would be a second control resting on an inference.
- **Restart every inbound trace with a header-stripping request wrapper (chosen).**

## Decision

- A servlet filter at `Ordered.HIGHEST_PRECEDENCE`, registered for at least `REQUEST` and `ASYNC`, wraps the request.
- It hides a fixed strip set: `traceparent`, `tracestate`, `b3`, `X-B3-TraceId`, `X-B3-SpanId`, `X-B3-ParentSpanId`,
  `X-B3-Sampled`, `X-B3-Flags` and `baggage`. It adds the propagators' own `fields()`, read at startup, so a header
  that a later upgrade starts reading is stripped anyway.
- Names match case-insensitively, and stripped headers are hidden from `getHeader`, `getHeaders` and
  `getHeaderNames`.
- Nothing is parsed, so the header is never validated, and the raw value never reaches an application log line.
- `consume` stays at its default and `type` stays unset. Setting `type` would override both directions.

## Consequences

- `trace.id` is a server-generated 128-bit value on every request. T-OBS-002 checks every inbound format, T-OBS-003
  checks that a pinned `traceparent` gives different ids, and T-OBS-004 checks that `-01` cannot force sampling.
- T-OBS-015 asserts that the propagators' `fields()` is a subset of the fixed list. That is the direction that fails
  when an upgrade adds a header. The reverse holds by construction and is not the guard.
- The raw header appears in no application log line (T-AUD-028), but only while Tomcat's access log is off. Its valve
  runs before any filter. A deployer who enables it must keep trace headers out of its pattern (R-AUD-032).
- A deployer who puts a tracing gateway in front will see its traces never continue into the application. That is by
  design. It must not be fixed by configuration or by removing the wrapper (R-OBS-015).
- Baggage is off (REJ-087), and the inbound `correlation.id` field is dropped (REJ-088). Neither is needed once
  `trace.id` is server-side.

## Reopening triggers

Each of these makes continuation a trusted-boundary decision, meaning which hop's trace is believed. None of them is
a configuration change.

- A deployer introduces an upstream tracing gateway (R-OBS-016).
- The SPA starts sending `traceparent`. That cannot happen until a trace header is added to the CORS allowed headers,
  which today hold only `Content-Type` and `X-CSRF-TOKEN`. A tripwire comment sits beside that line (R-OBS-017).
- The application makes an outbound call to a traced service (R-OBS-018).
- Any change needs a correlation field, from baggage or an inbound header. It must first decide how a caller-set
  value is kept out of the audit stream (R-OBS-014).
- Continuation is re-enabled for any reason. The rows around the session-id rotation then need their own join field
  (R-OBS-001).

## Sources

- Spring Boot 4.1.x source: `TracingProperties` (`Propagation` defaults, `Sampling` default 0.1, `Baggage`),
  `OpenTelemetryTracingProperties` (default sampler `PARENT_BASED_TRACE_ID_RATIO`),
  `OpenTelemetryTracingAutoConfiguration` (`otelSampler`, `otelContextPropagators`), `WebMvcObservationAutoConfiguration`
  (filter order and dispatcher types).
- Spring Boot 4.1 reference, Actuator, Tracing: "Logging Correlation IDs", "Propagating Traces", "Baggage".
- W3C Trace Context (W3C Recommendation): §3.4 Mutating the traceparent Field, §6.1, §7 Security Considerations,
  §7.2 Denial of Service.
- Fetch Living Standard: CORS-safelisted request-header.
- IM8 lm-4 (Audit Logging).
