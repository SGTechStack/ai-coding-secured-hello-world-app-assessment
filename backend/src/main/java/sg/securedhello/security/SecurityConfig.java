package sg.securedhello.security;

import jakarta.servlet.DispatcherType;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.firewall.RequestRejectedHandler;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;

/**
 * The application's security filter chain. Declaring it makes Boot's management security auto-configuration back
 * off, so the actuator rules are ours (ADR-061).
 *
 * <p>Authorization follows the matrix (ADR-043): whitelist first, the role-definition {@code denyAll()} before the
 * role guards, then {@code anyRequest().denyAll()}. Every refusal is written by {@link ProblemDetailWriter}
 * (ADR-031): an anonymous caller gets 401 {@code AUTHENTICATION_FAILED}, with no {@code WWW-Authenticate} challenge
 * (R-AUTH-005), and a signed-in one gets 403 {@code ACCESS_DENIED}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthorizationMatrix.class)
public class SecurityConfig {

    /** Role-definition paths: refused to everyone, including an admin (ADR-043; T-ADM-020). */
    static final String ROLE_DEFINITION_PATHS = "/api/admin/roles/**";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthorizationMatrix matrix,
            AuthenticationEntryPoint problemAuthenticationEntryPoint, AccessDeniedHandler problemAccessDeniedHandler) {
        return http
                .authorizeHttpRequests(requests -> {
                    // The /error dispatch only renders the envelope for a request that has already failed.
                    requests.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                            // Safe only with show-details: never, which makes every health subpath 404.
                            .requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
                            .requestMatchers(EndpointRequest.toAnyEndpoint()).denyAll();
                    matrix.whitelist().forEach(route ->
                            requests.requestMatchers(route.method(), route.path()).permitAll());
                    requests.requestMatchers(ROLE_DEFINITION_PATHS).denyAll();
                    matrix.roles().forEach((role, routes) -> routes.forEach(route ->
                            requests.requestMatchers(route.method(), route.path()).hasRole(role)));
                    requests.anyRequest().denyAll();
                })
                // Set explicitly, so no request shape falls through to a redirect or a default 403 (T-AUTH-015).
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemAuthenticationEntryPoint)
                        .accessDeniedHandler(problemAccessDeniedHandler))
                // The API never redirects: no saved request to return to, and no default /logout redirect.
                .requestCache(RequestCacheConfigurer::disable)
                .logout(LogoutConfigurer::disable)
                .build();
    }

    /** Envelope producer 2a: an unauthenticated request gets 401 {@code AUTHENTICATION_FAILED}. */
    @Bean
    AuthenticationEntryPoint problemAuthenticationEntryPoint(ProblemDetailWriter writer) {
        return (request, response, exception) -> writer.write(request, response, ErrorCode.AUTHENTICATION_FAILED);
    }

    /** Envelope producer 3: a signed-in caller the matrix refuses gets 403 {@code ACCESS_DENIED}. */
    @Bean
    AccessDeniedHandler problemAccessDeniedHandler(ProblemDetailWriter writer) {
        return (request, response, exception) -> writer.write(request, response, ErrorCode.ACCESS_DENIED);
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
