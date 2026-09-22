# App Standard: Structured Logging — Implementation Questions

Answer these questions with your team or product owner before implementation to determine which logging requirements apply to your application.

---

## 1. Application Context

### Q1: What is your application's primary architecture pattern?

**Select the pattern that best describes your application:**

- [ ] **REST API / Web Service** - Synchronous request/response operations
- [ ] **Batch Processing** - Scheduled or triggered batch jobs (Spring Batch, @Scheduled)
- [ ] **Event-Driven Consumer** - Consumes messages from queues/topics (Kafka, RabbitMQ, SQS)
- [ ] **Hybrid** - Combination of patterns above

> **Why this matters:** This question orients your team to the application's context before addressing specific logging requirements. Questions Q9-Q13 will determine which capabilities apply to your application.

---

## 2. Authentication & Authorization

### Q2: Does your application handle user authentication?

- [ ] **Yes** - Users authenticate with credentials, SSO, API keys, or certificates
- [ ] **Service-to-Service only** - Uses mutual TLS, service accounts, or API keys for machine-to-machine auth
- [ ] **No** - Authentication is handled upstream (API Gateway, OAuth proxy, service mesh)

**If YES or Service-to-Service only:**

**Required logging (§2.2):**
- **On success**: Log `user.id` UUID, authentication method, timestamp, MFA factor if used
- **On failure**: Log at WARN with timestamp and generic message ("Authentication failed")
- **Never log**: Credentials, passwords, tokens, reset links, raw usernames/emails, internal exception messages, or information indicating account existence

> **Recommendation:** Use `user.id` UUID for all user identity logging — UUIDs are non-PII and directly debuggable. Extract authentication method from your framework. Use identical error messages for all failure types. Include source IP only if required for security monitoring (consider privacy regulations).

> **If YES, proceed to Q3-Q6. If NO, skip to Q7.**

---

### Q3: Credential Management - What credential-related security features does your application handle?

**Select all that apply:**

- [ ] **Multi-Factor Authentication (MFA)** - Users enroll and verify MFA factors
- [ ] **Password changes/resets** - Users can change passwords or reset via self-service/admin
- [ ] **API key rotation** - Users or systems can rotate API keys
- [ ] **None** - All credential management handled by external identity provider

**Required logging (§2.2):**

**If MFA selected:**
- **MFA success**: Log factor type (e.g., `totp`, `sms-otp`, `hardware-token`, `push-notification`)
- **MFA enrollment/removal**: Log `user.id` UUID, factor type, outcome (`enrolled`/`removed`)
- **Never log**: OTP codes, TOTP secrets, backup codes, recovery phrases

**If password changes/resets selected:**
- **Password operation**: Log `user.id` UUID, credential type, outcome (success/failure)
- **Never log**: Old credential, new credential, reset token, temporary password

**If API key rotation selected:**
- **Key rotation**: Log key ID (not key value), rotation outcome, `user.id` UUID

---

### Q4: Does your application perform authorization checks?

- [ ] **Yes** - Checks user permissions, roles, or entitlements before granting access
- [ ] **No** - All authenticated users have the same access level

**If YES:**

**Required logging (§2.2):**
- **Authorization success**: Log ONLY for sensitive operations (admin actions, data exports, permission changes, cross-privilege-boundary access)
- **Authorization failure**: Always log at WARN with `user.id` UUID and generic reason (e.g., "Access denied", "Insufficient permissions")
- **Never expose**: Internal permission logic, role names, or policy details (e.g., ❌ "User lacks ADMIN_SUPER_USER role", ❌ "Requires ROLE_MANAGER")

> **Why this matters:** Logging every authorization check creates massive log volume with little security value. Log authorization failures and sensitive operation successes only.

---

### Q5: Does your application manage user sessions?

- [ ] **Yes** - Stateful sessions with session IDs
- [ ] **No** - Stateless (JWT tokens, no server-side session)

**If YES:**

