---
status: accepted
---

# ADR-065: Security-control tests boot the full context; `@WebMvcTest` and all slices are banned

Every test that proves a security control runs against a full application context. `@WebMvcTest` and every other
Spring Boot test slice are banned for these tests, even though slices are the documented fast path for controller
tests. A slice passes against a configuration that production never runs.

## Context

No security control in this application lives only in a controller. Each one depends on components that a slice
leaves out:

- the security filter chain and its ordering;
- Spring Session JDBC and the session table;
- the H2 file database, including the pessimistic row locks used by the lockout counter and the two-admin guard;
- the refresh-phase configuration validator, a `@Validated @ConfigurationProperties` bean;
- the rate limiters;
- the audit appender.

The Spring Boot reference (Testing, "Auto-configured Spring MVC Tests") says `@WebMvcTest` limits scanned beans to
`@Controller`, `@ControllerAdvice`, `@JacksonComponent`, `Converter`, `GenericConverter`, `Filter`,
`HandlerInterceptor`, `WebMvcConfigurer`, `WebMvcRegistrations` and `HandlerMethodArgumentResolver`. It also says
that regular `@Component` and `@ConfigurationProperties` beans are not scanned. The same page describes the usual
pattern: one controller, with `@MockitoBean` standing in for its collaborators.

So a slice test of the lockout, the session limits or the factor rules runs in one of two states. Either the
control is absent, or it is a mock. In both cases the test passes without proving anything.

## Decision

- Security-control tests use `@SpringBootTest` with `@AutoConfigureMockMvc` (level C). A test uses a real port
  (level P) wherever MockMvc bypasses Tomcat: `RemoteIpValve`, raw `Set-Cookie` headers, Tomcat meters, the
  trace-header wrapper, duplicate cookies and the request-header size budget.
- Contexts come from a small, fixed, named set, so the Spring test context cache is not fragmented:
  - `ctx-default`
  - `ctx-port`
  - `ctx-locktimeout`
  - `ctx-lockhold`: a 10 s lock timeout and a database of its own, for the tests that hold a row lock until the
    other side is seen waiting (the two-admin race and the tier-2 trip's lock order). Under `ctx-locktimeout`'s 50 ms
    the other side could time out before the release, so the proof would depend on scheduling.
  - `ctx-nondev`
  - the restart harness and the runner, which both run outside the cache
- An extra `@MockitoBean` or property override is a new context, and it needs a reason. A test that only wants to
  save time is not a reason.
- Pure decision logic is extracted into functions that need no Spring at all (level U): the admin guard's
  decision, token consumption and the TOTP window arithmetic. That is how the suite gets fast tests without slices.

## Considered options

- **Slices with mocked collaborators.** Fast, and the Boot default. Rejected for the reason above.
- **A full context per test class, with mocks as needed.** Correct, but every distinct set of mocks is a new cached
  context, and the suite's run time grows with the number of test classes.
- **Full contexts from a fixed named set (chosen).**

## Consequences

- The suite is slower than a slice-based one, and runs sequentially. The secret-leak canaries capture JVM-global
  output streams, and they, the shared clock (ADR-066) and the shared contexts all depend on sequential execution.
- No test enforces this ban. It lives in this ADR and in review. If a slice appears in a security test, the fix is
  to move the test into one of the named contexts, not to add the missing beans to the slice.
- Controller-shape tests with no security content (for example, JSON field naming) may still use a slice.
