# Enriching Logs with Mapped Diagnostic Context (MDC)

## 1. Introduction
Mapped Diagnostic Context (MDC) is a per-thread key-value store that logging frameworks use to enrich log events. It is useful when the same context, such as a request identifier, interface identifier, or batch metadata, should appear on multiple log lines for the same request, job, or process.

This guide explains how to set MDC at request boundaries, forward the correlation ID to downstream services, choose the right cleanup strategy, store `user.id` UUID when Spring Security is in use, create helper methods for repeated field groups, propagate MDC across async thread boundaries, and add one-off fields to a single log entry without using MDC.

## 2. Prerequisites
- Spring Boot 4.0+
- Structured logging enabled so that MDC fields appear in JSON log output
- Schema compliance: Use field values from [Log_Schema.md](../Log_Schema.md) for `event.category`, `event.type`, `event.action`, and `error_category` to ensure consistency across services

## 3. Setting and managing MDC context
This section covers how to set MDC at request boundaries, forward the correlation ID to downstream services, manage cleanup safely, and include user context when Spring Security is in use.

If Micrometer Tracing is already in use and `traceId` is sufficient as the correlation identifier, skip sections 3.1 and 3.2. Spring Boot auto-instruments HTTP requests and propagates trace context across service calls via standard headers (B3 or W3C `traceparent`) without custom code. Sections 3.1 and 3.2 apply when external callers (such as a partner system, API gateway, or frontend) supply their own `X-Correlation-ID` that must be accepted and propagated, or when the application does not use Micrometer Tracing.

### 3.1 Setting MDC at request boundaries
This section is only relevant if the application is a servlet-based web application with HTTP endpoints. If the application does not serve HTTP requests, skip this section.

An `OncePerRequestFilter` sets `correlation.id` in MDC once at the entry point so every log entry for that request inherits it automatically. It reads the `X-Correlation-ID` header from the incoming request, or generates a new UUID if none is present. The filter also logs request start and completion with HTTP method, path, status, duration, and outcome as required by the app standard.

```java
# File: src/main/java/com/example/MdcRequestFilter.java
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class MdcRequestFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = Optional.ofNullable(request.getHeader("X-Correlation-ID"))
            .orElse(UUID.randomUUID().toString());
        long startTime = System.currentTimeMillis();
        // putCloseable removes correlation.id when the block exits, even on exception.
        // If this filter sets multiple MDC fields, use MDC.put(...) with a finally block instead.
        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlation.id", correlationId)) {
            log.atInfo()
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", List.of("network"))
                .addKeyValue("event.type", List.of("start"))
                .addKeyValue("http.request.method", request.getMethod())
                .addKeyValue("url.path", request.getRequestURI())
                .log("Request received.");

            chain.doFilter(request, response);

            log.atInfo()
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", List.of("network"))
                .addKeyValue("event.type", List.of("end"))
                .addKeyValue("event.outcome", response.getStatus() < 400 ? "success" : "failure")
                .addKeyValue("http.response.status_code", response.getStatus())
                .addKeyValue("event.duration_ms", System.currentTimeMillis() - startTime)
                .log("Request completed.");
        }
    }
}
```

Spring Boot registers `@Component` filters automatically. By default, they run after Spring Security's filter chain (order Integer.MAX_VALUE vs. Spring Security's order -100). This is acceptable for most use cases, as application logs will have the correlation ID.

<note>

> **Note:** If you need correlation IDs in Spring Security's authentication and authorization logs (e.g., to trace failed login attempts or 403 errors), add `@Order(SecurityProperties.DEFAULT_FILTER_ORDER - 1)` to the filter class to ensure it runs before Spring Security:
> 
> ```java
> import org.springframework.boot.autoconfigure.security.SecurityProperties;
> import org.springframework.core.annotation.Order;
> 
> @Order(SecurityProperties.DEFAULT_FILTER_ORDER - 1)
> @Component
> public class MdcRequestFilter extends OncePerRequestFilter {
> ```

</note>

<warning>

> **Warning:** Avoid logging the client IP address, query parameters, authentication headers, or request and response bodies, as these may contain personal data or sensitive information.

</warning>

