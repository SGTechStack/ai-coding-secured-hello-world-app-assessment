package org.eds.demo.config;

import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eds.demo.common.AppErrorController;
import org.eds.demo.common.WebSpaController;
import org.eds.demo.common.WebSpaCsrfTokenRequestHandler;
import org.eds.demo.user.application.AppUserDetailsService;
import org.eds.demo.user.application.PasswordChangeService;
import org.eds.demo.user.domain.Role;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

/**
 * Central HTTP security configuration for the application and actuator filter chains.
 *
 * <p>CSRF protection uses {@link CookieCsrfTokenRepository#withHttpOnlyFalse()} so that JavaScript
 * clients can read the {@code XSRF-TOKEN} cookie and echo it back as the {@code X-XSRF-TOKEN}
 * request header on every mutating request (POST, PUT, DELETE, PATCH). The {@link
 * WebSpaCsrfTokenRequestHandler} follows Spring Security's recommended SPA pattern: it delegates
 * rendering to {@link XorCsrfTokenRequestAttributeHandler} for BREACH protection, but resolves the
 * raw cookie token when the SPA supplies it via the {@code X-XSRF-TOKEN} header. The actuator chain
 * disables CSRF because its callers are non-browser monitoring tools.
 *
 * <p>The API and admin API chains are identical in every profile, including {@code local}: the only
 * local addition is the separate H2 console chain in {@link LocalSecurityConfiguration}. An
 * optional {@link ApplicationChainCustomizer} bean can still extend the application chain.
 */
@Slf4j
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(AppProperties.class)
@RequiredArgsConstructor
class SecurityConfiguration {

  private final AppProperties appProperties;

  static final String LOGIN_URL = "/login";
  static final String LOGOUT_URL = "/logout";

  /** Admin REST endpoints; deliberately outside {@code /api/**} (see OpenApiGroupConfiguration). */
  static final String ADMIN_API_PATTERN = "/admin/api/**";

  static final String[] PUBLIC_ROUTES = {
    WebSpaController.SITE_ROOT,
    WebSpaController.SIGN_IN_PATH,
    WebSpaController.SIGN_IN_PATH + "/**",
    // The SPA sign-in page and the built assets it needs must load before anyone is signed in.
    // WebSpaController serves them by forwarding to /index.html and /assets/**, and the forward is
    // authorized again, so those targets must be public too. They are the same static build files.
    WebSpaController.SPA_SIGN_IN_PATH,
    WebSpaController.SPA_ROOT + "/assets/**",
    "/index.html",
    "/assets/**",
    LOGIN_URL,
    AppErrorController.ERROR_URL,
    AppErrorController.NOT_FOUND_PAGE,
    AppErrorController.SERVER_ERROR_PAGE
  };

  /**
   * Granted-authority names accepted by the SPA. A user must hold at least one of these to access
   * {@code /app/**}; authentication alone (no role) is not enough.
   */
  static final String[] APP_ROLES =
      Arrays.stream(Role.values()).map(role -> role.name()).toArray(String[]::new);

  /**
   * Pluggable delta applied to the application filter chain. Provide a bean of this type to extend
   * the chain without duplicating its base rules.
   */
  @FunctionalInterface
  interface ApplicationChainCustomizer {
    void customize(HttpSecurity http) throws Exception;
  }

  /**
   * Enables enterprise OIDC login on the application filter chain (ADR-DEMO-BE-0012). Provided by
   * {@code OidcClientConfiguration} only when a {@code ClientRegistrationRepository} exists (i.e.
   * the {@code feat-oidc} profile is active with a registered client), so plain profiles keep form
   * login untouched. Kept separate from {@link ApplicationChainCustomizer} so the two can coexist
   * when {@code feat-oidc} is stacked on {@code local} for local testing.
   */
  @FunctionalInterface
  interface OidcLoginCustomizer {
    void customize(HttpSecurity http) throws Exception;
  }

