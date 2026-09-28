---
status: accepted
---

# ADR-031: One RFC 9457 envelope from one writer; `sendError` prohibited

Every error response the API produces is an RFC 9457 `application/problem+json` body carrying a `code` extension
from one closed enum. One component, `ProblemDetailWriter`, is the only code allowed to write an error body, and
`HttpServletResponse.sendError(...)` is prohibited everywhere. The prescribed recipes do the opposite. They call
`sendError` and let Spring Boot's `BasicErrorController` format the body, so a maintainer following them would bring
back the inconsistent bodies this ADR exists to remove.

## Context

- The governing standard (§3.2 Error Contract) asks for a stable, machine-readable error body, and §5 asks that it
  conform to a documented schema. The user pillar defines no such schema.
- The recipes produce at least three incompatible shapes: Boot's default `/error` body (reached through `sendError`,
  as in the login recipe's failure handler and absolute-timeout filter), a single-key `{"error": …}` body, and
  RFC 9457 in the MFA and file recipes.
- The enumeration rule (ADR-033, ADR-032) needs every authentication failure to be the same response across three
  endpoints. That can only be tested if one component writes all of them.
- Spring Security handles its own exceptions in the filter chain, which runs before the `DispatcherServlet`. So an
  `@RestControllerAdvice` never sees an authentication or access-denied failure. `spring.mvc.problemdetails.enabled`
  (default `false`) only covers exceptions that reach Spring MVC.

## Considered options

- **Boot's default error body via `sendError` (the recipes' posture).** It has no stable code, a prose `message` that
  varies with the exception, and it is not RFC 9457.
- **A home-grown `{"error": …}` body.** No standard behind it, and it does not match the MFA pillar.
- **RFC 9457 with a `code` extension (chosen).** It matches the MFA and file pillars, so it is consistent across the
  organisation rather than invented.

## Decision

- **Envelope.** `type`, `title`, `status`, `detail`, `instance`, plus `traceId` and `code`. `code` is SCREAMING_SNAKE
  from a closed Java enum and is the only thing a client branches on. `type` is derived from `code` for RFC
  conformance and is never read by a client. RFC 9457 §3.1.4 itself says consumers should not parse `detail`, and
  clients here never branch on prose. `detail` is a constant per code and never carries an exception message.
- **One writer.** `ProblemDetailWriter` is injected into every component that can end a request with an error. At
  design time these are: the `@RestControllerAdvice` (extending `ResponseEntityExceptionHandler`), the
  `AuthenticationEntryPoint`, the authentication failure handlers (password login and the factor-grant filters), the
  `AccessDeniedHandler`, the concurrent-session `SessionInformationExpiredStrategy`, the `InvalidSessionStrategy`,
  the application's own filters (source rate limiting, absolute session lifetime), and the `/error` dispatch for
  anything that escapes all of them. Any new producer joins this list rather than writing its own body.
- **`sendError` is prohibited.** It forwards to the container's error dispatch and hands formatting to
  `BasicErrorController`, which is how a second 401 shape appears beside the first.
- The closed code enum, its status pairing and the validation-error extension are the error contract in the spec.
  They are not restated here.

## Consequences

- The set of producers is a list to maintain, not a count. The framework adds producers quietly: every Spring Security
  strategy with a default writer is one. A producer left on its default writes plain text or redirects.
- The main entry point must match `fetch` requests. Spring Security's form-login entry point is registered with a
  browser-request matcher that a JSON `Accept` does not satisfy, and a missed match falls through to a 403. So the
  SPA's `Accept` header is part of the server's security configuration (T-AUTH-015).
- `instance` must be set explicitly on authentication failures. Spring fills it from the request URI by default,
  which would make login, reset-request and registration failures differ (ADR-033).
- Tests: T-AUTH-008, T-AUTH-009, T-AUTH-010, T-AUTH-002 and T-AUTH-001 cover the producers, and T-AUTH-011 fails if
  any body has Boot's default shape or a code outside the enum. `/actuator/**` is exempt (REJ-066).

## Sources

- RFC 9457 (Problem Details for HTTP APIs), §3.1 members, §3.1.4 `detail`, §3.2 extension members.
- Spring Boot 4.1 reference, Servlet web applications, Error Handling (`/error`, `BasicErrorController`,
  `spring.mvc.problemdetails.enabled`).
- Spring Security reference, Servlet Architecture, Handling Security Exceptions (`ExceptionTranslationFilter`).
- Standalone User Access Control Application Standard §3.2 Error Contract, §5.
- Recipes: Standalone Session Login with CSRF Bootstrap (failure handler and absolute-timeout filter call
  `sendError`); Standalone Privileged User Administration and Password Reset, and Standalone Self-Service Password
  and History Management (forced-change filters call `sendError`).
