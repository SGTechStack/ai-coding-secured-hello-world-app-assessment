# 18: Rate-limit public endpoints before validation, not after

**What to build:** An unauthenticated caller can no longer write an unbounded number of audit events to the audit log by sending malformed bodies to a public endpoint. On every public endpoint that has a rate limiter, the limiter is acquired *before* request-body binding and validation, so a rejected body costs the caller its quota rather than costing the operator a log write.

The current order is inverted. `@Valid` binding runs during handler-method resolution, and `GlobalExceptionHandler.auditInputFailure` writes an `ACCESS_CONTROL` `validation` audit event — all before the handler body executes and calls `rateLimiters.<limiter>().acquire()`. So a malformed body never reaches the limiter and the audit write is unrated. The affected endpoints are `POST /api/client-events`, `POST /api/register`, and `POST /api/password-reset/*`. `IpThrottle` does not cover them; it is login-only.

Raised by the issue 14 reviewer. It is pre-existing on register and password-reset, but issue 14's CSRF exemption for `/api/client-events` (ADR 0002) removed the token bootstrap that previously made the attack cost something, so a bare malformed POST from any host is now free. `/api/client-events` is also the only one of the three a browser calls unprompted on every page load. The gap is recorded in issue 14's verification comment; see `docs/agents/reviewer-decisions.md`.

**Blocked by:** none (14 is done; this is a follow-up)

**Status:** resolved

- [x] On `POST /api/client-events`, `POST /api/register` and `POST /api/password-reset/*`, the rate limiter is acquired before body binding and validation, so a malformed body is refused 429 once the quota is spent and writes no further audit event.
- [x] The three endpoints use one shared mechanism, not three bespoke ones. Whether that is a filter, a `HandlerInterceptor`, or moving to manual validation after `acquire()` is decided during triage and recorded here.
- [x] A rate-limited refusal still returns the existing 429 shape and `Retry-After` header, and a valid request's behaviour and audit events are unchanged.
- [x] Tests cover, for each endpoint: a burst of malformed bodies is cut off by the limiter, and the number of `validation` audit events written is bounded by the limiter rather than by the number of requests.
- [x] Triage decides whether the `validation` audit event should be written at all for an unauthenticated caller that never passed the limiter, or only counted as a metric.

## Triage decision (2026-09-30)

**Mechanism: one `HandlerInterceptor`, driven by a method annotation.** `@RateLimitedByClientAddress(ClientAddressLimit.X)` on the handler method names the per-address limiter; `RateLimitBeforeBindingInterceptor.preHandle` acquires it. `preHandle` runs after handler mapping and before argument resolution, which is where `@Valid @RequestBody` binding and `GlobalExceptionHandler.auditInputFailure` happen. A refusal thrown from `preHandle` is resolved by the same `@RestControllerAdvice`, so the 429 body, `Retry-After` and `rate_limited` audit event are unchanged.

- Rejected a servlet filter: it runs before handler mapping, so it would need a second, path-based list of which endpoints are limited (and the base path), duplicating the mapping, and it would have to write the 429 body and audit event itself.
- Rejected manual validation after `acquire()`: three bespoke copies of `Validator` calls plus re-creating the `validation` problem body and audit per controller, and it does not cover a body that fails to parse (`HttpMessageNotReadableException` is raised during argument resolution regardless).
- The per-email reset-request limit is keyed by a field in the body, so it cannot run before binding; it stays in the handler, after validation. Only a validated email can reach it, so it adds no unrated audit path. Consequence: the per-IP reset-request limit is now taken first (previously second).

**The `validation` audit event stays.** Once the limiter runs first, no unauthenticated caller reaches validation without passing the limiter, so every `validation` event is bounded by the limiter's capacity; the premise of the question (a caller "that never passed the limiter") no longer occurs. The spec (audit table: input-validation failure → `access-control`) requires it, and dropping it to a metric would remove the signal an operator needs to see probing. A 429 still writes one `rate_limited` audit event per refusal, as the spec requires for every rate-limit breach; that is pre-existing and shared with login, and is not the gap this issue closes. Criterion 1's "writes no further audit event" is read as "no further `validation` audit event".

## Comments

### Verification

All five acceptance criteria met and ticked. Implemented in `082bf80c`.

**What was implemented.** A single `RateLimitBeforeBindingInterceptor` (a `HandlerInterceptor` that registers itself via `WebMvcConfigurer`) acquires the per-client-address limiter named by `@RateLimitedByClientAddress(ClientAddressLimit.X)` on the handler method. It runs after handler mapping and before argument resolution, so it precedes `@Valid @RequestBody` binding and `GlobalExceptionHandler.auditInputFailure`. The four address-limited endpoints — `POST /api/client-events`, `POST /api/register`, `POST /api/password-reset/request`, `POST /api/password-reset/confirm` — are annotated, and their in-handler `acquire(getRemoteAddr())` calls are removed. The per-email reset-request limit stays in the handler after validation. `docs/api/openapi.yaml` notes that per-address limits are counted before validation; `docs/agents/reviewer-decisions.md` records the mechanism decision.

**Verification steps.**
- TDD: the new `RateLimitBeforeValidationApiTest` failed first on all four endpoints (`Status expected:<429> but was:<400>`) and passed after the change. Per endpoint it sends a burst that alternates field-invalid and unparseable bodies, and asserts exactly `capacity` `validation` audit events and exactly one `rate_limited` event per extra request. Capacities come from the `RateLimitProperties` bean.
- Targeted tests only, by request; the full suite was not run. Implementer: `RateLimitBeforeValidationApiTest`, `ClientEventApiTest`, `RegistrationApiTest`, `RegistrationRateLimitApiTest`, `PasswordResetApiTest`, `ConcurrentRegistrationApiTest`, `OpenApiDocumentTest`, `ManagementEndpointsTest` → 120 tests, 0 failures. The reviewer re-ran `RateLimitBeforeValidationApiTest`, `RegistrationRateLimitApiTest`, `PasswordResetApiTest` and `ClientEventApiTest` → 44 tests, 0 failures, 0 errors.
- Reviewer: Must-fix 0, human decisions 0. Confirmed that OPTIONS/CORS preflight and `/error` dispatches are no-ops for the interceptor, that no address-keyed `acquire()` remains elsewhere in `src/main`, and that refused requests do not use up capacity. The reset-request order change (per-address checked before per-email) is safe: an address-refused request no longer spends the target email's quota.
- Skipped by request: KB retrieval and the code-reviewer final gate. The optional mutation gate was not run.

**Known gap, not tracked:** `README.md` §6 says three rate limiters key on the client address; there are four. This was already wrong before this issue and is outside its scope.
