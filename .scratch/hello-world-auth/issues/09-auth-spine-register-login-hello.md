# 09: Auth spine — register → login → hello

**What to build:** The full auth loop works in the SPA. A visitor registers (username/email/password ≥ 12; BCrypt hash; role USER, enabled) and gets clear conflict errors on duplicates. A user logs in via the controller-driven `AuthenticationManager` flow — the controller invokes `SessionAuthenticationStrategy` (change-session-id + CSRF rotation) and `securityContextRepository.saveContext(...)` itself, since the fixation DSL is a no-op without an auth filter. Spring Session JDBC over H2 persists sessions. The filter chain permits `/api/auth/**`, requires auth elsewhere, returns 401 (not redirect/403) for anonymous calls. CSRF via `csrf.spa()` plus a permit-all `GET /api/auth/csrf` bootstrap endpoint (deferred tokens). `GET /api/auth/me` returns `{username, role}` or 401; `GET /api/hello` requires auth and returns `"Hello, <username>"`. The SPA has login + register pages, a fetch wrapper (credentials + `X-XSRF-TOKEN` from the `XSRF-TOKEN` cookie, bootstrap on start), `/me` session detection on load, route guards, and a hello page showing the greeting.

**Blocked by:** 08: App skeleton + end-to-end ping

**Status:** implemented

- [x] Register with unique username/email + password ≥ 12 creates USER account, `enabled=true`, BCrypt hash — plaintext never logged/stored
- [x] Duplicate username or email → clear validation error, no account created
- [x] Password < 12 → validation error, no account created
- [x] Correct credentials → session created, secure cookie set; wrong password AND unknown username → identical generic `"Invalid username or password."`
- [x] `GET /api/hello` → `"Hello, <username>"` authenticated; 401 anonymous
- [x] `GET /api/auth/me` → `{username, role}` authenticated; 401 anonymous
- [x] Mutating request without `X-XSRF-TOKEN` → rejected (real CSRF path tested)
- [x] `GET /api/auth/csrf` emits the `XSRF-TOKEN` cookie (permit-all)
- [x] Session persists in `SPRING_SESSION` table (JDBC, not in-memory)
- [x] SPA: register → login → hello greeting works end-to-end; reload preserves session via `/me`
- [x] RFC 7807 problem+json error responses

Implementation notes: backend gained `spring-boot-starter-session-jdbc`
(Boot 4: Spring Session auto-config lives in `spring-boot-session` — without
the starter sessions silently stay container-local) and the full `user`/`auth`
packages. `SecurityConfig` now has the ratified chain: `csrf.spa()` +
explicit `csrfTokenRepository` *after* `spa()` (gh-18718 — `spa()`
overwrites a previously-set repo) so `CsrfAuthenticationStrategy` shares the
instance; `HttpStatusEntryPoint(401)`; `/actuator/health` kept permit-all
(operator probe must answer without credentials — ticket-08 AC). Login is the
documented controller pattern: `authenticate` → `CompositeSessionAuthentication
Strategy(CsrfAuthenticationStrategy + ChangeSessionIdAuthenticationStrategy)`
→ `saveContext`. `DefaultAuthenticationEventPublisher` is wired on the
`ProviderManager` now so ticket 11's listeners will actually fire. `Clock`
bean added; `AppUserDetailsService` already maps `locked_until`/`enabled`
onto `isAccountNonLocked()`/`isEnabled()`. `app.password-min-length`
externalized via `AppProperties`.

**New trap found (Boot 4.1 + MockMvc):** Boot's `EmbeddedWebServerConfiguration`
maps `server.servlet.session.cookie.*` onto `DefaultCookieSerializer`, but its
war-deployment fallback instead reads `ServletContext.getSessionCookieConfig()`
— which under MockMvc yields HttpOnly=false/SameSite=null regardless of
properties. Fixed with a `DefaultCookieSerializerCustomizer` bean that
re-applies `ServerProperties` (customizers run in both paths); verified live on
embedded Tomcat: `SESSION=…; Path=/; HttpOnly; SameSite=Lax`.

