# Logging Exceptions with Enhanced Details

## 1. Introduction
Exception logs are most useful when they include the stack trace, the full exception cause chain, and the context needed to diagnose the failure. This guide explains how to log exceptions with structured fields, consistent error identifiers, follow-up flags, and correlation context using SLF4J's fluent logging API. It also covers choosing the right log level, preserving nested exception causes, and avoiding duplicate log entries.

## 2. Prerequisites
- Spring Boot 4.0+
- Structured logging enabled if exception fields should appear in structured JavaScript Object Notation (JSON) output. For structured logging setup, see [Structured Logging, Trace Correlation, and Context Propagation](Structured_Logging_Trace_Correlation_And_Context_Propagation.md)
- ECS log format configured so that `.setCause(e)` auto-populates `error.message`, `error.type`, and `error.stack_trace`
- [Custom Structured Log Encoder](Custom_Structured_Log_Encoder.md) configured for underscore-to-dot field name mapping (required for `error_code`, `error_category`, and similar fields)

## 3. Log the exception and add structured fields
When an exception occurs, two distinct kinds of information are needed. The first is the stack trace. Without it, there is no record of the code path that failed, and diagnosing the root cause requires guesswork. The second is structured context. Without it, the entry cannot be searched, filtered, or routed by error code, action, or outcome.

`.setCause(e)` provides the stack trace automatically. When ECS is active, it also derives `error.message`, `error.type`, and `error.stack_trace` from the exception object, so those fields must not be set manually, as doing so creates a duplicate field conflict. `.addKeyValue(...)` provides the business context fields that ECS does not auto-populate, such as `error_code`, `event.action`, and `error_follow_up_action`.

Omitting `.setCause(e)` loses the stack trace. Omitting the structured fields makes the entry unsearchable by code or action. Both are required.

<warning>

> **Warning:** When using the ECS log format, `.setCause(e)` automatically populates `error.message`, `error.type`, and `error.stack_trace` from the exception. Do not manually add `addKeyValue("error.message", ...)` or `addKeyValue("error.type", ...)`. Doing so produces a duplicate field conflict.
> 
> Furthermore, setting `.addKeyValue("error.code", ...)` or `.addKeyValue("error.category", ...)` will cause Spring to throw a JSON writing error because the ECS formatter has already closed the `error` object. You **must** use underscore keys (e.g., `error_code` instead of `error.code`) and a custom encoder to map them correctly. See [Custom Structured Log Encoder](Custom_Structured_Log_Encoder.md).

</warning>

Choose the log level based on the severity and expectedness of the exception. Use `.atError()` for unrecoverable failures or unexpected system errors such as infrastructure timeouts, database connection failures, or unexpected null references. Use `.atWarn()` for recoverable or expected failures such as validation errors, business rule violations, or transient conditions the application handles gracefully. Avoid `.atInfo()` for exceptions.

```java
# File: src/main/java/com/example/MyService.java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyService {
    private static final Logger log = LoggerFactory.getLogger(MyService.class);

    public void performRiskyOperation() {
        try {
            if (Math.random() < 0.5) {
                throw new IllegalArgumentException("Invalid input provided");
            }
            log.info("Risky operation completed successfully.");
        } catch (Exception e) {
            log.atWarn()
                .setCause(e)
                .addKeyValue("error_code", 422)
                .addKeyValue("error_category", "application")
                .addKeyValue("event.action", "file-process")
                .addKeyValue("error_follow_up_action", false)
                .log("Risky operation failed.");
        }
    }
}
```

The same pattern applies to any catch block, regardless of exception type. The fields change. The structure does not.

```java
# File: src/main/java/com/example/FileService.java
} catch (IOException e) {
    log.atError()
        .setCause(e)
        .addKeyValue("error_code", 503)
        .addKeyValue("error_category", "server")
        .addKeyValue("event.action", "file-read")
        .addKeyValue("error_follow_up_action", true)
        .log("File read failed.");
}
```

