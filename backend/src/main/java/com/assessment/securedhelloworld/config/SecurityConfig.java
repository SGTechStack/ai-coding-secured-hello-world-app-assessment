package com.assessment.securedhelloworld.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.assessment.securedhelloworld.user.UserRepository;

import java.util.List;

/**
 * Base security wiring. Endpoints are permitAll only where the story
 * requires anonymous access (register, login, password-reset request/
 * confirm); everything else requires an authenticated session, and
 * {@code /api/admin/**} additionally requires the ADMIN role.
 *
 * <p>The Content-Security-Policy header set here (IM8 as-9) covers any
 * HTML this backend might ever render itself (e.g. framework default
 * error pages) — it does NOT protect the React SPA, which this backend
 * never serves. The SPA's own CSP is delivered via a {@code <meta
 * http-equiv="Content-Security-Policy">} tag in {@code frontend/index.html}
 * instead, since the SPA's static assets can be hosted independently of
 * this API (a response header from a server that never serves the
 * document has no effect on it).
 */
@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            SessionRegistry sessionRegistry,
            UserRepository userRepository,
            org.springframework.core.env.Environment env,
            @org.springframework.beans.factory.annotation.Value("${app.security.max-concurrent-sessions:5}") int maxConcurrentSessions) throws Exception {
        RequestMatcher h2Console = PathPatternRequestMatcher.withDefaults().matcher("/h2-console/**");
        boolean devProfileActive = List.of(env.getActiveProfiles()).contains("dev");
        AuthenticationEntryPoint unauthorizedEntryPoint = (request, response, authException) ->
                response.sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers(h2Console))
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; script-src 'self'; style-src 'self'; "
                    + "img-src 'self' data:; font-src 'self'; connect-src 'self'; "
                    + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'")))
            .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(unauthorizedEntryPoint))
            .sessionManagement(session -> session
                .maximumSessions(maxConcurrentSessions)
                .sessionRegistry(sessionRegistry)
                .expiredSessionStrategy(event ->
                    event.getResponse().sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED)))
            .authorizeHttpRequests(authorize -> {
                // The H2 console is permitAll ONLY when the dev profile is
                // active, independent of whether spring.h2.console.enabled
                // happens to be true — this is the profile gate the pre-
                // remediation audit flagged as missing (the filter chain
                // itself, not just application-dev.yml, now enforces it).
                if (devProfileActive) {
                    authorize.requestMatchers(h2Console).permitAll();
                } else {
                    authorize.requestMatchers(h2Console).denyAll();
                }
                authorize
                    .requestMatchers("/api/csrf").permitAll()
                    .requestMatchers("/api/register", "/api/login").permitAll()
                    .requestMatchers("/api/password-reset/**").permitAll()
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated();
            })
            .addFilterAfter(new ForcePasswordChangeFilter(userRepository), AuthorizationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            org.springframework.core.env.Environment env) {
        String frontendOrigin = env.getProperty("app.frontend.origin", "http://localhost:3000");

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(frontendOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
