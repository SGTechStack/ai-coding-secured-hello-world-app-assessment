package com.example.helloworldauth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Base security configuration inherited by every later feature slice.
 *
 * <p>Establishes: an explicit CORS allow-list with credentials, CSRF protection
 * backed by a cookie token repository (so the React SPA can read the token),
 * session-fixation protection, and the shared password encoder. Only the
 * unauthenticated {@code /api/ping} smoke endpoint is open at this stage;
 * feature slices add their own authorization rules.
 */
@Configuration
public class SecurityConfig {

    private final String frontendOrigin;

    public SecurityConfig(@Value("${app.cors.frontend-origin:http://localhost:3000}") String frontendOrigin) {
        this.frontendOrigin = frontendOrigin;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                // SPA sends the RAW cookie token back in the X-XSRF-TOKEN header.
                // The default XorCsrfTokenRequestAttributeHandler expects the
                // masked render value, which a cookie-reading SPA never sees, so
                // use the plain handler that compares the raw token. Without this
                // every state-changing request from the SPA is rejected — a bug
                // MockMvc's .with(csrf()) cannot surface because it fakes the
                // token plumbing end to end.
                .csrfTokenRequestHandler(
                    new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(sf -> sf.migrateSession()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/ping", "/api/register", "/api/login",
                    "/api/password-reset/request", "/api/password-reset/confirm").permitAll()
                // Admin module role guard, enforced server-side. Must sit BEFORE
                // anyRequest().authenticated() so it matches first. Later admin
                // slices (12/13/14) reuse this same /api/admin/** matcher.
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                // Unauthenticated -> 401 (no session/credentials).
                .authenticationEntryPoint(
                    new org.springframework.security.web.authentication.HttpStatusEntryPoint(
                        org.springframework.http.HttpStatus.UNAUTHORIZED))
                // Authenticated but lacking the role -> 403. Explicit so an
                // authenticated USER hitting /api/admin/** gets 403, never 401.
                .accessDeniedHandler(
                    new org.springframework.security.web.access.AccessDeniedHandlerImpl()))
            // No form-login/basic UI: this is a REST API driven by the SPA.
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(frontendOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
