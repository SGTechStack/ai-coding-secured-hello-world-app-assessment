package com.sgtechstack.helloworldauthapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * CORS is configured with an explicit allow-list of the frontend origin(s),
 * never a wildcard, because credentials (the session cookie) must be allowed
 * to travel cross-origin.
 *
 * This is exposed as a {@link CorsConfigurationSource} bean, consumed
 * directly by Spring Security's {@code HttpSecurity.cors(...)}, rather than
 * via a {@code WebMvcConfigurer}. Spring Security's filter chain sits in
 * front of Spring MVC dispatch, so endpoints handled entirely by security
 * filters (form login, logout) never reach MVC-level CORS configuration —
 * only the security-level CORS filter sees them.
 */
@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    public CorsConfig(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins,
            SecurityProperties securityProperties
    ) {
        this.allowedOrigins = validate(allowedOrigins, securityProperties.requireHttps());
    }

    /**
     * Rejects an unusable origin list at startup rather than booting with it.
     *
     * <p>This list is the load-bearing control for the whole cookie-auth
     * design: {@code allowCredentials} is true, so any origin named here can
     * make authenticated requests with a visitor's session and read the
     * responses. It arrives from an environment variable, which means a typo,
     * a stray wildcard, or a value widened during debugging and never reverted
     * silently removes that protection. Failing to start is the loud failure
     * this deserves; a warning in a log nobody reads is not.
     *
     * @throws IllegalStateException if any origin is blank, a wildcard,
     *                               malformed, or plaintext when HTTPS is
     *                               required
     */
    private static List<String> validate(List<String> origins, boolean requireHttps) {
        if (origins == null || origins.isEmpty()) {
            throw new IllegalStateException(
                    "app.cors.allowed-origins is empty. Set it to the frontend origin(s); "
                            + "there is no safe default.");
        }

        for (String origin : origins) {
            if (origin == null || origin.isBlank()) {
                throw new IllegalStateException("app.cors.allowed-origins contains a blank entry: " + origins);
            }

            if (origin.contains("*")) {
                throw new IllegalStateException(
                        "app.cors.allowed-origins must not contain a wildcard (got '" + origin
                                + "'). Credentials are allowed to travel cross-origin, so a wildcard would let any "
                                + "site act as an authenticated user.");
            }

            URI parsed;
            try {
                parsed = new URI(origin);
            } catch (URISyntaxException e) {
                throw new IllegalStateException(
                        "app.cors.allowed-origins contains an unparseable origin: '" + origin + "'", e);
            }

            String scheme = parsed.getScheme();
            if (scheme == null || parsed.getHost() == null) {
                throw new IllegalStateException(
                        "app.cors.allowed-origins entries must be absolute origins like https://app.example.com "
                                + "(got '" + origin + "')");
            }

            if (requireHttps && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalStateException(
                        "app.cors.allowed-origins must use https when app.security.require-https is true "
                                + "(got '" + origin + "'). Set require-https=false only for local development.");
            }
        }

        return List.copyOf(origins);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
