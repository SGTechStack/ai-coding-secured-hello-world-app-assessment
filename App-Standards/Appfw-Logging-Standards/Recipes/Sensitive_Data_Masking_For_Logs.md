# Sensitive Data Masking for Logs

## 1. Introduction
Logs can contain identifiers and operational context that should not be written in clear text. This guide explains how to prevent sensitive data from entering logs, apply masking in Logback when required, and verify that the resulting log output is safe for centralized storage.

## 2. Prerequisites
- Spring Boot 4.0+

## 3. Key Terminology

- **Prevention**: Exclude sensitive data before logging (most effective)
- **Redaction**: Completely remove sensitive content from logs
- **Masking**: Replace sensitive data with placeholders (e.g., `****`)
- **Partial Masking**: Show part of the data for validation (e.g., last 4 digits of a card)
- **Sanitization**: Remove characters that enable log injection attacks (carriage returns, line feeds)

## 4. Define what must not be logged
Follow the organization's data protection policy to determine what cannot be logged. Common regulatory frameworks that inform these policies include GDPR (personal data), HIPAA (protected health information), PCI DSS (payment card data), and SOX (audit trail requirements). Consult your organization's compliance and legal teams for specific requirements applicable to your system.

When in doubt, treat data as sensitive until its classification is confirmed.

Common examples of data that must not be logged:

