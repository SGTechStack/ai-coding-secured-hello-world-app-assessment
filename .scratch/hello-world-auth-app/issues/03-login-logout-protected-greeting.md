# 03: Login + logout + protected greeting

**What to build:** The core auth loop. A registered user logs in with username and password; on success a server-side session is created via a secure HttpOnly cookie (Spring Session), and `failed_login_attempts` resets to 0. Incorrect credentials are rejected with a generic error that doesn't reveal whether the username exists. An authenticated session can call the protected greeting endpoint and see `"Hello, <username>"`; without a valid session, that endpoint returns 401. Logout invalidates the server-side session and clears the cookie, and a replayed pre-logout cookie is rejected as unauthenticated.

**Blocked by:** 02 (needs a registered account to log in as)

**Status:** done

- [x] Correct credentials on a registered, enabled, non-locked account → session created, secure session cookie set, `failed_login_attempts` resets to 0.
- [x] Incorrect credentials → rejected with a generic error message that does not reveal whether the username exists; `failed_login_attempts` increments.
- [x] `GET /api/hello` with a valid authenticated session → `"Hello, <username>"`.
- [x] `GET /api/hello` with no session, or an invalid/expired one → 401.
- [x] Logout invalidates the server-side session and clears the session cookie.
- [x] A session cookie captured before logout is rejected as unauthenticated when replayed after logout.
- [x] Session cookie attributes: `HttpOnly`, `Secure` in production, `SameSite`; session-fixation protection in place.
- [x] CSRF protection enabled for login/logout (state-changing, cookie-based auth).
- [x] React app has working login and logout flows, and renders the protected greeting once logged in.

## Implementation notes

**Scoping decisions**

- Used Spring Security's built-in servlet `HttpSession` (backed by Tomcat's native session cookie) rather than adding `spring-session-core`. The spec names "Spring Session" but the actual requirement — server-side session state behind a secure cookie, invalidated on logout — is fully satisfied without the externalized-session library, which exists for horizontal scaling / pluggable storage that this app doesn't need. Cookie renamed from the container default (`JSESSIONID`) to `SESSION` via `server.servlet.session.cookie.*` in `application.yml`.
- `failed_login_attempts` increment/reset is wired here (reset to 0 on success in `LoginSuccessHandler`, increment on failure in `LoginFailureHandler`), but lockout *enforcement* (checking `locked_until`, locking after N failures) is explicitly ticket 04's job, not this one.

**Backend**

- New in `auth`: `UserPrincipal` (`UserDetails` adapter over `User`), `AppUserDetailsService` (replaces Spring Boot's auto-generated default user), `LoginSuccessHandler`/`LoginFailureHandler` (the latter always returns the identical generic `"Invalid username or password"` message whether the username exists or not — enumeration resistance), `LogoutSuccessResponseHandler` (204, no redirect), `RestAuthenticationEntryPoint` (401 JSON instead of a login-page redirect, since this is a JSON API), `CsrfTokenController` (`GET /api/csrf`), `LoginResponse` DTO.
- `HelloController`: `GET /api/hello` returns `"Hello, <username>"` via `@AuthenticationPrincipal`.
- `SecurityConfig`: `formLogin` at `/api/auth/login` (form-encoded `username`/`password`), `logout` at `/api/auth/logout`, CSRF enabled globally via `CookieCsrfTokenRepository.withHttpOnlyFalse()` (readable `XSRF-TOKEN` cookie, `X-XSRF-TOKEN` header), session-fixation protection is Spring Security's unconfigured default (already rotates the session ID on login).
- `CorsConfig` was converted from a `WebMvcConfigurer` to a `CorsConfigurationSource` bean consumed directly by `SecurityConfig`'s `.cors(...)`. This was a real bug found during live verification, not just a style choice — see below.

**Two real bugs found only by live browser verification (not caught by MockMvc tests)**

1. **CORS silently didn't apply to `/api/auth/login` or `/api/auth/logout`.** The original `WebMvcConfigurer`-based `CorsConfig` only affects MVC-dispatched requests. Spring Security's `formLogin`/`logout` are handled entirely inside the security filter chain, which runs *before* MVC dispatch, so those endpoints never saw the MVC CORS config at all — the browser got a hard CORS failure with no `Access-Control-Allow-Origin` header. Fixed by exposing a `CorsConfigurationSource` bean and wiring it into `HttpSecurity.cors(...)` directly. `/api/health`, `/api/auth/register`, and `/api/hello` happened to work before this fix only because they're plain `@RestController` endpoints that do reach MVC dispatch — the bug was specifically about security-filter-handled endpoints.
2. **The `SESSION` cookie name never took effect.** `spring.session.cookie.name` only applies when `spring-session-core` is on the classpath; without it, the property is silently ignored and Tomcat's own default (`JSESSIONID`) is used. Fixed by moving the cookie config to `server.servlet.session.cookie.*`, the correct property namespace for the servlet container's native session cookie.

Both were invisible to the MockMvc integration tests, since MockMvc doesn't exercise real cross-origin fetches or real servlet container cookie naming — this is why live browser verification (not just `mvn test`) mattered here.

**Frontend**

- `LoginForm.tsx`, `ProtectedGreeting.tsx` (greeting + logout button). `App.tsx` now branches between the logged-out view (login + register forms) and the logged-in view (protected greeting) based on local state set from a successful login.
- `api/client.ts` gained a `csrfHeader()` helper: reads the `XSRF-TOKEN` cookie, fetching `/api/csrf` first if it isn't present yet, and returns the `X-XSRF-TOKEN` header to attach. `register()` now also sends this header, since CSRF is enabled globally (registration is a state-changing POST too).

**Tests**

- `LoginLogoutHelloTest`: successful login (session created, cookie present, counter reset, subsequent `/api/hello` succeeds), wrong password (generic message, counter increments), unknown username (byte-for-byte identical generic message and empty `details`, proving no enumeration signal), logout + replay rejection (same session object reused via `MockHttpSession` propagation — MockMvc doesn't surface real `Set-Cookie` cookies on responses, so tests propagate the underlying session object instead, which is behaviorally equivalent to a real browser round-tripping the same cookie).
- `AuthControllerRegistrationTest` updated: every POST now needs `.with(csrf())` since CSRF became globally enabled in this ticket.
- `mvn clean verify`: 11/11 tests pass, BUILD SUCCESS (re-confirmed after the CORS/cookie fixes above).
- Frontend: `npm run typecheck` and `npm run build` both pass.

**Live verification**

Registered a fresh account, logged in, confirmed the `Set-Cookie` response header (`SESSION=...; Path=/; HttpOnly; SameSite=Lax`, no `Secure` since dev runs over HTTP as documented), saw the live greeting render, logged out (204), then directly called `/api/hello` again and confirmed a 401 with `{"message":"Authentication required"}` — the session was genuinely gone server-side, not just hidden client-side.
