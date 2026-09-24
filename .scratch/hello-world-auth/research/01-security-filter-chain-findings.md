# Findings: SecurityFilterChain design (Ticket 01)

Research date: Sept 2026. Sources are primary (docs.spring.io reference docs/Javadoc, spring.io release blog, spring-projects GitHub source & issues).

## 0. Version pins (Java 21-compatible, current stable)

| Component | Version | Evidence |
| --- | --- | --- |
| Spring Boot | **4.1.1** (released 2026-08-20; latest stable) | https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now , https://github.com/spring-projects/spring-boot/releases |
| Spring Security | **7.1.1** (managed by Boot 4.1.x; 7.1.0 listed in Boot 4.1 dependency upgrades) | https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes , https://docs.spring.io/spring-security/reference/whats-new.html |
| Spring Framework | 7.0.9+ (required by Boot 4.1.1) | https://docs.spring.io/spring-boot/4.1/system-requirements.html |
| Spring Session | 4.x (4.0 pairs with Boot 4 / Security 7; 4.1.0 latest stable) | https://github.com/spring-projects/spring-session/releases/tag/4.0.0 , https://docs.spring.io/spring-session/reference/4.0/guides/java-custom-cookie.html |
| Java | 21 — Boot 4.1 requires **Java 17 minimum**, compatible up to Java 26 | https://docs.spring.io/spring-boot/4.1/system-requirements.html |
| Maven | 3.6.3+ | https://docs.spring.io/spring-boot/4.1/system-requirements.html |
| Servlet | 6.1 (Tomcat 11.0.x embedded) | https://docs.spring.io/spring-boot/4.1/system-requirements.html |

