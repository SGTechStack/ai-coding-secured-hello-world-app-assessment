# Structured Logging, Trace Correlation, and Context Propagation in Spring Boot

## 1. Introduction
Structured logging records events as machine-readable key-value fields, making logs searchable and filterable by field in a log platform such as OpenSearch. Tracing adds trace and span identifiers so a request can be followed end-to-end, including across async work and downstream calls.

This guide explains how to configure structured JSON logging, trace correlation, and context propagation in a Spring Boot application. It covers setting up Micrometer Tracing, foundational logging configuration (levels, file rolling, groups), producing custom spans for internal methods, propagating context across async boundaries and outbound calls, and using Spring Boot's extensions for custom Logback configurations.

## 2. Prerequisites
- Spring Boot 4.0+
- Optional: Access to a trace platform such as Dynatrace for span visualization
- Schema compliance: Use field values from [Log_Schema.md](../Log_Schema.md) for `event.category`, `event.type`, `event.action`, and `error_category` to ensure consistency across services

## 3. Set up tracing
Spring Boot uses Micrometer Tracing as its tracing facade, which requires a backend implementation to create and export spans. Add Actuator and choose one tracing starter (OpenTelemetry or Zipkin) to enable trace propagation. If you are not exporting traces to an external platform, you can omit the endpoint configuration.

> **Note:** For detailed guidance on when to choose OpenTelemetry vs Zipkin and how Micrometer Tracing relates to OpenTelemetry, see [Understanding OpenTelemetry and Micrometer Tracing](Understanding_OpenTelemetry_And_Micrometer_Tracing.md).

<note>

> **Note:** W3C Trace Context (`traceparent`/`tracestate` headers) is the recommended propagation format. The OpenTelemetry starter uses W3C by default. Brave with Zipkin uses B3 propagation by default. Configure `management.tracing.propagation.type: w3c` if you use Brave and require W3C compatibility or baggage propagation across service boundaries.

</note>

### OpenTelemetry with OTLP
The `spring-boot-starter-opentelemetry` starter enables OTLP export of metrics and traces in a single dependency. Use this for new projects sending telemetry to OTLP-compatible backends such as Dynatrace.

```xml
# File: pom.xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-opentelemetry</artifactId>
    </dependency>
</dependencies>
```

Configure the OTLP trace export endpoint. Spring Boot defaults to HTTP on port 4318. Use gRPC on port 4317 if the collector requires it.

```yaml
# File: src/main/resources/application.yml
management:
  otlp:
    tracing:
      # Only include properties you need to change
      endpoint: http://localhost:4318/v1/traces # Default: http://localhost:4318/v1/traces
      transport: http                           # Default: http
```

### Brave with Zipkin
If your observability backend uses Zipkin instead of an OTLP-compatible endpoint, use `spring-boot-starter-zipkin`. This configures Micrometer Tracing to use Brave as its backend implementation.

```xml
# File: pom.xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-zipkin</artifactId>
</dependency>
```

Configure the Zipkin endpoint and explicitly set the propagation type to W3C if baggage propagation across services is required:

```yaml
# File: src/main/resources/application.yml
management:
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans # Default: http://localhost:9411/api/v2/spans
  tracing:
    propagation:
      type: w3c # Required for baggage propagation (Default: b3)
```

Both backend tracers (OpenTelemetry SDK and Brave) inject `traceId` and `spanId` into MDC automatically through Micrometer Tracing's integration, so log correlation works identically regardless of which backend is active.

### Set the sampling rate
Spring Boot samples 10% of requests by default. Trace platforms can be overwhelmed if every request is sampled, but too low a rate misses the spans needed for diagnosis. Adjust based on environment and traffic volume:

- Local and test: `1.0` (trace every request)
- Staging: `0.2` to `0.5` (mirror production without full cost)
- Production: `0.05` to `0.2` (depends on traffic and retention limits)

```yaml
# File: src/main/resources/application.yml
management:
  tracing:
    sampling:
      probability: 1.0 # Default: 0.1 (10%)
```

Sampling controls only which traces are **exported** to the trace platform. All requests still generate trace IDs that appear in logs via MDC, regardless of the sampling rate. A 10% sampling rate means 100% of logs contain trace IDs, but only 10% of trace spans are sent to the platform for storage and visualization.

### Set OpenTelemetry resource attributes
When using the OpenTelemetry starter, resource attributes determine how the service is identified in trace platforms. This is independent of the ECS service metadata (which controls the `service.name` in log output). Neither setting overrides the other.

If they are configured independently with different values, logs and traces will show mismatched service names. To avoid this, set `spring.application.name` once. Both the ECS formatter and the OTel SDK fall back to it automatically, requiring no separate configuration.

If you must override the trace span metadata specifically (without affecting logs), configure the OTel resource attributes:

```yaml
# File: src/main/resources/application.yml
management:
  opentelemetry:
    resource-attributes:
      service.name: order-service
      service.version: "2.1.0"
      deployment.environment: production
```

Spring Boot also reads standard OpenTelemetry environment variables (`OTEL_SERVICE_NAME`, `OTEL_RESOURCE_ATTRIBUTES`) at startup. If provided, `OTEL_SERVICE_NAME` overrides the `management.opentelemetry.resource-attributes.service.name` YAML value.

## 4. Configure logging
This section covers the core Spring Boot logging settings: enabling structured JSON output, writing to files, and controlling log levels. It also covers optional settings for plain-text output patterns, JSON field selection, ECS service metadata, and log timestamps.

### Set the application name
`spring.application.name` is the baseline identity for both logs and traces. The ECS encoder uses it as the `service.name` field in every log entry, and the OTel SDK uses it to identify the service in trace platforms. Both fall back to it automatically without further configuration.

```yaml
# File: src/main/resources/application.yml
spring:
  application:
    name: order-service
```

If `spring.application.name` is not set, `service.name` will be absent from logs and traces. Configure `logging.structured.ecs.service.*` or OTel resource attributes only if the service name must differ between logs and traces.

### Declare loggers
Each class that emits log entries needs an SLF4J logger. Declare it as a static field, or use `@Slf4j` to generate it automatically if Lombok is on the classpath.

```java
// Without Lombok
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

private static final Logger log = LoggerFactory.getLogger(MyService.class);
```

