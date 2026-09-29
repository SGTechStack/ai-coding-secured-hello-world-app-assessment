# 21 — Error contract shape

Type: grilling
Status: resolved
Blocked by: —
Map: [Secured Login App](../map.md)

## Question

What is the concrete shape of an error response body, and how is it produced?

Graduated from the map's **Not yet specified** by [08 — Authorization matrix](08-authorization-matrix.md), which discharged the prerequisite it was waiting on. The question is now sharp because the codes, statuses and raising sites are all fixed; only the envelope and one logging contradiction remain.

This is the standard's §3.2 error contract. Settle:

- **The envelope.** RFC 9457 `ProblemDetail` (Spring's built-in, `application/problem+json`) versus a custom body. The SPA is the only client, so the deciding factors are what the global exception handler can emit uniformly and whether `ProblemDetail`'s fixed field set can carry a stable `code` without abusing `type`.
- **Who writes it.** `Std:326` makes a global exception handler an `[Enforced Constraint]`, and 15 placed it in `common/`. But three response sites are **outside** MVC and cannot reach a `@RestControllerAdvice`: the `accessDeniedHandler` and the `authenticationEntryPoint` (both fixed by 08), and the tier-0 `PasswordChangeFilter`. Decide whether they share one serializer with the advice or duplicate the shape — duplication is how the two drift.
- **The `ERROR`-versus-`WARN` contradiction for Bean Validation failures.** The binding set disagrees with itself: `Structured_Logging_Application_Standard.md:97` says `ERROR`, `Structured_Logging_Application_Standard_Questions.md:298` says `WARN`. 05 surfaced it, 04 routed password failures around it through domain exceptions, and 19 discharged the *other* constraint on this patch (its encoder creates the `error` object when absent rather than stripping, so an `error_code` on a non-exception `ERROR` event is no longer silently dropped). This contradiction is the last piece and has no owner but this ticket.

### What is already settled and must not be re-litigated

- **The four codes and their statuses**, fixed by 08: `PASSWORD_CHANGE_REQUIRED` (`403`), `ACCESS_DENIED` (`403`), `LAST_USER_MANAGER` (`409`), `CURRENT_PASSWORD_INVALID` (`400`). This ticket wraps them; it does not rename or renumber them.
- **`Std:110` and `:247` do not conflict** — settled by 04. `:247` scopes itself to authentication outcomes, so a policy or state violation may name the violated rule.
- **The `401` body is empty**, by 08's ruling: `HttpStatusEntryPoint` writes a status and nothing else, which is `Std:247`'s generic response. **This ticket must not give the `401` an envelope.**
- **No non-authentication rejection on an authenticated path may use `401` or `403`** — 08's constraint, because `Std:437`'s global SPA interceptor treats both as a logout signal.
- **`/error` is `permitAll` and `server.error.whitelabel.enabled=false`** — 08's row 6. This ticket decides what reaches a client when the advice does *not* handle something, given that `/error` is a backstop rather than the normal path.
- **The `PasswordChangeFilter` never calls `sendError`** — 08, because an `ERROR` dispatch is authorized (`filterErrorDispatch=true`, verified) and because `sendError` cannot carry a code.

### Downstream

The map's **React screen and route inventory** fog waits on this: the SPA must distinguish `PASSWORD_CHANGE_REQUIRED` from `ACCESS_DENIED` to route a flagged user to the change screen rather than to an error page.

## Answer

Resolved by grilling, one round, two questions. Decisions delegated by the user to the orchestrating session after the evidence was presented.

**RFC 9457 `ProblemDetail` with a `code` extension property, written by a single `ProblemDetailWriter` bean shared by the advice and all off-MVC sites. The `401` keeps its empty body. The `ERROR`-versus-`WARN` contradiction does not exist.**

### Two of this ticket's three premises were wrong

**1. `Std:326` is not an enforced constraint, and not about exception handlers.** In `Standalone_User_Access_Control_Application_Standard.md`, `:326` is a *log-level* rule inside §3.4 — "`ERROR`: Authentication system failures (database unavailable, service errors), unrecoverable authentication errors, or critical security policy violations." The `[Enforced Constraint]` lines in that file are only `:238`, `:397-402`, `:407-409`, `:415-417`, and **none concerns an exception handler**.

The requirement is real but lives elsewhere: **`Structured_Logging_Application_Standard.md:325`** — "**[Enforced Constraint]** … Implement a global exception handler to catch and log unexpected, unhandled exceptions once at the boundary. Business-specific exceptions should be logged at the point they are handled in the business logic, not delegated to the global handler."

The decision is unchanged; the citation is corrected here and **must be corrected in 15**, which placed the handler in `common/` on the same bad cite. The relocated clause also carries a rider 15 did not: *business exceptions are logged where handled, not at the boundary* — which matches what 04 already built (`PasswordPolicy` / `PasswordHistoryService` raising domain exceptions), so no rework, but it is now a stated rule rather than a coincidence.

**2. The `ERROR`-versus-`WARN` contradiction dissolves on reading the full clauses.** This ticket inherited it from 05 as "the last piece, with no owner but this ticket." There is nothing to resolve.

- `Structured_Logging_Application_Standard.md:97` does **not** say `ERROR`. Verbatim: "**Rejection:** Log at `WARN` when the failure is not yet actionable or can be safely skipped … Log at `ERROR` when the rejection is definitive and non-retryable (e.g., a missing required field), as no further recovery is possible." It is a **conditional rule**, and its own example of definitive-and-non-retryable is *a missing required field* — precisely a Bean Validation failure.
- `Structured_Logging_Application_Standard_Questions.md:295`, under the heading **"Required logging (§2 - Failure Paths)"**, restates it verbatim in substance: "**Client validation failures**: Log at WARN if retryable, ERROR if definitive/non-retryable."
- `:298` — the line 05 cited as the contradiction — is prefixed `> **Recommendation:**`. It is a blockquote, not a requirement; it is scoped to `@Valid`-style client input only; and `:296` beside it keeps `ERROR` for business rules.

So the normative texts **agree**, and `:298` is a non-binding refinement of one branch of them. 05 read a recommendation against a required rule and called it a conflict.

**The rule adopted**, stated once so no later ticket re-opens it: `WARN` for client input validation failures, field names only, once per request (`:298`'s refinement, taken because it costs nothing); `ERROR` for business-rule violations with entity id and generic reason (`:296`); `ERROR` for unhandled exceptions at the boundary (`:105`). 04's domain-exception routing was a sound instinct but was never load-bearing.

### The envelope (`Q9`): `ProblemDetail`, with `code` as an extension property

**§3.2 specifies no envelope whatsoever.** It is a list of status mappings and message *strings*; it names no field to carry them. Its only shape constraints are `:245` ("a stable, machine-readable error body") and `:509` ("Error response bodies conform to the documented schema in all environments") — which **asserts a documented schema that the document does not contain**. Nor does anything else: `ProblemDetail`, `application/problem+json`, `@RestControllerAdvice` and `ErrorResponse` return **zero hits across the entire `Appfw-User-Standards/` tree**.

So this is an authoring decision, not a selection. Three facts decided it, all verified against the pinned jars rather than recalled:

1. **`setProperty` exists and is first-class.** `javap org.springframework.http.ProblemDetail` (`spring-web-7.0.8.jar`) shows `setProperty(String, Object)`, `setProperties(Map)`, `getProperties()`. A stable `code` needs no abuse of `type` — the worry this ticket raised is unfounded.
2. **Extensions serialize flat.** `ProblemDetailJacksonMixin` ships in **spring-web** and its constant pool carries `JsonAnyGetter` / `JsonAnySetter`, so `code` appears at the top level beside `status` and `detail`, not nested under `properties`.
3. **Boot 4 registers it outside MVC.** `spring-boot-jackson-4.0.7.jar` contains `JacksonAutoConfiguration$JsonProblemDetailsConfiguration$ProblemDetailJsonMapperBuilderCustomizer`, calling `addMixIn(ProblemDetail, ProblemDetailJacksonMixin)`, gated `@ConditionalOnClass(ProblemDetail.class)` — **on spring-web, not on `DispatcherServlet`**. The injected mapper therefore serializes `ProblemDetail` correctly from a plain filter. This is what makes the single-writer answer possible at all.

**In-tree precedent for exactly this shape**, in non-binding standards but written by the same authors: `Appfw-Mfa-Standards/MFA_Core/Base_Standalone_Reimplementation_Recipes.md:544` does `pd.setProperty("code", ex.getCode())` inside a `@RestControllerAdvice` over a typed hierarchy with codes like `"INVALID_PIN"`, `"ACCOUNT_LOCKED"`; and `Appfw-File-Standards/…/13-rfc-9457-error-responses.md` is a whole recipe for RFC 9457 whose worked body is RFC fields plus one extension (`traceId`).

**Rejected: the bare one-field `error` envelope.** It is the only concrete error body in the User standards (`Standalone_Privileged_User_Administration_and_Password_Reset.md:689-692`) — but it appears in a **commented-out `curl` response**, not code, and it contradicts `§3.2:255` on its own status (the comment says `413 Request Entity Too Large / 400`, the clause says `400` only). A commented example carrying a known error is not a specification.

**The concrete envelope:**

```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "detail": "Password change required before accessing this resource.",
  "instance": "/api/v1/hello",
  "code": "PASSWORD_CHANGE_REQUIRED"
}
```

`Content-Type: application/problem+json`. `code` is the stable machine-readable contract (`:245`); `detail` is human-facing and **must never carry the enumeration-sensitive distinctions `:247` and `:507` forbid**. `type` stays `about:blank` — we publish no error-type URIs, and `:509`'s "documented schema" is discharged by this section plus 08's code table, not by a dereferenceable URL.

The four codes and statuses are 08's and are not re-opened: `PASSWORD_CHANGE_REQUIRED` 403, `ACCESS_DENIED` 403, `LAST_USER_MANAGER` 409, `CURRENT_PASSWORD_INVALID` 400.

### Who writes it (`Q10`): one `ProblemDetailWriter` in `common/`

The off-MVC sites are **raw Servlet-API contracts**, verified by `javap` against `spring-security-web-7.0.6`: `AccessDeniedHandler.handle(HttpServletRequest, HttpServletResponse, AccessDeniedException)` and `AuthenticationEntryPoint.commence(HttpServletRequest, HttpServletResponse, AuthenticationException)`. Nothing MVC-specific crosses either interface, and `ProblemDetail` / `ErrorResponse` / `MediaType.APPLICATION_PROBLEM_JSON` all live in `spring-web`. But **there is no framework hook that writes a body outside MVC** — a filter must set the status, set the content type, and write the bytes itself.

So: **one `ProblemDetailWriter` bean in `common/`**, taking the injected `JsonMapper`, used by

1. the `@RestControllerAdvice` (via normal MVC return, which needs the writer only for consistency of `code` assignment),
2. 08's `accessDeniedHandler`,
3. 08's tier-0 `PasswordChangeFilter`,
4. **07's IP-throttle and per-account rate-limit filter, and 13's `AbsoluteSessionTimeoutFilter`** — sites this ticket did not know about when it was written, all settled in the same session, all off-MVC, all previously reaching for `sendError`.

That last point makes the writer more load-bearing than the ticket anticipated: it is **five** sites, not three, and `response.sendError` is banned chain-wide (08's finding: `filterErrorDispatch=true` means an `ERROR` dispatch is itself authorized, and `sendError` cannot carry a `code`). Recorded as a chain-wide rule rather than re-derived per filter.

**The `401` is exempt and stays exempt.** `HttpStatusEntryPoint` takes only a status and has no body-writing capability — confirmed by its `javap` signature — which is exactly 08's ruling and `Std:247`'s most generic response. The writer is never wired into the entry point.

### `/error` backstop

`server.error.whitelabel.enabled=false` and the `permitAll` row are 08's. What reaches a client when the advice does not handle something: Boot's `BasicErrorController` JSON, carrying `timestamp`/`status`/`error`/`path` and **no `code`**. Accepted rather than customized — it is a backstop for genuinely unhandled failures, `Questions:331` requires only that it return generic messages without stack traces, and giving it a `code` would imply a contract we cannot enumerate. **14 should assert it is unreachable in normal operation** rather than asserting its shape.

### Amends

- **15** — the global exception handler's citation is `Structured_Logging_Application_Standard.md:325`, not `Std:326`; and the handler carries the "business exceptions logged where handled" rider.
- **07, 13** — both gain a dependency on `ProblemDetailWriter` for their filters' rejection bodies; neither may call `sendError`.
- **12** — the `ERROR`/`WARN` rule above is settled here and is not 12's to re-open; 12 owns which *events* are logged, not at what level validation failures land.
- **14** — asserts the envelope on all four codes, asserts `Content-Type: application/problem+json`, asserts the `401` body is **empty**, and asserts `/error` is not reached.
- The map's **React screen and route inventory** fog loses its last dependency: the SPA distinguishes `PASSWORD_CHANGE_REQUIRED` from `ACCESS_DENIED` by reading `code`, both being `403`.