Note: in Boot 4, Spring Session auto-configuration moved to the `spring-boot-session` module — it must be an explicit dependency (https://github.com/spring-projects/spring-session/issues/3622). Also set `server.servlet.session.cookie.same-site` (and `secure`, `http-only`) explicitly; a Boot 4.1 regression can leave SameSite unset instead of `Lax` (https://github.com/spring-projects/spring-boot/issues/48830).

## 1. JSON-body REST login — controller calling `AuthenticationManager`

**Recommendation: a `@RestController` endpoint that calls `AuthenticationManager`, then persists the `SecurityContext` via `SecurityContextRepository`.** This is the pattern documented in the Spring Security reference ("Storing the Authentication manually": "You can use a custom filter or a Spring MVC controller endpoint"), not a custom filter.

Source: https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html (section "Storing the Authentication manually") and https://docs.spring.io/spring-security/reference/servlet/authentication/persistence.html .

Why the extra steps are mandatory on the 6.x/7.x line:

- `SessionManagementFilter` and `SecurityContextPersistenceFilter` are **not** in the default chain since Spring Security 6; only `SecurityContextHolderFilter` runs, and it **loads but does not save** the context. A manual login **must** call `securityContextRepository.saveContext(context, request, response)` — that is what creates the `HttpSession` and triggers the session cookie.
  Source: session-management.html ("Moving Away From SessionManagementFilter", "Understanding Require Explicit Save").
- Since there is no authentication filter, **session-fixation protection is not applied automatically** — the docs state "authentication mechanisms themselves must invoke the `SessionAuthenticationStrategy`." The controller must invoke one, e.g. a `CompositeSessionAuthenticationStrategy` of `CsrfAuthenticationStrategy` (rotates/clears the CSRF token on auth) + `ChangeSessionIdAuthenticationStrategy` (default fixation defense on Servlet 3.1+; delegates to `HttpServletRequest#changeSessionId()`). Configuring `.sessionManagement(s -> s.sessionFixation(...))` has **no effect** without an auth filter.
  Source: session-management.html ("Configuring Session Fixation Protection" — `changeSessionId` is the default on Servlet 3.1+ containers), persistence.html (new session id issued on login).
- Alternative considered: a custom `AbstractAuthenticationProcessingFilter` subclass that parses JSON gets `SessionAuthenticationStrategy`, context save, and success/failure handlers wired for free — but requires filter-order placement, request matcher, and entry-point plumbing. The documented controller pattern is simpler and equally correct; there is no built-in JSON login filter in the servlet stack.

## 2. CSRF for a cross-origin SPA — use `csrf.spa()` (new in Spring Security 7) **plus a bootstrap trigger**

**Spring Security 7.0 added first-class SPA CSRF support: `http.csrf(csrf -> csrf.spa())`.**
Source: https://docs.spring.io/spring-security/reference/7.0/whats-new.html ("Added support for SPA-based CSRF configuration") and https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html ("Single-Page Applications").

Per the 7.1.x source, `CsrfConfigurer.spa()` is exactly:
- `CookieCsrfTokenRepository.withHttpOnlyFalse()` — persists the token in a JS-readable `XSRF-TOKEN` cookie; token is read back from the `X-XSRF-TOKEN` header (Angular convention).
- `SpaCsrfTokenRequestHandler` (private composite): `handle()` delegates to `XorCsrfTokenRequestAttributeHandler` (BREACH-masked token exposed as request attribute); `resolveCsrfTokenValue()` uses the **plain** handler when the client sent the header value and the **xor** handler otherwise — i.e., the SPA may echo the *raw* cookie value in the header while rendered form values remain BREACH-masked.
Source: https://github.com/spring-projects/spring-security/blob/7.1.x/config/src/main/java/org/springframework/security/config/annotation/web/configurers/CsrfConfigurer.java ; BREACH default: csrf.html ("By default, the `XorCsrfTokenRequestAttributeHandler` is used").

**Critical caveat — `spa()` does NOT eagerly emit the cookie.** Tokens are loaded lazily: `CsrfFilter` calls `tokenRepository.loadDeferredToken(...)` and the token is only generated + persisted when the `DeferredCsrfToken` is accessed (`get()`), which for `CookieCsrfTokenRepository` writes `Set-Cookie: XSRF-TOKEN`. On a plain GET nothing touches the token, so no cookie is set — and the very first `POST /api/auth/login` already requires a valid token. Sources: CsrfFilter source (https://github.com/spring-projects/spring-security/blob/7.1.x/web/src/main/java/org/springframework/security/web/csrf/CsrfFilter.java), CookieCsrfTokenRepository source (loadToken returns null without the cookie; saveToken writes Set-Cookie: https://github.com/spring-projects/spring-security/blob/7.1.x/web/src/main/java/org/springframework/security/web/csrf/CookieCsrfTokenRepository.java), csrf.html ("Loading of the CsrfToken is now deferred by default").

**How the SPA obtains the token** — two sanctioned options:
1. **Bootstrap endpoint (recommended):** `GET /api/auth/csrf` with `permitAll`, using `CsrfTokenArgumentResolver`: `public CsrfToken csrf(CsrfToken t) { return t; }`. Serializing it forces generation → `XSRF-TOKEN` cookie is set. The docs' `/csrf` endpoint pattern is documented under "Mobile Applications" but explicitly "can be used for any type of application"; it "should be called when the application is launched... and also after authentication success and logout success" because `CsrfAuthenticationStrategy` and `CsrfLogoutHandler` clear the token. Source: csrf.html ("Mobile Applications" + SPA warning box).
2. A small `OncePerRequestFilter` after `CsrfFilter` that resolves the token on every request (the old 6.x `CsrfCookieFilter` pattern) — works but forces token load on every request; unnecessary given option 1.

The React app then reads the `XSRF-TOKEN` cookie (hence `withHttpOnlyFalse()`) and echoes it in `X-XSRF-TOKEN` on every mutating request; `fetch` must use `credentials: 'include'`.

**Known pitfall:** `spa()` unconditionally overwrites a previously-set custom `CsrfTokenRepository` — call `.spa()` first and customize after, or skip `spa()` (https://github.com/spring-projects/spring-security/issues/18718).

Alternative design (valid but not recommended here): keep the default `HttpSessionCsrfTokenRepository` + the `/csrf` bootstrap endpoint — avoids a JS-readable cookie but requires a session before login and doesn't save much.

## 3. CORS with credentials + CSRF

- CORS must be processed **before** Spring Security's authorization: preflight `OPTIONS` requests carry no cookies, so if security ran first they would be rejected. Provide a `UrlBasedCorsConfigurationSource` bean and enable `.cors(Customizer.withDefaults())` — Spring Security auto-detects a `UrlBasedCorsConfigurationSource` bean (or a `corsFilter` bean) and registers `CorsFilter` early in the chain. Source: https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html .
- `OPTIONS` is exempt from CSRF anyway (`DEFAULT_CSRF_MATCHER` protects only non-GET/HEAD/TRACE/OPTIONS). Source: CsrfFilter Javadoc/source above.
- `setAllowCredentials(true)` requires **explicit** `allowedOrigins` (`http://localhost:3000`) — wildcard `*` is invalid with credentials. Allow headers `Content-Type`, `X-XSRF-TOKEN` (and `X-CSRF-TOKEN`). The session cookie and `XSRF-TOKEN` cookie both travel only because credentials mode is on.
- SameSite nuance: `localhost:3000` ↔ `localhost:8080` are *cross-origin but same-site* (ports don't affect site), so `SameSite=Lax` session + CSRF cookies still work in dev. A genuinely cross-site deployment needs `SameSite=None; Secure` on both — session cookie via `server.servlet.session.cookie.same-site` / `DefaultCookieSerializer` (https://docs.spring.io/spring-session/reference/4.0/configuration/common.html) and the CSRF cookie via `CookieCsrfTokenRepository#setCookieCustomizer` (source above).
- New in Security 7.1: `PreFlightRequestFilter` / `cors.preFlightRequestHandler(...)` handles preflight even earlier (before `CorsFilter`) using a Spring Framework `PreFlightRequestHandler`; optional — the `CorsConfigurationSource` bean route is sufficient. Source: cors.html ("PreFlightRequestHandler and PreFlightRequestFilter"), https://github.com/spring-projects/spring-framework/issues/36482 .

## 4. `failed_login_attempts` / `locked_until` — split the concern

Recommended split (all server-side):

- **Lockout gate:** implement `UserDetails.isAccountNonLocked()` as `lockedUntil == null || lockedUntil.isBefore(now)`. `AbstractUserDetailsAuthenticationProvider` runs *pre-authentication checks* (`UserDetailsChecker`) **before** password verification and throws `LockedException` — a locked account is rejected even with the correct password, per spec. Source: https://docs.spring.io/spring-security/reference/7.1/api/java/org/springframework/security/authentication/dao/AbstractUserDetailsAuthenticationProvider.html (`setPreAuthenticationChecks`). `isEnabled()` maps `enabled` → `DisabledException` similarly.
- **Enumeration resistance is built in:** `hideUserNotFoundExceptions` defaults so that "username not found" and "wrong password" both surface as `BadCredentialsException` — the controller can return one generic 401 for any `AuthenticationException`. Source: same Javadoc (`setHideUserNotFoundExceptions`).
- **Counter bookkeeping:** `AuthenticationEventPublisher` events. Register `@Bean DefaultAuthenticationEventPublisher` and use `@EventListener` on `AuthenticationSuccessEvent` (reset `failed_login_attempts`) and `AuthenticationFailureBadCredentialsEvent` (increment; set `locked_until` at threshold). Source: https://docs.spring.io/spring-security/reference/servlet/authentication/events.html (event list incl. `AuthenticationFailureLockedEvent`, `AuthenticationFailureDisabledEvent`; `DefaultAuthenticationEventPublisher` bean required). Caveat: if you construct your own `ProviderManager`, call `setAuthenticationEventPublisher(...)` or no events fire; and unknown usernames also produce BadCredentials events — the listener should no-op when no user row exists.
- **Not** a custom `AuthenticationProvider`: it would duplicate DaoAuthenticationProvider's password/lockout machinery for no benefit. Service-level checks inside the login controller work but couple lockout to one entry point; events catch all authentication paths.
- IP-level throttling (Story 3) is orthogonal: do it in a dedicated filter/service keyed by IP before the login endpoint (auth events don't cleanly expose the request/IP).

## 5. Authorization rules

- `/api/auth/**` → `permitAll()` (login, register, csrf bootstrap, password reset)
- `/api/admin/**` → `hasRole("ADMIN")` (matches `ROLE_ADMIN`; return `new SimpleGrantedAuthority("ROLE_" + user.getRole())` from the `UserDetailsService`)
- `/api/hello` (and any other) → `authenticated()`
- Unauthenticated → 401: configure `exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))`. Without it the default may produce 403/redirects instead of the spec's 401. Authenticated-but-forbidden (USER on admin) → default `AccessDeniedHandlerImpl` → 403. CSRF failures → 403 via `AccessDeniedHandler`.
- Do **not** enable `formLogin`/`httpBasic` (no redirects, no basic prompt); keep `.logout(l -> l.logoutUrl("/api/auth/logout").deleteCookies("SESSION").logoutSuccessHandler((q,s,a)->s.setStatus(200)))` — `SecurityContextLogoutHandler` invalidates the session server-side (replay rejected → Story 4).

## 6. Recommended configuration sketch

```java
@Configuration
@EnableWebSecurity // optional under Boot, kept for clarity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())               // uses CorsConfigurationSource bean below
            .csrf(csrf -> csrf.spa())                      // CookieCsrfTokenRepository.withHttpOnlyFalse
                                                           // + SpaCsrfTokenRequestHandler (BREACH)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()          // login/register/csrf/reset
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/hello").authenticated()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .deleteCookies("SESSION")                            // Spring Session cookie name
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(200)))
            .securityContext(ctx -> ctx.securityContextRepository(securityContextRepository));
        return http.build();
    }

    @Bean UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(List.of("http://localhost:3000"));
        c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        c.setAllowedHeaders(List.of("Content-Type","X-XSRF-TOKEN","X-CSRF-TOKEN"));
        c.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", c);
        return source;
    }

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean AuthenticationManager authenticationManager(
            UserDetailsService uds, PasswordEncoder enc, AuthenticationEventPublisher pub) {
        var provider = new DaoAuthenticationProvider(uds);   // isAccountNonLocked -> LockedException
        provider.setPasswordEncoder(enc);
        var pm = new ProviderManager(provider);
        pm.setAuthenticationEventPublisher(pub);             // required or no auth events fire
        return pm;
    }

    @Bean AuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher aep) {
        return new DefaultAuthenticationEventPublisher(aep);
    }

    @Bean SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository repo) {
        return new CompositeSessionAuthenticationStrategy(List.of(
            new CsrfAuthenticationStrategy(repo),            // rotate CSRF token on login
            new ChangeSessionIdAuthenticationStrategy()));   // session-fixation protection
    }
}
```

Login endpoint (per the documented "Storing the Authentication manually" pattern, plus the session strategy the mechanism must invoke itself):

```java
@PostMapping("/api/auth/login")
public ResponseEntity<?> login(@RequestBody LoginRequest body,
                               HttpServletRequest req, HttpServletResponse res) {
    try {
        Authentication auth = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
        sessionAuthenticationStrategy.onAuthentication(auth, req, res);  // changeSessionId + CSRF rotate
        SecurityContext ctx = securityContextHolderStrategy.createEmptyContext();
        ctx.setAuthentication(auth);
        securityContextHolderStrategy.setContext(ctx);
        securityContextRepository.saveContext(ctx, req, res);            // creates session + cookie
        return ResponseEntity.ok(Map.of("username", auth.getName()));
    } catch (AuthenticationException ex) {                               // bad creds / locked / disabled
        return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
    }
}
```

CSRF bootstrap (required before first mutating request; call again after login/logout):

```java
@GetMapping("/api/auth/csrf")
public CsrfToken csrf(CsrfToken token) { return token; }   // forces generate+save -> XSRF-TOKEN cookie
```

Lockout listener:

```java
@Component
class LoginAttemptListener {
    @EventListener void onSuccess(AuthenticationSuccessEvent e)            { users.resetFailed(e.getAuthentication().getName()); }
    @EventListener void onBadCreds(AuthenticationFailureBadCredentialsEvent e) { users.recordFailureAndMaybeLock(e.getAuthentication().getName()); }
}
```

`UserDetails.isAccountNonLocked()` ⇒ `lockedUntil == null || lockedUntil.isBefore(Instant.now())`.

## 7. Notable surprises / gotchas

1. `csrf.spa()` exists only on Security 7+; on 6.5 the docs still show the hand-rolled `SpaCsrfTokenRequestHandler` + `CsrfCookieFilter`. On 7.x the request handler is built-in but the cookie is still **not** eagerly rendered — a bootstrap endpoint (or render filter) remains mandatory.
2. `spa()` silently discards a previously-configured custom `CsrfTokenRepository` (gh-18718) — ordering matters.
3. Manual login must both `saveContext` **and** invoke `SessionAuthenticationStrategy`; the `sessionManagement().sessionFixation()` DSL is a no-op without an authentication filter.
4. Events are only published if `DefaultAuthenticationEventPublisher` is registered **and** set on the `ProviderManager` you build yourself.
5. Security 7 `Authentication` results carry a `FACTOR_PASSWORD` authority (new MFA infra) — harmless, but shows up in authority lists (dao-authentication-provider.html).
6. Boot 4 split Spring Session auto-config into `spring-boot-session` — forgetting it silently disables Spring Session.
