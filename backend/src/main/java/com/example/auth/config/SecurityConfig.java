package com.example.auth.config;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Security baseline: cookie-based sessions, CORS allow-list with credentials,
 * CSRF via a non-HttpOnly XSRF-TOKEN cookie (double-submit for the SPA),
 * session-fixation protection, and security response headers (Story 53).
 *
 * The full authorization rule set is declared here so SecurityConfig does not
 * need to change as new endpoint groups are added in later slices.
 */
@Configuration
@EnableConfigurationProperties({
    CorsProperties.class,
    SecurityProperties.class,
    PasswordResetProperties.class,
    FrontendProperties.class,
    AdminProperties.class
})
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    HttpSessionSecurityContextRepository securityContextRepository) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> {
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse());
                csrf.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler());
            })
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(fixation -> fixation.changeSessionId()))
            .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
            .headers(headers -> headers
                // Keep Spring Security defaults: X-Frame-Options DENY, X-Content-Type-Options nosniff,
                // Cache-Control no-store on secured responses, HSTS (active over HTTPS).
                .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
            .authorizeHttpRequests(auth -> auth
                // Public auth surface
                .requestMatchers(
                        "/api/ping",
                        "/api/auth/csrf",
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/password-reset/request",
                        "/api/auth/password-reset/confirm"
                ).permitAll()
                // Admin-only surface — role check enforced server-side, never from client state
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // Everything else requires a valid session
                .anyRequest().authenticated())
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            /*
             * Without form-login or httpBasic, Spring Security's default entry point is
             * Http403ForbiddenEntryPoint. Replace with 401 so unauthenticated API callers
             * receive the correct HTTP status instead of a misleading 403.
             */
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    /**
     * Shared repository so the filter-chain reader and the login-controller writer
     * use the same session-backed store. Injected into AuthController to persist
     * SecurityContext after manual authentication.
     */
    @Bean
    HttpSessionSecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Exposes the auto-configured AuthenticationManager (backed by UserDetailsServiceImpl
     * + BCryptPasswordEncoder) so the custom login controller can call authenticate().
     */
    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
