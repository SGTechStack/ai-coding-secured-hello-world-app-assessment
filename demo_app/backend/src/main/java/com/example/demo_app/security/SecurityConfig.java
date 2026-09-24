package com.example.demo_app.security;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Session-based JSON API security.
 *
 * <ul>
 *   <li>Login is a REST endpoint ({@code AuthController}), not form login; it uses the {@link
 *       AuthenticationManager}, {@link SessionAuthenticationStrategy} and {@link
 *       SecurityContextRepository} beans defined here.
 *   <li>CSRF: cookie repository readable by JS ({@code XSRF-TOKEN} cookie echoed back as the
 *       {@code X-XSRF-TOKEN} header). The plain request handler is used because the SPA sends the
 *       raw cookie value, not a BREACH-masked one.
 *   <li>Failures render as JSON via {@link JsonSecurityErrorHandler}; nothing redirects.
 *   <li>Session fixation: login replaces the session with a new one ({@code newSession}).
 *   <li>Headers: a restrictive Content-Security-Policy (the API serves JSON only),
 *       Referrer-Policy and Permissions-Policy, on top of Spring Security's defaults.
 *   <li>The {@code prod} profile redirects plain HTTP to HTTPS; local dev and the e2e suite run
 *       over HTTP without it.
 * </ul>
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

  static final String CONTENT_SECURITY_POLICY = "default-src 'self'; frame-ancestors 'none'";
  static final String PERMISSIONS_POLICY = "camera=(), geolocation=(), microphone=()";

  @Bean
  SecurityFilterChain apiSecurity(
      HttpSecurity http,
      CsrfTokenRepository csrfTokenRepository,
      SecurityContextRepository securityContextRepository,
      JsonSecurityErrorHandler errorHandler,
      Environment environment)
      throws Exception {
    if (environment.acceptsProfiles(Profiles.of("prod"))) {
      // Replaces the deprecated requiresChannel().anyRequest().requiresSecure().
      http.redirectToHttps(Customizer.withDefaults());
    }
    return http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf")
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
        .securityContext(context -> context.securityContextRepository(securityContextRepository))
        // AuthController applies the sessionAuthenticationStrategy bean below, which matches this.
        .sessionManagement(session -> session.sessionFixation().newSession())
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                    .permissionsPolicyHeader(
                        permissions -> permissions.policy(PERMISSIONS_POLICY)))
        .exceptionHandling(
            ex -> ex.authenticationEntryPoint(errorHandler).accessDeniedHandler(errorHandler))
        .requestCache(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .build();
  }

  @Bean
  CsrfTokenRepository csrfTokenRepository() {
    return CookieCsrfTokenRepository.withHttpOnlyFalse();
  }

  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  /**
   * On login, replaces any existing session with a new one (fixation protection; only Spring
   * Security's own attributes are carried over) and rotates the CSRF token.
   */
  @Bean
  SessionAuthenticationStrategy sessionAuthenticationStrategy(
      CsrfTokenRepository csrfTokenRepository) {
    SessionFixationProtectionStrategy newSession = new SessionFixationProtectionStrategy();
    newSession.setMigrateSessionAttributes(false);
    return new CompositeSessionAuthenticationStrategy(
        List.of(newSession, new CsrfAuthenticationStrategy(csrfTokenRepository)));
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  AuthenticationManager authenticationManager(
      UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }
}
