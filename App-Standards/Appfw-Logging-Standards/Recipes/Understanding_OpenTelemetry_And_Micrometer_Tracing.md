# Understanding OpenTelemetry and Micrometer Tracing in Spring Boot

## 1. Introduction

When setting up distributed tracing in Spring Boot, developers often wonder whether to use OpenTelemetry or Micrometer Tracing. The answer is: **you use both**. They work together in layers rather than as alternatives.

**Micrometer Tracing** is Spring Boot's tracing facade that auto-configures trace context propagation and provides a vendor-neutral API for instrumentation. The backend tracer (OpenTelemetry SDK or Brave) injects `traceId` and `spanId` into MDC for log correlation.

**OpenTelemetry SDK** and **Brave** are the two tracer implementations that Micrometer Tracing uses underneath to create and export spans. You choose one by adding either `spring-boot-starter-opentelemetry` or `spring-boot-starter-zipkin` to your project.

This guide explains:
- How Micrometer Tracing, OpenTelemetry, and Brave relate as layers
- How to choose a backend (OpenTelemetry SDK vs Brave)
- When to use the Micrometer Tracing API, OpenTelemetry API, or OTel Java Agent for instrumentation
- When OpenTelemetry's polyglot design makes it the required choice for multi-language environments

## 2. Prerequisites
- Spring Boot 4.0+
- Familiarity with distributed tracing concepts: traces, spans, and context propagation

## 3. The layered architecture

**Micrometer Tracing is always present.** It is Spring Boot's auto-configuration layer that:
- Defines the instrumentation APIs (`ObservationRegistry`, `@Observed`, `Tracer`)
- Provides the integration layer for backend tracers to inject `traceId` and `spanId` into MDC
- Propagates trace context across HTTP boundaries
- Delegates span creation and export to a pluggable backend tracer

**You choose the tracer** by adding one starter:
- `spring-boot-starter-opentelemetry` → Uses OpenTelemetry SDK tracer → Exports OTLP
- `spring-boot-starter-zipkin` → Uses Brave tracer → Exports Zipkin JSON

```
Your application code
    │
    ▼
Micrometer Tracing API          ← Always present (Spring Boot auto-configuration)
    │
    ▼
Tracer: OTel SDK or Brave       ← Chosen by starter dependency
    │                              Injects traceId/spanId to MDC
    ▼
Exporter: OTLP or Zipkin        ← Sends spans to observability platform
```

**Key insight:** The Micrometer Tracing API works identically regardless of which tracer you choose. Switching from Brave to OpenTelemetry (or vice versa) requires only changing the starter dependency - your instrumentation code remains unchanged.

## 4. When do you need OpenTelemetry?

Since Micrometer Tracing works with either backend, the key question is: **when must you choose OpenTelemetry SDK instead of Brave?**

**You need OpenTelemetry SDK (`spring-boot-starter-opentelemetry`) when:**

1. **Your observability platform requires OTLP export**
   - Dynatrace, Jaeger, Grafana Tempo, or other OTLP-compatible backends
   - Zipkin can accept OTLP, but `spring-boot-starter-zipkin` uses Zipkin JSON format

2. **Polyglot services: Java + Go/Python/Node.js**
   - Micrometer Tracing is Java-only. OpenTelemetry provides SDKs for all major languages
   - All OTel SDKs use W3C `traceparent` headers by default, enabling seamless trace stitching across languages
   
   ```
   Java (Spring Boot) → Python (FastAPI) → Go (net/http)
        OTel SDK           OTel SDK          OTel SDK
            └─────────────────┴────────────────┘
              Single trace (same trace.id)
   ```
   
   - OTel semantic conventions (`http.request.method`, `http.response.status_code`) are consistent across all language SDKs, making cross-service dashboards and queries work without per-language adjustments

3. **New projects**
   - OpenTelemetry is the CNCF industry standard for observability
   - Avoids vendor lock-in to Zipkin infrastructure

4. **Export metrics and traces together**
   - `spring-boot-starter-opentelemetry` can export both to a single OTLP endpoint
   - `spring-boot-starter-zipkin` only exports traces

5. **Baggage propagation**
   - Built-in via W3C Trace Context with OTel
   - Requires explicit `propagation.type: w3c` configuration with Brave