**Required logging (§2.2, §3.4):**
- **Session lifecycle**: Log session start, end, logout, timeout events with `user.id` UUID
- **Session failures**: Log session hijacking attempts, invalid session tokens
- **Never log**: Session IDs or access tokens (in any form, including hashed)

> **Why this matters:** Even hashed session IDs enable session tracking. Log lifecycle events with user identifier for correlation, never the session identifier itself.

---

## 3. Business Operations

### Q6: Does your application make significant business decisions?

Examples: applying discount rules, approving/rejecting transactions, selecting calculation strategies, enforcing credit limits, dynamic pricing

- [ ] **Yes**
- [ ] **No**

**If YES:**

**Required logging (§2.3):**
- **Key decisions**: Log at INFO which path was taken and why (e.g., "Premium pricing applied", "Credit check passed")
- **Include**: Entity ID and decision reason (e.g., feature flag value, user tier, threshold crossed)
- **When NOT to log**: Routine internal logic (null checks, validation), trivial conditionals, decisions occurring >100 times/min

> **Log decisions that** cross significant business thresholds, trigger irreversible actions, have compliance implications, or support dispute resolution. Do NOT log internal calculation steps or routine conditionals.

---

### Q7: Does your application create, update, or delete core business entities?

Examples: user accounts, orders, transactions, contracts, financial records, invoices

- [ ] **Yes**
- [ ] **No**

**If YES:**

**Core entity criteria (§2.4):**

An entity qualifies as "core" if it meets **one or more** of: audit compliance requirements, involved in business decisions (Q6), used for dispute resolution, or has financial/legal significance.

**Exclude from logging:** Entities modified >100 times/min (log aggregates instead), transient entities, derived/computed entities, housekeeping fields.

**Required logging (§2.4):**
- **On entity operation**: Log at INFO with entity type, entity ID, operation type (e.g., "User account created with user UUID: abc-123")
- **Never log**: Field values, full entity payloads, or SQL statements in production

> **Threshold guidance:** If an entity is modified >100 times/min, log aggregate counts. For updates, log only status changes or significant field changes.

---

### Q8: Does your application provide privileged access to or export of sensitive data?

Examples: Admin viewing user PII, auditor exporting financial records, bulk data exports

- [ ] **Yes**
- [ ] **No**

**If YES:**