```java
// With Lombok: add @Slf4j on the class declaration
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MyService { ... }
```

Both approaches produce the same `log` field.

### Enable structured logging
Structured logging writes each log entry as a JSON object, making logs queryable and consistent across services. Use ECS format and set only the outputs your environment collects: console, file, or both.

```yaml
# File: src/main/resources/application.yml
logging:
  structured:
    format:
      console: ecs # Default: plain text
      file: ecs    # Default: plain text
```

MDC values are included automatically in ECS and Logstash output.

### Configure file output
Spring Boot logs to the console only by default. To retain log files locally for collection, support, or audit, set `logging.file.name` or `logging.file.path`.

```yaml
# File: src/main/resources/application.yml
logging:
  file:
    name: logs/application.log
```

Once file logging is enabled, Spring Boot rolls log files at 10 MB and keeps seven archives by default.

To change the default policy without a custom `logback-spring.xml`, use `logging.logback.rollingpolicy.*`:

```yaml
# File: src/main/resources/application.yml
logging:
  logback:
    rollingpolicy:
      file-name-pattern: logs/application-%d{yyyy-MM-dd}.%i.log # Default: ${LOG_FILE}.%d{yyyy-MM-dd}.%i.gz
      max-file-size: 50MB                                       # Default: 10MB
      max-history: 7                                            # Default: 7
      total-size-cap: 1GB                                       # Default: 0B (no cap)
      clean-history-on-start: false                             # Default: false
```

Set `clean-history-on-start: true` only when archives should be deleted on every application restart.

#### Rolling policy types
When using a custom `logback-spring.xml`, select a rolling policy based on your log volume and rotation requirements:

| Policy | Class | When to use |
|---|---|---|
| Time-based | `TimeBasedRollingPolicy` | Roll on a time boundary such as daily |
| Size and time-based | `SizeAndTimeBasedRollingPolicy` | Roll on time or size, best for higher log volume |
| Fixed window | `FixedWindowRollingPolicy` | Roll on size and keep a fixed number of archives |

Example using `SizeAndTimeBasedRollingPolicy`:

```xml
<rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
    <fileNamePattern>/var/log/application/application-%d{yyyy-MM-dd}.%i.ndjson</fileNamePattern>
    <maxFileSize>50MB</maxFileSize>
    <maxHistory>7</maxHistory>
    <totalSizeCap>1GB</totalSizeCap>
</rollingPolicy>
```

`totalSizeCap` limits the total archive size. When the cap is reached, Logback deletes the oldest files first.

### Configure log levels
Log levels control which entries are emitted. The default root level is `INFO`, which suppresses `DEBUG` and `TRACE` output. Use `logging.level.*` to set a global default and override it for specific packages: raise the level for noisy framework packages, lower it for areas under active development.

```yaml
# File: src/main/resources/application.yml
logging:
  level:
    root: INFO
    com.example.payments: DEBUG
    org.springframework.web: WARN
```

If Actuator is enabled, you can also change levels at runtime through `/actuator/loggers/{package}`. This is useful for short-term troubleshooting without restarting the application.

### Configure log groups
Log groups let you configure several related loggers under one name. Use them when a feature area spans multiple packages and should be tuned together.

```yaml
# File: src/main/resources/application.yml
logging:
  group:
    payments: com.example.payments,com.example.billing
  level:
    payments: DEBUG # Default: inherit from root
```

Spring Boot includes two built-in groups:

| Group | Loggers included |
|---|---|
| `web` | Spring MVC, codec, servlet, and actuator web components |
| `sql` | Spring JDBC, Hibernate SQL, and JOOQ |

```yaml
logging:
  level:
    web: DEBUG
    sql: DEBUG
```

### Customise JSON fields
Use these settings to control which fields appear in each JSON log entry. Common uses: removing fields you do not need and adding static fields that do not change at runtime.

`include` acts as a whitelist: only the listed fields are written. Omit it to keep all default fields and use `exclude` to remove specific ones instead.

```yaml
# File: src/main/resources/application.yml
logging:
  structured:
    json:
      include: "@timestamp", log.level, message, trace.id, span.id  # Restrict to this field set only. All other fields are dropped.
      exclude: process.id                                            # Remove a noisy default field
      add:
        service.system: enterprise-finance                           # Add static metadata not set elsewhere
        service.subsystem: order-processing
```

### Customise stack trace output
These settings limit the stack trace depth and length included in each structured JSON entry. Use them when full stack traces inflate log volume in production. The defaults are sufficient for most cases.

```yaml
# File: src/main/resources/application.yml
logging:
  structured:
    json:
      stacktrace:
        root: last
        max-length: 4096
        max-throwable-depth: 5
        include-common-frames: false
        include-hashes: true
```

### Set service metadata for ECS format
These ECS-specific settings embed `name`, `version`, and `environment` into each log entry for consistent application identification in a central log platform. They affect logs only, not OTel spans. Spring Boot falls back to `spring.application.name` if `logging.structured.ecs.service.name` is not set, so explicit values are only needed when the fallback is not specific enough.

```yaml
# File: src/main/resources/application.yml
logging:
  structured:
    ecs:
      service:
        name: order-service     # Default: ${spring.application.name}
        version: 2.1.0          # Default: none
        environment: production # Default: none
```

### Configure log timestamps
The ECS encoder uses the JVM default time zone for `@timestamp`. To standardise on a fixed zone, set the time zone at the process level so the application and log timestamps stay aligned:

```yaml
# File: docker-compose.yml or deployment manifest
environment:
  - TZ=Asia/Singapore
```

Or as a JVM argument:

```
-Duser.timezone=Asia/Singapore
```

Either approach produces `@timestamp` values with the `+08:00` offset.

### Customise console and file patterns
Pattern settings apply only when structured logging is **not** enabled. If `console` or `file` format is set to `ecs`, these patterns are ignored because the structured encoder controls the output. Use pattern settings mainly for local plain-text output:

```yaml
# File: src/main/resources/application.yml
logging:
  pattern:
    console: "%d{HH:mm:ss.SSS} %highlight(%-5level) [%thread] %cyan(%logger{36}) - %msg%n" # Default: Spring Boot default console pattern
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n"             # Default: Spring Boot default file pattern
```

