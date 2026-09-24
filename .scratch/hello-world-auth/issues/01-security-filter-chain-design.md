# Security filter-chain design

Type: research
Status: resolved
Blocked by: —

## Question

On the current stable Spring Boot / Spring Security line compatible with Java 21, what is the correct `SecurityFilterChain` design for this spec?

Sub-questions:

- How to expose a **JSON-body REST login** (`POST /api/auth/login`) — custom filter vs a controller calling `AuthenticationManager` — such that Spring Security still creates the session, applies session-fixation protection, and issues the session cookie.
- **CSRF for a cross-origin SPA:** which token mechanism (`CookieCsrfTokenRepository` + `XSRF-TOKEN` cookie vs deferred token + `XorCsrfTokenRequestAttributeHandler` / bootstrap endpoint) is correct on the current Spring Security line, and how the React app obtains the token before its first mutating request.
- **CORS with credentials** interplay: allow-listed origin + `Access-Control-Allow-Credentials` + CSRF together — ordering and pitfalls.
- Where do **`failed_login_attempts` / `locked_until` hooks** live: custom `AuthenticationProvider`, `AuthenticationEventPublisher` listeners, or service-level checks inside the login call?
- Authorization rules: `GET /api/hello` authenticated, `/api/admin/**` → `hasRole("ADMIN")`, auth endpoints permit-all.

Investigate against primary sources (Spring Security / Spring Session reference docs, version release notes). Pin the exact Spring Boot/Security versions recommended for Java 21.

Deliver a findings file plus a recommended configuration sketch; record the recommendation as the answer.

## Answer

Pin **Spring Boot 4.1.1 / Spring Security 7.1.x** (Java 17+ baseline, Java 21 fully supported; Spring Session 4.x via the `spring-boot-session` module). Recommendation:

- **Login:** a `@RestController` `POST /api/auth/login` calling `AuthenticationManager` (documented "Storing the Authentication manually" pattern) — not a custom filter. The controller must itself (a) invoke a `SessionAuthenticationStrategy` (`ChangeSessionIdAuthenticationStrategy` + `CsrfAuthenticationStrategy`) for session-fixation protection and CSRF rotation, and (b) call `securityContextRepository.saveContext(...)` — `SecurityContextHolderFilter` only loads the context since Spring Security 6.
- **CSRF:** use the new Spring Security 7 `csrf.spa()` (cookie repo `withHttpOnlyFalse` + BREACH-aware request handler), plus a `permitAll` `GET /api/auth/csrf` bootstrap endpoint (`CsrfToken` arg) — required because tokens are deferred and the cookie is not emitted until the token is touched; call it at app start and after login/logout.
- **CORS:** `UrlBasedCorsConfigurationSource` bean + `.cors(withDefaults())`; explicit allowed origin `http://localhost:3000`, `allowCredentials(true)`, allow `X-XSRF-TOKEN`. CorsFilter runs before authorization; OPTIONS preflights are CSRF-exempt.
- **Lockout:** `locked_until` → `UserDetails.isAccountNonLocked()` (pre-auth check throws `LockedException` before password verification); counters via `@EventListener` on `AuthenticationSuccessEvent`/`AuthenticationFailureBadCredentialsEvent` with a `DefaultAuthenticationEventPublisher` bean set on the `ProviderManager`.
- **Authorization:** `/api/auth/**` permitAll, `/api/admin/**` `hasRole("ADMIN")`, `/api/hello` authenticated; `HttpStatusEntryPoint(401)` so anonymous access yields 401 not 403/redirect; no formLogin/httpBasic.

Full rationale, per-claim citations, and config sketch: `../research/01-security-filter-chain-findings.md`
