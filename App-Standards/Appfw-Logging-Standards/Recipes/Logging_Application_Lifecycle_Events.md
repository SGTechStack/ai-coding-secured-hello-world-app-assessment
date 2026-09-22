# Logging Application Lifecycle Events

## 1. Introduction
Operational teams rely on logs to confirm that an application has started correctly and to diagnose startup failures without reading raw console output. This guide shows how to capture startup, startup failure, dependency health checks, and shutdown as structured log entries using the Spring Boot event model.

At startup, use `ApplicationRunner` to log the host name and IP address so operators can confirm which instance is running. After initialization completes, verify that critical dependencies (databases, external APIs) are reachable using `ApplicationReadyEvent`. For startup failures, add an `ApplicationFailedEvent` listener to capture the exception as a structured log entry. For shutdown, `ContextClosedEvent` records whether the application stopped cleanly, helping operators distinguish a planned shutdown from a crash.

## 2. Prerequisites
- **Spring Boot 4.0+**
- **ECS Structured Logging Enabled:** See [Structured Logging, Trace Correlation, and Context Propagation](Structured_Logging_Trace_Correlation_And_Context_Propagation.md) for setup.
- **Custom Structured Log Encoder:** Required for error field mapping (`error_code`, `error_category`, `error_follow_up_action`). See [Custom Structured Log Encoder](Custom_Structured_Log_Encoder.md).
- **Schema compliance:** Use field values from [Log_Schema.md](../Log_Schema.md) for `event.category`, `event.type`, `event.action`, and `error_category` to ensure consistency across services

## 3. Log startup

Use `ApplicationRunner` as the hook. It fires after the application context is fully initialised and all beans are available, but before the application starts accepting traffic. If you need to log after all startup initialization tasks have completed (including other ApplicationRunners), use `ApplicationReadyEvent` instead, annotating a plain method with `@EventListener(ApplicationReadyEvent.class)`. In distributed and containerised environments, include `host.name` and `host.ip` to identify which instance produced the log entry.

```java
# File: src/main/java/com/example/startup/ApplicationStartupLogger.java
package com.example.startup;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

@Slf4j
@Component
public class ApplicationStartupLogger implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        String hostname = resolveHostname();
        String ipAddress = resolveIpAddress();

        log.atInfo()
            .addKeyValue("event.kind", "event")
            .addKeyValue("event.category", List.of("process"))
            .addKeyValue("event.type", List.of("start"))
            .addKeyValue("event.action", "application-startup")
            .addKeyValue("event.outcome", "success")
            .addKeyValue("event.severity", "low")
            .addKeyValue("host.name", hostname)
            .addKeyValue("host.ip", ipAddress)
            .log("Application started.");
    }

    private String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unavailable";
        }
    }

    private String resolveIpAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "unavailable";
        }
    }
}
```

To log key non-secret configuration values such as feature flags or scheduler timezone, add them with `addKeyValue`. Never log credentials, connection strings with passwords, API keys, tokens, or authentication secrets. For guidance on what infrastructure details are acceptable in logs, see [Sensitive Data Masking for Logs](Sensitive_Data_Masking_For_Logs.md). For scheduled job registrations at startup, see [Logging Batch and Scheduled Job Operations](Logging_Batch_And_Scheduled_Jobs.md).

<note>

> **Note:** Service identity fields (`service.name`, `service.version`, `service.environment`) are written automatically by the ECS formatter and must not be set via `addKeyValue`. The `service` object is already closed by the time your code runs, and calling `addKeyValue("service.name", ...)` will cause a JSON writing error. In containerised environments, `InetAddress.getLocalHost()` can return the loopback address (`127.0.0.1`) if DNS is misconfigured.

</note>

## 4. Log dependency health checks

After the application starts, verify that critical dependencies are reachable before accepting traffic. Health checks confirm that databases, external APIs, message brokers, and file storage are available, allowing operators to detect configuration issues or network failures at startup.

Unlike the startup event which records that the application process launched (`event.kind: event`), health checks capture the current state of dependencies (`event.kind: state`). Use `event.type: connection` to indicate this is a connectivity check, not a transactional operation.

Perform a one-time health check after startup using `ApplicationReadyEvent`. This fires after all `ApplicationRunner` beans have executed and the application is ready to serve traffic, making it the correct point to verify dependencies before accepting requests.