Colour converters such as `%highlight` and `%cyan` are Logback-specific and only affect ANSI-capable terminals.

### Configure the shutdown hook
Spring Boot registers a JVM shutdown hook to flush and close Logback on exit. Disable it only when the servlet container manages shutdown instead, such as a WAR (Web Application Archive) deployment:

```yaml
# File: src/main/resources/application.yml
logging:
  register-shutdown-hook: false
```

## 5. Method observability and tracing
Spring Boot automatically creates spans for inbound HTTP requests and outbound HTTP/database calls. However, it does not automatically trace internal business methods.

This section covers how to explicitly instrument internal methods to produce custom spans, metrics, and correlated logs using Micrometer. If the auto-configured request-level spans are sufficient for your needs, you can skip this section.

### Apply common tags to all observations
Common tags attach as low cardinality key-value pairs to every observation, span, and timer metric. Use them for infrastructure context (region, deployment stack) that should appear on all instrumentation without being set individually.

```yaml
# File: src/main/resources/application.yml
management:
  observations:
    key-values:
      region: ap-southeast-1
      stack: production
```

To suppress framework-generated observations for specific packages (for example, to reduce noise from health check probes), disable them by name prefix:

```yaml
# File: src/main/resources/application.yml
management:
  observations:
    enable:
      spring.security: false
```

### @Observed for simple span and metric generation
`@Observed` is the recommended approach for instrumenting a method with a span and a timer metric. Applied to a class or method, it creates an observation that automatically produces both, with `traceId` and `spanId` in scope for all log entries inside.

Enable annotation support:

```yaml
# File: src/main/resources/application.yml
management:
  observations:
    annotations:
      enabled: true # Default: false
```

Add the AspectJ weaver dependency (required for the annotation to take effect):

```xml
# File: pom.xml
<dependency>
    <groupId>org.aspectj</groupId>
    <artifactId>aspectjweaver</artifactId>
</dependency>
```

Annotate the class to observe:

```java
# File: src/main/java/com/example/OrderService.java
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Observed(name = "order.service", contextualName = "process-order")
@Service
public class OrderService {

    public void processOrder(String orderId) {
        // Micrometer creates a span and a timer metric automatically for each call
        log.info("Processing order.");
    }
}
```

**When to use `@Observed` vs the Observation API:** Use `@Observed` for simple, class-wide instrumentation with minimal configuration. It applies the same observation name and tags to all method calls. Use the Observation API (`observe()`) when you need to attach dynamic, call-specific key-value pairs or instrument only specific code blocks within a method.

### Create custom observations and handlers with the Observation API
For finer-grained control, such as attaching specific key-value pairs to a span or adding context that varies per call, create an observation directly using `ObservationRegistry`.

**When to use `observe()` vs MDC:** While MDC is sufficient if you only need to add contextual fields to your log entries, you should use the `observe()` method when you also need to track the duration of a specific block of business logic, generate a distributed trace span, or record timer metrics. Every observation automatically produces both a timer metric and a trace span for the enclosed code block.

Key-value pairs determine where each value appears:
- **Low cardinality**: small, bounded set of values (for example, outcome or operation type). Added to both metric tags and span attributes.
- **High cardinality**: unbounded values (for example, order IDs, user IDs). Added to span attributes only, to prevent metric cardinality explosion.

```java
# File: src/main/java/com/example/OrderService.java
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OrderService {
    private final ObservationRegistry observationRegistry;

    public void processOrder(String orderId) {
        Observation.createNotStarted("order.process", observationRegistry)
            .lowCardinalityKeyValue("event.outcome", "success")  // → metric tag + span attribute
            .highCardinalityKeyValue("order.id", orderId)        // → span attribute only
            .observe(() -> {
                log.info("Processing order.");
                // ... business logic
            });
    }
}
```

The `observe()` callback starts the observation before the lambda runs and stops it when the lambda returns, so duration is measured automatically.

#### Processing observations with ObservationHandlers

When you call `observe()` in the example above, Micrometer does not generate metrics or spans by itself. Instead, it publishes lifecycle events to registered `ObservationHandler` beans.

Spring Boot Actuator automatically wires up the default handlers for you:
- `DefaultMeterObservationHandler`: Creates a timer and a long-task timer (for in-progress observations) metric
- `DefaultTracingObservationHandler`: Creates the trace span and injects `traceId` and `spanId` into MDC

You can extend this behavior by registering custom handlers to hook into the lifecycle of every observation globally. For example, to automatically log a structured entry every time *any* observation starts, stops, or fails, implement `ObservationHandler`:

```java
# File: src/main/java/com/example/CustomLoggingObservationHandler.java
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class CustomLoggingObservationHandler implements ObservationHandler<Observation.Context> {

    @Override
    public boolean supportsContext(Observation.Context context) {
        return true; // Apply to all observations, or restrict by checking context name/type
    }

    @Override
    public void onStart(Observation.Context context) {
        log.atInfo()
            .addKeyValue("event.action", context.getName())
            .addKeyValue("event.type", List.of("start"))
            .log("Observation started.");
    }

    @Override
    public void onStop(Observation.Context context) {
        log.atInfo()
            .addKeyValue("event.action", context.getName())
            .addKeyValue("event.type", List.of("end"))
            .log("Observation stopped.");
    }

    @Override
    public void onError(Observation.Context context) {
        log.atError()
            .addKeyValue("event.action", context.getName())
            .addKeyValue("event.type", List.of("end"))
            .addKeyValue("error_code", 500)
            .addKeyValue("error_category", "application")
            .addKeyValue("error_follow_up_action", true)
            .setCause(context.getError())
            .log("Observation failed.");
    }
}
```

With this handler registered as a Spring `@Component`, every observation across the application, including framework-generated ones for HTTP requests and database calls, will automatically emit these log entries without needing explicit `log.info()` statements inside each observed block.

## 6. Propagate context in async execution and outbound calls
This section covers context propagation for async execution and outbound HTTP calls. If your application makes HTTP calls to downstream services, read the outbound propagation guidance below. If your application does not use `@Async` or similar async task execution, you can skip the async-specific configuration.

MDC and trace context are stored in `ThreadLocal` variables. This means they are available only on the thread that set them. When `@Async` moves work to a worker thread from a pool, that worker thread starts with an empty context: no `traceId`, no `spanId`, no MDC fields. Any log entries on that thread appear as disconnected events with no trace correlation.

