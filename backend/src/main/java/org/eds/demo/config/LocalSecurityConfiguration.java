package org.eds.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Local-profile deltas for the API security chain and the H2 console.
 *
 * <p>The customizer bean implements the interface declared in {@link SecurityConfiguration}. It is
 * injected into the base chains as an {@code Optional} parameter — the base chains always own
 * construction and the final {@code anyRequest()} rule, so local additions cannot accidentally drop
 * shared security rules. The application chain has no local delta: sign-in and sign-out behave the
 * same in every profile.
 *
 * <p>Local-specific additions:
 *
 * <ul>
 *   <li>HTTP Basic is enabled on the API chain; CSRF is disabled there to allow {@code curl} calls
 *       without a token.
 *   <li>{@code /h2-console/**} gets its own filter chain (see {@link
 *       #h2ConsoleSecurityFilterChain}) so its relaxed CSP and framing rules don't leak into the
 *       SPA catch-all chain.
 * </ul>
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

  @Bean
  SecurityConfiguration.ApiChainCustomizer apiChainCustomizer() {
    return http ->
        http.httpBasic(
                basic ->
                    basic.authenticationEntryPoint(
                        SecurityProblemDetailHandlers.problemDetailEntryPoint()))
            .csrf(csrf -> csrf.disable());
  }
}
