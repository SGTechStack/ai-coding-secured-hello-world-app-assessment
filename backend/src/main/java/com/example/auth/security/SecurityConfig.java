package com.example.auth.security;

import com.example.auth.auth.ErrorResponse;
import java.util.List;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy;
import org.springframework.security.web.authentication.session.SessionLimit;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security wiring for the auth mechanism described in
 * {@code assessment-prd.md}: form-login + CSRF + Spring Session (JDBC), cross-origin
 * CORS for the separately-hosted SPA, BCrypt password hashing, account lockout,
 * IP throttling, and role-based (USER/ADMIN) authorization. MFA and the JWT
 * bearer-token alternative remain out of scope; see the PRD's Out of Scope
 * section and {@code docs/jwt-alternative.md}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // hideUserNotFoundExceptions defaults to true: unknown-username and
        // wrong-password both collapse to BadCredentialsException, which is
        // what keeps the /login failure body identical for both cases
        // (anti-enumeration). Left at its default deliberately.
        return provider;
    }

    /**
     * Not auto-wired by Spring Boot here: that only happens for an
     * AuthenticationManager built via the shared {@code
     * AuthenticationManagerBuilder}, which our manually-constructed {@code
     * ProviderManager} bypasses entirely. Without this explicit wiring,
     * {@link LoginAttemptListener} would never receive an authentication
     * success/failure event to act on.
     */
    @Bean
    public AuthenticationEventPublisher authenticationEventPublisher(
            ApplicationEventPublisher applicationEventPublisher) {
        return new DefaultAuthenticationEventPublisher(applicationEventPublisher);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            DaoAuthenticationProvider authenticationProvider, AuthenticationEventPublisher eventPublisher) {
        ProviderManager providerManager = new ProviderManager(authenticationProvider);
        providerManager.setAuthenticationEventPublisher(eventPublisher);
        return providerManager;
    }

    /**
     * Looks up every user's active sessions so a password reset (Story 7) can
     * force-expire all of them via {@code SessionInformation.expireNow()}.
     * Backed by the Spring Session JDBC store rather than an in-memory map:
     * sessions are found by the principal name the repository indexes on
     * every save, so nothing has to register them at login, and the expired
     * flag is written to the session row itself. Declared explicitly so
     * {@code sessionConcurrency()} below picks up this registry instead of
     * creating an in-memory default that would never see those sessions.
     */
    @Bean
    public <S extends Session> SessionRegistry sessionRegistry(FindByIndexNameSessionRepository<S> sessionRepository) {
        return new SpringSessionBackedSessionRegistry<>(sessionRepository);
    }

    /**
     * Frontend and backend now run on separate origins (see
     * {@code app.cors.allowed-origins}), so cross-origin requests need an
     * explicit allow-list rather than the same-origin default. Credentials
     * (the session cookie) are allowed, matching {@code fetch(...,
     * {credentials: 'include'})} on the frontend.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * {@code server.servlet.session.cookie.*} only reaches the session cookie,
     * so the XSRF-TOKEN cookie is given the same SameSite/Secure attributes
     * here explicitly -- otherwise it would carry no SameSite at all, and
     * Secure only when the request itself arrived over TLS (not the case
     * behind a TLS-terminating proxy). A non-blank {@code domain} widens the
     * cookie to a parent domain, which prod needs so the SPA on a sibling
     * subdomain can read the token via {@code document.cookie}; blank keeps
     * it host-only.
     */
    static CookieCsrfTokenRepository csrfTokenRepository(String sameSite, boolean secure, String domain) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> {
            cookie.sameSite(sameSite).secure(secure);
            if (!domain.isBlank()) {
                cookie.domain(domain);
            }
        });
        return repository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager,
            ObjectMapper objectMapper,
            CorsConfigurationSource corsConfigurationSource,
            IpLoginThrottleService ipLoginThrottleService,
            Environment environment,
            @Value("${server.servlet.session.cookie.same-site}") String cookieSameSite,
            @Value("${server.servlet.session.cookie.secure}") boolean cookieSecure,
            @Value("${app.csrf.cookie-domain}") String csrfCookieDomain)
            throws Exception {
        // Gated on the active profile itself, not on spring.h2.console.enabled --
        // that property merely controls whether Boot registers the console
        // servlet, and its absence must never be the only thing standing
        // between the console and an unauthenticated, CSRF-exempt SQL prompt
        // if it's ever accidentally turned on outside dev.
        boolean h2ConsoleExemptionsEnabled = environment.acceptsProfiles(Profiles.of("dev"));
        JsonUsernamePasswordAuthenticationFilter loginFilter =
                new JsonUsernamePasswordAuthenticationFilter(objectMapper);
        loginFilter.setAuthenticationManager(authenticationManager);
        // AbstractAuthenticationProcessingFilter defaults to a request-scoped-only
        // SecurityContextRepository. http.formLogin() would normally rewire this to
        // match HttpSecurity's session-aware repository; since we register this
        // filter manually instead, that wiring must happen explicitly, or a
        // successful login never actually persists into an HttpSession.
        loginFilter.setSecurityContextRepository(new HttpSessionSecurityContextRepository());
        // Same story for session-authentication behavior: http.formLogin()/
        // .sessionManagement() would normally wire fixation protection onto
        // the filter automatically. Bypassing formLogin() for the custom JSON
        // filter means that wiring is skipped by default too -- without this,
        // session-ID rotation-on-login (the App-Standards rotation
        // requirement) would silently do nothing. No registration strategy
        // is needed alongside it: the Spring Session-backed SessionRegistry
        // finds sessions through the store's principal-name index. Story 7
        // allows multiple concurrent sessions per user (a password reset
        // invalidates *all* of them, not just the newest), so no
        // ConcurrentSessionControlAuthenticationStrategy is wired here --
        // a new login must not evict any of the user's other sessions.
        loginFilter.setSessionAuthenticationStrategy(new SessionFixationProtectionStrategy());
        loginFilter.setAuthenticationSuccessHandler((request, response, authentication) -> {
            // Same contract as before the migration: 200, empty body, no redirect.
            response.setStatus(200);
        });
        loginFilter.setAuthenticationFailureHandler((request, response, exception) -> {
            // Same generic body for every failure reason -- wrong password,
            // unknown username, or an active lockout (LockedException) --
            // which is what keeps the anti-enumeration property intact
            // across the account-lockout feature too.
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter()
                    .write(objectMapper.writeValueAsString(new ErrorResponse("Invalid username or password")));
        });

        http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> {
                    csrf.csrfTokenRepository(csrfTokenRepository(cookieSameSite, cookieSecure, csrfCookieDomain))
                            .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler());
                    if (h2ConsoleExemptionsEnabled) {
                        // The H2 console submits its own forms without this
                        // app's CSRF token. Gated on the dev profile itself
                        // (not on spring.h2.console.enabled) so this exemption
                        // can't silently apply outside dev.
                        csrf.ignoringRequestMatchers("/h2-console/**");
                    }
                })
                .headers(headers -> {
                    if (h2ConsoleExemptionsEnabled) {
                        // Spring Security's default (frameOptions().deny())
                        // blocks the H2 console's self-framing UI from
                        // rendering at all.
                        headers.frameOptions(frameOptions -> frameOptions.sameOrigin());
                    }
                })
                // Runs before SecurityContextHolderFilter loads a session's
                // SecurityContext, so a session past its absolute lifetime is
                // invalidated first and this request is treated as anonymous.
                .addFilterBefore(
                        new AbsoluteSessionTimeoutFilter(AbsoluteSessionTimeoutFilter.DEFAULT_MAX_SESSION_AGE),
                        SecurityContextHolderFilter.class)
                // Runs before AuthenticationManager, so a throttled IP never
                // reaches password comparison at all.
                .addFilterBefore(
                        new IpThrottleFilter("/api/auth/login", ipLoginThrottleService),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAt(loginFilter, UsernamePasswordAuthenticationFilter.class)
                // Forces the deferred CSRF token to render its XSRF-TOKEN cookie
                // on every response, not only when something happens to resolve it.
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionConcurrency(concurrency -> concurrency
                        // maximumSessions(...) is what actually registers
                        // ConcurrentSessionFilter (SessionManagementConfigurer only
                        // adds it when a session limit has been set at all) --
                        // without calling this, PasswordResetService's
                        // SessionInformation.expireNow() calls would flag sessions
                        // as expired in the registry but nothing would ever check
                        // for that on a later request, so the old session cookie
                        // would keep working. SessionLimit.UNLIMITED registers the
                        // filter while still allowing unlimited concurrent sessions
                        // per user, matching Story 7's "all existing sessions
                        // [plural] invalidated" on reset, not a max-1 login cap.
                        .maximumSessions(SessionLimit.UNLIMITED)
                        // Left at its default, ConcurrentSessionFilter responds with
                        // a 200 and a plain-text body ("This session has been
                        // expired...") -- `getMe()` on the frontend treats any `ok`
                        // response as success and tries to JSON-parse the body,
                        // which would throw. A plain 401 with no body matches
                        // exactly how AuthController#me() itself already reports
                        // "not authenticated", so the frontend needs no special case.
                        .expiredSessionStrategy(event -> event.getResponse().setStatus(401))))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        // Without formLogin()/httpBasic(), the default entry point is
                        // Http403ForbiddenEntryPoint (403). A plain 401 with no body
                        // matches the contract AuthController#me() already used, so
                        // the frontend's "not authenticated" handling needs no
                        // special-casing between the two now-authenticated() endpoints
                        // and the ones still deciding 401 themselves.
                        (request, response, authException) -> response.setStatus(401)))
                .authorizeHttpRequests(authorize -> {
                    // CORS preflight is unauthenticated by nature -- the browser
                    // never attaches the session cookie or CSRF header to it.
                    authorize.requestMatchers(HttpMethod.OPTIONS, "/**")
                            .permitAll()
                            .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login")
                            .permitAll()
                            // A password-reset request/confirm must be reachable by
                            // someone who is, by definition, not logged in.
                            .requestMatchers("/api/password-reset/**")
                            .permitAll();
                    if (h2ConsoleExemptionsEnabled) {
                        authorize.requestMatchers("/h2-console/**").permitAll();
                    }
                    authorize
                            // Admin rule must precede the broader catch-all below, or
                            // the catch-all's .authenticated() would shadow this and
                            // let any authenticated non-admin user through.
                            .requestMatchers("/api/admin/**")
                            .hasRole("ADMIN")
                            .requestMatchers("/api/auth/me", "/api/hello")
                            .authenticated()
                            // Deny-by-default: anything not explicitly listed above
                            // requires at least a real authenticated session.
                            .anyRequest()
                            .authenticated();
                })
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        // SecurityContextLogoutHandler (added by default) invalidates
                        // the session, and Spring Session itself then expires the
                        // SESSION cookie on the response -- so no deleteCookies()
                        // here, which would only add a second, duplicate clearing
                        // header. This handler just reports 200 with no
                        // redirect/body, matching the pre-migration contract.
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler()));

        return http.build();
    }
}
