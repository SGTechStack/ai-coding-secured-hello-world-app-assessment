# 01: Backend logout endpoint

**What to build:** `POST /api/v1/auth/logout` via Spring Security's logout configurer. It invalidates the session, expires `JSESSIONID`, clears `XSRF-TOKEN`, and returns an empty `204`. It is idempotent, open without a session, and CSRF-protected. See spec §Implementation Decisions › Backend.

**Blocked by:** none

**Status:** done

- [x] Logged in + valid CSRF → `204`, empty body, `JSESSIONID` expired, `XSRF-TOKEN` cleared
- [x] After logout, `GET /me` with the old session → `401`
- [x] No session + valid CSRF → `204`; logging out twice → `204` both times
- [x] Missing or wrong CSRF token → `403` `FORBIDDEN` in the standard error body; the session still works afterwards
- [x] `GET /api/v1/auth/logout` does not end the session
- [x] Security headers present on the logout response
- [x] Tests at the MockMvc HTTP seam, using the real cookie CSRF flow (see the note in the existing login API test); `mvn verify` passes, including JaCoCo ≥80%

## Comments

- Implementer (01): no spec deviations. Notes:
  - All changes are in `SecurityConfig.apiSecurity`. The logout configurer matches only `POST /api/v1/auth/logout` (`PathPatternRequestMatcher`) and answers with `HttpStatusReturningLogoutSuccessHandler(NO_CONTENT)`. Session invalidation, clearing the `SecurityContext` and the CSRF logout handler are the defaults.
  - There is no `permitAll` rule for the path. It isn't needed because the logout filter runs after `CsrfFilter` and before authorization, and it ends the request itself. A missing or bad token gets the JSON `403`, and an anonymous caller gets `204`.
  - `JSESSIONID` is expired with a custom `CookieClearingLogoutHandler`, not `deleteCookies("JSESSIONID")`. The custom handler copies name, path, `HttpOnly`, `Secure` and `SameSite` from `server.servlet.session.cookie`. The default would drop `HttpOnly` and take `Secure` from `request.isSecure()`. Over real HTTP the header is `JSESSIONID=; Max-Age=0; Path=/; Secure; HttpOnly; SameSite=Strict` (without `Secure` under `dev`).
  - Tests: `LogoutApiTest` covers the MockMvc seam, with one test per acceptance box. `SessionCookieTest` gains one real-HTTP test for the expired cookie's attributes, because MockMvc doesn't run Tomcat's cookie processor. The same test checks that the old `JSESSIONID` gets `401` from `/me`, which proves the session is invalidated on the server and not only in a `MockHttpSession`.
  - The logout response only expires `XSRF-TOKEN` and doesn't issue a new one. The SPA's existing "prime if the cookie is missing" path gets a fresh token before the next `POST`, as the spec expects. Logging out twice therefore needs a fresh `GET /csrf` between the calls, and the tests do this.
  - `GET /api/v1/auth/logout` isn't matched by the logout filter and goes through normal authorization and routing. No handler exists for it. The test asserts only that the session survives, not the status code.
