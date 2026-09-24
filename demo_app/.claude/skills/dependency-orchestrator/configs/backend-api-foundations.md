---
name: backend-api-foundations
description: Standard dependency graph template for backend/API services. Covers the 5 universal cross-cutting concerns every backend needs: structured logging, HTTP exception mapping, OpenAPI docs, auth service, and architectural tests & linting. Everything else (DB, schema, service layers) is derived from user stories via backwards mapping. Includes feature module definitions (MFA, notifications, file management, etc.) with scope tiers.
---

# Backend API Foundations

When used inside the dependency-orchestrator, return the Foundation Template JSON, Story Groups, and any applicable Feature Modules / Scope Tiers.

## Core Foundation Nodes

Always included in every backend/API service. These form the base DAG that all feature nodes attach to.

### Template JSON

```json
{
  "nodes": [
    { "id": "logging",           "name": "Structured Logging (request logging, correlation ID injection, log config)", "category": "backend" },
    { "id": "exception_mapping", "name": "HTTP Exception Mapping (global error handler, error response format)",       "category": "backend" },
    { "id": "openapi_docs",      "name": "OpenAPI / Swagger Configuration (base path, API docs UI)",                   "category": "backend" },
    { "id": "auth_service",      "name": "Authentication Service (schema + data access + auth logic)",                 "category": "backend" },
    { "id": "arch_tests",        "name": "Architectural Tests & Linting (ArchUnit rules, code style enforcement)",     "category": "backend" }
  ],
  "edges": []
}
```

All 5 nodes are independent of each other — they can all start Day 0 in parallel.

**Ordering rule:** These foundation nodes must complete before feature work begins:
- **`logging`**, **`auth_service`**, **`arch_tests`** → every feature node depends on these (all features need structured logging, auth gating, and must pass arch rules)
- **`exception_mapping`**, **`openapi_docs`** → only feature nodes that expose HTTP endpoints depend on these

When the orchestrator builds the DAG, it must add edges from these foundation nodes to every feature node derived by backwards mapping (respecting the HTTP distinction above).

## Story Groups

```json
{
  "story_groups": [
    {
      "id": "INFRA-BE-01",
      "title": "Backend cross-cutting infrastructure is ready",
      "owned_nodes": ["logging", "exception_mapping", "openapi_docs", "arch_tests"],
      "verifiable": "Structured logs emit with correlation IDs, errors return standardised HTTP responses, Swagger UI is accessible, arch tests pass in CI"
    },
    {
      "id": "INFRA-BE-02",
      "title": "Auth service is operational",
      "owned_nodes": ["auth_service"],
      "verifiable": "Login/logout works, sessions persist, protected endpoints reject unauthenticated requests"
    }
  ]
}
```

