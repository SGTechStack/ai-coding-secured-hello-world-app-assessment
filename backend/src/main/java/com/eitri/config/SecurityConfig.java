package com.eitri.config;

import com.eitri.audit.AuditLogger;
import com.eitri.auth.AbsoluteSessionTimeoutFilter;
import com.eitri.auth.AccountRefreshFilter;
import com.eitri.auth.AuditLogoutHandler;
import com.eitri.auth.Role;
import com.eitri.auth.UserMdcFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Baseline security for a React SPA calling a session-cookie backed JSON API.
 * Features open their public endpoints (e.g. login) by adding request matchers here.
 */
@Configuration
@EnableConfigurationProperties({
    AuthorizationProperties.class,
    SessionSecurityProperties.class,
    LoginSecurityProperties.class,
    CorsProperties.class
})
public class SecurityConfig {

    // JSON-only API: nothing may load from, run in, or frame its responses.
    static final String CONTENT_SECURITY_POLICY = "default-src 'none'; frame-ancestors 'none'";

    // No page of this app uses device features.
    static final String PERMISSIONS_POLICY = "geolocation=(), microphone=(), camera=()";

    static final String CSRF_HEADER = "X-CSRF-TOKEN";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${app.api.base-path}") String basePath,
            AuthorizationProperties authorization,
            CorsProperties corsProperties,
            CsrfTokenRepository csrfTokenRepository,
            SessionSecurityProperties sessionProperties,
            SessionRegistry sessionRegistry,
            AuditLogoutHandler auditLogoutHandler,
            AbsoluteSessionTimeoutFilter absoluteSessionTimeoutFilter,
            AccountRefreshFilter accountRefreshFilter,
            UserMdcFilter userMdcFilter)
            throws Exception {
        RequestMatcher publicEndpoints = publicEndpoints(basePath);
        http
                .authorizeHttpRequests(auth -> {
                    // Preserve the original status when the servlet container renders Spring Security errors.
                    auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    auth.requestMatchers(publicEndpoints).permitAll();
                    auth.requestMatchers(HttpMethod.POST, basePath + "/auth/logout").authenticated();
                    authorization.rolesByRequest().forEach((permission, roles) -> auth
                            .requestMatchers(permission.method(), permission.path())
                            .hasAnyAuthority(roles.stream().map(Role::authority).toArray(String[]::new)));
                    auth.anyRequest().denyAll();
                })
                // Explicit allow-list for the SPA's own origin; preflights are answered before authorization.
                .cors(cors -> cors.configurationSource(corsConfigurationSource(basePath, corsProperties)))
                // Synchronizer token in the session, exposed by {base}/csrf and masked by the default XOR handler.
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
                // On top of the defaults (nosniff, X-Frame-Options: DENY).
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS_POLICY))
                        // Sent on secure requests only, with the security-headers recipe's value.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true).preload(true).maxAgeInSeconds(31536000)))
                // JSON API: reply 401 instead of redirecting to a login page.
                .exceptionHandling(ex -> ex
                        // sendError, so the container renders the {"message"} body (ErrorBodyConfig).
                        .authenticationEntryPoint((request, response, exception) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                        // Also used by the CSRF filter, which runs before authorization.
                        .accessDeniedHandler(accessDeniedHandler(publicEndpoints)))
                .logout(logout -> logout
                        // LogoutFilter precedes authorization, so it must not consume anonymous requests.
                        .logoutRequestMatcher(request -> isAuthenticatedLogoutRequest(
                                request, basePath + "/auth/logout"))
                        // Custom handlers run before session invalidation, while the session hash is available.
                        .addLogoutHandler(auditLogoutHandler)
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("SESSION")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setHeader("Clear-Site-Data", "\"cache\",\"cookies\",\"storage\"");
                            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                        }))
                .sessionManagement(session -> session.sessionConcurrency(concurrency -> concurrency
                        .maximumSessions(sessionProperties.maximumSessions())
                        .maxSessionsPreventsLogin(false)
                        .sessionRegistry(sessionRegistry)
                        .expiredSessionStrategy(event -> event.getResponse()
                                .sendError(HttpServletResponse.SC_UNAUTHORIZED))))
                .addFilterAfter(absoluteSessionTimeoutFilter, SecurityContextHolderFilter.class)
                // Reloads the account so disabled/deleted accounts and role changes apply to live sessions.
                .addFilterAfter(accountRefreshFilter, AbsoluteSessionTimeoutFilter.class)
                .addFilterAfter(userMdcFilter, AnonymousAuthenticationFilter.class);
        return http.build();
    }

    /** Endpoints anyone may call. State-changing ones still need the session's CSRF token. */
    static RequestMatcher publicEndpoints(String basePath) {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        return new OrRequestMatcher(
                paths.matcher(HttpMethod.GET, "/actuator/health"),
                paths.matcher(HttpMethod.GET, basePath + "/csrf"),
                paths.matcher(HttpMethod.POST, basePath + "/auth/login"),
                paths.matcher(HttpMethod.POST, basePath + "/auth/register"),
                paths.matcher(HttpMethod.POST, basePath + "/auth/password-reset/request"),
                paths.matcher(HttpMethod.POST, basePath + "/auth/password-reset/confirm"));
    }

    /**
     * A CSRF failure on a protected endpoint without an authenticated session answers 401, like the
     * authorization that would follow it: the client has no session to act with, so a replayed or
     * missing cookie is "unauthenticated" rather than "forbidden". Every other denial stays 403.
     */
    static AccessDeniedHandler accessDeniedHandler(RequestMatcher publicEndpoints) {
        return (request, response, exception) -> {
            boolean unauthenticatedCsrfFailure = exception instanceof CsrfException
                    && !isAuthenticated(SecurityContextHolder.getContext().getAuthentication())
                    && !publicEndpoints.matches(request);
            response.sendError(unauthenticatedCsrfFailure
                    ? HttpServletResponse.SC_UNAUTHORIZED
                    : HttpServletResponse.SC_FORBIDDEN);
        };
    }

    private static boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    /** Credentialed CORS for {base}/** from the configured origins only; any other origin is refused. */
    static CorsConfigurationSource corsConfigurationSource(String basePath, CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE, CSRF_HEADER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(Duration.ofMinutes(10));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(basePath + "/**", configuration);
        return source;
    }

    private static boolean isAuthenticatedLogoutRequest(HttpServletRequest request, String logoutPath) {
        return HttpMethod.POST.matches(request.getMethod())
                && request.getRequestURI().equals(request.getContextPath() + logoutPath)
                && isAuthenticated(SecurityContextHolder.getContext().getAuthentication());
    }

    /** H2 console, enabled by the dev profile only: renders in frames and posts forms without a CSRF token. */
    @Bean
    @Order(1)
    @ConditionalOnBooleanProperty("spring.h2.console.enabled")
    SecurityFilterChain h2ConsoleSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(PathRequest.toH2Console())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName(CSRF_HEADER);
        return repository;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    UserMdcFilter userMdcFilter() {
        return new UserMdcFilter();
    }

    @Bean
    AbsoluteSessionTimeoutFilter absoluteSessionTimeoutFilter(
            Clock clock, SessionSecurityProperties properties, AuditLogger auditLogger) {
        return new AbsoluteSessionTimeoutFilter(clock, properties, auditLogger);
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /** BCrypt only (strength 10). Hashes keep the {@code {bcrypt}} prefix that the schema checks. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder()));
    }
}
