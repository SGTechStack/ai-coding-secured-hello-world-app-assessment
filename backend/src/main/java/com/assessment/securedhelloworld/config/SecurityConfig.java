package com.assessment.securedhelloworld.config;

import com.assessment.securedhelloworld.security.AbsoluteSessionTimeoutFilter;
import com.assessment.securedhelloworld.security.AppUserDetailsService;
import com.assessment.securedhelloworld.security.CsrfCookieFilter;
import com.assessment.securedhelloworld.security.JsonAccessDeniedHandler;
import com.assessment.securedhelloworld.security.JsonAuthEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.context.SecurityContextHolderFilter;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Central security baseline (ticket 01). Every later ticket's endpoints slot into the matcher
 * list below rather than opening a new filter chain, so there is exactly one place CORS, CSRF,
 * session, and access-control policy are decided (IM8 ac-1: default-deny, one seam).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Value("${app.security.session.absolute-timeout-minutes}")
    private long absoluteSessionTimeoutMinutes;

    @Bean
    public PasswordEncoder passwordEncoder() {
        // IM8 as-6 / as-14: the PRD-mandated, standard BCryptPasswordEncoder — no custom hashing.
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(AppUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // Default true: an unknown username surfaces as the same BadCredentialsException as a
        // wrong password. Kept explicit because it's load-bearing for enumeration resistance.
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Exposed as a bean (not just the {@code .sessionFixation(...)} DSL shorthand) so the manual,
     * JSON-based login flow in {@code AuthController} can invoke the exact same fixation
     * protection the filter chain declares, rather than two independent implementations of the
     * same control drifting apart (IM8 as-11).
     */
    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JsonAuthEntryPoint authEntryPoint, JsonAccessDeniedHandler accessDeniedHandler,
                                            SessionAuthenticationStrategy sessionAuthenticationStrategy,
                                            SecurityContextRepository securityContextRepository)
            throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .addFilterBefore(new AbsoluteSessionTimeoutFilter(Duration.ofMinutes(absoluteSessionTimeoutMinutes)), SecurityContextHolderFilter.class)
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        // Session-fixation protection: a fresh session id is issued on
                        // authentication (IM8 as-11). Same bean AuthController calls manually.
                        .sessionAuthenticationStrategy(sessionAuthenticationStrategy))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        // HSTS header writer only emits over HTTPS requests, so it is naturally a
                        // no-op for the accepted-gap local `dev` profile (IM8 as-10, dp-3 waiver A5).
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        // Invalidate the server-side session AND clear the cookie (PRD Story 4):
                        // a session id captured before logout is rejected as unauthenticated when
                        // replayed afterward, because the session itself is gone server-side.
                        .invalidateHttpSession(true)
                        .deleteCookies("SESSION")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/auth/password-reset/**").permitAll()
                        .requestMatchers("/api/csrf").permitAll()
                        .requestMatchers("/api/hello").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // IM8 lm-16: the metrics/health hook is authenticated (ADMIN), not left
                        // open, and not on a separate management port — a recorded, deliberate
                        // choice (spec.md "Design decisions").
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().denyAll());

        return http.build();
    }
}