## 4. Add consistent error identifiers
Without consistent error codes and categories, failures from different services appear as unrelated noise. With them, a dashboard can group all `422` validation errors, an alert can trigger on any `application`-category failure, and a search can filter to a specific operation in seconds.

`error_code` is the status or error code meaningful to the caller. `error_category` classifies the nature of the failure: `application` for business logic errors, `network` for external API failures, `database` for database errors, and `server` for internal infrastructure failures, so failures can be routed to the right owner. `error_follow_up_action` indicates whether the error requires manual intervention. Set to `true` for errors that need human action (for example, infrastructure failures, data corruption, external system outages). Set to `false` for errors the system handles automatically (for example, validation errors returned to client, retryable transient failures).

> **Note:** Use values from [Log_Schema.md](../Log_Schema.md) for `error_category` and `event.action` to ensure consistency across services and enable proper aggregation in log platforms.

```java
# File: src/main/java/com/example/MyService.java
log.atError()
    .setCause(e)
    .addKeyValue("error_code", 422)
    .addKeyValue("error_category", "application")
    .addKeyValue("error_follow_up_action", false)
    .log("Risky operation failed.");
```

The same approach applies to infrastructure failures. Only the code and category change to reflect the different nature of the error.

```java
# File: src/main/java/com/example/ExternalApiClient.java
log.atError()
    .setCause(e)
    .addKeyValue("error_code", 503)
    .addKeyValue("error_category", "network")
    .addKeyValue("error_follow_up_action", true)
    .log("External API call failed.");
```

When using ECS, `error.type`, `error.message`, and `error.stack_trace` are automatically derived from the exception passed to `.setCause(e)`. Do not set these manually. When schema alignment requires `error.stack_trace`, populate that field from the captured exception stack trace in the structured output rather than logging the same exception twice.

## 5. Include correlation and business context
If correlation or business context is already stored in Mapped Diagnostic Context (MDC), it will be included in the exception log automatically. If that context is not already in MDC, add the required fields directly to the exception log entry.

```java
# File: src/main/java/com/example/MyService.java
log.atError()
    .setCause(e)
    .addKeyValue("correlation.id", "txn-abc-123-xyz")
    .addKeyValue("interface.system", "PartnerCrm")
    .addKeyValue("event.action", "file-process")
    .log("Risky operation failed.");
```

## 6. Preserve exception cause chains
When an exception is caught and wrapped before being rethrown, the original exception is the root cause of the failure. If the wrapper does not chain the original, `.setCause(wrappedException)` at the log site provides only the wrapper's stack trace. The database error, parse failure, or network timeout that triggered the problem is lost.

Always pass the original exception as a constructor argument when wrapping:

```java
// AVOID: swallows the original cause
} catch (IOException e) {
    throw new ServiceException("File read failed");
}
```

```java
// PREFER: preserves the full cause chain
} catch (IOException e) {
    throw new ServiceException("File read failed", e);
}
```

When `.setCause(e)` is called on the wrapped exception at the log site, ECS traverses the full cause chain and includes the root exception in `error.stack_trace`. The chain is only preserved if each wrapping exception passes the original as a constructor argument.

## 7. Avoid duplicate or unsafe exception logs
When an exception is caught and rethrown, it is tempting to log it at each layer. The result is the same stack trace appearing two or three times in the log pipeline, inflating storage costs and making it harder to count real incidents.

Log the exception once at the point where it is definitively handled. For example, log it in a global exception handler or at the top-level service boundary that returns an error response. Do not log it in intermediate layers that simply catch and rethrow.

```java
// AVOID: logs the exception, then rethrows it to be logged again by the caller or global handler
} catch (Exception e) {
    log.atError().setCause(e).log("Service failed.");
    throw e;
}
```

