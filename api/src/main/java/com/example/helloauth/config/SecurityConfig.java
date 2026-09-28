package com.example.helloauth.config;

import com.example.helloauth.domain.Role;
import com.example.helloauth.settings.AppProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The security contract for the whole API, in one readable place.
 *
 * <p>Two things are deliberately absent. There is no {@code UserDetailsService} and no
 * {@code AuthenticationProvider}: credential checking lives in {@code AuthenticationService} because
 * lockout and throttling have to happen in the same decision, and splitting that across a provider
 * and an event listener would scatter one rule over three files. And there is no {@code formLogin},
 * because this backend serves a separate-origin JSON client and has no pages to redirect to.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            SecurityContextRepository securityContextRepository,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler)
            throws Exception {

        return http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                // Cookie-based authentication means an ambient credential, which means CSRF is a
                // live risk on every state-changing endpoint — including login and logout, not just
                // the "interesting" ones. Enabled globally rather than per-path so nothing added
                // later is unprotected by omission.
                .csrf(
                        csrf ->
                                csrf.csrfTokenRepository(
                                        CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // The filter does what a hand-written logout endpoint would have to get right:
                // invalidates the server-side session, clears the context, and expires the cookie.
                .logout(
                        logout ->
                                logout.logoutUrl("/api/auth/logout")
                                        .logoutSuccessHandler(
                                                new HttpStatusReturningLogoutSuccessHandler(
                                                        HttpStatus.NO_CONTENT))
                                        .invalidateHttpSession(true)
                                        .clearAuthentication(true)
                                        .deleteCookies("SESSION"))
                .exceptionHandling(
                        handling ->
                                handling.authenticationEntryPoint(authenticationEntryPoint)
                                        .accessDeniedHandler(accessDeniedHandler))
                .securityContext(
                        context -> context.securityContextRepository(securityContextRepository))
                .sessionManagement(
                        session ->
                                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(
                        auth ->
                                auth
                                        // Preflights carry no credentials and must not be answered
                                        // with a 401, or the real request never happens.
                                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                                        .permitAll()
                                        .requestMatchers(
                                                "/api/auth/csrf",
                                                "/api/auth/me",
                                                "/api/auth/register",
                                                "/api/auth/login",
                                                "/api/auth/password-reset/request",
                                                "/api/auth/password-reset/confirm")
                                        .permitAll()
                                        // Path-level, not annotation-level: a method added to the
                                        // admin controller later is protected whether or not anyone
                                        // remembers to annotate it.
                                        .requestMatchers("/api/admin/**")
                                        .hasRole(Role.ADMIN.name())
                                        // Default deny. Every endpoint not named above needs a
                                        // session, including ones that do not exist yet.
                                        .anyRequest()
                                        .authenticated())
                .build();
    }

    /**
     * Exposed as a bean so that {@code SessionService} writes the security context to exactly the
     * place the filter chain later reads it from. Two different repositories here would produce a
     * login that reports success and a next request that is anonymous.
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        // An explicit list, never a pattern. Browsers refuse a wildcard origin on credentialed
        // requests, and the session cookie makes every request credentialed.
        configuration.setAllowedOrigins(properties.cors().allowedOrigins());
        configuration.setAllowedMethods(
                List.of(
                        HttpMethod.GET.name(),
                        HttpMethod.POST.name(),
                        HttpMethod.PATCH.name(),
                        HttpMethod.DELETE.name(),
                        HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(List.of("Content-Type", "Accept", "X-XSRF-TOKEN"));
        // Without this the session cookie is neither sent nor stored cross-origin, and the entire
        // authentication mechanism silently does nothing.
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
