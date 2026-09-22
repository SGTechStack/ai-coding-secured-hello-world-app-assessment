# Custom Structured Log Encoder

## 1. Introduction

When using Spring Boot's built-in Elastic Common Schema (ECS) format for structured logging, you may encounter a JSON writing error when attempting to add custom key-value pairs using dotted notation (e.g., `addKeyValue("error.code", 503)`). 

This occurs because Spring Boot's `ElasticCommonSchemaStructuredLogFormatter` constructs and closes several pre-defined nested JSON objects (like `error`, `service`, `log`, `process`, and `ecs`) *before* custom fields are processed. When you use a dotted key like `error.code`, Spring's JSON writer attempts to insert it into the `error` object. Because the formatter has already sealed that object, Spring throws an error.

<note>

> **Note:** This issue is specific to the **ECS format** (`logging.structured.format.console=ecs`). Spring Boot's other built-in formats (`logstash` and `gelf`) write all fields flat without true nested JSON objects, so dotted keys do not clash in those configurations.

</note>

To circumvent this limitation, applications use keys with underscore separators (e.g., `error_code` or `error_category`) to ensure they are safely written as flat, root-level fields. The underscore separator aligns with ECS's snake_case convention and provides clear visual distinction from the dotted target format.

This guide explains how to build a custom `StructuredLogEncoder` that intercepts the final JSON output and cleanly migrates these underscore-separated workaround fields back into their architecturally correct nested objects. Specifically, this guide will demonstrate how to:

1. Transform root-level workaround keys (like `error_code`) into properly nested fields inside the sealed `error` object (e.g., `error.code`).
2. Automatically resolve and inject `error.category` based on the type of Exception thrown, eliminating the need for developers to set it manually.

<important>

> **Important:** This solution extends `StructuredLogEncoder`, an internal Spring Boot class in the `org.springframework.boot.logging.logback` package. It is not a documented public extension point, meaning its API may change in future Spring Boot releases without a migration guide. You must retest this custom encoder after every Spring Boot version upgrade.

</important>

## 2. Prerequisites
- **Spring Boot 4.0+**
- **ECS Structured Logging Enabled:** Configured via `application.yaml` (`logging.structured.format.console=ecs`) or `logback-spring.xml` (`<format>ecs</format>`). See [Structured Logging, Trace Correlation, and Context Propagation](Structured_Logging_Trace_Correlation_And_Context_Propagation.md) for setup.
- **Jackson Databind:** Required for JSON manipulation. Included automatically with `spring-boot-starter-web`. For non-web applications, add `com.fasterxml.jackson.core:jackson-databind` explicitly.
- **Custom Logback Configuration:** A `logback-spring.xml` file is required to register the custom encoder (section 4.4). If ECS was previously enabled via `application.yaml` alone, this file must be added.
- **Schema compliance:** Use field values from [Log_Schema.md](../Log_Schema.md) for `error.category` and other standardized fields to ensure consistency across services

## 3. How the encoder transforms fields

Attempting to log custom fields using dotted keys that conflict with pre-sealed objects, such as `addKeyValue("error.code", 503)`, results in a JSON writing error. To safely log these values, the application must use an alternative format, such as `error_code`. This allows Spring to write the value as a plain root-level field without conflict:

```json
{
  "error": {
    "type": "java.io.IOException",
    "message": "Connection refused"
  },
  "error_code": 503
}
```

The custom encoder intercepts this output, extracts `error_code` from the root, and properly nests its value as `code` inside the `error` object. It also resolves `error.category` directly from the throwable. The final, transformed output is fully compliant with the structured log schema:

```json
{
  "error": {
    "type": "java.io.IOException",
    "message": "Connection refused",
    "code": 503,
    "category": "network"
  }
}
```

### 3.1 Resolving conflicts with other ECS objects

