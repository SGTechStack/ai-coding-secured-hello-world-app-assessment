# ADR-0011: Spring Session (JDBC) as the session store

## Status

Accepted. Supersedes [ADR-0009](0009-container-http-session-not-spring-session.md).

## Context

The PRD's overview names Spring Session as the session mechanism. ADR-0009
recorded a deliberate deviation: the servlet container's `HttpSession` plus an
in-memory `SessionRegistryImpl`, on the grounds that a single-instance app on
in-memory H2 gains nothing observable from an external session store.

That reasoning still holds technically, but the PRD is the specification this
codebase is assessed against, and "Spring Session" is one of the few places it
names a concrete technology. Meeting it literally is worth more here than the
dependency it avoids.

## Decision

Adopt Spring Session with the JDBC store (`spring-boot-starter-session-jdbc`),
on the application's existing datasource. JDBC rather than Redis because it
adds no new infrastructure: the sessions live in `SPRING_SESSION` /
`SPRING_SESSION_ATTRIBUTES` tables next to the app's own.

- `SessionRegistry` is now a `SpringSessionBackedSessionRegistry`. The rest of
  the forced-expiry design from ADR-0002 is unchanged: `SessionTerminationService`
  still calls `SessionInformation.expireNow()`, and `ConcurrentSessionFilter`
  (registered by `maximumSessions(SessionLimit.UNLIMITED)`) still turns that
  into a 401 on the session's next request.
- Sessions are found through the store's principal-name index, so
  `RegisterSessionAuthenticationStrategy` and `HttpSessionEventPublisher` are
  gone; only `SessionFixationProtectionStrategy` remains on the login filter.
- The session cookie is Spring Session's default `SESSION`, not `JSESSIONID`.
  `server.servlet.session.cookie.*` and `server.servlet.session.timeout` keep
  their meaning: Spring Boot applies them to Spring Session's cookie
  serializer and max inactive interval.
- `spring.session.jdbc.initialize-schema=always` creates the session tables in
  every profile, in the same spirit as Hibernate's `ddl-auto=update`
  (ADR-0008). Re-running against existing tables is tolerated: Boot's session
  schema initializer continues past the resulting "already exists" errors.

## Consequences

- Sessions are no longer process-local. With a persistent prod database they
  survive a restart and would be visible to a second app instance, which
  removes the scaling caveat ADR-0009 carried. With in-memory H2 they still
  vanish on restart, along with everything else.
- Session attributes are stored with Java serialization, so anything put in
  the session (today only the `SecurityContext`, whose principal is Spring
  Security's own `User`) must be `Serializable`, and a change to those classes'
  serialized form invalidates existing sessions.
- Every authenticated request now costs a session read and a last-access
  write against the database.
- MockMvc tests must carry the `SESSION` cookie between requests; a
  `MockHttpSession` passed to the request builder is ignored by
  `SessionRepositoryFilter`.
- Spring Boot applies `server.servlet.session.cookie.*` to Spring Session only
  when it runs its own embedded server. Under MockMvc it treats the mock
  servlet context as a WAR deployment and the `SESSION` cookie carries no
  `HttpOnly`/`SameSite` at all, so those attributes are asserted against a
  real server in `SessionCookieAttributesTest`. The same applies to an actual
  WAR deployment: the cookie settings would then have to come from the
  container, not from these properties.
- Expired rows are removed by Spring Session's scheduled cleanup job (every
  minute by default), not by the servlet container.