```java
// PREFER: rethrow without logging and let the boundary handler log once with full context
} catch (Exception e) {
    throw new ServiceException("Service failed", e);
}

// At the boundary (e.g., global exception handler):
} catch (ServiceException e) {
    log.atError()
        .setCause(e)
        .addKeyValue("error_code", 500)
        .addKeyValue("error_category", "application")
        .log("Service failed.");
}
```

If an intermediate layer must log before rethrowing, for example to capture context that is unavailable at the boundary, log the fields without repeating the full exception at the boundary.

```java
// Intermediate layer logs context, boundary logs full exception:
} catch (IOException e) {
    log.atWarn()
        .addKeyValue("file.path", filePath)
        .addKeyValue("event.outcome", "failure")
        .log("File read failed, rethrowing.");
    throw new ServiceException("Service failed", e);
}

// At the boundary:
} catch (ServiceException e) {
    log.atError()
        .setCause(e)  // Full exception logged here only
        .addKeyValue("error_code", 500)
        .log("Service failed.");
}
```

Do not place sensitive values such as passwords, tokens, full personal data, or raw payloads in exception messages or structured fields. If those values are needed for support, log a masked or hashed form instead. For detailed guidance on preventing sensitive data in logs, see [Sensitive Data Masking for Logs](Sensitive_Data_Masking_For_Logs.md#5-prevent-sensitive-values-from-being-logged).

## 8. Verification
Trigger a failure path.

Verify that:
- the exception is logged with a stack trace
- fields such as `error_code`, `error.type`, `error.message`, and `error_follow_up_action` appear as structured fields
- if using a custom encoder, verify that `error.code`, `error.category`, and `error.follow_up_action` appear in the JSON output with proper dotted names (not underscore names), confirming the custom encoder correctly remapped them
- the structured output includes the exception stack trace in the configured stack trace field used for `error.stack_trace`
- the log entry includes correlation or business context such as `correlation.id` or `interface.system`
- sensitive values are not written to the message or structured fields
- nested exception causes are visible in the stack trace output

## 9. Conclusion
This guide explained how to log exceptions with stack traces and structured fields, choose the appropriate log level, preserve exception cause chains, add consistent error identifiers, include correlation and business context, and avoid duplicate or unsafe exception logs.

### Key Takeaways
- **Log the exception**: Use `.setCause(e)` to capture the stack trace and populate ECS error fields automatically
- **Choose the right level**: Use `.atError()` for unrecoverable failures and `.atWarn()` for recoverable or expected exceptions
- **Preserve cause chains**: Always pass the original exception when wrapping to maintain the full diagnostic chain
- **Add structured fields**: Include `error_code`, `error_category`, and `error_follow_up_action` for searchability and diagnosis
- **Use schema-compliant values**: Use values from the Log_Schema.md for `error_category` and `event.action` to ensure consistency across services and enable proper aggregation in log platforms
- **Include context**: Add `correlation.id`, `interface.system`, and other business context for root cause analysis
- **Avoid duplication**: Log exceptions at the handling point, not at every layer
- **Prevent sensitive leaks**: Sanitize error messages to avoid exposing PII, credentials, or URLs

With this approach, exception logs are easier to search, classify, and troubleshoot.

## 10. References

Related guides:
- [Log Schema](../Log_Schema.md)
- [Enriching Logs with MDC](Enriching_Logs_With_MDC.md)
- [Sensitive Data Masking for Logs](Sensitive_Data_Masking_For_Logs.md)

SLF4J:
- [SLF4J Javadoc: LoggingEventBuilder](https://www.slf4j.org/apidocs/org/slf4j/spi/LoggingEventBuilder.html)
- [SLF4J Javadoc: MDC](https://www.slf4j.org/apidocs/org/slf4j/MDC.html)

Spring Boot:
- [Spring Boot Reference: Logging](https://docs.spring.io/spring-boot/reference/features/logging.html)
- [Spring Boot Reference: Observability](https://docs.spring.io/spring-boot/reference/actuator/observability.html)