`TaskDecorator` addresses this at the thread boundary: before the task runs on the worker thread, it captures the context from the submitting thread and restores it. After the task completes, it clears the context so it does not leak into the next task that runs on that thread.

For sync methods, `@Observed` alone is sufficient. For `@Async` methods, both are needed.

| | @Observed | TaskDecorator |
|---|---|---|
| **Answers** | Which methods to trace | How logging context survives a thread hand-off |
| **Operates at** | Method boundary (calling thread) | Executor boundary (worker thread) |
| **Generates spans** | Yes | No, carries context only |

`ContextPropagatingTaskDecorator` is the recommended solution for context propagation across Spring-managed executors. It automatically propagates all registered context accessors (such as MDC, trace context, and Spring Security context) to worker threads executing `@Async` tasks, scheduled jobs, and message listeners. It avoids the need to write custom decorators for standard contexts. It does not apply to a raw `ExecutorService` or a `CompletableFuture` using its default executor. Those require manual propagation.

The following table shows what is covered by the decorator in a standard Spring Boot application and what requires additional work.

| Context | Auto-propagated | Notes |
|---|---|---|
| MDC | Yes | Spring Boot registers the accessor automatically |
| Trace context | Yes | Spring Boot registers the accessor automatically |
| Spring Security context | Yes | Spring Security 6+ registers its accessor automatically |
| Custom `ThreadLocal` | No | Requires a custom `ThreadLocalAccessor` bean |
| `RequestContextHolder` | No | Rarely needed in async methods, as async work typically runs after the HTTP request has returned |

Without propagation, an async log loses its trace and MDC fields:
```json
{"message":"Fetching order"}
```

With a task decorator, those fields are preserved:
```json
{"message":"Fetching order","trace.id":"abc123","span.id":"def456","correlation.id":"42"}
```

Because the decorator adds a small amount of overhead, avoid applying it to thread pools that execute millions of extremely brief tasks per second. If only specific async flows require logging context, configure a dedicated executor equipped with the decorator for those tasks.

If your application does not define a custom executor, Spring Boot auto-configures a default `ThreadPoolTaskExecutor`. The example below shows how to explicitly configure it with the decorator. If you already have a custom executor bean, inject the decorator into your existing configuration instead of creating a second executor.

```java
# File: src/main/java/com/example/ContextPropagationConfiguration.java
@EnableAsync
@Configuration(proxyBeanMethods = false)
class ContextPropagationConfiguration {
    @Bean
    ContextPropagatingTaskDecorator contextPropagatingTaskDecorator() {
        return new ContextPropagatingTaskDecorator();
    }

    @Bean("applicationTaskExecutor")
    AsyncTaskExecutor applicationTaskExecutor(ContextPropagatingTaskDecorator decorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("app-async-");
        executor.setTaskDecorator(decorator);
        executor.initialize();
        return executor;
    }
}
```

**Important:** If your application already defines a custom `ThreadPoolTaskExecutor` bean, do not copy the `applicationTaskExecutor` bean definition above. Instead, inject the `ContextPropagatingTaskDecorator` into your existing executor configuration and call `executor.setTaskDecorator(decorator);`.

### Example: Request context across async execution
The controller sets an MDC value at the start of the request and clears it in a finally block so the context does not leak to unrelated requests.
```java
# File: src/main/java/com/example/OrderController.java
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

@Slf4j
@RestController
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable String id) {
        MDC.put("correlation.id", id);
        try {
            service.fetchOrderAsync(id);
            log.info("Order requested");
            return ResponseEntity.accepted().build();
        } finally {
            MDC.remove("correlation.id");
        }
    }
}
```

The async method logs with the same MDC values because the decorator propagated the context when the work was queued.
```java
# File: src/main/java/com/example/OrderService.java
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OrderService {

    @Async
    public void fetchOrderAsync(String id) {
        log.info("Fetching order");
    }
}
```

### Example: Outbound calls with trace propagation
Trace propagation to downstream services (via HTTP headers like `traceparent`) happens automatically, provided you construct your HTTP clients correctly. 

To ensure the trace context is passed along, **you must use Spring Boot's auto-configured HTTP client builders** to create your clients. Spring Boot automatically injects Micrometer interceptors into these builders, which handle attaching the necessary headers to outgoing requests invisibly.

**Do not instantiate clients directly** (e.g., `new RestTemplate()` or `RestClient.create()`). Manually created clients lack these interceptors, causing the distributed trace to break silently at the outbound call. The examples below demonstrate the correct, builder-based instantiation.

#### Example: RestClient
```java
# File: src/main/java/com/example/OrderClient.java
@Component
class OrderClient {
    private final RestClient restClient;

    OrderClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    OrderDetails getOrder(String id) {
        return restClient.get()
                .uri("https://orders/api/orders/{id}", id)
                .retrieve()
                .body(OrderDetails.class);
    }
}
```

#### Example: WebClient
```java
# File: src/main/java/com/example/OrderClient.java
@Component
class OrderClient {
    private final WebClient webClient;

    OrderClient(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    Mono<OrderDetails> getOrder(String id) {
        return webClient.get()
                .uri("https://orders/api/orders/{id}", id)
                .retrieve()
                .bodyToMono(OrderDetails.class);
    }
}
```

#### Example: RestTemplate
```java
# File: src/main/java/com/example/OrderClient.java
@Component
class OrderClient {
    private final RestTemplate restTemplate;

    OrderClient(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    OrderDetails getOrder(String id) {
        return restTemplate.getForObject("https://orders/api/orders/{id}", OrderDetails.class, id);
    }
}
```

### Propagate business context with baggage
Baggage is a set of key-value pairs attached to a trace that propagates automatically to downstream services and surfaces in log entries via MDC. Use it when a business identifier (such as a tenant ID or correlation ID) needs to appear in logs and traces across service boundaries without being passed explicitly through every method call.

#### Baggage vs. MDC
While standard `MDC.put()` is perfect for adding contextual data to the *local* application's logs (requiring no YAML configuration), it does not cross network boundaries. If your application makes an HTTP call to another microservice, the local MDC data is left behind.

