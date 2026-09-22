package com.sgtechstack.helloworldauthapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgtechstack.helloworldauthapp.auth.IpLoginThrottle;
import com.sgtechstack.helloworldauthapp.auth.IpThrottleFilter;
import com.sgtechstack.helloworldauthapp.auth.LoginFailureHandler;
import com.sgtechstack.helloworldauthapp.auth.LoginSuccessHandler;
import com.sgtechstack.helloworldauthapp.auth.LogoutSuccessResponseHandler;
import com.sgtechstack.helloworldauthapp.auth.RestAccessDeniedHandler;
import com.sgtechstack.helloworldauthapp.auth.RestAuthenticationEntryPoint;
import com.sgtechstack.helloworldauthapp.auth.RestSessionExpiredStrategy;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Session-cookie auth: form login produces a server-side session backed by
 * a secure cookie, logout invalidates it, and CSRF protection covers every
 * state-changing endpoint since a cookie is an ambient credential a
 * malicious page could otherwise ride along with.
 *
 * CORS is wired here via {@code .cors(...)} using the {@link CorsConfig}
 * bean, not left to Spring MVC's {@code WebMvcConfigurer}: login and
 * logout are handled entirely by Spring Security's filter chain, which
 * runs before MVC dispatch, so MVC-level CORS configuration never sees
 * those requests.
 *
 * Session-fixation protection is Spring Security's default: the session ID
 * is rotated on successful authentication (ChangeSessionIdAuthenticationStrategy),
 * so no explicit override is configured below.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Tracks each principal's active {@code HttpSession}s so a password
     * reset (or any future "log out everywhere" action) can invalidate
     * every session belonging to a user, not just the one making the
     * request. Requires {@link org.springframework.security.web.session.HttpSessionEventPublisher}
     * (registered as a servlet listener bean below) so the registry is
     * notified when sessions are created and destroyed.
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public ServletListenerRegistrationBean<HttpSessionEventPublisher> httpSessionEventPublisher() {
        return new ServletListenerRegistrationBean<>(new HttpSessionEventPublisher());
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            LoginSuccessHandler loginSuccessHandler,
            LoginFailureHandler loginFailureHandler,
            LogoutSuccessResponseHandler logoutSuccessHandler,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            IpLoginThrottle ipLoginThrottle,
            ObjectMapper objectMapper,
            SessionRegistry sessionRegistry,
            RestSessionExpiredStrategy sessionExpiredStrategy,
            RestAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // Runs before Spring Security's own login-processing filter, so
                // a throttled IP is rejected before an authentication attempt
                // is even made.
                .addFilterBefore(
                        new IpThrottleFilter(ipLoginThrottle, objectMapper),
                        UsernamePasswordAuthenticationFilter.class
                )
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler)
                )
                // Session-fixation protection: Spring Security's default session
                // management already rotates the session ID on authentication
                // (changeSessionId()). maximumSessions/sessionRegistry here is for
                // tracking, not capping, concurrent sessions: it feeds SessionRegistry
                // so a password reset can invalidate every session for a user, not
                // to limit how many a user may have open.
                .sessionManagement(session -> session
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)
                        .expiredSessionStrategy(sessionExpiredStrategy)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/auth/register", "/api/health", "/api/csrf", "/api/auth/login",
                                "/api/auth/password-reset/request", "/api/auth/password-reset/confirm"
                        )
                        .permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(loginSuccessHandler)
                        .failureHandler(loginFailureHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(logoutSuccessHandler)
                        .invalidateHttpSession(true)
                        .deleteCookies("SESSION")
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                );

        return http.build();
    }
}
