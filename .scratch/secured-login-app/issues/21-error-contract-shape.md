# 21 — Error contract shape

Type: grilling
Status: open
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