**Baggage** solves this by attaching key-value pairs to the distributed trace context. Because Baggage lives in the tracing system (Micrometer) rather than the logging system (SLF4J), you must explicitly configure Spring Boot to bridge this data into your logs and network requests using `application.yml` (explained below). If you only care about local logging, use `MDC.put()` directly to avoid this configuration overhead.

#### Implementing Baggage
Create baggage using the `Tracer` API and wrap it in a try-with-resources block so the scope is closed when the work completes:

```java
# File: src/main/java/com/example/OrderController.java
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

@Slf4j
@RestController
public class OrderController {
    private final Tracer tracer;

    public OrderController(Tracer tracer) {
        this.tracer = tracer;
    }

    @PostMapping("/orders")
    public ResponseEntity<Void> createOrder(@RequestBody OrderRequest request) {
        try (BaggageInScope scope = tracer.createBaggageInScope("tenant.id", request.getTenantId())) {
            log.info("Order creation requested.");
            // tenant.id is in scope for all log entries and downstream calls within this block
            return ResponseEntity.accepted().build();
        }
    }
}
```

To propagate the value over the network and surface it in MDC automatically, configure both `remote-fields` and `correlation.fields` in your YAML:

- **`remote-fields`**: Tells Micrometer to inject the Baggage into outgoing HTTP headers (e.g., `baggage: tenant.id=...`) so downstream services receive it.
- **`correlation.fields`**: Tells Micrometer to automatically copy the Baggage into the local MDC so it appears in the JSON log output of the *current* application.

```yaml
# File: src/main/resources/application.yml
management:
  tracing:
    baggage:
      remote-fields:
        - tenant.id
      correlation:
        fields:
          - tenant.id
```

If you omit `correlation.fields`, the Baggage will travel across the network but will not appear in your local logs.

<note>

> **Note:** W3C trace context propagation (`traceparent`/`tracestate` headers) propagates baggage automatically. B3 propagation (used by Zipkin by default) does not propagate baggage to downstream services. Configure `management.tracing.propagation.type: w3c` if baggage must cross service boundaries with a Zipkin setup.

</note>

## 7. Track long-running business processes with correlation.id

While baggage (Section 6) propagates business context automatically within a single distributed trace, `correlation.id` is designed for processes that span multiple separate requests over time. Use baggage when a value needs to cross service boundaries within one request. Use `correlation.id` when the process involves multiple unrelated requests (for example, order placement followed by a fulfillment job hours later).

`trace.id` covers a single technical request. A business process often spans multiple separate requests, background jobs, or scheduled steps that each produce their own `trace.id`. Querying by `trace.id` alone returns only one slice of the full process.

`correlation.id` is a stable business identifier set at the start of the process and carried through every step. It is not generated by the tracing system. The application sets it explicitly and logs it on every related entry. A log platform query for that value returns the complete lifecycle regardless of how many `trace.id` values were involved.

```java
// File: src/main/java/com/example/OrderController.java
@PostMapping("/orders")
public ResponseEntity<Void> placeOrder(@RequestBody OrderRequest request) {
    String correlationId = request.getOrderReference(); // stable business key
    MDC.put("correlation.id", correlationId);
    try {
        orderService.place(request);
        log.atInfo()
            .addKeyValue("event.action", "order-placement")
            .addKeyValue("event.outcome", "success")
            .log("Order placed.");
        return ResponseEntity.accepted().build();
    } finally {
        MDC.remove("correlation.id");
    }
}
```

The `correlation.id` value must be persisted with the order data (e.g., in the database, message queue, or job parameters) so the fulfillment job can retrieve and set it when processing begins.

When the fulfillment job runs hours later in a separate request context, it sets the same `correlation.id`:

```java
// File: src/main/java/com/example/FulfillmentJob.java
MDC.put("correlation.id", order.getOrderReference());
try {
    log.atInfo()
        .addKeyValue("event.action", "order-fulfillment")
        .addKeyValue("event.outcome", "success")
        .log("Order fulfilled.");
} finally {
    MDC.remove("correlation.id");
}
```

A query for `correlation.id: "ORD-20240101-0042"` in Splunk or OpenSearch returns both the placement and fulfillment log entries, with different `trace.id` values but a shared business context.

## 8. Observe data pipelines and batch jobs

Batch and scheduled jobs present a unique observability challenge: logging every processed item floods the log platform, but logging only job start and end provides no visibility into data throughput or partial failures.

The recommended pattern is to set job identity fields in MDC for the duration of the job, and emit a single structured summary at the end of each step with counts for total, success, and failure records.

> **Note:** This section provides a general pattern for data pipeline and batch job logging. For Spring Batch applications requiring comprehensive job and step lifecycle tracking with all batch.* schema fields, see [Logging Batch and Scheduled Jobs](Logging_Batch_And_Scheduled_Jobs.md).

```java
// File: src/main/java/com/example/PaymentBatchJob.java
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;

MDC.put("batch.job.id", jobId);
MDC.put("batch.job.name", "payment-reconciliation");
try {
    int total = 0, success = 0, failure = 0;
    for (PaymentRecord record : records) {
        total++;
        try {
            process(record);
            success++;
        } catch (Exception e) {
            failure++;
            log.atWarn()
                .addKeyValue("event.outcome", "failure")
            .addKeyValue("error_code", 500)
            .addKeyValue("error_category", "application")
            .addKeyValue("error_follow_up_action", true)
                .setCause(e)
                .log("Record processing failed.");
        }
    }
    log.atInfo()
        .addKeyValue("event.action", "payment-reconciliation")
        .addKeyValue("event.outcome", failure == 0 ? "success" : "partial-success")
        .addKeyValue("record", List.of(Map.of(
            "name", "payment-record",
            "total", total,
            "success", success,
            "failure", failure
        )))
        .log("Step completed.");
} finally {
    MDC.remove("batch.job.id");
    MDC.remove("batch.job.name");
}
```

> **Note:** When logging custom error fields, you must use underscore keys (e.g., `error_code` instead of `error.code`). Spring Boot's ECS formatter pre-seals the `error` object, so using dotted keys causes a JSON writing error. A custom encoder safely remaps the underscore keys back to their proper nested schema fields. See [Custom Structured Log Encoder](Custom_Structured_Log_Encoder.md).

This produces a single queryable entry per step run:

