package com.sgtechstack.helloworldauthapp.config;

import com.sgtechstack.helloworldauthapp.auth.LoginFailureHandler;
import com.sgtechstack.helloworldauthapp.auth.LoginSuccessHandler;
import com.sgtechstack.helloworldauthapp.auth.LogoutSuccessResponseHandler;
import com.sgtechstack.helloworldauthapp.auth.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
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

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            LoginSuccessHandler loginSuccessHandler,
            LoginFailureHandler loginFailureHandler,
            LogoutSuccessResponseHandler logoutSuccessHandler,
            RestAuthenticationEntryPoint authenticationEntryPoint
    ) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler)
                )
                // Session-fixation protection: Spring Security's default session
                // management already rotates the session ID on authentication
                // (changeSessionId()), so no explicit .sessionManagement(...) override
                // is needed here to get that behaviour.
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/register", "/api/health", "/api/csrf", "/api/auth/login")
                        .permitAll()
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
                );

        return http.build();
    }
}