**You can use Brave (`spring-boot-starter-zipkin`) when:**

1. **Existing Zipkin infrastructure** is already in place
2. **Pure Java fleet** with no non-Java services now or planned
3. **B3 propagation** is already standardized across your organization

**Additional considerations:**

- **Message queues**: W3C headers only propagate via HTTP. For Kafka/RabbitMQ, trace context must be manually injected into message headers. OTel auto-instrumentation handles this when both producer and consumer use OTel SDKs.

- **Security**: Spring Boot accepts incoming `traceparent` headers by default. For public endpoints, most production systems strip trace headers at API gateways/load balancers to prevent untrusted trace injection.

## 5. Backend selection criteria

Now that you understand when OpenTelemetry is required, use the following criteria to choose between the two starters.

| Criterion | spring-boot-starter-opentelemetry | spring-boot-starter-zipkin |
|---|---|---|
| **Export format** | OTLP (HTTP or gRPC) | Zipkin JSON |
| **Compatible platforms** | Dynatrace, Jaeger, Grafana Tempo, any OTLP-compatible backend | Zipkin, some Grafana setups |
| **Default propagation** | W3C Trace Context | B3 |
| **Also exports metrics** | Yes, via OTLP | No |
| **Baggage propagation** | Built-in via W3C | Requires `propagation.type: w3c` |
| **When to prefer** | New projects, OTLP-compatible backends | Existing Zipkin infrastructure |

### Use spring-boot-starter-opentelemetry when:
- The observability backend accepts OTLP (Dynatrace, Jaeger, Grafana Tempo)
- Metrics and traces should be exported together to a single endpoint
- The service must interoperate with other services using W3C `traceparent` headers
- This is a new project with no existing Zipkin infrastructure

### Use spring-boot-starter-zipkin when:
- The observability backend is Zipkin
- An existing fleet of services already uses B3 propagation and changing formats would require coordinating across teams

Both starters inject `traceId` and `spanId` into MDC automatically. Log correlation works identically regardless of which is chosen.

### What you get automatically

Before configuring anything, understand what Spring Boot provides out of the box. With just Actuator and a tracing starter, Spring Boot's auto-configuration sets up instrumentation filters and interceptors. When an entry point is triggered, the backend tracer (Brave or OpenTelemetry SDK) generates the trace ID and creates spans through Micrometer Tracing's integration layer.

**✅ Auto-instrumented (trace ID automatically generated):**

1. **HTTP requests** - All incoming requests to `@RestController` or `@Controller` endpoints
   ```java
   @RestController
   public class OrderController {
       @GetMapping("/orders")
       public String getOrders() {
           // ✅ New trace ID generated automatically
           // ✅ traceId and spanId in all logs within this request
           log.info("Processing order request");
           return "done";
       }
   }
   ```

2. **Scheduled tasks** - Methods annotated with `@Scheduled`
   ```java
   @Service
   public class BatchJobService {
       @Scheduled(cron = "0 0 * * * *")
       public void dailyJob() {
           // ✅ New trace ID generated automatically
           log.info("Running daily job");
       }
   }
   ```

3. **Message consumers** - Kafka `@KafkaListener`, RabbitMQ listeners
   ```java
   @Service
   public class OrderEventConsumer {
       @KafkaListener(topics = "orders")
       public void handleOrderEvent(String message) {
           // ✅ Trace ID from message header OR new trace ID if not present
           log.info("Processing order event");
       }
   }
   ```

**⚠️ Requires additional configuration:**

