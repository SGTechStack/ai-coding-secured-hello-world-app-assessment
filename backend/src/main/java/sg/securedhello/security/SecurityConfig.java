package sg.securedhello.security;

import java.time.Clock;
import java.time.Duration;

import jakarta.servlet.DispatcherType;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.filter.CorsFilter;

import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.config.OriginsProperties;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;
import sg.securedhello.mfa.TotpFactorEntryPoint;
import sg.securedhello.mfa.TotpFactorGrant;
import sg.securedhello.security.csrf.HeaderOnlyCsrfTokenRequestHandler;
import sg.securedhello.security.csrf.SessionOnlyCsrfTokenRepository;
import sg.securedhello.security.login.SignIn;
import sg.securedhello.security.ratelimit.AuthRateLimiter;
import sg.securedhello.security.ratelimit.RequestBodyCapFilter;
import sg.securedhello.security.ratelimit.RequestBodyProperties;
import sg.securedhello.security.ratelimit.SourceRateLimitFilter;
import sg.securedhello.security.source.SourceKeyResolver;
import sg.securedhello.session.AbsoluteLifetimeFilter;
import sg.securedhello.session.SessionLifetimeProperties;

/**
 * The application's security filter chain. Declaring it makes Boot's management security auto-configuration back
 * off, so the actuator rules are ours (ADR-061).
 *
 * <p>Authorization follows the matrix (ADR-043): whitelist first, the role-definition {@code denyAll()} before the
 * role guards, then {@code anyRequest().denyAll()}. A guard on {@code /api/admin/**} also requires the second factor,
 * after the role ({@link AdminFactorRules}). Method security repeats the role check only. Every refusal is written by
 * {@link ProblemDetailWriter} (ADR-031): an anonymous caller gets 401 {@code AUTHENTICATION_FAILED}, with no
 * {@code WWW-Authenticate} challenge (R-AUTH-005), and a signed-in one gets 403 {@code ACCESS_DENIED}.
 *
 * <p>Filter order (ADR-038): the {@link SourceRateLimitFilter}, then {@code SecurityContextHolderFilter}, the
 * header writer and {@code CorsFilter}, then the {@link AbsoluteLifetimeFilter}, whose 401 so carries the CORS and
 * security headers, the {@link RequestBodyCapFilter} and {@code CsrfFilter}, and
 * later the login filter, whose converter reads the body (T-RL-012). The {@link ForcedChangeFilter} runs last, just
 * before authorization.
 *
 * <p>CSRF (ADR-036; ADR-040): a session-bound synchronizer token, read from the {@code X-CSRF-TOKEN} header only, on
 * every unsafe method, stored without ever creating a session, and never set as a cookie.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties({AuthorizationMatrix.class, SessionLifetimeProperties.class})
public class SecurityConfig {

    /** Role-definition paths: refused to everyone, including an admin (ADR-043; T-ADM-020). */
    static final String ROLE_DEFINITION_PATHS = "/api/admin/roles/**";

    /**
     * The API's own Content-Security-Policy, distinct from the document policy (R-HDR-010; T-HDR-002). A JSON-only
     * origin loads nothing and may be framed by nothing; the header is defence in depth, not document-CSP evidence
     * (R-HDR-004).
     */
    static final String API_CONTENT_SECURITY_POLICY = "default-src 'none'; frame-ancestors 'none'";

    /** Web only: the recovery runner runs the same context with no web server (ADR-072). */
    @Bean
    @ConditionalOnWebApplication
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthorizationMatrix matrix,
            AuthenticationEntryPoint problemAuthenticationEntryPoint, AccessDeniedHandler problemAccessDeniedHandler,
            SessionRepository<?> sessionRepository, OriginsProperties origins, ProblemDetailWriter writer,
            SignIn signIn, SessionLifetimeProperties lifetime, Clock clock, AuthRateLimiter limiter,
            SourceKeyResolver sourceKeys, HttpSessionIdResolver sessionIds, AuditEmitter audit,
            RequestBodyProperties requestBody, TotpFactorEntryPoint totpFactorEntryPoint) {
        // W is what the repository really applies, not the raw timeout property (T-SES-033). Nothing is saved.
        Duration idleWindow = sessionRepository.createSession().getMaxInactiveInterval();
        CsrfTokenRepository csrfTokens = new SessionOnlyCsrfTokenRepository();
        CsrfTokenRequestHandler csrfHandler = new HeaderOnlyCsrfTokenRequestHandler();
        // Sign-in, the concurrent-session filter and sign-out (ADR-038); the login composite rotates the CSRF token.
        signIn.configure(http, csrfTokens, csrfHandler, idleWindow);
        CorsConfiguration cors = CorsPolicy.configuration(origins);
        AdminFactorRules.requireBothRuleSets(matrix.rolesByRoute().keySet());
        AdminFactorRules adminFactorRules = new AdminFactorRules(lifetime.absolute(), clock);
        return http
                .addFilterBefore(new SourceRateLimitFilter(limiter, sourceKeys, sessionIds, audit, writer,
                        (request, response) -> CorsPolicy.allowOrigin(cors, request, response)),
                        SecurityContextHolderFilter.class)
                .addFilter(CorsPolicy.filter(cors, writer))
                // Spring Security's default header set (HSTS on secure requests only, nosniff, X-Frame-Options DENY)
                // plus the API policy.
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp
                        .policyDirectives(API_CONTENT_SECURITY_POLICY)))
                // After CorsFilter and HeaderWriterFilter, so its 401 carries the SPA's CORS headers and the security
                // headers; still before CsrfFilter (ADR-038). Added first, so it runs before the body cap.
                .addFilterAfter(new AbsoluteLifetimeFilter(idleWindow, lifetime.absolute(), clock, writer, audit),
                        CorsFilter.class)
                .addFilterAfter(new RequestBodyCapFilter(requestBody.maxBodyBytes(), writer), CorsFilter.class)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokens)
                        .csrfTokenRequestHandler(csrfHandler))
                // A forced-change session reaches the five allowlisted routes only (ADR-046; REJ-055).
                .addFilterBefore(new ForcedChangeFilter(writer), AuthorizationFilter.class)
                .authorizeHttpRequests(requests -> {
                    // The /error dispatch only renders the envelope for a request that has already failed.
                    requests.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                            // Safe only with show-details: never, which makes every health subpath 404.
                            .requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
                            .requestMatchers(EndpointRequest.toAnyEndpoint()).denyAll();
                    matrix.whitelist().forEach(route ->
                            requests.requestMatchers(route.method(), route.path()).permitAll());
                    requests.requestMatchers(ROLE_DEFINITION_PATHS).denyAll();
                    // The role guards; on the admin surface, the role then both factors (ADR-021; ADR-026).
                    matrix.rolesByRoute().forEach((route, roles) -> requests
                            .requestMatchers(route.method(), route.path()).access(adminFactorRules.rule(route, roles)));
                    requests.anyRequest().denyAll();
                })
                // Set explicitly, so no request shape falls through to a redirect or a default 403 (T-AUTH-015).
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemAuthenticationEntryPoint)
                        // A missing or expired FACTOR_TOTP gets its own entry point (412/422); every other refusal,
                        // CSRF included, the problem handler (ADR-026).
                        .defaultAccessDeniedHandlerFor(problemAccessDeniedHandler, AnyRequestMatcher.INSTANCE)
                        .defaultDeniedHandlerForMissingAuthority(totpFactorEntryPoint, TotpFactorGrant.AUTHORITY))
                // The API never redirects: no saved request to return to, and sign-out answers 204 (SignIn). The cache
                // is an explicit NullRequestCache (ADR-040), so no filter can fall back to the session-backed default.
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .build();
    }

    /** Envelope producer 2a: an unauthenticated request gets 401 {@code AUTHENTICATION_FAILED}. */
    @Bean
    AuthenticationEntryPoint problemAuthenticationEntryPoint(ProblemDetailWriter writer) {
        return (request, response, exception) -> writer.write(request, response, ErrorCode.AUTHENTICATION_FAILED);
    }

    /** Envelope producer 3: 403 {@code ACCESS_DENIED}, or {@code CSRF_TOKEN_INVALID} for a CSRF refusal. */
    @Bean
    AccessDeniedHandler problemAccessDeniedHandler(ProblemDetailWriter writer, AuditEmitter audit) {
        return new ProblemAccessDeniedHandler(writer, audit);
    }

    /**
     * A request the firewall rejects (for example an encoded path separator) gets 400 {@code VALIDATION_FAILED}. The
     * default handler would call {@code sendError} instead.
     */
    @Bean
    RequestRejectedHandler problemRequestRejectedHandler(ProblemDetailWriter writer) {
        return (request, response, exception) -> writer.write(request, response, ErrorCode.VALIDATION_FAILED);
    }
}