```java
# File: src/main/java/com/example/startup/DependencyHealthChecker.java
package com.example.startup;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DependencyHealthChecker {

    private final DataSource dataSource;
    private final HttpClient httpClient;

    public DependencyHealthChecker(DataSource dataSource) {
        this.dataSource = dataSource;
        this.httpClient = HttpClient.newHttpClient();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void checkDependenciesAtStartup() {
        List<Map<String, String>> connections = new ArrayList<>();
        boolean allHealthy = true;

        // Check database
        String dbStatus = checkDatabase();
        connections.add(Map.of(
            "name", "CustomerDB",
            "type", "database",
            "status", dbStatus
        ));
        if ("down".equals(dbStatus)) {
            allHealthy = false;
        }

        // Check external API
        String apiStatus = checkExternalApi("https://api.partner.com/health");
        connections.add(Map.of(
            "name", "PartnerAPI",
            "type", "api",
            "status", apiStatus
        ));
        if ("down".equals(apiStatus)) {
            allHealthy = false;
        }

        // Log health check result
        if (allHealthy) {
            log.atInfo()
                .addKeyValue("event.kind", "state")
                .addKeyValue("event.category", List.of("configuration"))
                .addKeyValue("event.type", List.of("connection"))
                .addKeyValue("event.outcome", "success")
                .addKeyValue("event.severity", "low")
                .addKeyValue("service.connection", connections)
                .log("All dependencies are healthy.");
        } else {
            log.atError()
                .addKeyValue("event.kind", "state")
                .addKeyValue("event.category", List.of("configuration"))
                .addKeyValue("event.type", List.of("connection"))
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("event.severity", "critical")
                .addKeyValue("service.connection", connections)
                .addKeyValue("error_code", 503)
                .addKeyValue("error_category", "network")
                .addKeyValue("error_follow_up_action", true)
                .log("One or more dependencies are unreachable at startup.");
        }
    }

    private String checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(5) ? "up" : "down";
        } catch (Exception e) {
            log.debug("Database health check failed", e);
            return "down";
        }
    }

    private String checkExternalApi(String healthUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(healthUrl))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return (response.statusCode() >= 200 && response.statusCode() < 300) ? "up" : "down";
        } catch (Exception e) {
            log.debug("External API health check failed: {}", healthUrl, e);
            return "down";
        }
    }
}
```

<note>

> **Note:** This example shows a one-time startup health check. For continuous health monitoring during operation, use Spring Boot Actuator's `/actuator/health` endpoint with metrics exporters (e.g., Prometheus, Micrometer) rather than scheduled logging, which floods logs and increases storage costs. Scheduled health check logging generates hundreds of entries per day per instance and should be avoided.

</note>

<note>

> **Note:** Startup health check failures use `event.severity: critical` because the application cannot function properly if dependencies are unreachable before accepting traffic. This should block deployment or trigger immediate alerts. The `service.connection` field is an array of objects, where each object represents one dependency. Use `service_connection` (underscore key) if you need to add this field when the `service` object is pre-sealed by ECS. See [Custom Structured Log Encoder](Custom_Structured_Log_Encoder.md) for the workaround pattern.

</note>

## 5. Log startup failure

Spring Boot's built-in `FailureAnalyzer` mechanism handles common startup failures such as port conflicts and missing required configuration, printing a diagnostic message to the console automatically. For structured log output that is queryable in the log platform, listen for `ApplicationFailedEvent` to capture any startup failure and its exception as structured fields.

```java
# File: src/main/java/com/example/startup/ApplicationStartupFailureLogger.java
package com.example.startup;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ApplicationStartupFailureLogger {

    @EventListener
    public void onApplicationFailed(ApplicationFailedEvent event) {
        Throwable failure = event.getException();
        log.atError()
            .setCause(failure)
            .addKeyValue("event.kind", "event")
            .addKeyValue("event.category", List.of("process"))
            .addKeyValue("event.type", List.of("error"))
            .addKeyValue("event.action", "application-startup")
            .addKeyValue("event.outcome", "failure")
            .addKeyValue("event.severity", "critical")
            .addKeyValue("error_code", 500)
            .addKeyValue("error_category", "application")
            .addKeyValue("error_follow_up_action", true)
            .log("Application failed to start.");
    }
}
```

<note>

> **Note:** `ApplicationFailedEvent` fires before the application context is fully closed. If the logging infrastructure has not been initialised at the point of failure, this listener may not produce output. In that case, the error is visible in the console output only. `error_code`, `error_category`, and `error_follow_up_action` use non-dotted keys because the ECS formatter pre-defines and closes the `error` object after populating `error.type`, `error.message`, and `error.stack_trace` from `.setCause()`. Using dotted `error.*` keys for custom fields after the object is closed causes a JSON writing error. See [Custom Structured Log Encoder](Custom_Structured_Log_Encoder.md) for how these non-dotted keys are mapped to their schema field names.

</note>

## 6. Log shutdown

Listen for `ContextClosedEvent` to record a clean shutdown as a structured entry.

```java
# File: src/main/java/com/example/startup/ApplicationShutdownLogger.java
package com.example.startup;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ApplicationShutdownLogger {

    @EventListener
    public void onApplicationShutdown(ContextClosedEvent event) {
        log.atInfo()
            .addKeyValue("event.kind", "event")
            .addKeyValue("event.category", List.of("process"))
            .addKeyValue("event.type", List.of("end"))
            .addKeyValue("event.action", "application-shutdown")
            .addKeyValue("event.outcome", "success")
            .addKeyValue("event.severity", "low")
            .log("Application shutting down.");
    }
}
```

<note>

