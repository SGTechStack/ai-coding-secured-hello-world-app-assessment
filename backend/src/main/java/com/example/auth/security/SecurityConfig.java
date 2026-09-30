package com.example.auth.security;

import com.example.auth.audit.AuditLogger;
import com.example.auth.auth.ErrorCode;
import com.example.auth.auth.ErrorResponse;
import com.example.auth.security.ratelimit.RateLimitExceededException;
import com.example.auth.security.ratelimit.RateLimiters;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HeaderWriterLogoutHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionLimit;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.header.writers.ClearSiteDataHeaderWriter;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring Security wiring for {@code assessment-prd.md} under App-Standards / IM8:
 *
 * <ul>
 *   <li>JSON login ({@link JsonUsernamePasswordAuthenticationFilter}) with rate limits, account
 *       lockout ({@link LoginAttemptListener}) and BCrypt(12).
 *   <li>Spring Session JDBC sessions (ADR-0009): one concurrent session per user, session id
 *       rotated on login, 15 min idle / 8 h absolute lifetime.
 *   <li>Session-stored CSRF token (ADR-0003) served by {@link CsrfController}, rotated on login.
 *   <li>CORS allow-list for the separately hosted SPA; API security headers; deny-by-default
 *       authorisation with USER/ADMIN roles.
 *   <li>Every security rejection answers with the standard {@link ErrorResponse} JSON and is
 *       audited.
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String LOGOUT_URL = "/api/auth/logout";

    /** The API only ever returns JSON/text: nothing may load, frame or submit anything. */
    static final String API_CONTENT_SECURITY_POLICY =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

    static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=()";

    private static final long HSTS_MAX_AGE_SECONDS = 31_536_000L;

    /** Cost 12 per App-Standards UAC (ADR-0006); overridable only to speed up tests. */
    @Bean
    public PasswordEncoder passwordEncoder(@Value("${app.security.password.bcrypt-strength:12}") int strength) {
        return new BCryptPasswordEncoder(strength);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // Password first, account status second: by default locked/disabled accounts are rejected
        // *before* BCrypt runs, so they answer measurably faster than a real account with a wrong
        // password -- a timing oracle for account state. Checking status afterwards makes every
        // failure path cost one BCrypt verification (unknown usernames already pay for a dummy
        // hash inside DaoAuthenticationProvider).
        provider.setPreAuthenticationChecks(user -> {});
        provider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
        // hideUserNotFoundExceptions stays at its default (true): unknown username and wrong
        // password both surface as BadCredentialsException.
        return provider;
    }

    /**
     * Not auto-wired by Spring Boot for a manually built {@code ProviderManager}; without it {@link
     * LoginAttemptListener} would never see a success/failure event.
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
     * Session registry backed by Spring Session's principal-name index, so concurrent-session
     * control sees every session in the shared JDBC store (not just this instance's memory).
     */
    @Bean
    public <S extends Session> SpringSessionBackedSessionRegistry<S> sessionRegistry(
            FindByIndexNameSessionRepository<S> sessionRepository) {
        return new SpringSessionBackedSessionRegistry<>(sessionRepository);
    }

    /** CSRF token kept in the server-side session only; the SPA fetches it via {@link CsrfController}. */
    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    /**
     * Frontend and backend run on separate origins, so cross-origin requests need an explicit
     * allow-list. Credentials (the session cookie) are allowed, matching {@code fetch(...,
     * {credentials: 'include'})} on the frontend.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                List.of("Content-Type", "X-CSRF-TOKEN", RequestLoggingContextFilter.CORRELATION_HEADER));
        configuration.setExposedHeaders(
                List.of(HttpHeaders.RETRY_AFTER, RequestLoggingContextFilter.CORRELATION_HEADER));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager,
            ObjectMapper objectMapper,
            SpringSessionBackedSessionRegistry<?> sessionRegistry,
            CsrfTokenRepository csrfTokenRepository,
            CorsConfigurationSource corsConfigurationSource,
            RateLimiters rateLimiters,
            AuditLogger auditLogger,
            Environment environment)
            throws Exception {
        // Gated on the active profile itself, not on spring.h2.console.enabled -- that property
        // merely controls whether Boot registers the console servlet, and must never be the only
        // thing standing between the console and an unauthenticated, CSRF-exempt SQL prompt.
        boolean h2ConsoleExemptionsEnabled = environment.acceptsProfiles(Profiles.of("dev"));
        RequestMatcher h2Console = PathPatternRequestMatcher.withDefaults().matcher("/h2-console/**");

        JsonUsernamePasswordAuthenticationFilter loginFilter =
                new JsonUsernamePasswordAuthenticationFilter(objectMapper, rateLimiters);
        loginFilter.setAuthenticationManager(authenticationManager);
        // A manually registered login filter gets none of formLogin()'s wiring, so the session
        // context repository and the session strategies must be set explicitly -- otherwise a
        // login would not persist, the session id would not rotate, and the CSRF token would
        // survive authentication.
        loginFilter.setSecurityContextRepository(new HttpSessionSecurityContextRepository());
        ConcurrentSessionControlAuthenticationStrategy concurrentSessions =
                new ConcurrentSessionControlAuthenticationStrategy(sessionRegistry);
        // App-Standards UAC: one active session per user; a new login ends the older session
        // (its next request gets 401) rather than being refused.
        concurrentSessions.setMaximumSessions(SessionLimit.of(1));
        loginFilter.setSessionAuthenticationStrategy(new CompositeSessionAuthenticationStrategy(List.of(
                concurrentSessions,
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository))));
        loginFilter.setAuthenticationSuccessHandler((request, response, authentication) -> response.setStatus(200));
        loginFilter.setAuthenticationFailureHandler((request, response, exception) -> {
            if (exception instanceof LoginRateLimitedException rateLimited) {
                auditLogger.rateLimited(rateLimited.getLimiter());
                response.setHeader(
                        HttpHeaders.RETRY_AFTER,
                        String.valueOf(RateLimitExceededException.retryAfterSeconds(rateLimited.getRetryAfter())));
                writeError(objectMapper, response, 429, ErrorCode.RATE_LIMITED, "Too many login attempts. Please try again later.");
                return;
            }
            // One body for every other reason -- unknown user, wrong password, locked, disabled --
            // so the response never reveals which (anti-enumeration).
            writeError(objectMapper, response, 401, ErrorCode.INVALID_CREDENTIALS, "Invalid username or password");
        });

        // ConcurrentSessionFilter also runs the logout handlers when a request arrives on a session
        // that a newer login evicted; audit that as a termination, not as the user logging out.
        RequestMatcher logoutRequest = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, LOGOUT_URL);
        LogoutHandler auditLogout = (request, response, authentication) -> {
            if (logoutRequest.matches(request)) {
                auditLogger.loggedOut(publicIdOf(authentication));
            } else {
                auditLogger.sessionsTerminated(publicIdOf(authentication), "concurrent_login", 1);
            }
        };

        http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> {
                    csrf.csrfTokenRepository(csrfTokenRepository);
                    if (h2ConsoleExemptionsEnabled) {
                        // The H2 console submits its own forms without this app's CSRF token.
                        csrf.ignoringRequestMatchers(h2Console);
                    }
                })
                .headers(headers -> {
                    headers.httpStrictTransportSecurity(hsts -> hsts
                                    .maxAgeInSeconds(HSTS_MAX_AGE_SECONDS)
                                    .includeSubDomains(true))
                            .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                            .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", PERMISSIONS_POLICY));
                    ContentSecurityPolicyHeaderWriter csp = new ContentSecurityPolicyHeaderWriter(API_CONTENT_SECURITY_POLICY);
                    if (h2ConsoleExemptionsEnabled) {
                        // The dev-only H2 console is a framed HTML app: exempt just that path from
                        // the API's CSP and allow it to frame itself; everything else keeps DENY.
                        RequestMatcher notH2Console = new NegatedRequestMatcher(h2Console);
                        headers.frameOptions(frameOptions -> frameOptions.disable())
                                .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(notH2Console, csp))
                                .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                        notH2Console,
                                        new XFrameOptionsHeaderWriter(XFrameOptionsHeaderWriter.XFrameOptionsMode.DENY)))
                                .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                        h2Console,
                                        new XFrameOptionsHeaderWriter(XFrameOptionsHeaderWriter.XFrameOptionsMode.SAMEORIGIN)));
                    } else {
                        headers.addHeaderWriter(csp);
                    }
                })
                // Before SecurityContextHolderFilter loads a session's SecurityContext, so a session
                // past its absolute lifetime is invalidated first and the request is anonymous.
                .addFilterBefore(
                        new AbsoluteSessionTimeoutFilter(AbsoluteSessionTimeoutFilter.DEFAULT_MAX_SESSION_AGE),
                        SecurityContextHolderFilter.class)
                .addFilterAfter(new RequestLoggingContextFilter(), SecurityContextHolderFilter.class)
                .addFilterAt(loginFilter, UsernamePasswordAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionConcurrency(concurrency -> concurrency
                        // Registers ConcurrentSessionFilter, which answers a request on a session
                        // that concurrent-session control has expired.
                        .maximumSessions(SessionLimit.of(1))
                        .sessionRegistry(sessionRegistry)
                        .expiredSessionStrategy(event -> writeError(
                                objectMapper, event.getResponse(), 401, ErrorCode.UNAUTHENTICATED, "Session expired"))))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> writeError(
                                objectMapper, response, 401, ErrorCode.UNAUTHENTICATED, "Authentication required"))
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            Authentication authentication =
                                    org.springframework.security.core.context.SecurityContextHolder.getContext()
                                            .getAuthentication();
                            if (accessDeniedException instanceof CsrfException) {
                                auditLogger.accessDenied(publicIdOf(authentication), "csrf");
                                writeError(objectMapper, response, 403, ErrorCode.CSRF_INVALID, "Invalid or missing CSRF token");
                            } else {
                                auditLogger.accessDenied(publicIdOf(authentication), "forbidden");
                                writeError(objectMapper, response, 403, ErrorCode.FORBIDDEN, "Access denied");
                            }
                        }))
                .authorizeHttpRequests(authorize -> {
                    // CORS preflight carries no cookie or CSRF header by nature.
                    authorize.requestMatchers(HttpMethod.OPTIONS, "/**")
                            .permitAll()
                            // Boot's error page: an error on a public endpoint must not turn into 401.
                            .requestMatchers("/error")
                            .permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/auth/csrf")
                            .permitAll()
                            .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login")
                            .permitAll()
                            // Reached by someone who is, by definition, not logged in.
                            .requestMatchers(HttpMethod.POST, "/api/password-reset/request", "/api/password-reset/confirm")
                            .permitAll();
                    if (h2ConsoleExemptionsEnabled) {
                        authorize.requestMatchers(h2Console).permitAll();
                    }
                    authorize
                            // Must precede the catch-all, or .authenticated() would shadow it.
                            .requestMatchers("/api/admin/**")
                            .hasRole("ADMIN")
                            // Deny-by-default: anything not listed needs an authenticated session.
                            .anyRequest()
                            .authenticated();
                })
                .logout(logout -> logout.logoutUrl(LOGOUT_URL)
                        .addLogoutHandler(auditLogout)
                        // App-Standards: tell the browser to drop cached API responses and the SPA's
                        // storage. (Written on HTTPS requests only.) The session itself is invalidated
                        // by the default SecurityContextLogoutHandler, which makes Spring Session
                        // delete it and expire the SESSION cookie with its original attributes.
                        .addLogoutHandler(new HeaderWriterLogoutHandler(new ClearSiteDataHeaderWriter(
                                ClearSiteDataHeaderWriter.Directive.CACHE, ClearSiteDataHeaderWriter.Directive.STORAGE)))
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler()));

        return http.build();
    }

    private static UUID publicIdOf(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AppUserDetails user
                ? user.getPublicId()
                : null;
    }

    static void writeError(
            ObjectMapper objectMapper, HttpServletResponse response, int status, ErrorCode code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(code, message)));
    }
}
