# 14: Operations: metrics and dependency scan

**What to build:** An operator can watch request latency, traffic, errors and saturation on a management port that isn't publicly reachable, and can run a dependency scan that fails on critical CVEs. See the spec's story 111, "Security configuration" (metrics), "Repository and stack", and "Testing Decisions" (dependency scan). The README deployment notes are ticket 16.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] Spring Boot Actuator runs on a separate, configurable management port and exposes only `health` (status only, no details) and `prometheus`. The metrics include `http.server.requests` (latency, traffic, errors), JVM metrics, and connection-pool saturation.
- [ ] The API port serves no Actuator endpoints, and the API's filter chain and public paths are unchanged.
- [ ] A Maven `security` profile runs OWASP Dependency-Check (`mvn -P security verify`) and fails the build on critical CVEs.
- [ ] The SPA reports client-side errors (including those caught by the app's `ErrorBoundary`) and basic performance data to an operator-visible destination, without sending user data or error details beyond what is needed. The mechanism is decided when this issue is picked up. (Added from the issue 01 IM8 lm-16 gate finding; see `docs/agents/reviewer-decisions.md`.)
- [ ] Tests cover: Actuator endpoints are absent from the API port; `health` on the management port returns status only; and `prometheus` exposes `http.server.requests` after a request.