Frontend: `react-router-dom` added; `apiFetch` gained `bootstrapCsrf`,
`register`, `login` (re-bootstraps CSRF post-login — token rotates), `getMe`;
`AuthProvider` probes `/me` on load; `RequireAuth`/`RedirectIfAuthed` route
guards; login/register/hello pages on hand-written shadcn-style primitives
(`design-taste-frontend` skill is not installed in `.agents/skills` — polish
applied manually against the existing token theme). Note: `erasableSyntaxOnly`
in the tsconfig forbids TS parameter properties.

## Comments

**Verification (do-work-min, 2026-09-15):**

- Implemented: full auth loop — register (BCrypt, USER/enabled, 409 problem+json
  on duplicates, 400 on <12-char password), controller-driven login with manual
  `SessionAuthenticationStrategy` + `saveContext`, `csrf.spa()` +
  `GET /api/auth/csrf` bootstrap, `/api/auth/me` probe, personalized
  `/api/hello`, Spring Session JDBC persisting to `SPRING_SESSION`, RFC 7807
  errors throughout.
- Test seams: primary HTTP seam via `MockMvc` + `@SpringBootTest` on H2, CSRF
  on with real `GET /api/auth/csrf` → `X-XSRF-TOKEN` flow; fixtures seeded via
  `UserRepository`; session persistence asserted against the `SPRING_SESSION`
  table via `JdbcTemplate`.
- Verification steps: `mvn test` 23/23 green (AuthFlowTests 13, ApiSmokeTests 5,
  H2ConsoleDevProfileTests 4, contextLoads 1); `npm run build` clean; `oxlint`
  0 errors (2 benign fast-refresh warnings). Live curl on embedded Tomcat
  confirmed: csrf bootstrap emits `XSRF-TOKEN`, register 201, login 200 sets
  `SESSION` HttpOnly+SameSite=Lax (and clears `XSRF-TOKEN` per rotation),
  `me`/`hello` return principal/greeting with the cookie and 401 without,
  wrong password → 401 `application/problem+json` "Invalid username or
  password."
- Deviation recorded in code: `/actuator/health` permit-all (operator probe
  must be unauthenticated); spec's "everything else authenticated" would
  otherwise lock the health check behind a session.
- Not yet (later tickets): logout endpoint (10), lockout counters/IP throttle
  listeners (11), reset flow (12), admin (13), prod-profile hardening (14).

## Comments

**Verification (do-work-min, 2026-09-15):**

- Implemented: full auth loop — register (BCrypt, ≥12, conflict errors), controller-driven login with manual `SessionAuthenticationStrategy` + `saveContext`, Spring Session JDBC, `csrf.spa()` + `/api/auth/csrf` bootstrap, `/api/auth/me`, personalized `/api/hello`, SPA login/register pages with route guards and `/me` session restore.
- Verification steps: `mvn test` 49/49 green; `npm run build` + oxlint clean; live curl checks (cookie flags, rotation, 401s, problem+json); sessions persisted in `SPRING_SESSION` (JdbcTemplate-asserted).
- Reviewer loop: clean on first pass (Must-fix=0).
- Final gate: semgrep PASS, thermo-nuclear/spring-security/spring-web WARN (mediums are deferred-by-design to tickets 11/14) → aggregate **PASS**; report `artifacts/code-reviewer/09-auth-spine-register-login-hello-compliance.html`.
- Mutation gate: 98.8% killed (85/86); Critical/High survivors eliminated via test-only changes; report `artifacts/mutation-testing/09-auth-spine-mutation.md`.
- All acceptance-criteria checkboxes ticked. Commits: `d102718` (feature), `7aa63e4` (mutation-strengthened tests).
- Documented deviation: `/actuator/health` stays permit-all (ticket-08 health-probe AC). New trap recorded: Boot 4.1 MockMvc cookie mapping takes the war-deployment path — `DefaultCookieSerializerCustomizer` re-applies `server.servlet.session.cookie.*`.
