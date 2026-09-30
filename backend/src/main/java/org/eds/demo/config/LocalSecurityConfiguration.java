package org.eds.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Local-profile deltas for the API and application security chains.
 *
 * <p>Both beans implement the customizer interfaces declared in {@link SecurityConfiguration}. They
 * are injected into the base chains as {@code Optional} parameters — the base chains always own
 * construction and the final {@code anyRequest()} rule, so local additions cannot accidentally drop
 * shared security rules.
 *
 * <p>Local-specific additions:
 *
 * <ul>
 *   <li>{@code /api/login-options} is permit-all (used by the mock login page).
 *   <li>HTTP Basic is enabled on the API chain; CSRF is disabled there to allow {@code curl} calls
 *       without a token.
 *   <li>{@code /local-mock-login.html} is permit-all.
 *   <li>CSRF is ignored for {@code /login} and {@code /logout}.
 *   <li>Form login uses an explicit login page at {@code /login}.
 *   <li>{@code /internal/**} is permit-all; the {@link
 *       org.eds.demo.config.internal.InternalPreAuthFilter} still runs so headers are honoured.
 *   <li>{@code /h2-console/**} gets its own filter chain (see {@link
 *       #h2ConsoleSecurityFilterChain}) so its relaxed CSP and framing rules don't leak into the
 *       SPA catch-all chain.
 * </ul>
 */
@Configuration
@Profile("local")
class LocalSecurityConfiguration {

  private static final String H2_CONSOLE_ALL = "/h2-console/**";

  private static final String[] UNSAFE_PUBLIC_ROUTES = {
    LocalMockLoginController.LOGIN_PAGE, "/local-mock-login.js"
  };

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
        http.authorizeHttpRequests(
                authorize ->
                    authorize
                        .requestMatchers(LocalMockLoginController.LOGIN_OPTIONS_URL)
                        .permitAll())
            .httpBasic(
                basic ->
                    basic.authenticationEntryPoint(
                        SecurityProblemDetailHandlers.problemDetailEntryPoint()))
            .csrf(csrf -> csrf.disable());
  }

  /**
   * Adds the mock login page as a permit-all route, ignores CSRF for login/logout, and sets an
   * explicit login page. Applied before {@code anyRequest().authenticated()} in the base chain.
   */
  @Bean
  SecurityConfiguration.ApplicationChainCustomizer applicationChainCustomizer() {
    return http ->
        http.csrf(
                csrf ->
                    csrf.ignoringRequestMatchers(
                        SecurityConfiguration.LOGIN_URL, SecurityConfiguration.LOGOUT_URL))
            .authorizeHttpRequests(
                authorize -> authorize.requestMatchers(UNSAFE_PUBLIC_ROUTES).permitAll())
            .formLogin(formLogin -> formLogin.loginPage(SecurityConfiguration.LOGIN_URL));
  }
}