Tooling for each core node is configured in the [Project Configuration](#project-configuration) section at the end of this file.

### Dependency Rationale

- **logging** — structured request logging with correlation ID / trace ID injection into request context. Every subsequent feature inherits request-scoped tracing. In Spring Boot: `logback-spring.xml` + MDC filter. In Express: `pino` + middleware.
- **exception_mapping** — global HTTP exception handler that converts application exceptions into standardised error responses (status code, error body format). In Spring Boot: `@ControllerAdvice`. In Express: error middleware. Must exist before any route can return meaningful errors.
- **openapi_docs** — OpenAPI/Swagger configuration: base path, API metadata, docs UI endpoint. In Spring Boot: `springdoc-openapi` + `@OpenAPIDefinition`. Feature routes add their own annotations but the docs skeleton must exist first.
- **auth_service** — self-contained: includes its own schema, data access, and auth logic. Consult `standards.md` for which user standard applies (SSO vs standalone).
- **arch_tests** — architectural test rules (ArchUnit or equivalent) enforcing layer boundaries, naming conventions, dependency direction. Plus linting/formatting config. Runs in CI from Day 0.

**Why no `project_setup`, `env_config`, `db_setup`, or `middleware` nodes?** These are either trivial (env config is a file), assumed (project scaffolding is a prerequisite to starting any work), or feature-derived (DB setup and schema design are discovered by backwards mapping when a story needs persistence). The old `middleware` node is split into its substantive parts: `logging` and `exception_mapping`.

### Foundation Connection Points

Every feature node derived by backwards mapping depends on these foundation nodes:
- **All feature nodes** → depend on `logging`, `auth_service`, `arch_tests`
- **Feature nodes that expose HTTP endpoints** → additionally depend on `exception_mapping` and `openapi_docs`

---

## Project Configuration

Editable: change the **Recommended** column to swap tooling (must exist in `duration-defaults.md`). Do NOT edit Node IDs, Alternatives, or Edges.

### Core Foundation Tooling

| Node ID | Recommended | Alternatives |
|---|---|---|
| `logging` | logback + MDC filter (Spring Boot) | pino + middleware, Winston |
| `exception_mapping` | @ControllerAdvice (Spring Boot) | Express error middleware, NestJS exception filter |
| `openapi_docs` | springdoc-openapi (Spring Boot) | swagger-jsdoc, NestJS Swagger |
| `auth_service` | JWT auth (schema + data access + logic) | Session auth |
| `arch_tests` | ArchUnit (Spring Boot) | eslint-plugin-boundaries |

### Feature Module Reference

The backwards mapper derives these from stories. When a story's backward chain reveals a need for one, consult `standards.md` for the standard mapping and hard dependencies. Modules marked **HTTP** additionally depend on `exception_mapping` and `openapi_docs`. Pick the lowest scope tier that satisfies all consumers.

| Module | HTTP | Description | Scope Tier | Capabilities |
|---|---|---|---|---|
| `mcc_auth` | — | OAuth2 outbound credentials | `request_context` | Sync outbound calls only |
| `mcc_auth` | — | OAuth2 outbound credentials | `full` | + background client (async/batch) |
| `mpds` | — | Personnel data retrieval (outbound) | — | Minimal (no tiering) |
| `mcns_core` | — | Single SMS/email notification delivery | `minimal_sms` | SMS only, sync, no retry |
| `mcns_core` | — | Single SMS/email notification delivery | `minimal_email` | Email only, templates, no retry |
| `mcns_core` | — | Single SMS/email notification delivery | `sms_email` | SMS + email, basic retry |
| `mcns_batch` | — | Batch notification retry pipeline | `full` | Batch sends, retry queue, DLQ |
| `mfa_standalone` | — | PIN/TOTP MFA, admin-reset only | `pin_only` | PIN factor only |
| `mfa_standalone` | — | PIN/TOTP MFA, admin-reset only | `totp_only` | TOTP only, admin-reset on loss |
| `mfa_standalone` | — | PIN/TOTP MFA, admin-reset only | `full` | PIN + TOTP |
| `mfa_standalone_ct` | — | PIN/TOTP MFA + critical transaction gating | `full` | MFA + critical transaction gating |
| `mfa_cloud` | — | OTP + cloud TOTP with KMS encryption | `otp_only` | OTP factor only (SMS/email codes) |
| `mfa_cloud` | — | OTP + cloud TOTP with KMS encryption | `full` | OTP + cloud TOTP with re-provisioning |
| `mfa_cloud_ct` | — | Cloud MFA + critical transaction gating | `full` | Cloud MFA + critical transaction gating |
| `mfa_frontend_standalone` | — | React MFA module for standalone deployment | — | — |
| `mfa_frontend_mcc` | — | React MFA module for MCC deployment | — | — |
| `report_core` | **HTTP** | Fixed-schema PDF/XLSX/CSV report generation | `single_format` | One format, one template |
| `report_core` | **HTTP** | Fixed-schema PDF/XLSX/CSV report generation | `multi_format` | Multiple formats + templates |
| `report_programmatic` | **HTTP** | Dynamic columns + programmatic report generation | `full` | Dynamic columns + programmatic design |
| `interface_batch` | **HTTP** | Batch file interface + status/trigger endpoints | `inbound_only` | Single inbound file, no lock |
| `interface_batch` | **HTTP** | Batch file interface + status/trigger endpoints | `outbound_only` | Single outbound file |
| `interface_batch` | **HTTP** | Batch file interface + status/trigger endpoints | `full` | Inbound + outbound + ShedLock |
| `file_mcc` | **HTTP** | File upload/download on AWS | `upload_only` | Upload + basic validation (AWS) |
| `file_mcc` | **HTTP** | File upload/download on AWS | `full` | + S3 clean store + SFS virus scan |
| `file_standalone` | **HTTP** | File upload/download local | `upload_only` | Upload + basic validation (local) |
| `file_standalone` | **HTTP** | File upload/download local | `full` | + storage quota + local promotion |

### Infrastructure Addition Tooling

The backwards mapper derives these from stories (e.g., a story mentioning "cache invalidation" → `caching_layer`).

| Node ID | Description | Recommended | Alternatives | Edges |
|---|---|---|---|---|
| `caching_layer` | Redis / caching | Redis (Lettuce / ioredis) | Memcached, Caffeine (in-process) | feature nodes → `caching_layer` as needed |
| `event_queue` | Event-driven / async messaging | Apache Kafka | SQS, RabbitMQ | feature service → `event_queue` |
| `monitoring_alerting` | Observability | Prometheus + Grafana | Datadog, New Relic | no predecessors (Day 0) |