Logback provides a built-in `MDCInsertingServletFilter` that automatically populates MDC with common request fields, which may appear to be a simpler alternative to the custom filter above. It is not recommended under this standard because it sets fields that must not be logged, including client IP (`req.remoteHost`, `req.xForwardedFor`) and query strings (`req.queryString`). Its position in the filter chain is also not guaranteed without explicit ordering configuration.

### 3.2 Forwarding correlation ID to downstream services
This section is only relevant if the application makes outbound HTTP calls to other services. If it does not, skip this section.

The `correlation.id` value stored in MDC appears on all log entries produced by this service but does not carry across HTTP calls to downstream services automatically. Without explicit forwarding, downstream logs will have no record of the correlation identifier and cross-service tracing by correlation ID will break. The forwarding mechanism depends on the HTTP client in use.

<note>

> **Note:** Use `RestClient` for Spring MVC applications making synchronous HTTP calls. Use `WebClient` for Spring WebFlux applications or when non-blocking I/O is required.

</note>

**RestClient**

Register a `ClientHttpRequestInterceptor` on the `RestClient` bean to attach the correlation ID as an outgoing header:

```java
# File: src/main/java/com/example/CorrelationIdInterceptor.java
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

public class CorrelationIdInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String correlationId = MDC.get("correlation.id");
        if (correlationId != null) {
            request.getHeaders().add("X-Correlation-ID", correlationId);
        }
        return execution.execute(request, body);
    }
}
```

```java
# File: src/main/java/com/example/AppConfig.java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder
            .requestInterceptor(new CorrelationIdInterceptor())
            .build();
    }
}
```

**WebClient**

For `WebClient`, register an `ExchangeFilterFunction` to attach the correlation ID on each outgoing request. This approach is for servlet-based (Spring MVC) applications that use `WebClient` as the HTTP client for non-blocking outbound calls:

```java
# File: src/main/java/com/example/CorrelationIdWebClientFilter.java
import org.slf4j.MDC;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

public class CorrelationIdWebClientFilter implements ExchangeFilterFunction {

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        String correlationId = MDC.get("correlation.id");
        if (correlationId == null) {
            return next.exchange(request);
        }
        ClientRequest modified = ClientRequest.from(request)
            .header("X-Correlation-ID", correlationId)
            .build();
        return next.exchange(modified);
    }
}
```

```java
# File: src/main/java/com/example/AppConfig.java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AppConfig {

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder
            .filter(new CorrelationIdWebClientFilter())
            .build();
    }
}
```

<note>

> **Note:** This filter reads `correlation.id` from MDC and attaches it as an `X-Correlation-ID` header on each outbound request. It works correctly in a Spring MVC application because the HTTP call is made on a servlet thread that carries MDC. In a Spring WebFlux application, the call runs on a reactive scheduler thread that does not carry MDC, so `MDC.get(...)` returns null and the header will not be set. In a WebFlux application, Micrometer Tracing propagates trace headers automatically without custom code. For custom correlation IDs in a WebFlux application, Reactor Context is required instead of MDC, which is outside the scope of this guide.

</note>