  /**
   * Permits all actuator requests without authentication. Runs first so monitoring tools are never
   * blocked by the app or API chains.
   */
  @Bean
  @Order(SecurityFilterChainOrder.ACTUATOR)
  SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) {
    return http.securityMatcher(EndpointRequest.toAnyEndpoint())
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
        .build();
  }

  /**
   * Secures all {@code /api/**} REST endpoints. Unauthenticated requests receive a JSON 401 rather
   * than a login redirect. All endpoints require authentication; authentication is handled by an
   * upstream identity provider.
   *
   * <p>The chain is the same in every profile (CSRF on, session sign-in via {@code POST /login}).
   */
  @Bean
  @Order(SecurityFilterChainOrder.API)
  SecurityFilterChain apiSecurityFilterChain(
      HttpSecurity http, PasswordChangeService passwordChangeService) throws Exception {
    var chain =
        withPasswordChangeRequired(
            withApiExceptionHandling(withSpaCsrf(http.securityMatcher("/api/**"))),
            passwordChangeService);
    return chain.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated()).build();
  }

  /**
   * Secures all {@code /admin/api/**} endpoints, served to the separately deployed admin frontend.
   * Only {@link Role#ADMIN} is admitted; every other request is denied (JSON 401/403). Reuses the
   * Same CSRF and sign-in rules as the API chain in every profile.
   */
  @Bean
  @Order(SecurityFilterChainOrder.ADMIN_API)
  SecurityFilterChain adminApiSecurityFilterChain(
      HttpSecurity http, PasswordChangeService passwordChangeService) throws Exception {
    var chain =
        withPasswordChangeRequired(
            withApiExceptionHandling(withSpaCsrf(http.securityMatcher(ADMIN_API_PATTERN))),
            passwordChangeService);
    return chain
        .authorizeHttpRequests(authorize -> authorize.anyRequest().hasRole(Role.ADMIN.name()))
        .build();
  }

  /**
   * Catch-all chain for the React SPA and HTML pages. Public routes ({@code /}, {@code /login},
   * {@code /welcome/**}, etc.) are permit-all; everything else requires authentication.
   * Unauthenticated requests are redirected to the login page rather than returning a 401.
   *
   * <p>An optional {@link ApplicationChainCustomizer} bean is applied before the final {@code
   * anyRequest().authenticated()} rule.
   */
  @Bean
  SecurityFilterChain applicationSecurityFilterChain(
      HttpSecurity http,
      Optional<ApplicationChainCustomizer> customizer,
      Optional<OidcLoginCustomizer> oidcLoginCustomizer,
      PasswordChangeService passwordChangeService)
      throws Exception {
    var chain =
        withPasswordChangeRequired(
            withContentSecurityPolicy(
                withSignInAndSignOut(withSpaCsrf(http)),
                appProperties.security().csp().policyDirectives()),
            passwordChangeService);
    if (customizer.isPresent()) {
      customizer.get().customize(chain);
    }
    if (oidcLoginCustomizer.isPresent()) {
      oidcLoginCustomizer.get().customize(chain);
    }
    return chain
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(PUBLIC_ROUTES)
                    .permitAll()
                    .requestMatchers(WebSpaController.SPA_ROOT, WebSpaController.SPA_ROOT + "/**")
                    .hasAnyRole(APP_ROLES)
                    .anyRequest()
                    .authenticated())
        .build();
  }

  /**
   * Refuses everything but change-password and sign-out while a Temporary Password is unchanged.
   */
  static HttpSecurity withPasswordChangeRequired(
      HttpSecurity http, PasswordChangeService passwordChangeService) {
    return http.addFilterBefore(
        new PasswordChangeRequiredFilter(passwordChangeService), AuthorizationFilter.class);
  }

  static HttpSecurity withSpaCsrf(HttpSecurity http) throws Exception {
    return http.csrf(
        csrf ->
            csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new WebSpaCsrfTokenRequestHandler()));
  }

  static HttpSecurity withApiExceptionHandling(HttpSecurity http) throws Exception {
    return http.exceptionHandling(
        ex ->
            ex.accessDeniedHandler(SecurityProblemDetailHandlers.problemDetailAccessDeniedHandler())
                .authenticationEntryPoint(SecurityProblemDetailHandlers.problemDetailEntryPoint()));
  }

  /**
   * Sign-in is a JSON endpoint ({@code SignInController}), so there is no form login.
   * Unauthenticated page requests are redirected to the SPA sign-in page; sign-out ends the server
   * session and answers 204 with no redirect.
   */
  static HttpSecurity withSignInAndSignOut(HttpSecurity http) throws Exception {
    return http.exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                    new LoginUrlAuthenticationEntryPoint(WebSpaController.SPA_SIGN_IN_PATH)))
        .logout(
            logout ->
                logout
                    .logoutUrl(LOGOUT_URL)
                    .logoutSuccessHandler(
                        new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));
  }

  static HttpSecurity withContentSecurityPolicy(HttpSecurity http, String policyDirectives)
      throws Exception {
    return http.headers(
        headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(policyDirectives)));
  }

  /** Checks submitted credentials against the stored delegating-encoder hash. */
  @Bean
  AuthenticationManager authenticationManager(
      AppUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    var provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }
}
