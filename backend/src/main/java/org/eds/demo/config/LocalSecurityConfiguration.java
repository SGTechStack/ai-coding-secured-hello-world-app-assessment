package org.eds.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Local-profile delta: the H2 console gets its own filter chain.
 *
 * <p>The API, admin API and application chains have no local delta: CSRF, sign-in and sign-out
 * behave the same in every profile, so what works locally works when deployed. Call the API with
 * {@code curl} by signing in through {@code POST /login} (see README). The {@code /h2-console/**}
 * chain (see {@link #h2ConsoleSecurityFilterChain}) keeps its relaxed CSP and framing rules out of
 * the SPA catch-all chain.
 */
@Configuration
@Profile("local")
class LocalSecurityConfiguration {

  private static final String H2_CONSOLE_ALL = "/h2-console/**";

  /**
   * Dedicated chain for the H2 console, matched only on {@code /h2-console/**}. The console renders
   * inline {@code <script>} tags with no nonce, so it needs {@code 'unsafe-inline'} in {@code
   * script-src}; scoping that relaxation to its own chain keeps the SPA catch-all chain's CSP
   * untouched.
   */
  @Bean
  @Order(SecurityFilterChainOrder.H2_CONSOLE)
  SecurityFilterChain h2ConsoleSecurityFilterChain(HttpSecurity http) throws Exception {
    return http.securityMatcher(H2_CONSOLE_ALL)
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
        .csrf(csrf -> csrf.disable())
        .headers(
            headers ->
                headers
                    .frameOptions(frame -> frame.sameOrigin())
                    .contentSecurityPolicy(
                        csp ->
                            csp.policyDirectives(
                                "default-src 'self'; script-src 'self' 'unsafe-inline'; "
                                    + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
                                    + "object-src 'none'; frame-ancestors 'self';")))
        .build();
  }
}
