# 01: Backend skeleton and test harness

**What to build:** A Spring Boot 4.1 / Security 7.1 / Java 21 Maven application that starts on port 8080 and exposes only `GET /actuator/health`. That endpoint returns health with no details, and every other actuator endpoint is explicitly denied (ADR-061). This ticket also lays the test foundation every later ticket uses: one injectable, forward-only `Clock` bean that is mutable in tests (ADR-066), the fixed Spring test contexts (`ctx-default`, `ctx-port`, `ctx-locktimeout`, `ctx-nondev`), each on a temporary H2 file with BCrypt cost 4 in shared contexts, and the first ArchUnit rules. There is no `test` profile (ADR-067). The suite runs sequentially (REJ-058), with OTLP export off (REJ-068).

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] The app starts, and `GET /actuator/health` returns `UP` with no `components` or details.
- [ ] Every other `/actuator/**` path is refused.
- [ ] Main code gets time only from the `Clock` bean. An ArchUnit rule bans ambient time in main code and `Thread.sleep` in tests (T-ARCH-001).
- [ ] An ArchUnit rule bans `@WebMvcTest` and every other slice annotation on security-control tests (T-ARCH-006; ADR-065).
- [ ] The four named contexts exist and boot on temporary H2 files. `ctx-nondev` asserts production values without refreshing a production context.
- [ ] Surefire and Failsafe are pinned at exactly 3.6.0 (R-BLD-005).
- [ ] A `@Proves("T-…")` annotation exists for tests to cite test-plan rows.
