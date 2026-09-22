package com.sgtechstack.helloworldauthapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Baseline security configuration. Login/session machinery lands in a later
 * ticket; for now this only wires up password hashing and opens the
 * registration endpoint, while keeping every other endpoint authenticated
 * by default (least privilege).
 *
 * CSRF is left off for now: session-cookie auth (the thing CSRF protects
 * against) doesn't exist yet, and this decision will be revisited once
 * login introduces the session cookie, per the spec's requirement that CSRF
 * protection cover all state-changing, cookie-authenticated endpoints.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/register", "/api/health").permitAll()
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
