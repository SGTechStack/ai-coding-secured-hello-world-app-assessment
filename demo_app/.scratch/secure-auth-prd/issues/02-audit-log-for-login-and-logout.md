# 02: Audit log for login and logout

**What to build:** An operator can see who logged in, who failed and who logged out, from which IP, as structured log lines on a dedicated `AUDIT` logger. The component is built so that later tickets only add events to it. See spec §Implementation Decisions › Audit log.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] An `AuditLog` component logs to the `AUDIT` logger through the SLF4J key-value API with fields `event`, `actor` (username or `anonymous`), `ip` and `outcome`, plus event-specific fields.
- [x] The event vocabulary is fixed (an enum or constant set covering the spec's full list), so later tickets can't invent ad-hoc names.
- [x] `LOGIN_SUCCESS`, `LOGIN_FAILURE` and `LOGOUT` are emitted at the right points.
- [x] Every user-supplied value has CR/LF and other control characters replaced. A username containing `\r\n` is logged on one line.
- [x] Passwords, CSRF tokens and session IDs never appear in any captured log output.
- [x] Outside the `dev` profile, Spring Boot's structured console logging (ECS JSON) is on.
- [x] Tests assert on captured log output at the HTTP seam, not on mocks. `mvn verify` passes.

## Comments

- `AuditLog` (package `com.example.demo_app.audit`) exposes `record(AuditEvent, actor, request)` and `record(AuditEvent, actor, request, Map<String, ?> fields)`. `actor == null` logs `anonymous`; `ip` is `request.getRemoteAddr()`. Admin tickets pass `target` (and e.g. old/new role) through `fields`, which are logged after the standard four in iteration order (use a `LinkedHashMap`).
- `AuditEvent` is the full fixed vocabulary from the spec. Each constant carries its `outcome` (`success`/`failure`), so call sites can't pair an event with the wrong outcome.
- Values are neutralised by replacing `\p{Cc}`, `\p{Cf}`, `\p{Zl}` and `\p{Zp}` characters with `_` (covers CR/LF, NUL, tab, NEL, U+2028/2029).
- The message repeats the pairs as `key=value` text, because Spring Boot's plain `dev` console pattern doesn't print SLF4J key-value pairs. Under ECS JSON the pairs also become top-level fields (`event`, `actor`, `ip`, `outcome`). Note: ECS itself reserves `event` as an object; the spec's field name was kept, but a log shipper with a strict ECS mapping would need a rename (`logging.structured.json.rename`).
- `LOGIN_FAILURE` is logged in `AuthController` for any `AuthenticationException` with the submitted username as actor; blank-field `400`s are not login attempts and aren't audited. `LOGOUT` is a `LogoutHandler` in `SecurityConfig`, so a logout without a session is audited as `anonymous`.
- ECS JSON: `logging.structured.format.console: ecs` in `application.yml`, overridden to `""` in `application-dev.yml`.
- Test caveat: Logback is initialised once per JVM by the first Spring context, so in `mvn verify` the console format depends on test order. `AuditLogApiTest` therefore matches the `key=value` message (same in both formats); `AuditLogTest` checks the key-value pairs with a Logback `ListAppender`; `ProdProfileTest`/`DevProfileSessionCookieTest` check the format property. MockMvc session IDs are short counters, so the "no session ID" check relies on the fixed field set rather than a string search.