The `error` object is not the only pre-defined structure in ECS output. `ElasticCommonSchemaStructuredLogFormatter` automatically writes several other nested objects before custom key-value pairs are appended. Using `addKeyValue` with a key that attempts to nest data inside *any* of these sealed objects will trigger a JSON writing error.

The objects that commonly require workarounds for dynamic custom fields are:

| ECS object | Common dynamic fields requiring workarounds |
|---|---|
| `error` | `code`, `category`, `follow_up_action` |
| `service` | `system`, `subsystem`, `connection.type`, `connection.status` |

<note>

> **Note:** Other ECS objects (`log`, `process`, `ecs`) are fully auto-populated by the framework and should never be set via `addKeyValue`. For a complete list of auto-populated fields, see the [Spring Boot ElasticCommonSchemaStructuredLogFormatter source](https://github.com/spring-projects/spring-boot/blob/main/spring-boot-project/spring-boot/src/main/java/org/springframework/boot/logging/structured/ElasticCommonSchemaStructuredLogFormatter.java).

</note>

#### Handling Static Configuration vs. Dynamic Fields

It is critical to distinguish between **static fields** that are configured once for the entire application (such as the application's name or environment) and **dynamic fields** that vary per log event.

<important>

> **Important:** Do not use `addKeyValue` to set static service fields. If `logging.structured.ecs.service.*` is configured in `application.yaml`, the ECS formatter automatically writes the `service` object (with `name`, `version`, `environment`) on every log entry and closes it. 
> 
> Calling `addKeyValue("service.name", "...")` at log time will cause a JSON writing error because the object is already closed. These values are already in the output attempting to set them again is both redundant and incorrect.
> 
> ```yaml
> # application.yaml — configure static fields once; do not repeat at log time
> logging:
>   structured:
>     ecs:
>       service:
>         name: medical-demo
>         version: ${APP_VERSION:0.0.1-SNAPSHOT}
>         environment: ${spring.profiles.active:prod}
> ```
> 
> ```java
> // Correct — service fields are automatically pulled from application.yaml
> log.atInfo().log("Processing started.");
> 
> // Incorrect — throws a JSON writing error because the service object is already closed
> log.atInfo()
>     .addKeyValue("service.name", "medical-demo")
>     .log("Processing started.");
> ```

</important>

Conversely, if your application needs to log **dynamic** fields that logically belong inside these sealed objects (per the Log Schema) and vary at runtime, you must use the underscore-key workaround and extend the custom encoder to process them.

While this guide's implementation (Section 4) specifically demonstrates processing `error.code` and `error.category`, the same regex-based technique must be applied to handle other common schema conflicts:

| Log schema field | Workaround Key | Required Encoder Action |
|---|---|---|
| `error.code` | `error_code` | Inject into the `error` object |
| `error.category` | `error_category` | Inject into the `error` object |
| `error.follow_up_action` | `error_follow_up_action` | Inject into the `error` object |
| `service.system` | `service_system` | Inject into the `service` object |
| `service.subsystem` | `service_subsystem` | Inject into the `service` object |
| `service.connection.type` | `service_connection_type` | Inject into the `service` object |
| `service.connection.status` | `service_connection_status` | Inject into the `service` object |

## 4. Implementation

### 4.1 Error category resolver

To eliminate the need for developers to manually set `error.category` on every log entry, the encoder uses a resolver to automatically map thrown exceptions to standardized categories.

<important>

> **Important:** `error.category` is **not part of the official ECS specification**. It is a custom field extension defined in [Log_Schema.md](../Log_Schema.md) to enable consistent error categorization and alerting across services. Before implementing the resolver, verify that the category values below match those defined in Log_Schema.md to ensure compliance.

</important>

The following custom categories are used:

| `error.category` value | When to use |
|---|---|
| `server` | HTTP 5xx responses from upstream services |
| `network` | Connectivity failures, timeouts, DNS issues |
| `cert/auth` | Certificate errors, authentication failures, 401/403 responses |
| `database` | Database connection failures, query errors |
| `application` | Business logic errors, validation failures, unexpected application state |
| `data` | Data format errors, parsing failures, schema mismatches |
| `others` | Errors that do not fit any of the categories above |

Define the resolver as a separate class so it can be tested independently and reused. To map exceptions efficiently, the implementation uses `instanceof` checks. Because `instanceof` evaluates subclasses, mapping a parent exception naturally handles all its specific variants without needing explicit entries (e.g., mapping `DataAccessException` automatically covers `DataIntegrityViolationException`).

Each category is a named entry in an ordered list of predicates. Rules are evaluated top-to-bottom and the first match wins. To add a new category, add a `Map.entry(...)` at the appropriate position.

**Ordering constraint:** More specific exception types must appear before their parent types. For example, `ConnectException extends SocketException`, so the `network` rule must appear before any rule that matches `SocketException`. If a subclass appears after its parent, the parent will always match first and the subclass entry is unreachable.

```java
# File: src/main/java/com/example/logging/ErrorCategoryResolver.java
package com.example.logging;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.net.ssl.SSLException;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

public final class ErrorCategoryResolver {

    // Rules are evaluated in order — more specific exception types must appear before their parent types.
    // To add a new category, insert a new Map.entry(...) at the appropriate position.
    private static final List<Map.Entry<Predicate<Throwable>, String>> RULES = List.of(
        Map.entry(
            t -> t instanceof HttpServerErrorException
                || t instanceof WebClientResponseException.ServerError
                || (t instanceof ResponseStatusException rse
                    && rse.getStatusCode().value() >= 500 && rse.getStatusCode().value() < 600),
            "server"
        ),
        Map.entry(
            t -> t instanceof ConnectException       // ConnectException and SocketTimeoutException extend
                || t instanceof SocketTimeoutException  // SocketException, so they must appear before it
                || t instanceof UnknownHostException
                || t instanceof SocketException,
            "network"
        ),
        Map.entry(
            t -> t instanceof SSLException
                || t instanceof CertificateException
                || t instanceof AccessDeniedException
                || t instanceof AuthenticationException
                || (t instanceof ResponseStatusException rse
                    && (rse.getStatusCode().value() == 401 || rse.getStatusCode().value() == 403)),
            "cert/auth"
        ),
        Map.entry(
            t -> t instanceof SQLException
                || t instanceof DataAccessException
                || t instanceof PersistenceException,
            "database"
        ),
        Map.entry(
            t -> t instanceof MethodArgumentNotValidException
                || t instanceof JsonProcessingException
                || t instanceof ParseException,
            "data"
        ),
        Map.entry(
            t -> t instanceof IllegalArgumentException
                || t instanceof IllegalStateException
                || t instanceof UnsupportedOperationException,
            "application"
        )
    );

    private ErrorCategoryResolver() {
    }

    public static String resolve(Throwable throwable) {
        if (throwable == null) {
            return "others";
        }
        return RULES.stream()
            .filter(e -> e.getKey().test(throwable))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse("others");
    }
}
```

### 4.2 Custom encoder

We cannot pre-process the event before it reaches the formatter because `ElasticCommonSchemaStructuredLogFormatter` strictly controls the creation and sealing of the `error` object. Any attempt to write a custom nested structure into it beforehand will either be overwritten or cause a JSON syntax conflict. 

Therefore, the only viable solution is to extend `StructuredLogEncoder` and override `encode()` to post-process the raw JSON string after the formatter has completed its work but before the bytes are written to the output stream.

To make this solution generic, the encoder uses a regular expression to find any field starting with a recognized ECS workaround prefix (`error_`, `service_`, `log_`, `process_`, or `ecs_`).

The steps are:
1. Call `super.encode(event)` to produce the standard ECS JSON output.
2. Parse the JSON using Jackson for safe manipulation.
3. Extract all fields matching the workaround pattern (`error_`, `service_`, etc.) and group them by their target object.
4. Auto-resolve the `error.category` if a Throwable exists and a category wasn't explicitly provided.
5. Inject the gathered fields into their respective nested objects. If an object does not already exist, create it.
6. Serialize back to JSON bytes.

```java
# File: src/main/java/com/example/logging/CustomStructuredLogEncoder.java
package com.example.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.logging.logback.StructuredLogEncoder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class CustomStructuredLogEncoder extends StructuredLogEncoder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public byte[] encode(ILoggingEvent event) {
        byte[] original = super.encode(event);
        try {
            return postProcess(original, event);
        } catch (Exception e) {
            // Fallback to original output if processing fails
            return original;
        }
    }

    private byte[] postProcess(byte[] original, ILoggingEvent event) throws Exception {
        ObjectNode root = (ObjectNode) MAPPER.readTree(original);
        
        // 1. Extract and remove workaround fields from root
        Map<String, ObjectNode> targetObjects = new HashMap<>();
        Iterator<String> fieldNames = root.fieldNames();
        List<String> toRemove = new ArrayList<>();
        
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            
            // Check if field matches workaround pattern: error_, service_, log_, process_, ecs_
            if (fieldName.matches("(error|service|log|process|ecs)_.+")) {
                String[] parts = fieldName.split("_", 2);
                String targetObject = parts[0];
                String subField = parts[1];
                
                // Get or create target object
                ObjectNode target = targetObjects.computeIfAbsent(
                    targetObject, 
                    k -> root.has(k) ? (ObjectNode) root.get(k) : MAPPER.createObjectNode()
                );
                
                // Move field value from root to target object
                target.set(subField, root.get(fieldName));
                toRemove.add(fieldName);
            }
        }
        
        // Remove workaround fields from root
        toRemove.forEach(root::remove);
        
        // 2. Auto-resolve error.category if throwable exists and category not explicitly set
        if (event.getThrowableProxy() instanceof ThrowableProxy tp) {
            ObjectNode errorNode = targetObjects.computeIfAbsent(
                "error", 
                k -> root.has(k) ? (ObjectNode) root.get(k) : MAPPER.createObjectNode()
            );
            
            if (!errorNode.has("category")) {
                errorNode.put("category", ErrorCategoryResolver.resolve(tp.getThrowable()));
            }
        }
        
        // 3. Inject modified objects back into root
        targetObjects.forEach((name, node) -> {
            if (node.size() > 0) {
                if (root.has(name)) {
                    // Merge into existing object
                    ObjectNode existing = (ObjectNode) root.get(name);
                    node.fields().forEachRemaining(entry -> 
                        existing.set(entry.getKey(), entry.getValue())
                    );
                } else {
                    // Add new object
                    root.set(name, node);
                }
            }
        });
        
        return MAPPER.writeValueAsBytes(root);
    }
}
```

<note>

> **Note on separator customization:** This implementation uses underscore (`_`) as the separator (e.g., `error_code`), which aligns with ECS's snake_case convention. To use a different separator (e.g., colon, hyphen), modify the regex pattern in the `matches()` call and the separator string in the `split()` call. For example, to use colon: change `ecs)_` to `ecs):` and update the split logic accordingly.

</note>

### 4.3 Add the classes to the application

Copy `ErrorCategoryResolver` and `CustomStructuredLogEncoder` into your application's source tree. 

These are pure Java utility classes. Because logging initializes before the Spring application context is fully built, they must remain independent and should **not** be annotated as Spring beans (e.g., avoid `@Component`).

```text
src/main/java/com/example/logging/
├── ErrorCategoryResolver.java
└── CustomStructuredLogEncoder.java
```

### 4.4 Register the encoder in Logback configuration

Configure Logback to use your custom encoder by defining it in `logback-spring.xml`. 

You must specify the fully qualified class name in the `<encoder>` tag. Additionally, you must include the `<format>ecs</format>` property so the parent `StructuredLogEncoder` class correctly initializes the underlying ECS formatter.

```xml
# File: src/main/resources/logback-spring.xml
<configuration>
    <appender name="STRUCTURED_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <!-- Use the custom encoder class -->
        <encoder class="com.example.logging.CustomStructuredLogEncoder">
            <!-- Required: Instructs the parent class to use the ECS formatter -->
            <format>ecs</format>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="STRUCTURED_CONSOLE"/>
    </root>
</configuration>
```

## 5. How applications log errors

No further application-side setup is needed beyond registering the encoder. To log an error, the application simply attaches the exception via `.setCause(e)` and provides any dynamic custom fields using the underscore workaround. 

The custom encoder automatically intercepts these fields, nests them into the sealed objects, and removes them from the root.

<note>

> **Note:** Workaround keys (like `error_code`) are only injected if the target ECS object exists in the output. The encoder always strips these keys from the root — but if no throwable is passed via `.setCause(e)`, the `error` object is never created, so there is nowhere to inject the value. The key is removed and does not appear in the final output.

</note>

```java
# File: src/main/java/com/example/PaymentService.java
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PaymentService {

    public void processPayment() {
        try {
            // ... operation
        } catch (Exception e) {
            log.atError()
                .setCause(e)
                .addKeyValue("event.outcome", "failure")     // Does not clash, written as flat root field
                .addKeyValue("error_code", 503)              // Workaround key, injected into "error" by encoder
                .addKeyValue("error_follow_up_action", true) // true: manual intervention required; false: automatically handled
                .log("Payment failed.");
            // Note: error.category is automatically resolved from the exception type.
            // Only set error_category explicitly if the auto-resolver's mapping is incorrect for this specific case.
        }
    }
}
```

Because our generic encoder (from Section 4.2) looks for *any* field starting with `error_` and automatically injects the remaining string into the `error` object, the final output seamlessly aligns with the Log Schema:

```json
{
  "@timestamp": "2024-01-01T10:15:32.000Z",
  "log.level": "ERROR",
  "message": "Payment failed.",
  "event.outcome": "failure",
  "error": {
    "type": "java.net.ConnectException",
    "message": "Connection refused",
    "stack_trace": "...",
    "code": 503,
    "follow_up_action": true,
    "category": "network"
  }
}
```

## 6. Verification

To comprehensively verify the custom encoder, trigger a flow that logs an exception alongside both error-specific workaround keys and other ECS workaround keys (e.g., `service_system`).

```java
try {
    // ... operation
} catch (IllegalArgumentException e) {
    log.atError()
        .setCause(e)
        .addKeyValue("error_code", 400)
        .addKeyValue("error_category", "application")
        .addKeyValue("error_follow_up_action", false)
        .addKeyValue("service_system", "payment-gateway")
        .log("Invalid payment request.");
}
```

Inspect the resulting JSON log output and verify the following:

**1. Schema Compliance**
- `error.category` values match those defined in [Log_Schema.md](../Log_Schema.md)

**2. Structure and Nesting**
- `code` and `category` are correctly nested inside the `error` object.
- `system` is correctly nested inside the `service` object (the encoder should append it if `service` didn't exist, or inject it if it did).
- No `error_code`, `error_category`, or `service_system` keys remain at the root of the JSON payload.
- No duplicate or conflicting field names appear.

**2. Standard Field Preservation**
- The standard fields auto-populated by the formatter (like `error.type`, `error.message`, `error.stack_trace`, and `service.name`) are still present and unmodified.

**3. Error Category Resolution**
Trigger requests throwing different exception types to verify the auto-resolver matches the mapping rules:
- `org.springframework.web.client.HttpServerErrorException` -> `error.category` should be `"server"`
- `java.net.ConnectException` -> `error.category` should be `"network"`
- `java.sql.SQLException` -> `error.category` should be `"database"`
- `org.springframework.security.access.AccessDeniedException` -> `error.category` should be `"cert/auth"`
- `java.lang.IllegalArgumentException` -> `error.category` should be `"application"`
- `com.fasterxml.jackson.core.JsonProcessingException` -> `error.category` should be `"data"`
- Unmapped exceptions -> `error.category` should be `"others"`

**4. Explicit Override Precedence**
- If you explicitly set `.addKeyValue("error_category", "custom-category")`, verify that the final JSON contains `"category": "custom-category"` inside the `error` object, successfully overriding the auto-resolved value.

## 7. Conclusion
Apply the practices in this guide to cleanly inject dynamic custom fields into pre-sealed ECS objects, automate error categorisation, and ensure compliance with the log schema.

The post-processing approach demonstrated here handles any pre-sealed object in the ECS formatter (e.g., `error`, `service`). More broadly, it serves as a robust pattern for any scenario where an underlying log formatter restricts direct injection into nested JSON members.

### Key Takeaways

**The ECS Dotted-Key Conflict**
- **ECS pre-closes nested objects:** Spring Boot's ECS formatter seals `error`, `service`, `log`, `process`, and `ecs` objects before evaluating custom keys. Dotted keys like `error.code` attempt to reopen sealed objects, crashing the JSON writer.
- **Logstash and GELF are immune:** These formats write flat JSON without nested objects, avoiding dotted-key conflicts entirely.

**The Workaround and Implementation**
- **Use non-dot separators for dynamic fields:** Use a designated separator (like an underscore) for runtime dynamic fields (e.g., `error_code` instead of `error.code`). Spring writes these safely at the root level.
- **Post-process the output:** The custom encoder overrides `encode()` to modify the final JSON string *after* the formatter safely generates the base output.
- **Handle prefixes dynamically:** The custom encoder uses a generic regular expression to process *any* field starting with a recognized ECS prefix, ensuring scalability for future schema additions.
- **Auto-resolve error categories:** `ErrorCategoryResolver` maps exception types to standard categories (e.g., `network`, `database`), eliminating manual mapping while still allowing explicit overrides via workaround keys (e.g., `error_category`).
- **`error_` keys require a throwable:** Workaround keys like `error_code` are stripped from root and injected into the `error` object. If `.setCause(e)` is not called, no `error` object is created and the keys are silently dropped.

**Configuration and Usage**
- **Keep logging classes independent:** The encoder and resolver initialize before the Spring context. They must remain pure Java classes without Spring annotations (e.g., `@Component`).
- **Configure static fields in YAML:** Set static identifiers (e.g., `service.name`) via `logging.structured.ecs.service.*` in `application.yaml`. Do not use `addKeyValue`.
- **Register via `logback-spring.xml`:** Register the custom encoder in a `-spring` configuration file and include `<format>ecs</format>` to ensure the parent class initializes the ECS formatter.

## 8. References

Related guides:
- [Log Schema Compliance](../Log_Schema.md)
- [Structured Logging, Trace Correlation, and Context Propagation](Structured_Logging_Trace_Correlation_And_Context_Propagation.md)

Elastic:
- [Elastic Common Schema: error fields](https://www.elastic.co/guide/en/ecs/current/ecs-error.html)

Spring Boot:
- [Spring Boot: ElasticCommonSchemaStructuredLogFormatter](https://github.com/spring-projects/spring-boot/blob/main/spring-boot-project/spring-boot/src/main/java/org/springframework/boot/logging/structured/ElasticCommonSchemaStructuredLogFormatter.java)
- [Spring Boot: StructuredLogEncoder](https://github.com/spring-projects/spring-boot/blob/main/spring-boot-project/spring-boot/src/main/java/org/springframework/boot/logging/logback/StructuredLogEncoder.java)
