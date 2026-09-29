# 09 — HTTP security: CSRF bootstrap, CORS and headers

Type: grilling
Status: open
Blocked by: 01
Map: [Secured Login App](../map.md)

## Question

How are CSRF protection, CORS and security headers configured for a cross-origin React SPA using cookie-based sessions?

Answer `Q25` (frontend type), `Q26` (external-domain resources, which drives CSP) and `Q27` (CORS origin allowlist and credential policy), against [`Common_Security_Headers_and_SPA_CSRF_Configuration.md`](../../../App-Standards/Appfw-User-Standards/Shared_Recipes/Common_Security_Headers_and_SPA_CSRF_Configuration.md) and [`Standalone_Session_Login_with_CSRF_Bootstrap.md`](../../../App-Standards/Appfw-User-Standards/User_Standalone/Standalone_User_Access_Control_Recipes/Standalone_Session_Login_with_CSRF_Bootstrap.md).

The PRD's NFRs make this load-bearing rather than boilerplate: CSRF enabled for **all** state-changing endpoints precisely because auth is cookie-based; an explicit origin allowlist with `Access-Control-Allow-Credentials: true`; `HttpOnly`, `Secure` (prod) and `SameSite` cookie attributes; session-fixation protection (`prd/assessment-prd.md:117`).

The hard part is the **CSRF bootstrap**: a cross-origin SPA cannot read an `HttpOnly` CSRF cookie, and it needs a token before its *first* state-changing call — which includes login and registration, both unauthenticated. Settle how the SPA obtains its first token, which cookie/header names are used, and what `SameSite` value permits the cookie to travel from `localhost:3000` to `localhost:8080` at all. Note the tension to resolve explicitly: `SameSite=Strict` would break the cross-origin flow, while `SameSite=None` requires `Secure`, which local HTTP cannot provide — and local HTTPS is out of scope. State how dev and prod differ, and record the accepted dev gap.

Also decide the security-header set (HSTS, CSP, `X-Content-Type-Options`, frame options) and whether HSTS is configured despite local HTTP.

Blocked on 01 (origins and path shape).

**Amended by [05 — Which logging standards actually bind a two-process app](05-logging-standards-applicability.md).** The security filter chain this ticket defines must accommodate **MDC filters**, and their placement is a chain-ordering decision, not a logging detail — which is why it lands here rather than on [12 — Audit and logging contract](12-audit-and-logging-contract.md).

`Structured_Logging_Application_Standard.md:323` is an `[Enforced Constraint]`: register an MDC filter or interceptor that extracts application-defined fields from each incoming request — a correlation id and **the `user.id` UUID after authentication** — and clears all MDC fields in a `finally` block. 05 established that satisfying it takes **two filters, not one**, and that both placements are chain decisions:

- **`user.id` requires a filter *inside* the Spring Security chain.** `Recipes/Enriching_Logs_With_MDC.md:284` — a request filter registered outside the chain "runs before authentication completes and the user context will not be available". The recipe's placement is `http.addFilterAfter(new MdcUserFilter(), AnonymousAuthenticationFilter.class)` (`MDC:355`), registered **in the `SecurityFilterChain` bean**, not as a `@Component` or via `FilterRegistrationBean` (`MDC:350` warning). It must check the principal type, not just `isAuthenticated()`, because `AnonymousAuthenticationToken.isAuthenticated()` returns `true` (`MDC:330`); `user.id` is simply absent for anonymous requests.
- **Getting a correlation id onto failed logins and 403s requires running *before* Spring Security.** `MDC:75` notes that a `@Component` filter defaults to order `Integer.MAX_VALUE`, i.e. **after** Spring Security's `-100` — so authentication-failure and authorization-denial logs would carry no correlation id. `MDC:79` gives the fix: `@Order(SecurityProperties.DEFAULT_FILTER_ORDER - 1)`. **This application's entire audit surface is exactly those events** — failed login, lockout, 403 — so the default ordering is not acceptable here.

Settle in this ticket:

- The concrete ordering of both MDC filters relative to `CsrfFilter`, to `AnonymousAuthenticationFilter`, and to **the custom JSON `AuthenticationFilter` that [01 — Context, topology and API surface](01-context-topology-and-api-surface.md) introduced** (registered via `addFilterAt(..., UsernamePasswordAuthenticationFilter.class)`). 01 already flagged that filter as the one security-critical component with no standards recipe behind it; adding two MDC filters around it makes the chain order the thing most likely to be got wrong silently.
- That `finally`-block MDC clearing holds on **every** exit path through this chain, including authentication failure and access denial, which do not return through the normal handler path.
- Do **not** use Logback's built-in `MDCInsertingServletFilter`: `MDC:98` rejects it under this standard because it populates client IP and query strings, both on the must-not-log list (`Structured_Logging_Application_Standard.md:230-231`). Related: whether the client IP is logged at all is [18 — Client IP in logs](18-client-ip-in-logs.md)'s decision, and if 18 permits it, this chain is where it is read — from `getRemoteAddr()`, since there is no gateway to make `X-Forwarded-For` trustworthy.
- Whether this application accepts an inbound `X-Correlation-ID` header from the SPA at all. 05's recommendation is no — untrusted input, no gateway to strip it, and accepting it triggers `Structured_Logging_Application_Standard.md:276`'s CRLF-sanitisation obligation (log forging, CWE-117). [12](12-audit-and-logging-contract.md) owns the final call; this ticket owns whether the header is read in the chain, so coordinate rather than decide twice.