- **@Async methods** - Trace propagation to `@Async` methods requires configuring executors with `ContextPropagatingTaskDecorator`. Without this configuration, async methods will not have trace IDs. See [Enriching Logs with MDC](Enriching_Logs_With_MDC.md#6-propagate-context-in-async-work) for async context propagation setup.

**❌ NOT auto-instrumented (no trace ID unless called from entry point):**

1. **Internal service methods** - Regular `@Service` or `@Component` methods
   ```java
   @Service
   public class OrderService {
       public void processOrder(String orderId) {
           // ❌ NO trace ID if called directly from main() or standalone
           // ✅ Inherits trace ID if called within HTTP/scheduled/message request
           log.info("Processing order");
       }
   }
   ```

2. **Main method / CommandLineRunner** - Standalone application startup
   ```java
   @SpringBootApplication
   public class Application {
       public static void main(String[] args) {
           // ❌ NO trace ID (not a traced entry point)
           SpringApplication.run(Application.class, args);
       }
   }
   ```

3. **Background threads** - Manually created threads without context propagation
   ```java
   public void startBackgroundTask() {
       new Thread(() -> {
           // ❌ NO trace ID (new thread, context not propagated)
           log.info("Background task running");
       }).start();
   }
   ```

**Key insight:** Once a trace ID is generated at an entry point (HTTP request, scheduled task, or message), all logs within that execution context automatically include the same `traceId` and `spanId` - no additional code required. Internal method calls inherit the trace ID from the entry point.

### Configuration reference

Now that you understand what's provided automatically, here's how to enable it. Spring Boot tracing requires `spring-boot-starter-actuator`, which provides the observability infrastructure (metrics, health checks, and tracing configuration). Add Actuator plus one tracing starter to `pom.xml`.

```xml
<!-- File: pom.xml -->
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    
    <!-- Choose ONE tracing starter: -->
    
    <!-- Option 1: OpenTelemetry (recommended for new projects) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-opentelemetry</artifactId>
    </dependency>
    
    <!-- Option 2: Zipkin/Brave (for existing Zipkin infrastructure) -->
    <!--
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-zipkin</artifactId>
    </dependency>
    -->
</dependencies>
```

```yaml
# File: src/main/resources/application.yml
management:
  otlp:  # For OpenTelemetry starter
    tracing:
      endpoint: http://localhost:4318/v1/traces
  zipkin:  # For Zipkin starter
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
  tracing:
    sampling:
      probability: 1.0  # 100% for local/test; 0.2-0.5 for staging; 0.05-0.2 for production
    propagation:
      type: w3c  # Required for Zipkin starter if baggage must cross service boundaries
```

The configuration above is sufficient for most applications.

## 6. Custom instrumentation (optional)

Section 6 covers **custom instrumentation** - when you want to create additional trace spans for internal business logic methods that aren't automatically instrumented. Most applications don't need this.

**Skip Section 6 if:**
- You only need traceId/spanId in logs (automatic)
- You only trace HTTP requests and database calls (automatic)

**Read Section 6 if:**
- You want custom spans for internal service methods
- You need to add span attributes for business context

Once a backend is chosen, there are three approaches for creating custom spans: the Micrometer Tracing API, the OpenTelemetry API, or the OTel Java Agent. They are not mutually exclusive, but each suits different scenarios.

### 6.1 Micrometer Tracing API (recommended default)

The Micrometer Tracing API includes `ObservationRegistry`, `@Observed`, and `Tracer`. It is the recommended choice for Spring Boot applications because it produces both a trace span and a timer metric from a single `observe()` call, and it integrates naturally with Spring Boot's auto-configuration.

**Use the Micrometer Tracing API when:**
- Instrumenting Spring beans, services, or controllers
- A timer metric is needed alongside the trace span
- Baggage must be set in a way that propagates automatically to MDC and downstream headers
- The instrumentation should work regardless of which backend (OTel or Brave) is active

```java
// File: src/main/java/com/example/OrderService.java
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OrderService {
    private final ObservationRegistry observationRegistry;

    public OrderService(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    public void processOrder(String orderId) {
        Observation.createNotStarted("order.process", observationRegistry)
            .lowCardinalityKeyValue("event.outcome", "success")
            .highCardinalityKeyValue("order.id", orderId)
            .observe(() -> {
                log.atInfo()
                    .addKeyValue("order.id", orderId)
                    .log("Processing order.");
            });
    }
}
```

The `observe()` call above produces a trace span named `order.process` and a timer metric named `order.process` with the tag `event.outcome=success`. The `order.id` value appears on the span only, not as a metric tag, because it was declared as a high-cardinality key-value pair.

> **Note on Cardinality:** Low-cardinality attributes (status codes, outcomes, HTTP methods) have limited possible values and are safe as metric tags. High-cardinality attributes (order IDs, user IDs) have many unique values and must not be used as metric tags, as they explode metric dimensions. Use `.lowCardinalityKeyValue()` for metric tags and `.highCardinalityKeyValue()` for span-only fields. This distinction only applies to the Micrometer API; the OpenTelemetry API does not produce metrics.

For simple cases where no custom key-value pairs are needed, `@Observed` is a lighter alternative:

```java
// File: src/main/java/com/example/OrderService.java
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Observed(name = "order.service", contextualName = "process-order")
@Service
public class OrderService {

    public void processOrder(String orderId) {
        log.atInfo()
            .addKeyValue("order.id", orderId)
            .log("Processing order.");
    }
}
```

`@Observed` requires `management.observations.annotations.enabled: true` and the `aspectjweaver` dependency. See [Structured Logging, Trace Correlation, and Context Propagation](Structured_Logging_Trace_Correlation_And_Context_Propagation.md) for the full setup.

### 6.2 OpenTelemetry API

The OpenTelemetry API (`io.opentelemetry.api.trace.Tracer`) is an alternative for creating spans directly using the OTel SDK. It is available when `spring-boot-starter-opentelemetry` is on the classpath.

**Use the OpenTelemetry API when:**
- The code is a shared library or utility that must work in non-Spring environments (for example, a standalone Java library used both in Spring and in plain Java processes)
- OTel-specific span events or attributes are required that have no equivalent in the Micrometer API (for example, `span.addEvent(...)`)
- The team is targeting OpenTelemetry semantic conventions directly and wants portability across observability platforms without a Spring dependency

```java
// File: src/main/java/com/example/OrderService.java
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OrderService {
    private final Tracer tracer;

    public OrderService(Tracer tracer) {
        this.tracer = tracer;
    }

    public void processOrder(String orderId) {
        Span span = tracer.spanBuilder("order.process")
            .setAttribute("order.id", orderId)
            .startSpan();
        try (var scope = span.makeCurrent()) {
            log.atInfo()
                .addKeyValue("order.id", orderId)
                .log("Processing order.");
        } finally {
            span.end();
        }
    }
}
```

The `Tracer` bean is provided automatically by Spring Boot when `spring-boot-starter-opentelemetry` is on the classpath. The `span.makeCurrent()` call propagates the span into the current thread context so that `traceId` and `spanId` appear in MDC and in log output.

**This approach does not produce timer metrics.** If metrics are also required for the instrumented block, use the Micrometer Tracing API instead or add a separate `Timer` recording.

### 6.3 OTel Java Agent

The OTel Java Agent is a third approach that sits outside the Spring starter model entirely. It attaches to the JVM at startup via `-javaagent` and uses bytecode manipulation to instrument the application without any code changes. It is not a dependency added to `pom.xml` and does not interact with Spring Boot auto-configuration.

The Java Agent and Spring Boot starter are both valid zero-code instrumentation approaches. The Java Agent provides broader automatic instrumentation coverage (150+ libraries), while the Spring Boot starter integrates natively with Spring's auto-configuration and supports GraalVM Native Image. Choose based on the constraints below.

**Use the OTel Java Agent when:**
- The application must be instrumented without modifying source code (for example, a legacy application or a third-party JAR)
- The environment supports attaching the agent at JVM startup (standard JVM deployments, Docker containers)
- Coverage for 150+ libraries (JDBC, Hibernate, Kafka, gRPC, HTTP clients) is needed without writing any instrumentation code

**Do not use the OTel Java Agent when:**
- The application uses GraalVM Native Image: the agent relies on dynamic bytecode manipulation which is incompatible with Ahead-of-Time (AOT) compilation. Use `spring-boot-starter-opentelemetry` instead for native builds.
- Another JVM monitoring agent is already attached (only one agent can safely perform bytecode manipulation at a time)
- The JVM startup overhead introduced by agent bytecode scanning is above the environment's threshold

```
# Deployment: attach the agent at JVM startup
java -javaagent:/path/to/opentelemetry-javaagent.jar \
     -Dotel.service.name=order-service \
     -Dotel.exporter.otlp.endpoint=http://localhost:4318 \
     -jar order-service.jar
```

The agent injects `traceId` and `spanId` into MDC automatically, so log correlation works the same as with the Spring Boot starter. No `spring-boot-starter-opentelemetry` dependency is needed when using the agent.

| | OTel Java Agent | spring-boot-starter-opentelemetry | spring-boot-starter-zipkin |
|---|---|---|---|
| **Code changes required** | None | Add dependency + config | Add dependency + config |
| **GraalVM Native Image** | Not supported | Supported | Supported |
| **Auto-instrumented libraries** | 150+ | Spring MVC, JDBC, Kafka, MongoDB, R2DBC, Logback | Spring MVC, JDBC |
| **Custom spans** | OTel API or Micrometer | OTel API or Micrometer | Micrometer only |
| **Export format** | OTLP | OTLP | Zipkin JSON |
| **MDC injection** | Automatic | Automatic | Automatic |

### 6.4 Comparison summary

| | Micrometer Tracing API | OpenTelemetry API |
|---|---|---|
| **Works with Brave backend** | Yes | No (OTel starter only) |
| **Works with OTel backend** | Yes | Yes |
| **Produces metrics** | Yes (timer + long-task timer) | No |
| **Supports baggage** | Yes, via `Tracer.createBaggageInScope()` | Yes, via `Baggage.current()` |
| **Span events** | Not directly supported | Yes, via `span.addEvent(...)` |
| **Spring integration** | Native | Works as a Spring bean |
| **Non-Spring use** | Requires Micrometer dependency | Standard OTel API, no Spring needed |
| **Recommended for Spring Boot** | Yes | Only when OTel-specific features are required |

## 7. Propagation format

Propagation format determines how trace context is carried in HTTP headers between services.

| Format | W3C Trace Context | B3 |
|---|---|---|
| **Headers** | `traceparent`, `tracestate` | `X-B3-TraceId`, `X-B3-SpanId`, `X-B3-Sampled` |
| **Standard** | IETF standard | Zipkin format |
| **OTel starter default** | ✅ Yes | No |
| **Zipkin starter default** | No | ✅ Yes |
| **Baggage support** | ✅ Yes | ❌ No |
| **Polyglot compatible** | ✅ All OTel SDKs | Only Java/Brave |

**Use W3C Trace Context (recommended):**
- New projects
- Polyglot services (non-Java services in the fleet)
- Baggage propagation required

**Use B3 only when:**
- Existing Zipkin infrastructure with B3 already deployed fleet-wide

**Override propagation format:**
```yaml
management:
  tracing:
    propagation:
      type: w3c  # Options: w3c, b3, b3_multi
```

> **Note:** When using `spring-boot-starter-zipkin` and need W3C compatibility, explicitly set `type: w3c`. The OpenTelemetry starter uses W3C by default and requires no configuration.

## 8. Decision guide

**Which backend starter?**
1. Backend accepts OTLP, or new project, or polyglot fleet → `spring-boot-starter-opentelemetry`
2. Existing Zipkin infrastructure → `spring-boot-starter-zipkin`

**Which instrumentation approach?**
1. Legacy application (no code changes) → OTel Java Agent via `-javaagent`
2. GraalVM Native Image → `spring-boot-starter-opentelemetry` with Micrometer API
3. Spring bean needing metrics → `ObservationRegistry` or `@Observed`
4. Non-Spring library or OTel span events needed → OTel API directly
5. Default → Micrometer Tracing API

**Which propagation format?**
1. Baggage needed, or polyglot services, or new project → W3C
2. Existing Zipkin B3 fleet → Retain B3 unless migrating

## 9. Verification

### Basic tracing (automatic)

Trigger an HTTP request to your application and verify:

1. **Logs contain trace IDs:**
   ```json
   {"@timestamp":"2024-01-01T10:15:00.067+08:00","log.level":"INFO","trace.id":"803B448A0489F84084905D3093480352","span.id":"3425F23BB2432450","message":"Processing order"}
   ```

2. **Trace ID appears at entry points:**
   - HTTP requests to `@RestController` endpoints
   - `@Scheduled` methods
   - `@KafkaListener` methods

3. **Trace ID propagates to downstream services:**
   - Check outbound HTTP request headers contain `traceparent` (W3C) or `X-B3-TraceId` (B3)
   - Same `trace.id` appears in logs across multiple services

4. **Spans exported to observability platform:**
   - Verify spans appear in your trace platform (Dynatrace, Jaeger, Zipkin)
   - HTTP request spans show method, path, and status code

### Custom instrumentation (if implemented)

If you added custom spans using Section 6, additionally verify:

- **Custom spans appear as child spans** in the trace platform
- **Timer metrics** appear in `/actuator/metrics/<observation-name>` if using `ObservationRegistry` or `@Observed`
- **Span attributes** contain expected key-value pairs
- **Baggage fields** appear in MDC and downstream service traces if configured

## 10. Conclusion

This guide explained how Micrometer Tracing, OpenTelemetry, and Brave relate in Spring Boot, when to choose each backend, and how automatic vs custom instrumentation work.

### Key Takeaways

**Understanding the architecture**
- **Micrometer Tracing is a facade** that requires a backend tracer (OpenTelemetry SDK or Brave)
- **No default tracer** — you must add either `spring-boot-starter-opentelemetry` or `spring-boot-starter-zipkin`
- **Backend tracers generate trace IDs** and inject them into MDC through Micrometer's integration
- **API portability** — switching backends requires only changing the starter; instrumentation code remains unchanged

**Automatic tracing (no code required)**
- **Adding Actuator + tracing starter is sufficient** for most applications
- **Trace IDs auto-generated** at entry points: HTTP requests, `@Scheduled` tasks, message consumers, `@Async` methods
- **All logs within request context** automatically include `traceId` and `spanId`
- **Spans exported automatically** for HTTP, JDBC, Kafka without custom code

**When to choose OpenTelemetry SDK**
1. Observability platform requires OTLP (Dynatrace, Jaeger, Grafana Tempo)
2. Polyglot services (Java + Go/Python/Node.js)
3. New projects (industry standard, no vendor lock-in)
4. Export metrics and traces together
5. Baggage propagation required

**When Brave is acceptable**
- Existing Zipkin infrastructure with B3 propagation already deployed

**Custom instrumentation (optional)**
- **Use Micrometer API by default** (`ObservationRegistry`, `@Observed`) — works with both backends, produces metrics
- **Use OTel API only for** non-Spring libraries or OTel-specific features (span events)
- **Use OTel Java Agent for** zero-code instrumentation of legacy apps (not GraalVM compatible)

**Propagation**
- **Use W3C Trace Context** (required for baggage, polyglot compatibility, industry standard)
- Configure `type: w3c` explicitly when using `spring-boot-starter-zipkin`

## 11. References

Related guides:
- [Log Schema](../Log_Schema.md)
- [Structured Logging, Trace Correlation, and Context Propagation](Structured_Logging_Trace_Correlation_And_Context_Propagation.md)
- [Enriching Logs with MDC](Enriching_Logs_With_MDC.md)

Spring Boot:
- [Spring Boot Reference: Tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)
- [Spring Boot Reference: Observability](https://docs.spring.io/spring-boot/reference/actuator/observability.html)
- [Spring Boot Reference: OpenTelemetry](https://docs.spring.io/spring-boot/reference/actuator/tracing.html#actuator.micrometer-tracing.tracer-implementations.otel-zipkin)
- [Spring Boot 4.0 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes)

Micrometer:
- [Micrometer Tracing Reference](https://micrometer.io/docs/tracing)
- [Micrometer Observation Reference](https://micrometer.io/docs/observation)
- [Micrometer Context Propagation Reference](https://docs.micrometer.io/micrometer/reference/contextpropagation.html)

OpenTelemetry:
- [OpenTelemetry Java SDK](https://opentelemetry.io/docs/languages/java/)
- [OpenTelemetry Java Agent](https://opentelemetry.io/docs/zero-code/java/agent/)
- [OpenTelemetry Spring Boot Starter](https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/)
- [OpenTelemetry Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/)
- [OpenTelemetry Context Propagation](https://opentelemetry.io/docs/concepts/context-propagation/)
- [OpenTelemetry Language APIs and SDKs](https://opentelemetry.io/docs/languages/)
- [W3C Trace Context Specification](https://www.w3.org/TR/trace-context/)

Dynatrace:
- [Dynatrace Docs: OpenTelemetry API](https://docs.dynatrace.com/docs/extend-dynatrace/opentelemetry/opentelemetry-api)
- [Dynatrace Docs: OTLP API](https://docs.dynatrace.com/docs/extend-dynatrace/opentelemetry/otlp)

Further reading:
- [Spring Blog: OpenTelemetry with Spring Boot](https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot/)
- [OpenTelemetry Java Agent vs Micrometer Tracing (blog.frankel.ch)](https://blog.frankel.ch/opentelemetry-tracing-spring-boot/)
