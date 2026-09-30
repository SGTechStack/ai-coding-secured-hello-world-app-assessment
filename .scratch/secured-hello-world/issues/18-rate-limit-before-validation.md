# 18: Rate-limit public endpoints before validation, not after

**What to build:** An unauthenticated caller can no longer write an unbounded number of audit events to the audit log by sending malformed bodies to a public endpoint. On every public endpoint that has a rate limiter, the limiter is acquired *before* request-body binding and validation, so a rejected body costs the caller its quota rather than costing the operator a log write.

The current order is inverted. `@Valid` binding runs during handler-method resolution, and `GlobalExceptionHandler.auditInputFailure` writes an `ACCESS_CONTROL` `validation` audit event — all before the handler body executes and calls `rateLimiters.<limiter>().acquire()`. So a malformed body never reaches the limiter and the audit write is unrated. The affected endpoints are `POST /api/client-events`, `POST /api/register`, and `POST /api/password-reset/*`. `IpThrottle` does not cover them; it is login-only.

Raised by the issue 14 reviewer. It is pre-existing on register and password-reset, but issue 14's CSRF exemption for `/api/client-events` (ADR 0002) removed the token bootstrap that previously made the attack cost something, so a bare malformed POST from any host is now free. `/api/client-events` is also the only one of the three a browser calls unprompted on every page load. The gap is recorded in issue 14's verification comment; see `docs/agents/reviewer-decisions.md`.

**Blocked by:** none (14 is done; this is a follow-up)

**Status:** needs-triage

- [ ] On `POST /api/client-events`, `POST /api/register` and `POST /api/password-reset/*`, the rate limiter is acquired before body binding and validation, so a malformed body is refused 429 once the quota is spent and writes no further audit event.
- [ ] The three endpoints use one shared mechanism, not three bespoke ones. Whether that is a filter, a `HandlerInterceptor`, or moving to manual validation after `acquire()` is decided during triage and recorded here.
- [ ] A rate-limited refusal still returns the existing 429 shape and `Retry-After` header, and a valid request's behaviour and audit events are unchanged.
- [ ] Tests cover, for each endpoint: a burst of malformed bodies is cut off by the limiter, and the number of `validation` audit events written is bounded by the limiter rather than by the number of requests.
- [ ] Triage decides whether the `validation` audit event should be written at all for an unauthenticated caller that never passed the limiter, or only counted as a metric.
