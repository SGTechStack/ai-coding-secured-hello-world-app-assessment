---
name: logging-patterns
description: Backend Java logging conventions. ECS structured logging, @Slf4j, MDC request tracing, and AI-friendly log analysis via logs/log.json. Use when user asks about logging, structured logs, request tracing, or analyzing log output.
---

# Logging Patterns (Backend)

Logging conventions for the `backend/` Spring Boot 4 application. This project uses ECS (Elastic Common Schema) structured logging to file and human-readable console output.

## When to Use

TRIGGER when:

- User says "add logging", "improve logs", "structured logging"
- User asks about MDC, request tracing, or correlation IDs
- User wants to analyze `logs/log.json` or asks "what happened in this request"
- User runs `jq` on log files
- Adding logging to new backend services/controllers

SKIP:

- Frontend/React debugging
- Test failures unrelated to log output
- General "debug this" without logging context

---

## Current Setup

```
Console  → human-readable (default Spring format) — for local dev
File     → ECS JSON at ./logs/log.json             — for AI analysis & production
```

Configured in `application.properties`:

```properties
logging.structured.format.file=ecs
logging.file.name=./logs/log.json
logging.level.root=INFO
```

### ECS Field Reference

```json
{
  "@timestamp": "2026-01-29T10:15:30.123Z",
  "log.level": "INFO",
  "message": "Order created",
  "ecs.version": "8.11",
  "service.name": "demo-backend",
  "process.thread.name": "virtual-123",
  "log.logger": "org.eds.demo.user.application.UserService",
  "requestId": "req-abc123",
  "userId": "user-789"
}
```

---

## Adding Logging to New Code

### 1. Declare the Logger

```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OrderService {
    // `log` is available automatically
}
```

### 2. Level Guidelines

| Level | Use for                                 | Example                                |
| ----- | --------------------------------------- | -------------------------------------- |
| ERROR | Unexpected failures requiring attention | External service down, data corruption |
| WARN  | Recoverable issues, degraded state      | Retry succeeded, fallback used         |
| INFO  | Business events, state transitions      | User created, order completed          |
| DEBUG | Flow details useful during development  | Method entry/exit, intermediate values |

### 3. Parameterized Messages

```java
// Always use placeholders — never concatenate
log.info("User registered: userId={}", user.getId());
log.debug("Processing request: path={}, method={}", path, method);

// Guard expensive computations
if (log.isDebugEnabled()) {
    log.debug("Full payload: {}", objectMapper.writeValueAsString(payload));
}
```

### 4. Structured Key-Value Pairs

For fields that should appear as distinct ECS JSON keys (searchable, filterable):

```java
import static net.logstash.logback.argument.StructuredArguments.kv;

log.info("Order created",
    kv("orderId", order.getId()),
    kv("userId", user.getId()),
    kv("total", order.getTotal()),
    kv("step", "order_created"));
```

Use structured fields when:

- The value is something you'd filter/aggregate on (IDs, amounts, status codes)
- You want AI to extract it without parsing the message string

---

## MDC Request Tracing

### Filter Setup

Place in `backend/src/main/java/org/eds/demo/config/`:

```java
package org.eds.demo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTracingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_REQUEST_ID = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

### Standard MDC Keys

| Key         | Source                                  | Purpose                                  |
| ----------- | --------------------------------------- | ---------------------------------------- |
| `requestId` | `X-Request-Id` header or generated UUID | Correlate all logs from one HTTP request |
| `userId`    | Set after authentication resolves       | Track which user triggered the action    |

MDC values automatically appear as top-level fields in ECS JSON output.

---

## Reading Logs with AI / jq

The structured log file is at `./logs/log.json` (relative to backend working dir).

```bash
# Recent errors
jq 'select(.["log.level"] == "ERROR")' backend/logs/log.json | tail -20

# Follow a specific request
jq 'select(.requestId == "req-abc123")' backend/logs/log.json

# Find slow operations (if duration_ms is logged)
jq 'select(.duration_ms > 1000)' backend/logs/log.json

# Errors in the last 5 minutes
jq --arg since "$(date -u -v-5M +%Y-%m-%dT%H:%M:%S)" \
  'select(.["@timestamp"] > $since and .["log.level"] == "ERROR")' \
  backend/logs/log.json

# Group by logger
jq -s 'group_by(.["log.logger"]) | map({logger: .[0]["log.logger"], count: length})' \
  backend/logs/log.json
```

### AI Analysis Workflow

1. Read `backend/logs/log.json` with `jq` to filter relevant entries
2. Use `requestId` to follow a single request across services/layers
3. Check `log.level` for errors, then read surrounding context by timestamp
4. Look at `log.logger` to identify which class/layer failed

---

## Profile-Specific Logging

| Profile        | Console                 | File     | Notes                  |
| -------------- | ----------------------- | -------- | ---------------------- |
| `local`        | Human-readable          | ECS JSON | Default dev experience |
| `test`         | Root=WARN               | —        | Quiet test output      |
| `unsafe-debug` | Verbose (SQL, Security) | ECS JSON | Add for deep debugging |

To enable verbose logging locally:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local,unsafe-debug
```
