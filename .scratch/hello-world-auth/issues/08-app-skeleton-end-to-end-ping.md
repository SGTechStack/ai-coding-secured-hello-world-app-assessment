# 08: App skeleton + end-to-end ping

**What to build:** Both applications exist and talk to each other. A Spring Boot 4.1.x / Java 21 / Maven backend boots on `localhost:8080` with Spring Data JPA over H2 (dev profile) and exposes `GET /actuator/health` plus a placeholder `GET /api/hello` (permit-all for now, static response). A Vite + TypeScript + React SPA with shadcn/ui + Tailwind runs on `localhost:3000` and fetches that endpoint cross-origin — proving the CORS allow-list (`allowCredentials(true)`) and the two-origin topology work end to end.

**Blocked by:** None (can start immediately)

**Status:** implemented

- [x] Backend boots; `GET /actuator/health` reports UP
- [x] `GET /api/hello` returns a response over HTTP
- [x] SPA loads on `localhost:3000` and displays the API response fetched with `credentials: 'include'`
- [x] CORS rejects/disallows non-allow-listed origins
- [x] `@SpringBootTest` context-loads test passes; SPA builds clean (`tsc` + `vite build`)

Implementation notes: `backend/` (Spring Boot 4.1.1, `spring-boot-starter-webmvc`
— `starter-web` is deprecated on the 4.x line; test support split into
`spring-boot-starter-webmvc-test` / `-security-test` with
`@AutoConfigureMockMvc` now in `org.springframework.boot.webmvc.test.autoconfigure`)
and `frontend/` (Vite + TS + React, Tailwind v4 + shadcn plumbing, port 3000).
Security chain is permit-all skeleton + CORS source bean; CSRF left at secure
default, `csrf.spa()` + auth rules land in ticket 09. Env quirk: Maven needs
`MAVEN_OPTS="-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT"` behind Zscaler TLS
inspection (documented in README).

Correction pass: dev H2 console made actually reachable — Boot 4 split the
console into the `spring-boot-h2console` module (added to the pom), and a
`@Profile("dev")` `@Order(1)` filter chain scoped to `/h2-console/**` grants
SAMEORIGIN framing + CSRF exemption there while the rest of the app keeps
DENY + CSRF-on. Verified live: console 200/SAMEORIGIN, `/api/hello` still
DENY and POSTs still 403.

## Comments

**Verification (do-work-min, 2026-09-15):**

- Implemented: two-origin skeleton — Spring Boot 4.1.1 API on :8080 (JPA/H2, actuator, CORS allow-list with credentials) + Vite/TS React SPA on :3000 fetching `/api/hello` with `credentials: 'include'`.
- Verification steps: `mvn test` 10/10 green (ApiSmokeTests 5, H2ConsoleDevProfileTests 4, contextLoads 1); `npm run build` clean; live checks — `/actuator/health` UP, `/api/hello` 200, CORS preflight allow/reject verified, dev-profile H2 console 200/SAMEORIGIN while `/api/hello` keeps DENY + CSRF-403.
- Reviewer loop: 1 must-fix found (dead H2-console config) → corrected, re-reviewed clean (Must-fix=0).
- Final gate: thermo-nuclear WARN, semgrep PASS, spring-security WARN, spring-web PASS → aggregate **PASS**; report at `artifacts/code-reviewer/08-app-skeleton-end-to-end-ping-compliance.html`.
- All acceptance-criteria checkboxes ticked. Committed as `dfc0e5d` (`feat(auth): scaffold Spring Boot + React two-origin skeleton`).
