package com.example.helloauth.config;

import jakarta.servlet.DispatcherType;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.boot.session.autoconfigure.DefaultCookieSerializerCustomizer;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.access.DelegatingAccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The ratified security chain (ticket 01 findings):
 *
 * <ul>
 *   <li>CSRF uses the OWASP <em>Synchronizer Token</em> pattern: the token
 *       lives only in the server-side session
 *       ({@link HttpSessionCsrfTokenRepository}), never in a cookie. The SPA
 *       reads it from the {@code GET /api/auth/csrf} JSON body, keeps it in
 *       memory and echoes it in {@code X-XSRF-TOKEN}. The XOR request
 *       handler masks each emitted copy (BREACH). Tokens are deferred, so
 *       the endpoint exists to force generation.</li>
 *   <li>The anonymous-facing endpoints are permit-all by explicit path —
 *       not {@code /api/auth/**}, so a future route under that prefix can't
 *       ship unauthenticated by accident. {@code /api/admin/**} is
 *       ADMIN-only, everything else authenticated; anonymous → 401 via
 *       {@code HttpStatusEntryPoint} (never redirect/403).</li>
 *   <li>No formLogin/httpBasic — login is controller-driven
 *       ({@link com.example.helloauth.auth.AuthController}), which is why the
 *       {@link SessionAuthenticationStrategy} and
 *       {@link SecurityContextRepository} are explicit beans the controller
 *       injects.</li>
 *   <li>Spring Session JDBC persists the session; the context repository is
 *       the standard HttpSession-backed one (the session itself is the
 *       repository-managed one).</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            CsrfTokenRepository csrfTokenRepository,
            SecurityContextRepository securityContextRepository) throws Exception {
        http
            .cors(Customizer.withDefaults())
            // Synchronizer Token pattern (OWASP CSRF cheat sheet): the token
            // is stored in the session and compared against the header. Not
            // csrf.spa() — that installs the cookie (naive double-submit)
            // repository. The repository bean is shared with
            // CsrfAuthenticationStrategy and CsrfLogoutHandler.
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler()))
            .securityContext(context ->
                context.securityContextRepository(securityContextRepository))
            // Defense-in-depth headers (security-review F-01). This is a
            // JSON-only API — no HTML, script, or frame rendering — so the
            // CSP denies every source and every framing ancestor outright;
            // the SPA's own CSP belongs to its static host, out of repo
            // scope. Referrer-Policy stops the ?token= reset-link URL from
            // leaking to third-party origins via Referer. The defaults this
            // configurer already emits (nosniff, DENY framing, no-store,
            // X-XSS-Protection: 0) are untouched.
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'none'; frame-ancestors 'none'"))
                .referrerPolicy(referrer -> referrer.policy(
                    ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicy(permissions -> permissions.policy(
                    "camera=(), microphone=(), geolocation=()")))
            .authorizeHttpRequests(auth -> auth
                // Security 7 authorizes every dispatcher type, including
                // ERROR — permit it so anonymous requests that hit the
                // container error dispatch (e.g. a 404 on a permitted path)
                // surface the real status instead of a blanket 401.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/auth/register", "/api/auth/login",
                    "/api/auth/csrf", "/api/auth/me",
                    "/api/auth/password-reset/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // The operator health probe must answer without credentials
                // (ticket-08 AC); "everything else authenticated" would make
                // the uptime check useless.
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                .accessDeniedHandler(accessDeniedHandler()))
            // POST /api/auth/logout (ticket 10). CSRF stays on — with CSRF
            // enabled logoutUrl() matches POST only. The default handlers run:
            // SecurityContextLogoutHandler clears the context and invalidates
            // the session, which deletes the SPRING_SESSION row server-side —
            // a replayed pre-logout cookie resolves to anonymous → 401.
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                // Clears the CSRF token (the SPA re-bootstraps after logout).
                // Added explicitly even though the configurer may wire one
                // itself — a duplicate clear is idempotent.
                .addLogoutHandler(new CsrfLogoutHandler(csrfTokenRepository))
                // Belt-and-suspenders: invalidation already expires the Spring
                // Session cookie, this also sends the clearing Set-Cookie.
                .deleteCookies("SESSION")
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler()));
        return http.build();
    }

    /**
     * Dev-profile-only chain that makes the H2 console reachable under the
     * secure defaults. The console is a frameset (needs {@code X-Frame-Options:
     * SAMEORIGIN}, not the default {@code DENY}) and POSTs without CSRF tokens.
     * Scoped to {@code /h2-console/**} via the security matcher, so the rest of
     * the app keeps DENY framing and CSRF-on. Absent without the {@code dev}
     * profile — prod leaves the console disabled entirely.
     */
    @Bean
    @Profile("dev")
    @Order(1)
    SecurityFilterChain h2ConsoleSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(PathRequest.toH2Console())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(csrf -> csrf.ignoringRequestMatchers(PathRequest.toH2Console()))
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }

    /**
     * Origin allow-list comes from {@code app.cors.allowed-origins} (ticket
     * 14): the dev/local default is the Vite origin; prod binds
     * {@code APP_CORS_ALLOWED_ORIGINS} with a blank default — an unset
     * allow-list permits nothing (fail-closed), so the list is always an
     * explicit deployment decision, never a hardcoded one.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.getCors().getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "Authorization"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * DaoAuthenticationProvider gives the lockout gate for free:
     * {@code isAccountNonLocked()} is a pre-auth check that throws
     * {@code LockedException} before password verification, and user-not-found
     * collapses to {@code BadCredentialsException} for enumeration resistance.
     * The event publisher must be set on a hand-built ProviderManager or no
     * authentication events fire — ticket 11 keeps them for audit logging only
     * (lockout/throttle state is driven service-level in
     * {@link com.example.helloauth.auth.LoginService}, not by listeners).
     */
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder, AuthenticationEventPublisher eventPublisher) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        ProviderManager providerManager = new ProviderManager(provider);
        providerManager.setAuthenticationEventPublisher(eventPublisher);
        return providerManager;
    }

    @Bean
    AuthenticationEventPublisher authenticationEventPublisher(
            ApplicationEventPublisher applicationEventPublisher) {
        return new DefaultAuthenticationEventPublisher(applicationEventPublisher);
    }

    /**
     * The login mechanism invokes this itself — session-fixation protection
     * via session-id change plus CSRF token rotation (the SPA re-bootstraps
     * its token after login because {@link CsrfAuthenticationStrategy}
     * clears the old one).
     */
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(
            CsrfTokenRepository csrfTokenRepository) {
        return new CompositeSessionAuthenticationStrategy(List.of(
            new CsrfAuthenticationStrategy(csrfTokenRepository),
            new ChangeSessionIdAuthenticationStrategy()));
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Session-backed CSRF token store (Synchronizer Token pattern). The token
     * is a random value kept in the Spring Session row and never written to a
     * cookie, so cookie injection from a sibling subdomain or plain-HTTP
     * network cannot plant a matching value (the weakness OWASP cites for the
     * naive double-submit cookie). The header name stays {@code X-XSRF-TOKEN}
     * so the CORS allow-list and the SPA contract are unchanged.
     *
     * <p>Declared as a bean so {@link #sessionAuthenticationStrategy} (rotate
     * on login) and the logout handler (clear on logout) share the instance.
     */
    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName(CSRF_HEADER_NAME);
        return repository;
    }

    /** Header the SPA echoes the token in; also on the CORS allow-list. */
    static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";

    /** Problem title that marks a CSRF rejection; the SPA retries once on it. */
    static final String CSRF_REJECTED_TITLE = "Invalid CSRF token";

    /**
     * CSRF failures get a distinguishable RFC 7807 body so the SPA can tell
     * "token missing or stale" (for example the session expired, taking the
     * token with it) apart from an authorization denial, fetch a fresh token
     * and retry once. Every other access denial keeps the default bare 403.
     */
    static AccessDeniedHandler accessDeniedHandler() {
        LinkedHashMap<Class<? extends AccessDeniedException>, AccessDeniedHandler> handlers =
            new LinkedHashMap<>();
        handlers.put(CsrfException.class, (request, response, ex) -> {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\""
                + CSRF_REJECTED_TITLE + "\",\"status\":403,"
                + "\"detail\":\"Missing or invalid CSRF token.\"}");
        });
        return new DelegatingAccessDeniedHandler(handlers, new AccessDeniedHandlerImpl());
    }

    /**
     * Re-applies {@code server.servlet.session.cookie.*} onto Spring Session's
     * {@code DefaultCookieSerializer}. Boot's embedded-server auto-config does
     * this mapping itself, but its war-deployment fallback reads the
     * {@code ServletContext}'s {@code SessionCookieConfig} instead — which in
     * non-embedded environments (e.g. MockMvc) yields HttpOnly=false and no
     * SameSite regardless of the properties. Customizers run in both paths, so
     * this keeps the session cookie's security attributes deterministic.
     */
    @Bean
    DefaultCookieSerializerCustomizer sessionCookieCustomizer(
            ServerProperties serverProperties) {
        org.springframework.boot.web.server.Cookie cookie =
            serverProperties.getServlet().getSession().getCookie();
        return serializer -> {
            if (cookie.getName() != null) {
                serializer.setCookieName(cookie.getName());
            }
            if (cookie.getPath() != null) {
                serializer.setCookiePath(cookie.getPath());
            }
            if (cookie.getDomain() != null) {
                serializer.setDomainName(cookie.getDomain());
            }
            if (cookie.getMaxAge() != null) {
                serializer.setCookieMaxAge((int) cookie.getMaxAge().getSeconds());
            }
            if (cookie.getHttpOnly() != null) {
                serializer.setUseHttpOnlyCookie(cookie.getHttpOnly());
            }
            if (cookie.getSecure() != null) {
                serializer.setUseSecureCookie(cookie.getSecure());
            }
            if (cookie.getSameSite() != null) {
                serializer.setSameSite(cookie.getSameSite().attributeValue());
            }
        };
    }

    /**
     * Injected wherever lockout/expiry time math happens — tests control time
     * by substituting this bean (ticket 05 seam). The user details service
     * already uses it for the {@code locked_until} check.
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