**Required audit logging (§3.4):**
- **Privileged access**: Log when users with elevated permissions access sensitive data (admin viewing another user's account, support accessing customer records)
- **Bulk exports**: Log report downloads, CSV exports, API bulk queries
- **Include**: `user.id` UUID, resource type, count or scope (e.g., "Exported 1,000 user records")
- **Never log**: Actual data values, record contents, or query results

> **Why this matters:** Privileged access and bulk exports are high-risk operations for data breaches and compliance violations. Audit logs provide evidence for forensic investigation and regulatory requirements.

---

## 4. External Systems

### Q9: Does your application call external HTTP services?

Examples: third-party REST APIs, partner services, downstream microservices

- [ ] **Yes**
- [ ] **No**

**If YES:**

**Required logging (§2.6):**
- **Before call**: Log base URL and path, HTTP method, start time. Do NOT log: URL query parameters, request body, auth headers
- **On success**: Log HTTP status code, duration
- **On failure**: Log duration, status code, all error fields
- **Inject correlation ID** into outbound request headers (e.g., `X-Correlation-ID`, `traceparent`)

**Error handling strategy (§2 - Failure Paths):**
- **Retryable errors** (503, timeouts): Log at ERROR, indicate retry will occur, log each retry attempt with attempt number
- **Permanent errors** (400, 401, 403, 404): Log at ERROR, do NOT retry

**Resilience patterns:**

Do you use circuit breakers or fallbacks? (e.g., Resilience4j, Spring Cloud Circuit Breaker)
- [ ] **Yes**
- [ ] **No**

**If YES:** Log circuit breaker state changes (open/closed/half-open) and fallback invocations at WARN.

---

### Q10: Does your application connect to non-HTTP external systems?

Examples: SFTP servers, message brokers (Kafka, RabbitMQ), databases (connection-level), LDAP

- [ ] **Yes**
- [ ] **No**

**If YES:**

**Required logging (§2.6):**
- **On connection established**: Log outcome, duration
- **On connection failure**: Log duration, all error fields
- **Never log**: Host, URL, database name, credentials, or connection strings (use environment-configured identifiers instead)

> **Recommendation:** Configure system identifiers as environment variables (e.g., `DB_IDENTIFIER=primary-db`) and log the identifier, not the hostname (e.g., "Connection to primary-db in 45ms" not "Connection to prod-db-01.internal:3306").

---

## 5. Async & Batch Processing

### Q11: Does your application consume messages from queues or topics?

Examples: Kafka, RabbitMQ, SQS, Azure Service Bus

- [ ] **Yes**
- [ ] **No**

**If YES:**

**Required logging (§2.5):**
- **On message received**: Extract correlation ID from headers, set in MDC. Log message ID, queue/topic name
- **On processing complete**: Log outcome (success/failure), duration
- **On acknowledgment**: Log ACK or NACK outcome
- **On dead letter**: Log reason, final attempt count, all error fields
- **After processing**: Clear MDC in `finally` block
- **Never log**: Message body/payload (may contain sensitive data)

> **Implementation note:** Extract correlation ID from message headers (e.g., `X-Correlation-ID`, `traceparent`, `X-Request-ID`). Generate UUID if not present.

**Do you have a dead-letter queue configured?**
- [ ] **Yes**
- [ ] **No**

---

### Q12: Does your application run scheduled batch jobs?

Examples: Spring Batch, @Scheduled tasks, cron jobs

- [ ] **Yes**
- [ ] **No**

**If YES:**

**Required logging (§2.5):**
- **At application startup**: Log each job's schedule (job name, cron expression, human-readable description, timezone)
- **Before job execution**: Set job context in MDC (job ID, job name, retry attempt count). Log input parameter names (not values if they may contain PII/secrets)
- **After job completion**: Log outcome (success/failure), duration. For Spring Batch, log item counts (read, written, skips, rollbacks). If processing files, log file name, size, creation time, integrity check results. Clear MDC in `finally` block

**Do any jobs process files?**
- [ ] **Yes**
- [ ] **No**

**If YES, what integrity checks are performed?**
- [ ] Hash validation (e.g., SHA-256 checksum)
- [ ] Digital signature validation
- [ ] File size validation
- [ ] Other: _________________

> **Anti-pattern to avoid:** Logging inside item processing loops generates massive volume. Log aggregate counts at step completion instead (e.g., "Processed 1,000 items: 800 success, 200 failed").

---

### Q13: Does your application use @Async background tasks?

Examples: @Async methods, CompletableFuture, async Spring components

- [ ] **Yes**
- [ ] **No**

**If YES:**

**Required implementation (§4):**
- **Register TaskDecorator**: Copy MDC across async thread boundaries
- **Propagate correlation ID**: From parent thread to async thread
- **Clear MDC**: After async task completes

> **Why this matters:** Micrometer Context Propagation auto-propagates `traceId` and `spanId` but NOT custom MDC fields. A `TaskDecorator` is required for custom fields.

---

## 6. Error Handling, Security & Observability

### Q14: How does your application handle validation errors?

Examples: missing required fields, invalid email format, out-of-range values

- [ ] **Client input validation** (e.g., Bean Validation, @Valid)
- [ ] **Business rule validation** (e.g., insufficient funds, duplicate order)
- [ ] **No explicit validation** (accepts all input)

**Required logging (§2 - Failure Paths):**
- **Client validation failures**: Log at WARN if retryable, ERROR if definitive/non-retryable. Never log user-submitted values (may contain sensitive data)
- **Business rule violations**: Log at ERROR with rule that failed (generic), entity ID, outcome

> **Recommendation:** Log client validation failures at WARN once per request with field names only. Log business rule violations at ERROR with entity ID and generic reason. For batch validation, log aggregate counts at completion.

---

### Q15: Does your application retry failed operations?

Examples: retrying external API calls, message processing, database transactions

- [ ] **Yes** - Implements retry logic for transient failures
- [ ] **No** - Fails immediately without retry

**If YES:**

**Required logging (§2 - Failure Paths):**
- **First failure**: Log at ERROR with all error fields, indicate retry will occur
- **Each retry attempt**: Log at WARN with attempt number and reason
- **Final failure**: Log at ERROR after max retries exhausted (extract max retry count from retry configuration), include total attempts
- **Retry success**: Log at INFO when retry succeeds, include attempt number

> **Note:** Extract retry count and backoff strategy from your retry configuration at runtime, not hard-coded values.

---

### Q16: Does your application have a global exception handler?

- [ ] **Yes** - @ControllerAdvice, @ExceptionHandler, or framework-level handler
- [ ] **No** - Relies on framework default error handling

**Required implementation (§4):**
- **Implement global exception handler**: Catch and log unexpected exceptions at the boundary
- **Log once**: At the handling point - do NOT log and rethrow at multiple levels
- **Map error categories**: Assign each error type to `server`, `network`, `cert/auth`, `database`, `application`, `data`, or `others`
- **Include all error fields**: As defined in the standard
- **Return generic error messages**: To clients (do NOT expose stack traces or internal details)

> **Why this matters:** Mapping errors to standard categories enables effective filtering and alerting. Example category mappings: `USER_NOT_FOUND` → `data`, `PAYMENT_GATEWAY_TIMEOUT` → `network`, `DATABASE_CONNECTION_FAILED` → `database`.

---

### Q17: What is the data classification level of your application?

- [ ] **Public** - No sensitive data
- [ ] **Internal** - Business data, no PII
- [ ] **Confidential** - Contains PII, financial data, or business secrets
- [ ] **Highly Restricted** - Regulated data (GDPR, HIPAA, PCI-DSS)

> **Classification guide:**
> - Contains PII (names, emails, phone numbers, addresses)? → **Confidential**
> - Subject to GDPR, HIPAA, PCI-DSS regulations? → **Highly Restricted**
> - Only business data, no user information? → **Internal**
> - Publicly accessible data? → **Public**
>
> **Default:** If unsure, assume **Confidential** and implement hashing + masking. Easier to relax later than retrofit after a data exposure.

**For Confidential and Highly Restricted:**

**Required security logging (§3.3, §3.5):**
- **Never log sensitive data directly** - Credentials, payment data, health records, PII, tokens, secrets
- **Use `user.id` UUID** for user correlation — never log raw usernames, emails, or hashed PII
- **Implement automated masking** at the logging boundary for sensitive field name patterns (e.g., `password`, `token`, `ssn`, `cardNumber`)
- **Use pseudonymization or tokenization** where required by regulation
- **Protect logs at rest** (WORM storage, cryptographic signatures for audit trails)
- **Transmit logs securely** (TLS to centralized log system)

**Masking format:**
- [ ] **Complete masking** - Replace entire value with `***MASKED***` (recommended)
- [ ] **Partial visibility** - Show first/last N characters (only for industry-standard cases: last 4 of card, last 4 of SSN)

> **Why this matters:** Framework-level masking provides defense-in-depth. Without it, developers must sanitize at every log call site (error-prone).

---

### Q18: Does your application accept user-generated input that appears in logs?

Examples: usernames, search queries, file names, error messages from users

- [ ] **Yes**
- [ ] **No**

**If YES:**

**Required security controls (§3.5):**
- **Sanitize user input**: Before logging to prevent log injection (CWE-117)
- **Strip CRLF characters**: (`\r`, `\n`) to prevent log forging
- **Encode control characters**: That could corrupt log structure
- **Apply automated masking**: At the logging boundary

> **Best practice:** Log user input in structured fields (e.g., `user.search_query="..."`) not in message text.
>
> **Why this matters:** Log injection allows attackers to embed forged log entries. Example: User input `"admin\n2026-06-05 ERROR Access granted"` creates fake ERROR entry without sanitization.

---

### Q19: What correlation strategy will your application use?

**Step 1: Distributed Tracing - Do you need to correlate logs across multiple services?**

- [ ] **Yes** - Multi-service architecture requiring end-to-end trace correlation
- [ ] **No** - Single service or no tracing requirement
- [ ] **Unsure** - Need to evaluate

**Evaluation criteria (if Unsure):** You need distributed tracing if:
- Your application calls other microservices or backends as part of a request
- You need to trace a user request across multiple services
- You have difficulty correlating logs across services during incidents

You do NOT need distributed tracing if:
- Your application is a standalone service with no downstream dependencies
- All operations are self-contained within one service

**If YES:**

Distributed tracing provides `traceId` and `spanId` in logs for correlation across service boundaries.

> **Why this matters:** A single request may span 5-10 services. Without trace IDs, correlating logs requires matching timestamps and user IDs (error-prone). Trace IDs provide one identifier connecting all entries across the request path.

---

**Step 2: Custom Correlation IDs - Do you need to correlate with external systems or multi-request workflows?**

Examples: business process IDs, request IDs from upstream systems, transaction IDs

- [ ] **Yes** - Need to propagate custom correlation fields
- [ ] **No** - Trace ID and span ID are sufficient (if using distributed tracing from Step 1)

**When to use custom correlation IDs:**
- Upstream systems (API gateway, load balancer) generate request IDs that must be preserved
- Business processes span multiple user requests (e.g., multi-step workflow, saga pattern)
- Integration with external partners requires specific correlation headers

**When trace ID is sufficient:**
- Single-request operations within your service mesh
- All services in the call chain support W3C Trace Context
- No requirement to correlate with external partner systems

**If YES:**

**Required implementation (§4):**
- Register MDC filter to extract correlation ID from request header
- Generate correlation ID if not present in request
- Set correlation ID in MDC at request start
- Clear MDC in `finally` block at request end
- Propagate to outbound calls via request headers

**Implementation note:** Extract correlation ID from upstream headers (e.g., `X-Correlation-ID`, `X-Request-ID`). Generate a UUID if not present.

> **Recommendation:** Always generate a correlation ID if upstream doesn't provide one to ensure complete traceability.

---

**Step 3: Additional MDC Fields - Do you need additional context beyond trace/correlation IDs?**

Examples: User ID, Tenant ID, Job ID, Business Process ID

- [ ] **Yes** - Need additional MDC fields
- [ ] **No** - Trace ID / Correlation ID from Steps 1-2 are sufficient

**If YES:**

**Required constraints (§3.1):**
- **Limit to 5-10 application-defined fields maximum** (excludes trace.id and span.id)
- **Store only scalar, fixed values** (no collections, no domain objects)
- **Store `user.id` UUID** for user identity in MDC — never store raw usernames, emails, or hashed PII
- **Never store session IDs** in MDC (even hashed)
- **Clear all MDC fields** in `finally` blocks

> **Recommendation:** Start minimal. Trace ID and correlation ID handle most needs. Add custom fields only for specific gaps (tenant ID for multi-tenant apps, job ID for batch processing, user ID for user-level correlation).

---

### Q20: Does your application generate audit events that require separation from application logs?

Audit events include: authentication, authorization failures, privileged access, credential changes, sensitive data access/export (from Q2, Q4, Q5, Q8)

- [ ] **Yes** - Application generates audit-worthy events
- [ ] **No** - No audit requirements

**If YES:**

**Required implementation (§3.4, §4):**
- **Route audit events to dedicated appender** - Separate from standard application logs
- **Configure independent retention** - Audit logs typically have longer retention than application logs
- **Apply stricter access controls** - Limit who can view/modify audit logs
- **Ensure integrity controls** - Prevent tampering or deletion of audit records

> **Why this matters:** Mixing audit events with application logs means a log rotation policy change can inadvertently affect compliance-required audit trails. Separation ensures audit retention, access controls, and alerting rules are independently managed for regulatory compliance.