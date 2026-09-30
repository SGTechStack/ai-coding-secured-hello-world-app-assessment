# INFRA-BE-01: Backend cross-cutting infrastructure is ready

`infrastructure` · wave 1

| Effort | Float |
| --- | --- |
| 2.0 days | 0.5 days |

## Acceptance criteria

- Structured logs emit with a correlation ID on every request.
- Application exceptions are converted to RFC 9457 `ProblemDetail` responses with a stable shape.
- Swagger UI / OpenAPI docs are reachable in the `dev` profile.
- ArchUnit layering tests pass.

## Dev tasks

1. `logging` (0.5 d) — logback config, `CorrelationIdFilter` populating MDC, `AuditLogger` for security events.
2. `exception_mapping` (0.25 d) — `@RestControllerAdvice` mapping validation, conflict, auth, throttle and generic errors.
3. `openapi_docs` (0.25 d) — springdoc dependency + `OpenApiConfig`.
4. `be_arch_tests` (0.5 d) — ArchUnit rules: controllers do not touch repositories, no entity leaves the web layer.

## Dependencies

- Blocked by: —
- Unblocks: 1, 2, 5, 6, 8, 12