```json
{
  "message": "Step completed.",
  "batch.job.name": "payment-reconciliation",
  "event.outcome": "partial-success",
  "record": [{"name":"payment-record","total":1500,"success":1487,"failure":13}]
}
```

A log platform query for `batch.job.name: "payment-reconciliation" AND record.failure > 0` returns all runs with failures. Charting `record[0].total` over time shows processing volume trends. Individual item failures are logged inline at `WARN` so they can be investigated without emitting a log entry per successful record.

## 9. Inspect recent HTTP activity with the HTTP exchanges endpoint
This section is only relevant if `spring-boot-starter-actuator` is on the classpath. If the application does not use Spring Boot Actuator, skip this section.

Spring Boot Actuator's `/actuator/httpexchanges` endpoint records the last 100 HTTP exchanges in memory. It is useful for quickly inspecting recent request method, URI, response status, and duration during local debugging without querying a log platform.

It has three important limitations:

- Data is stored in memory only and is lost on restart. It is not shared across instances.
- It retains only the last 100 exchanges. Older entries are silently discarded.
- It is not a substitute for structured logging or a centralised log platform for production monitoring.

Restrict access to this endpoint in production using Spring Security. See [Spring Boot Reference: HTTP Exchanges](https://docs.spring.io/spring-boot/reference/actuator/http-exchanges.html) for setup.

## 10. Use Spring Boot structured encoders for custom Logback

While Spring Boot's `application.yml` properties cover most standard logging requirements, some applications need advanced Logback capabilities, such as environment-specific appender routing, custom field masking, or complex filtering. These advanced scenarios require a custom Logback configuration file, typically `logback-spring.xml`.

Providing a custom `logback-spring.xml` disables Spring Boot's automatic application of logging defaults. However, you can explicitly include Spring Boot's default configurations using `<include>` tags (demonstrated below). Most `logging.*` properties in `application.yml` will no longer be automatically applied—you must read them explicitly via `<springProperty>` or use the automatically exposed system variables shown in the table below. This section explains how to implement a custom Logback setup while maintaining two critical Spring Boot features: **structured JSON formatting** (ensuring logs remain in ECS format) and **property resolution** (the ability to read values like `spring.application.name` or `logging.file.path` from your YAML file directly into your Logback XML).

### Supported configuration file names
Spring Boot automatically loads a custom Logback configuration when it finds one of the following files on the classpath:

- `logback-spring.xml` *(recommended)*
- `logback.xml`
- `logback-spring.groovy`
- `logback.groovy`

Always prefer the `-spring` variants (e.g., `logback-spring.xml`). These allow Spring Boot to fully control Logback's initialization, which is required to use powerful extensions like `<springProfile>` and `<springProperty>`. Standard files like `logback.xml` are loaded by Logback too early in the startup lifecycle for these Spring-specific features to work.

### Maintain structured JSON formatting
Providing a custom `logback-spring.xml` completely disables Spring Boot's default logging auto-configuration. If you simply define a standard `RollingFileAppender` with a pattern, your logs will revert to plain text. 

To keep your logs in structured JSON format (like ECS), you must explicitly configure your appenders to use Spring Boot's `StructuredLogEncoder`. This encoder connects directly to the `logging.structured.format.*` properties in your `application.yml`.

```xml
# File: src/main/resources/logback-spring.xml
<configuration>
    <appender name="STRUCTURED_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH:-/var/log/application}/application.ndjson</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH:-/var/log/application}/application-%d{yyyy-MM-dd}.ndjson</fileNamePattern>
            <maxHistory>${LOG_FILE_MAX_HISTORY:-7}</maxHistory>
        </rollingPolicy>
        <!-- Use the Spring Boot structured encoder to maintain ECS formatting -->
        <encoder class="org.springframework.boot.logging.logback.StructuredLogEncoder">
            <format>${FILE_LOG_STRUCTURED_FORMAT:-ecs}</format>
            <charset>${FILE_LOG_CHARSET:-UTF-8}</charset>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="STRUCTURED_FILE"/>
    </root>
</configuration>
```

Alternatively, if you do not need a heavily customized appender, you can simply include Spring Boot's built-in structured appender definitions directly in your XML:

```xml
# File: src/main/resources/logback-spring.xml
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <include resource="org/springframework/boot/logging/logback/structured-file-appender.xml"/>
    <root level="INFO">
        <appender-ref ref="STRUCTURED_FILE"/>
    </root>
</configuration>
```

### Accessing Spring Boot properties in Logback
To prevent hardcoding paths or application names in your Logback XML, you should reuse the properties already defined in your `application.yml`.

**1. Automatic System Properties**
During initialization, Spring Boot automatically exposes several core logging properties as JVM system variables. You can reference these directly in `logback-spring.xml` using the standard `${VARIABLE:-default}` syntax:

| YAML Property | XML Variable | Default |
|---|---|---|
| `logging.file.name` | `${LOG_FILE}` | (none) |
| `logging.file.path` | `${LOG_PATH}` | (none) |
| `logging.pattern.console` | `${CONSOLE_LOG_PATTERN}` | Spring Boot default pattern |
| `logging.pattern.file` | `${FILE_LOG_PATTERN}` | Spring Boot default pattern |
| `logging.pattern.dateformat` | `${LOG_DATEFORMAT_PATTERN}` | `yyyy-MM-dd'T'HH:mm:ss.SSSXXX` |
| `logging.structured.format.console` | `${CONSOLE_LOG_STRUCTURED_FORMAT}` | (none) |
| `logging.structured.format.file` | `${FILE_LOG_STRUCTURED_FORMAT}` | (none) |

**2. Using `<springProperty>`**
For YAML properties that are not automatically exposed (such as `spring.application.name`), use the `<springProperty>` extension. This pulls the value from the Spring Environment and binds it to a Logback variable.

```xml
# File: src/main/resources/logback-spring.xml
<configuration>
    <springProperty name="LOG_PATH" source="logging.file.path" defaultValue="/var/log/application"/>
    <springProperty name="SERVICE_NAME" source="spring.application.name" defaultValue="unknown-service"/>

    <appender name="STRUCTURED_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/${SERVICE_NAME}.ndjson</file>
        <!-- ... rolling policy and encoder ... -->
    </appender>
</configuration>
```

### Environment-specific routing with `<springProfile>`
The `<springProfile>` extension allows you to activate specific Logback configuration blocks based on the active Spring profile. This lets you maintain a single `logback-spring.xml` file that adapts to different environments (e.g., logging to the console in local development, but writing structured JSON to a file in production).

```xml
# File: src/main/resources/logback-spring.xml
<configuration>
    <!-- Human-readable console output in all non-production environments -->
    <springProfile name="!production">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>
        <root level="DEBUG">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>

    <!-- Structured file output in production -->
    <springProfile name="production">
        <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
        <include resource="org/springframework/boot/logging/logback/structured-file-appender.xml"/>
        <root level="INFO">
            <appender-ref ref="STRUCTURED_FILE"/>
        </root>
    </springProfile>
</configuration>
```

`<springProfile>` supports logical expressions: `name="production"` matches the production profile, `name="!production"` matches anything else, and `name="staging | production"` matches either.

## 11. Choose log fields that enable platform queries

Not all structured fields contribute equally to observability. The fields from the log schema that most directly enable platform queries are those with a consistent name, a small bounded set of values, and presence on every relevant log entry.

The reason bounded values matter is that log platforms aggregate by field value. A field like `event.outcome` with values `success` and `failure` can be counted, graphed, and alerted on. A field that contains free-text cannot be aggregated reliably because the values differ across services and over time.

The reason presence matters is that a field missing from some log entries produces incomplete aggregations. If `event.duration_ms` appears only when an operation is slow, duration queries will miss the majority of data points. Fields that belong to a whole operation should be set in MDC so they appear on every related entry automatically.

### Fields that enable rate and count queries
| Field | Values | Observable use |
|---|---|---|
| `event.outcome` | `success`, `failure` | Error rate per operation, per service |
| `log.level` | `ERROR`, `WARN`, `INFO` | Error rate from log level alone |
| `error.category` | `application`, `network`, `database` | Route failures to the right owner |
| `error.code` | HTTP status or internal code | Alert on specific error codes |
| `error.follow_up_action` | `true`, `false` | Flag requiring manual intervention, enables alerting rules in log platforms |

### Fields that enable latency queries
| Field | Observable use |
|---|---|
| `event.duration_ms` | Latency percentiles, slow operation detection, SLA compliance |
| `event.start`, `event.end` | Operation time range for timeline queries |

### Fields that enable deployment and service correlation
| Field | Observable use |
|---|---|
| `service.name` + `service.version` | Correlate error rate changes with deployments |
| `service.environment` | Separate production and staging signals |
| `batch.job.name` + `event.outcome` | Job success rate dashboard |

### Fields that enable cross-service correlation
| Field | Observable use |
|---|---|
| `trace.id` | All log entries for a single request across all services |
| `correlation.id` | Business-level operation correlation independent of tracing |
| `tenant.id` (via baggage) | All log entries for a single tenant across all services |
| `user.id` | All actions performed by a specific user correlated across services |

For implementation details on setting `user.id` in MDC, see [Logging Authentication and Authorization Events](Logging_AuthN_And_AuthZ_Events.md).

### Outcome in structured fields, not in the message

Placing outcome information only in the log message makes the log unqueryable by outcome:

```java
// AVOID: outcome in free-text message — cannot filter by event.outcome
log.info("Payment was successful after processing.");
log.error("Payment processing encountered an error: " + e.getMessage());
```

```java
// PREFER: outcome as a structured field — queryable and aggregatable
log.atInfo()
    .addKeyValue("event.outcome", "success")
    .addKeyValue("event.duration_ms", duration)
    .log("Payment processed.");

log.atError()
    .setCause(e)
    .addKeyValue("event.outcome", "failure")
    .addKeyValue("error_code", 503)
    .addKeyValue("error_category", "network")
    .log("Payment failed.");
```

Both log entries above produce a message readable by a human. Only the second pair supports error rate queries, latency analysis, and alert routing by a platform.

## 12. Verification
To comprehensively verify the observability setup, trigger a single flow that touches all configured boundaries: an incoming HTTP request that invokes an `@Observed` internal method (which generates custom metrics), creates local MDC data, creates Baggage, submits an `@Async` task, and finally makes an outbound HTTP call to a downstream service using a Spring-configured builder.

Observe the logs and traces, and verify that:

- **Structured JSON Formatting:** The log output is valid, structured JSON (e.g., ECS format) and contains `service.name`, `service.version`, and `service.environment` automatically via the formatter. If a custom `logback-spring.xml` is used, verify it successfully pulled properties (like `logging.file.path`) from `application.yml` via `<springProperty>` and wrote the `.ndjson` file. If using custom error fields, verify that `error.code`, `error.category`, and `error.follow_up_action` appear in the JSON output with proper dotted names (not underscore names), confirming the custom encoder correctly remapped them.
- **Timestamp Configuration:** The `@timestamp` field in the JSON log matches the explicitly configured process timezone (e.g., `+08:00` for `Asia/Singapore`).
- **Trace Correlation & Span Generation:** 
  - The `trace.id` and `span.id` fields are present in *every* log entry for the flow.
  - The `trace.id` remains identical across the entire flow.
  - A new `span.id` is generated for the incoming request, the `@Observed` internal method, and the outbound HTTP call.
- **Context Propagation (Async & Network):** 
  - The `trace.id` and MDC fields survive the thread hand-off and appear in the logs generated by the `@Async` worker thread.
  - Any Baggage fields configured in `management.tracing.baggage.correlation.fields` appear automatically in the local JSON logs.
  - The outbound HTTP client successfully passes the W3C `traceparent` and Baggage headers to the downstream service.
- **Custom Observation Data:** The custom `lowCardinalityKeyValue` (e.g., `event.outcome`) and `highCardinalityKeyValue` (e.g., `order.id`) defined via the `ObservationRegistry` appear in the exported trace span.
- **Trace Export & Sampling:** If a trace platform (e.g., Dynatrace, Zipkin) is configured and the sampling rate permits, the distributed trace is exported successfully. The span hierarchy (web request → internal method → outbound call) should be clearly visible in the UI.

```text
# Output: example
{"@timestamp":"2024-01-01T10:15:00.067+08:00","log.level":"INFO","trace.id":"803B448A0489F84084905D3093480352","span.id":"3425F23BB2432450","message":"Request completed","tenant.id":"ABC-123"}
```

## 13. Conclusion
This guide explained how to establish a practical Spring Boot baseline for structured JSON logging, trace correlation, async context propagation, and custom Logback configurations. It covered foundational configurations (log levels, structured formatting, file rolling), observability building blocks that connect logs, traces, and metrics (the Observation API, `@Observed`, common tags), patterns for maintaining context across async threads and network boundaries, and field selection guidance for effective platform querying.

### Key Takeaways

**Tracing Setup**
- **Use W3C Trace Context**: Use the OpenTelemetry starter (W3C by default) for new projects. Configure `management.tracing.propagation.type: w3c` if using Brave to ensure baggage propagates across service boundaries.
- **Set sampling wisely**: Use `0.05-0.2` for production to avoid overwhelming the tracing platform, and `1.0` for local development.

**Core Logging**
- **Set service identity**: Define `spring.application.name` once to consistently identify the service across both logs and traces.
- **Enable structured logging**: Use `logging.structured.format: ecs` for machine-readable JSON output.
- **Standardise on Singapore Time (UTC+8)**: Set `TZ=Asia/Singapore` in the deployment environment or `-Duser.timezone=Asia/Singapore` as a JVM argument.
- **Configure log levels and groups**: Use `logging.level.*` to tune verbosity per package, and `logging.group.*` to manage related loggers together.
- **Enable file output with rolling policy**: Set `logging.file.name` or `logging.file.path`. Spring Boot applies a 10 MB rolling policy automatically.

**Observability and Instrumentation**
- **Apply common observation tags**: Use `management.observations.key-values.*` to attach infrastructure context (e.g., region, stack) to all spans and metrics.
- **Use @Observed for standard tracing**: Annotate a class or method to create an observation automatically. Requires `management.observations.annotations.enabled: true`.
- **Use the Observation API for granular tracking**: Use `Observation.createNotStarted()` along with `.observe()` to manually track logic duration, emitting metrics and custom trace spans.

**Context Propagation**
- **Maintain context across async threads**: Use `ContextPropagatingTaskDecorator` to ensure MDC and trace context survive thread hand-offs in Spring-managed executors.
- **Propagate to downstream services**: Always use Spring Boot's auto-configured HTTP client builders (RestClient, WebClient, RestTemplate) so trace headers are injected automatically.
- **Propagate business identifiers with Baggage**: Use `Tracer.createBaggageInScope()` and configure `correlation.fields` and `remote-fields` in YAML to distribute IDs across logs and network calls.
- **Track business processes with correlation.id**: Set a stable business key in MDC at each step of a multi-request process so a single log query assembles the full lifecycle regardless of how many `trace.id` values were involved.

**Batch Jobs**
- **Emit a step summary, not per-record entries**: Set job identity in MDC, log record summaries as a `record` array with `name`, `total`, `success`, and `failure` counts once at step completion, and log individual failures at `WARN` inline. This keeps log volume low while preserving query capability.

**Field Selection**
- **Use bounded field values**: `event.outcome: failure` can be counted and graphed; free-text messages cannot. Use schema-defined values consistently.
- **Put operation-scoped fields in MDC**: A field absent from some entries produces incomplete aggregations. Fields that belong to a whole operation should appear on every related entry.

**Custom Logback**
- **Use -spring variants**: Name your custom file `logback-spring.xml` so Spring Boot features remain available.
- **Maintain JSON formatting**: Explicitly use `StructuredLogEncoder` in custom appenders so logs don't revert to plain text.
- **Bridge properties and environments**: Use `<springProperty>` to map YAML values into Logback, and `<springProfile>` to activate different configurations per environment.

With this baseline, logs are consistent and searchable, tracing connects activity across threads and services for end-to-end visibility, and business processes are traceable across multiple requests.

## 14. References

Related guides:
- [Log Schema](../Log_Schema.md)
- [Enriching Logs with MDC](Enriching_Logs_With_MDC.md)
- [Custom Structured Log Encoder](Custom_Structured_Log_Encoder.md)

Spring Boot:
- [Spring Boot Reference: Logging](https://docs.spring.io/spring-boot/reference/features/logging.html)
- [Spring Boot How-to: Logging](https://docs.spring.io/spring-boot/how-to/logging.html)
- [Spring Boot Reference: Tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)
- [Spring Boot Reference: Observability](https://docs.spring.io/spring-boot/reference/actuator/observability.html)
- [Micrometer Observation Reference](https://micrometer.io/docs/observation)
- [Spring Boot Reference: RestClient](https://docs.spring.io/spring-boot/reference/io/rest-client.html)
- [Spring Framework Reference: WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)
- [Spring Framework Reference: Aspect Oriented Programming with Spring](https://docs.spring.io/spring-framework/reference/core/aop.html)
- [Spring Framework Reference: AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/using-aspectj.html)
- [Spring Boot Reference: Task Execution and Scheduling](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html)
- [Spring Framework Javadoc: EnableAsync](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html)
- [Spring Framework Javadoc: Async](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Async.html)
- [Spring Framework Javadoc: ContextPropagatingTaskDecorator](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/task/support/ContextPropagatingTaskDecorator.html)
- [Spring Boot Reference: HTTP Exchanges](https://docs.spring.io/spring-boot/reference/actuator/http-exchanges.html)
- [Spring Blog: Structured Logging in Spring Boot 3.4](https://spring.io/blog/2024/11/05/structured-logging-in-spring-boot-3-4/)
- [Spring Boot Reference: Application Events and Listeners](https://docs.spring.io/spring-boot/reference/features/spring-application.html#features.spring-application.application-events-and-listeners)
- [Spring Boot 4.0 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes)
- [Spring Boot Reference: Application Properties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html)
- [Dynatrace Docs: OpenTelemetry API](https://docs.dynatrace.com/docs/extend-dynatrace/opentelemetry/opentelemetry-api)
- [Dynatrace Docs: OTLP API](https://docs.dynatrace.com/docs/extend-dynatrace/opentelemetry/otlp)

Elastic:
- [Elastic Common Schema Reference](https://www.elastic.co/guide/en/ecs/current/index.html)

OpenTelemetry:
- [OpenTelemetry Semantic Conventions](https://opentelemetry.io/docs/concepts/semantic-conventions/)