- Passwords, API keys, authentication tokens, and credentials
- Session identifiers, JWTs, OAuth tokens, and refresh tokens
- Database connection strings with embedded credentials (externalize credentials via secrets management)
- Encryption keys, private keys, certificates, and keystores
- Full payment card data (Primary Account Number), Card Verification Value (CVV), and financial account numbers
- Government-issued identifiers (Social Security Number (SSN), passport, driver's license numbers)
- Personal health information and biometric data
- Personally identifiable information (PII): email addresses, phone numbers, physical addresses, full names (without explicit consent and lawful basis)
- Security questions and answers
- Full filesystem paths revealing deployment structure (e.g., error messages referencing `/opt/deploy/company-secrets/`)

## 5. Prevent sensitive values from being logged
The safest approach is to prevent sensitive data from reaching the logging layer in the first place. Once a value is written to a log, it may be stored in centralized log platforms, shipped to third-party services, or accessed by operators who should not see it. Masking after the fact is a fallback — prevention at the source is the primary control.

The most common source of accidental sensitive data in logs is logging objects directly. A domain object passed to `addKeyValue` will serialize all of its fields, including ones that are safe alongside ones that are not.

```java
# File: src/main/java/com/example/PaymentService.java
// AVOID: payment contains card number, CVV, and billing address
log.atInfo()
    .addKeyValue("payment", payment)
    .log("Payment processed.");
```

The fix is to log only the specific fields that are known to be safe. This requires thinking about each field individually rather than passing the whole object.

```java
# File: src/main/java/com/example/PaymentService.java
// PREFER: only safe, well-defined fields are logged
log.atInfo()
    .addKeyValue("correlation.id", payment.getCorrelationId())
    .addKeyValue("event.action", "payment-process")
    .addKeyValue("event.outcome", "success")
    .log("Payment processed.");
```

The same principle applies to any object that may contain mixed-sensitivity data. If these fields are needed across multiple log entries, consider using Mapped Diagnostic Context (MDC) (see [Enriching Logs with MDC](Enriching_Logs_With_MDC.md)).

```java
# File: src/main/java/com/example/UserService.java
// AVOID: user object may contain SSN, email, date of birth
log.atInfo()
    .addKeyValue("user", user)
    .log("Profile updated.");

// PREFER: log only the identifier that is needed for correlation
log.atInfo()
    .addKeyValue("user.id", user.getId())
    .addKeyValue("event.action", "profile-update")
    .addKeyValue("event.outcome", "success")
    .log("Profile updated.");
```

> **Note:** Always use `user.id` (UUID) for user identity in logs. UUIDs are non-PII and directly debuggable. Never log raw usernames, email addresses, or other PII as user identifiers.

Do not log without careful inspection:

- Raw request/response objects (may contain credentials in headers)
- Domain objects with sensitive fields
- Exception messages and stack traces (may expose URLs, parameters, or business logic)
- HTTP headers, query parameters, and URL query strings
- Full request/response bodies without sanitization

> **Production Log Levels:** Disable `DEBUG` and `TRACE` log levels in production environments. These verbose levels often log full request/response payloads, detailed stack traces, and internal state that may inadvertently expose sensitive data. Use `INFO` level for operational logging and `WARN`/`ERROR` for actionable issues.

### Exception Handling
Exception messages and stack traces may expose infrastructure details such as hostnames, ports, database names, and file paths. However, these details are acceptable in access-controlled production logs because they provide critical diagnostic value for troubleshooting.

The primary threat is credentials appearing in stack traces. Prevent this by externalizing credentials via secrets management (environment variables, HashiCorp Vault, AWS Secrets Manager) so connection strings never contain passwords or API keys.

Always use `.setCause(e)` to capture full stack traces for production diagnostics:

```java
# File: src/main/java/com/example/PaymentService.java
// PREFER: always include .setCause() for diagnostic value
} catch (SQLException e) {
    log.atError()
        .setCause(e)
        .addKeyValue("error_code", 503)
        .addKeyValue("error_category", "database")
        .addKeyValue("error_follow_up_action", true)
        .log("Database connection failed.");
    // Stack trace captured for root cause analysis
    // Credentials prevented by externalizing database passwords via secrets management
}
```

For comprehensive exception logging guidance including how to preserve exception cause chains, see [Logging Exceptions with Enhanced Details](Logging_Exceptions_With_Enhanced_Details.md).

### Sanitization to Prevent Log Injection
User-controlled input can contain characters that break log parsing or inject false log records. A value like `"login\nERROR admin password=secret"` written directly to a log file inserts a second line that looks like a real log entry. Structured JSON output mitigates this for most parsers, but removing control characters before logging is still the safer approach.

```java
# File: src/main/java/com/example/UserService.java
// AVOID: user input written directly to a log field
log.atInfo()
    .addKeyValue("event.action", request.getParameter("action"))
    .log("User action recorded.");

// PREFER: sanitize before logging
String userAction = request.getParameter("action");
String sanitized = userAction.replaceAll("[\\r\\n|]", "");
log.atInfo()
    .addKeyValue("event.action", sanitized)
    .log("User action recorded.");
```

### Database and External Logging
Do not actively log database connection strings with credentials, SQL statements, or query payloads as structured fields. SQL statements may expose sensitive data values in where clauses or parameters. Database connection strings must use externalized credentials (environment variables, secrets management) to prevent passwords from appearing in logs. For SQL diagnostics, use database audit tooling or enable Object-Relational Mapping (ORM)/Java Persistence API (JPA) logging only in non-production environments, with parameter values redacted.

Infrastructure details (hostnames, database names, ports) that appear passively in exception stack traces are acceptable in access-controlled production logs, as they provide critical diagnostic value.

### Strategies for Safe Logging
If sensitive values must be logged, choose one of these approaches:

1. **Exclude entirely**: Skip the sensitive field
2. **Replace with identifier**: Use `correlation.id` or transaction ID instead of actual value
3. **Use summary only**: Log `event.outcome` (success/failure) instead of the value
4. **Sanitize parts**: Log only non-sensitive portions of complex objects
5. **Dedicated objects**: Create data transfer objects (DTOs) with only safe fields

## 6. Mask sensitive fields in Logback
This section is only relevant if the application uses `logstash-logback-encoder` as its Logback encoder. If the application does not use this encoder, skip this section and rely on prevention at the source as described in section 5.

Prevention catches the cases you know about — the fields you own, the objects you control. Masking addresses the cases that slip through: a library that logs a URL with query parameters, a framework that serializes a request object, or a configuration property that appears in an error message. The masking layer sits between the logging code and the output, replacing matched field values before they are written.

This is a second line of defense, not a substitute for prevention. If a field is masked in Logback but logged by ten services, fixing the source is more reliable than maintaining masking configuration across all of them. Apply masking for fields where prevention is not feasible.

This example uses the Logstash Logback Encoder with `MaskingJsonGeneratorDecorator` for structured JSON logs.

Add the dependency to your project:

```xml
# File: pom.xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
</dependency>
```

Configure masking by field path in your Logback configuration:

```xml
# File: src/main/resources/logback-spring.xml
<configuration>
    <appender name="STRUCTURED_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <jsonGeneratorDecorator class="net.logstash.logback.decorate.CompositeJsonGeneratorDecorator">
                <decorator class="net.logstash.logback.mask.MaskingJsonGeneratorDecorator">
                    <defaultMask>****</defaultMask>
                    <path>url.query</path>
                    <!-- Add additional field paths only when policy requires masking -->
                </decorator>
            </jsonGeneratorDecorator>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="STRUCTURED_CONSOLE"/>
    </root>
</configuration>
```

> **Note:** Mask by field path (not by value) for precise control and performance. Add only field paths required by policy, matching schema field names (e.g., `url.query`, `url.path`, `error.message`). For partial masking using regex patterns (e.g., showing last 4 digits) or custom masking logic, see the [Logstash Logback Encoder documentation](https://github.com/logfellow/logstash-logback-encoder#masking-json-generator-decorator).

### Important Limitations
Masking applies to JSON fields only, **not** to the log message text. Therefore:

- Exclude sensitive data from the log `message` field during logging
- Exclude sensitive data from ad-hoc structured fields
- Sanitize all error-related fields before logging (errors can expose sensitive context, code paths, and implementation details)

## 7. Verify masking behavior
After implementing masking, verify it works as expected in non-production and production environments.

### Manual Testing
Trigger requests that include sensitive values covered by your masking configuration:

```java
# File: src/test/java/com/example/LogMaskingTest.java
@Test
void testUrlQueryMasking() {
    // Send request with sensitive query parameter
    String response = restTemplate.getForObject(
        "http://localhost:8080/api/users?api_key=sk_live_abc123xyz789&status=active",
        String.class
    );

    // Verify log output
    String logs = /* read from log file or capture */;
    assertThat(logs).contains("url.query").contains("****");
    assertThat(logs).doesNotContain("sk_live_abc123xyz789");
}
```

### Verification Checklist
- Masked fields show `****` (or your mask) instead of raw values
- Sensitive values absent from the log `message` field
- Unmasked fields remain unchanged and readable
- Same masking rules apply to all output locations
- Configuration covers all expected sensitive field paths
- No sensitive data in related fields (e.g., if masking `url.query`, check `url.path`, `message`, `error.message`)
- Exception stack traces do not contain credentials (passwords, API keys, tokens)—infrastructure details (hostnames, ports, database names) are acceptable
- DEBUG and TRACE log levels are disabled in production environments
- User identifiers use `user.id` (UUID) — no raw usernames, emails, or hashed identifiers in logs

## 8. Conclusion
This guide explained how to prevent sensitive data from entering logs, apply masking as a secondary control, and verify that the resulting log output is safe for centralized storage.

### Key Takeaways
- **Prevention first**: Exclude sensitive data at the source before logging
- **Control log levels**: Disable DEBUG and TRACE in production to prevent verbose output exposing sensitive data
- **Sanitize input**: Remove log injection attack vectors from user input
- **Handle exceptions carefully**: Always use `.setCause(e)` to capture stack traces for diagnostics; prevent credentials from appearing via secrets management
- **Use explicit fields**: Log only safe, well-defined fields instead of raw objects
- **Mask as backup**: Apply field-based masking for sensitive fields that escape prevention
- **Verify compliance**: Test that sensitive data is absent from log output and masking rules work as expected

With prevention and masking controls in place, logs remain useful for operations while protecting sensitive data.

## 9. References

Related guides:
- [Log Schema](../Log_Schema.md)
- [Logging Exceptions with Enhanced Details](Logging_Exceptions_With_Enhanced_Details.md)
- [Logging Authentication and Authorization Events](Logging_AuthN_And_AuthZ_Events.md)
- [Enriching Logs with MDC](Enriching_Logs_With_MDC.md)

Spring Boot:
- [Spring Boot Reference: Logging](https://docs.spring.io/spring-boot/reference/features/logging.html)

Logback:
- [Logstash Logback Encoder: MaskingJsonGeneratorDecorator](https://github.com/logfellow/logstash-logback-encoder#masking-json-generator-decorator)

Further reading:
- [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)
- [Best Logging Practices for Safeguarding Sensitive Data](https://betterstack.com/community/guides/logging/sensitive-data/)
- [AWS CloudWatch: Mask Sensitive Log Data](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/mask-sensitive-log-data.html)