> **Note:** `ContextClosedEvent` fires when `ApplicationContext.close()` is called, including when the JVM shutdown hook triggers. It fires before any bean destruction begins, making it the earliest reliable point to write a shutdown log entry. `@PreDestroy` fires later in the shutdown sequence, per bean, as each is destroyed, which is too late for a reliable shutdown log.

</note>

## 7. Verification
Start the application, allow it to finish startup, then stop it gracefully. Also trigger a startup failure by setting an invalid datasource URL in `application.yml`. To test health check failures, temporarily make a dependency unreachable (e.g., stop the database or use an invalid API URL).

Verify that:
- **Schema compliance:** `event.category`, `event.type`, `event.action`, `error_category` values match those defined in [Log_Schema.md](../Log_Schema.md)
- The startup log entry includes `host.name` and `host.ip`
- The startup log entry has `event.kind: event`, `event.category: ["process"]`, `event.type: ["start"]`, `event.action: application-startup`, `event.outcome: success`, and `event.severity: low`
- The startup failure log entry has `event.kind: event`, `event.category: ["process"]`, `event.type: ["error"]`, `event.action: application-startup`, `event.outcome: failure`, `event.severity: critical`, and the exception is attached (providing error type, message, and stack trace) with `error_code`, `error_category`, and `error_follow_up_action` present
- The health check log entry has `event.kind: state` (not "event"), `event.category: ["configuration"]`, `event.type: ["connection"]`, and `service.connection` array with each dependency showing `name`, `type`, and `status` ("up" or "down")
- When all dependencies are healthy: `event.outcome: success`, `event.severity: low`
- When any dependency is unreachable: `event.outcome: failure`, `event.severity: critical`, with `error_code`, `error_category`, and `error_follow_up_action` present
- The shutdown log entry has `event.kind: event`, `event.category: ["process"]`, `event.type: ["end"]`, `event.action: application-shutdown`, `event.outcome: success`, and `event.severity: low`
- None of the lifecycle entries appear more than once (no duplicate listeners)

## 8. Conclusion
With these hooks in place, startup, startup failure, health checks, and shutdown are each captured as queryable structured events, providing complete visibility into application lifecycle and dependency health.

### Key Takeaways
- **`ApplicationRunner` for startup**: Fires after the context is fully initialised and all beans are available, but before the application starts accepting traffic. Use `ApplicationReadyEvent` if you need to run after all ApplicationRunners have completed
- **Log `host.name` and `host.ip` at startup**: Identifies which instance produced the log entry in distributed and containerised environments
- **`ApplicationFailedEvent` for startup failures**: Spring Boot's `FailureAnalyzer` handles common failures automatically. Add this listener when you need the exception captured as structured fields in the log platform
- **Health checks use `event.kind: state`**: Unlike transactional events (`event.kind: event`), health checks capture current dependency state. Use `event.type: connection` to indicate connectivity checks
- **`service.connection` array for multi-dependency logging**: Log all dependency statuses in a single log entry with an array of objects, each containing `name`, `type`, and `status`
- **One-time startup health check**: Use `ApplicationReadyEvent` to check dependencies after initialization but before accepting traffic. For continuous monitoring, use Actuator metrics rather than scheduled logging to avoid context bloat
- **`ContextClosedEvent` for shutdown**: Fires before any bean destruction begins, making it the earliest reliable point to write a shutdown log entry. Prefer it over `@PreDestroy`, which fires per bean during destruction
- **Keep lifecycle hooks stateless**: These hooks run during critical application phases. Avoid blocking calls or business logic inside them
- **Use non-dotted keys for custom `error.*` fields**: The ECS formatter closes the `error` object after auto-populating it. Use `error_code`, `error_category`, and `error_follow_up_action` and map them through a custom encoder (see [Custom Structured Log Encoder](Custom_Structured_Log_Encoder.md))

## 9. References

Related guides:
- [Logging Batch and Scheduled Job Operations](Logging_Batch_And_Scheduled_Jobs.md)
- [Custom Structured Log Encoder](Custom_Structured_Log_Encoder.md)
- [Structured Logging, Trace Correlation, and Context Propagation](Structured_Logging_Trace_Correlation_And_Context_Propagation.md)

Spring Boot:
- [Spring Boot Reference: Application Events and Listeners](https://docs.spring.io/spring-boot/reference/features/spring-application.html#features.spring-application.application-events-and-listeners)
- [Spring Boot Reference: Logging](https://docs.spring.io/spring-boot/reference/features/logging.html)
- [Spring Boot Reference: Actuator Endpoints - Health](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html#actuator.endpoints.health)
- [Spring Boot Reference: Task Execution and Scheduling](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html)
- [Spring Boot How-to: Failure Analyzers](https://docs.spring.io/spring-boot/how-to/application.html#howto.application.failure-analyzer)

Spring Framework:
- [Spring Framework Reference: Application Context Events](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events)

SLF4J:
- [SLF4J Manual: Fluent Logging API](https://www.slf4j.org/manual.html)