Both `ClientHttpRequestInterceptor` and `ExchangeFilterFunction` read from MDC at the time the request is made and use whatever correlation ID is active on the calling thread. For outbound calls made from an async thread, MDC will be empty unless it was copied to that thread first. See [section 5](#5-propagate-mdc-in-async-tasks) for how to carry MDC across thread boundaries before making the call.

### 3.3 Choosing the right cleanup strategy
Always pair every `MDC.put(...)` with a corresponding removal to prevent stale values from leaking into the next request on a pooled thread. The right cleanup method depends on how many keys are set, whether a `catch` block needs to log with those keys, and whether this code owns the full MDC scope for the thread.

**`MDC.putCloseable`: one key, no catch-block logging needed**

Wrap the key in a try-with-resources block. The key is removed automatically when the block exits, even if an exception is thrown.

```java
# File: src/main/java/com/example/OrderController.java
import org.slf4j.MDC;

@GetMapping("/orders/{id}")
public ResponseEntity<Order> getOrder(@PathVariable String id) {
    try (MDC.MDCCloseable ignored = MDC.putCloseable("order.id", id)) {
        log.atInfo().log("Order requested.");
        return ResponseEntity.accepted().build();
    }
    // order.id is removed here whether or not an exception was thrown
}
```

Do not use `putCloseable` when:
- A `catch` block needs to log with that key. Try-with-resources closes the resource before `catch` runs, so the key is already gone by the time the catch block executes.
- The key may already be set on the current thread. `putCloseable` removes the key on close rather than restoring the previous value (see [SLF4J Issue #404](https://github.com/qos-ch/slf4j/issues/404)).

In either case, use `MDC.put(...)` with a `finally` block instead.

**`MDC.remove(...)` in a `finally` block: multiple keys, or catch-block logging needed**

Set all keys before the `try` block, then remove only those keys in `finally`. This keeps other MDC fields intact. `traceId` and `spanId` set by Micrometer Tracing are not affected.

```java
# File: src/main/java/com/example/FileProcessor.java
MDC.put("file.name", fileName);
MDC.put("event.action", "file-process");
try {
    log.atInfo().log("Processing file.");
} catch (Exception e) {
    log.atError()
        .setCause(e)
        .addKeyValue("error_code", 500)
        .addKeyValue("error_category", "application")
        .addKeyValue("error_follow_up_action", true)
        .log("Processing failed.");  // file.name and event.action are still present here
} finally {
    MDC.remove("file.name");
    MDC.remove("event.action");
}
```

**`MDC.clear()`: only when this code owns the entire MDC scope for the thread**

Use `MDC.clear()` only at entry points that control the full thread lifecycle, such as the top of a batch job. It removes everything on the thread, including `traceId` and `spanId` set by Micrometer Tracing. Calling it in shared or nested code will silently strip those fields from all subsequent log entries on that thread.

```java
# File: src/main/java/com/example/BatchRunner.java
import org.slf4j.MDC;

public void runJob() {
    MDC.put("batch.job.id", "42");
    MDC.put("batch.job.name", "dailyReportJob");
    try {
        log.atInfo().log("Starting job.");
    } finally {
        MDC.clear();
    }
}
```

### 3.4 Including user context with Spring Security
This section is only relevant if the application uses Spring Security. If Spring Security is not in use, skip this section.

By default, `MdcRequestFilter` runs outside the Spring Security filter chain and has no access to the authenticated user. A separate filter placed inside the Spring Security chain, after authentication completes, is required to read the authenticated principal and set `user.id` in MDC.

The Spring Security filter chain runs filters in this order (see [Spring Security Reference: Servlet Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html)):

| Position | Filter | Purpose |
|---|---|---|
| Earlier | Authentication filters (e.g. `UsernamePasswordAuthenticationFilter`, JWT filters) | Attempt to authenticate the request and populate `SecurityContextHolder` |
| Later | `AnonymousAuthenticationFilter` | If no authentication has been set, assigns an anonymous token as a fallback |
| After | `MdcUserFilter` (placement point) | `SecurityContextHolder` is always populated here, whether authenticated or anonymous |

Place `MdcUserFilter` after `AnonymousAuthenticationFilter` so `SecurityContextHolder` is always populated when the filter runs. Extract the UUID from the authenticated principal and write it to MDC as `user.id`. UUIDs are non-PII and directly debuggable — no hashing required.

```java
# File: src/main/java/com/example/MdcUserFilter.java
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class MdcUserFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            // AnonymousAuthenticationToken.isAuthenticated() also returns true, so check type before extracting UUID
            if (!(authentication instanceof AnonymousAuthenticationToken)
                    && authentication.getPrincipal() instanceof UserAccount user) {
                MDC.put("user.id", user.getId().toString());
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("user.id");
        }
    }
}
```

The `instanceof` checks are required because `AnonymousAuthenticationToken.isAuthenticated()` returns `true`, so checking `isAuthenticated()` alone is not enough to confirm a real user is present. Without the principal type check, the filter could attempt to extract a UUID from an anonymous token. `user.id` is not set for anonymous requests.

<warning>

> **Warning:** Register the filter in the Spring Security configuration rather than as a `@Component` or via `FilterRegistrationBean`. Registering it outside the security chain means it runs before authentication completes and the user context will not be available.

</warning>

```java
# File: src/main/java/com/example/SecurityConfig.java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.addFilterAfter(new MdcUserFilter(), AnonymousAuthenticationFilter.class);
        // ... other security configuration
        return http.build();
    }
}
```

## 4. Create helper methods for repeated MDC fields
When the same MDC fields are set in multiple services, a single typo such as `"interface.System"` instead of `"interface.system"` breaks cross-service queries, and a missing `MDC.remove(...)` leaks the field into the next request.

A helper method addresses both risks. It centralises the field names in one place and pairs `put` with `remove` so cleanup is enforced for all callers. Each helper should cover one field group that is always set and removed together. That said, MDC is designed for moderate-frequency updates, so avoid calling helpers inside high-frequency operations such as per-item processing loops.

### Example: Integration fields
If the same integration fields are used across multiple services, define a helper for that field group.

```java
# File: src/main/java/com/example/IntegrationLogContext.java
import org.slf4j.MDC;

public final class IntegrationLogContext {

    private IntegrationLogContext() {
    }

    public static void putBaseFields(String interfaceSystem, String interfaceType, String direction) {
        MDC.put("interface.system", interfaceSystem);
        MDC.put("interface.type", interfaceType);
        MDC.put("interface.direction", direction);
    }

    public static void removeBaseFields() {
        MDC.remove("interface.system");
        MDC.remove("interface.type");
        MDC.remove("interface.direction");
    }
}
```

Use the helper where integration processing begins.

```java
# File: src/main/java/com/example/MyFileProcessorService.java
public void processFile(String fileName) {
    IntegrationLogContext.putBaseFields("PartnerCrm", "file", "inbound");
    try {
        log.atInfo()
            .addKeyValue("file.name", fileName)
            .log("Processing file.");
    } finally {
        IntegrationLogContext.removeBaseFields();
    }
}
```

### Example: Scheduled or batch fields
If the same job fields are set across scheduled jobs or batch flows, define a separate helper for that field group.

```java
# File: src/main/java/com/example/BatchLogContext.java
import org.slf4j.MDC;

public final class BatchLogContext {

    private BatchLogContext() {
    }

    public static void putJobFields(String jobName, String jobId, String cronExpression) {
        MDC.put("batch.job.name", jobName);
        MDC.put("batch.job.id", jobId);
        MDC.put("trigger.cron.expression", cronExpression);
    }

    public static void removeJobFields() {
        MDC.remove("batch.job.name");
        MDC.remove("batch.job.id");
        MDC.remove("trigger.cron.expression");
    }
}
```

Use that helper where the scheduled or batch work begins.

```java
# File: src/main/java/com/example/BatchScheduleLogger.java
private static final String CRON_EXPRESSION = "*/30 * * * * *";

@Scheduled(cron = CRON_EXPRESSION)
public void logSchedule() {
    BatchLogContext.putJobFields("dailyReportJob", "42", CRON_EXPRESSION);
    try {
        log.atInfo().log("Scheduled batch execution triggered.");
    } finally {
        BatchLogContext.removeJobFields();
    }
}
```

Use this pattern for repeated field groups. If a field is used in only one place, set and remove it inline instead.

## 5. Propagate MDC in async tasks
This section is only relevant if the application uses async task execution, for example with `@Async` methods or a `ThreadPoolTaskExecutor`. If the application does not use async tasks, skip this section.

MDC is thread-local, so when a task is submitted to an async thread, the worker thread starts with an empty MDC map and loses all context from the originating thread.

### With Micrometer Tracing

Apply `ContextPropagatingTaskDecorator` to the executor by calling `executor.setTaskDecorator(new ContextPropagatingTaskDecorator())`. When `micrometer-context-propagation` is on the classpath (included automatically with Micrometer Tracing), the decorator propagates both trace context and custom MDC fields to the worker thread at task submission.

Use Option A if all async methods need MDC context. Use Option B if only specific methods do.

<note>

> **Note:** The decorator adds a small amount of overhead per task submission. Avoid applying it to executors that handle a large number of very short, high-frequency tasks.

</note>

**Option A: Apply globally to all `@Async` methods**

Implement `AsyncConfigurer` to set the decorated executor as the application-wide default:

```java
# File: src/main/java/com/example/AppConfig.java
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AppConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);       // Tune based on workload
        executor.setMaxPoolSize(5);        // Tune based on workload
        executor.setQueueCapacity(10);     // Tune based on workload
        executor.setThreadNamePrefix("CustomAsync-");
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.initialize();
        return executor;
    }
}
```

**Option B: Apply to specific `@Async` methods only**

Define the executor as a named bean and reference it by name on the method:

```java
# File: src/main/java/com/example/AppConfig.java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AppConfig {

    @Bean("customTaskExecutor")
    public Executor customTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);       // Tune based on workload
        executor.setMaxPoolSize(5);        // Tune based on workload
        executor.setQueueCapacity(10);     // Tune based on workload
        executor.setThreadNamePrefix("CustomAsync-");
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.initialize();
        return executor;
    }
}
```

Reference the named executor on the async method:

```java
@Async("customTaskExecutor")
public void processAsync() { ... }
```

### Without Micrometer Tracing

Use a manual `TaskDecorator` that copies the MDC map from the submitting thread to the worker thread. Replace the `setTaskDecorator(...)` call in either option above with the following:

```java
executor.setTaskDecorator(runnable -> {
    Map<String, String> mdcContext = MDC.getCopyOfContextMap();
    return () -> {
        try {
            if (mdcContext != null) MDC.setContextMap(mdcContext);
            runnable.run();
        } finally {
            MDC.clear();
        }
    };
});
```

<note>

> **Note:** The `finally` block clears the MDC on the worker thread after the task completes, preventing context from leaking into the next task on the same pooled thread.

</note>

## 6. Add one-off fields without using MDC
MDC persists until explicitly removed. Using it for a field that appears on only one log entry requires cleanup that serves no purpose and leaks if the `remove` is missed.

For fields needed on only one entry, add them directly with `.addKeyValue(...)`. The field is written to that entry and nowhere else. No cleanup is required.

```java
// AVOID: MDC for a field used on a single entry
MDC.put("file.size", String.valueOf(fileSize));
log.atInfo().log("Processing file.");
MDC.remove("file.size");  // easy to forget, leaks if an exception is thrown before this line
```

Instead, add the field directly on the log entry:

```java
# File: src/main/java/com/example/AnyService.java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnyService {
    private static final Logger log = LoggerFactory.getLogger(AnyService.class);

    public void logFileEvent(String correlationId, long fileSize, String fileName) {
        log.atInfo()
            .addKeyValue("correlation.id", correlationId)
            .addKeyValue("file.size", fileSize)
            .addKeyValue("file.name", fileName)
            .log("Processing file.");
    }
}
```

<note>

> **Note:** Fields like `error.code` and `error.category` cannot be set with a plain `.addKeyValue("error.code", ...)` call when the ECS formatter is active. Spring interprets dotted keys as paths into nested objects and throws a runtime exception because the `error` object is already written by the formatter. A custom encoder is required to inject these fields correctly. See [Custom Structured Log Encoder](Custom_Structured_Log_Encoder.md).

</note>

## 7. Verification
Trigger a request and a flow that uses repeated MDC fields, along with any async paths and outbound HTTP calls that apply to the application.

Verify that:
- All log entries for the same request share the same `correlation.id` value
- MDC fields appear only on log entries that are part of the same request, job, or process
- MDC fields do not leak into the next request or the next scheduled execution
- Helper method fields (set via a context helper such as `IntegrationLogContext` or `BatchLogContext`) are absent from log entries outside the scope where they were set
- If Spring Security is in use: `user.id` (UUID) appears on authenticated requests and is absent on anonymous requests
- If async tasks are in use: async log entries carry the same MDC values as the originating thread. See [section 5](#5-propagate-mdc-in-async-tasks) for setup, because MDC does not cross thread boundaries automatically.
- If outbound HTTP calls are made: outgoing requests carry the `X-Correlation-ID` header with the same value as the current request. See [section 3.2](#32-forwarding-correlation-id-to-downstream-services) for setup, because MDC values are not forwarded as HTTP headers without explicit interceptor or filter configuration.
- One-off fields added with `addKeyValue(...)` appear only on that single log entry

## 8. Conclusion
This guide explained how to set MDC at request boundaries, manage cleanup safely, propagate context across async threads, and keep field scope narrow to prevent leaks.

### Key Takeaways
- **Use MDC for repeated context**: Set once, reuse across related log entries (request IDs, job IDs, batch metadata)
- **Set MDC at request boundaries**: Use an `OncePerRequestFilter` to set fields once at the entry point so all log entries for that request inherit the context automatically
- **Store user identity as UUID**: Set `user.id` to the UUID from the authenticated principal. Never write raw usernames, emails, or credentials to MDC
- **Choose the right cleanup strategy**: Use `putCloseable` for a single key with no catch-block logging, `MDC.remove(...)` in a `finally` block for multiple keys, and `MDC.clear()` only when the code owns the entire thread lifecycle
- **Create helpers for field groups**: Centralise field names and pair `put` with `remove` so cleanup is enforced across all callers
- **Forward correlation ID outbound**: Attach `correlation.id` from MDC to outgoing HTTP requests using a `ClientHttpRequestInterceptor` for `RestClient` or an `ExchangeFilterFunction` for `WebClient`
- **Propagate in async work**: If Micrometer Tracing is in use, apply `ContextPropagatingTaskDecorator` to the executor. If not, use a manual `TaskDecorator` with `MDC.getCopyOfContextMap()` and `MDC.setContextMap()`
- **Keep one-off fields out of MDC**: Use `addKeyValue(...)` for fields that appear on only one log entry

## 9. References

Related guides:
- [Structured Logging, Trace Correlation, and Context Propagation](Structured_Logging_Trace_Correlation_And_Context_Propagation.md)
- [Custom Structured Log Encoder](Custom_Structured_Log_Encoder.md)

SLF4J:
- [SLF4J Javadoc: MDC](https://www.slf4j.org/apidocs/org/slf4j/MDC.html)
- [SLF4J Javadoc: LoggingEventBuilder](https://www.slf4j.org/apidocs/org/slf4j/spi/LoggingEventBuilder.html)
- [SLF4J Issue #404: putCloseable removes key rather than restoring previous value](https://github.com/qos-ch/slf4j/issues/404)

Logback:
- [Logback Manual: Mapped Diagnostic Context](https://logback.qos.ch/manual/mdc.html)
- [Logback Javadoc: MDCInsertingServletFilter](https://logback.qos.ch/apidocs/ch.qos.logback.classic/ch/qos/logback/classic/helpers/MDCInsertingServletFilter.html)

Spring Boot:
- [Spring Boot Reference: Logging](https://docs.spring.io/spring-boot/reference/features/logging.html)
- [Spring Boot Reference: Task Execution and Scheduling](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html)
- [Spring Boot Reference: Observability](https://docs.spring.io/spring-boot/reference/actuator/observability.html)

Spring Framework:
- [Spring Framework Javadoc: OncePerRequestFilter](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/filter/OncePerRequestFilter.html)
- [Spring Framework Reference: RestClient](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)
- [Spring Framework Javadoc: ClientHttpRequestInterceptor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/http/client/ClientHttpRequestInterceptor.html)
- [Spring Framework Javadoc: ContextPropagatingTaskDecorator](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/task/support/ContextPropagatingTaskDecorator.html)
- [Spring Framework Javadoc: ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework Javadoc: EnableAsync](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html)
- [Spring Framework Javadoc: Scheduled](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)

Spring WebFlux:
- [Spring Framework Reference: WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)
- [Spring Framework Javadoc: ExchangeFilterFunction](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/reactive/function/client/ExchangeFilterFunction.html)

Spring Security:
- [Spring Security Reference: Servlet Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html)
- [Spring Security Javadoc: AnonymousAuthenticationFilter](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/web/authentication/AnonymousAuthenticationFilter.html)

Micrometer:
- [Micrometer Context Propagation Reference](https://docs.micrometer.io/micrometer/reference/contextpropagation.html)

Further reading:
- [Baeldung: MDC in Log4j 2 and Logback](https://www.baeldung.com/mdc-in-log4j-2-logback)
