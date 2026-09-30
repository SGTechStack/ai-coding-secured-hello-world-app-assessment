package com.assessment.auth.security;

import com.assessment.auth.audit.AuditEvent;
import com.assessment.auth.audit.AuditLogger;
import com.assessment.auth.common.ApiErrorCode;
import com.assessment.auth.common.ApiProperties;
import com.assessment.auth.common.MdcUserFilter;
import com.assessment.auth.common.ProblemDetailWriter;
import com.assessment.auth.user.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.time.Clock;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

/**
 * The security chain (spec.md S3, S4).
 *
 * <p><strong>{@code @EnableMethodSecurity} is deliberately absent.</strong> The authorization
 * matrix is the sole mechanism, and no {@code @PreAuthorize} exists anywhere in this codebase — the
 * five in the standards' recipes were deleted rather than rewritten, because they name authorities
 * that do not exist under this application's role model (ticket 03, ticket 08).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final ApiProperties apiProperties;
  private final AppSecurityProperties securityProperties;
  private final SessionPolicyProperties sessionPolicyProperties;
  private final RateLimitProperties rateLimitProperties;
  private final ProblemDetailWriter problemDetailWriter;
  private final AuditLogger auditLogger;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final UserRepository userRepository;

  public SecurityConfig(
      ApiProperties apiProperties,
      AppSecurityProperties securityProperties,
      SessionPolicyProperties sessionPolicyProperties,
      RateLimitProperties rateLimitProperties,
      ProblemDetailWriter problemDetailWriter,
      AuditLogger auditLogger,
      ObjectMapper objectMapper,
      Clock clock,
      UserRepository userRepository) {
    this.apiProperties = apiProperties;
    this.securityProperties = securityProperties;
    this.sessionPolicyProperties = sessionPolicyProperties;
    this.rateLimitProperties = rateLimitProperties;
    this.problemDetailWriter = problemDetailWriter;
    this.auditLogger = auditLogger;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.userRepository = userRepository;
  }

  /**
   * {@code ROLE_USER_MANAGER > ROLE_USER}.
   *
   * <p>This is load-bearing rather than decorative precisely because matrix row 20 ({@code GET
   * /hello}) is the <em>only</em> {@code hasRole('USER')} row: without the hierarchy a USER_MANAGER
   * would be refused the greeting endpoint, and story 1.7 asserts they are not.
   */
  @Bean
  public RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.withDefaultRolePrefix().role("USER_MANAGER").implies("USER").build();
  }

  /**
   * Backed by Spring Session, not {@code SessionRegistryImpl}.
   *
   * <p>{@code SessionRegistryImpl} is in-memory, so the one-session limit would silently reset on
   * restart — and Std:409 requires the limit to hold "across requests and restarts".
   */
  @Bean
  public SessionRegistry sessionRegistry(
      FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
    return new SpringSessionBackedSessionRegistry<>(sessionRepository);
  }

  @Bean
  public AuthenticationManager authenticationManager(AccountAuthenticationProvider provider) {
    return new ProviderManager(provider);
  }

  /**
   * A bean rather than an inline {@code new}, so its counters can be reset between integration
   * tests. It is added to the chain by {@code addFilterBefore} below, not registered as a servlet
   * filter — it must sit inside the security chain, ahead of CSRF and authentication.
   */
  @Bean
  public RateLimitFilter rateLimitFilter() {
    return new RateLimitFilter(
        apiProperties.basePath(),
        rateLimitProperties,
        problemDetailWriter,
        auditLogger,
        objectMapper);
  }

  /**
   * Stops Boot from <em>also</em> installing {@link #rateLimitFilter()} in the plain servlet chain.
   *
   * <p>Any {@code Filter} bean is auto-registered by default. That would run the limiter twice per
   * request — consuming two tokens for one call, halving every configured limit — and the second
   * copy would sit outside the security chain entirely.
   */
  @Bean
  public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
      RateLimitFilter filter) {
    FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  /**
   * CORS on the chain.
   *
   * <p>Three defects in the source recipe are fixed here: the recipe's chain never calls {@code
   * .cors(...)} at all, its {@code SecurityProperties} type is undefined, and it leaves {@code
   * ${api.base-path}} unresolved inside a Java string literal.
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    // An explicit allowlist. setAllowedOrigins (not ...Patterns) cannot express a wildcard, which
    // matters because allowCredentials=true plus a wildcard origin is a credential leak.
    configuration.setAllowedOrigins(securityProperties.allowedOrigins());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-CSRF-TOKEN"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      AuthenticationManager authenticationManager,
      SessionRegistry sessionRegistry,
      RoleHierarchy roleHierarchy)
      throws Exception {

    String base = apiProperties.basePath();

    JsonAuthenticationFilter authenticationFilter =
        new JsonAuthenticationFilter(
            base + "/auth/login",
            objectMapper,
            auditLogger,
            securityProperties.responseTimeFloor());
    authenticationFilter.setAuthenticationManager(authenticationManager);
    // Wired EXPLICITLY, and this is not optional bookkeeping.
    //
    // `.sessionManagement().sessionFixation()` and `.maximumSessions()` configure the strategy that
    // the formLogin configurer hands to ITS filter. formLogin is disabled here and this filter is
    // installed with addFilterAt, so it never receives that strategy and silently falls back to
    // NullAuthenticatedSessionStrategy -- no session-id rotation on login (a session fixation hole)
    // and no registration with the SessionRegistry (so the one-session limit never triggers).
    // Both are story 1.6 acceptance criteria and both fail quietly without these three lines.
    ConcurrentSessionControlAuthenticationStrategy concurrentSessionControl =
        new ConcurrentSessionControlAuthenticationStrategy(sessionRegistry);
    concurrentSessionControl.setMaximumSessions(sessionPolicyProperties.maxConcurrentSessions());
    concurrentSessionControl.setExceptionIfMaximumExceeded(false);
    authenticationFilter.setSessionAuthenticationStrategy(
        new CompositeSessionAuthenticationStrategy(
            List.of(
                concurrentSessionControl,
                new ChangeSessionIdAuthenticationStrategy(),
                new RegisterSessionAuthenticationStrategy(sessionRegistry))));
    authenticationFilter.setSecurityContextRepository(
        new org.springframework.security.web.context.DelegatingSecurityContextRepository(
            new org.springframework.security.web.context.HttpSessionSecurityContextRepository(),
            new org.springframework.security.web.context.RequestAttributeSecurityContextRepository()));

    http.securityMatcher(AnyRequestMatcher.INSTANCE)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(
            csrf ->
                csrf
                    // Session-bound Synchronizer Token. CookieCsrfTokenRepository is STRICTLY
                    // PROHIBITED (Std:238), so no XSRF-TOKEN cookie exists at all -- and its
                    // absence is an acceptance test (story 1.4).
                    .csrfTokenRepository(new HttpSessionCsrfTokenRepository())
                    .csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler()))
        // NO endpoint is CSRF-exempt. Login, register and both reset endpoints are permitAll AND
        // CSRF-protected, which is what makes the SPA bootstrap GET /csrf -> POST /auth/login ->
        // GET /currentUser mandatory. Logout keeps CSRF deliberately: Std:438 forbids "fixing" the
        // expired-session 401 (spec.md S4).
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(
                        csp -> csp.policyDirectives("default-src 'self'; object-src 'none';"))
                    .permissionsPolicyHeader(
                        permissions ->
                            permissions.policy("geolocation=(), microphone=(), camera=()"))
                    .frameOptions(frame -> frame.deny())
                    .contentTypeOptions(contentType -> {})
                    .httpStrictTransportSecurity(
                        hsts ->
                            hsts.maxAgeInSeconds(31536000)
                                .includeSubDomains(true)
                                // No `preload`: it is a public-registry commitment that cannot be
                                // withdrawn quickly, and the binding set does not require it.
                                .preload(false))
                    .referrerPolicy(
                        referrer ->
                            referrer.policy(
                                org.springframework.security.web.header.writers
                                        .ReferrerPolicyHeaderWriter.ReferrerPolicy
                                    .NO_REFERRER)))
        .sessionManagement(
            session ->
                session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    // The framework default, stated explicitly because it is a decision.
                    .sessionFixation(fixation -> fixation.changeSessionId())
                    .maximumSessions(sessionPolicyProperties.maxConcurrentSessions())
                    .sessionRegistry(sessionRegistry)
                    // Explicit, and NOT optional. Without it SessionManagementConfigurer installs
                    // ResponseBodySessionInformationExpiredStrategy, which writes a plain-text
                    // sentence and LEAVES THE STATUS AT 200. The session really is dead, so the
                    // SPA would receive a 200 carrying prose where it expected JSON, never see a
                    // 401, and never run the interceptor that logs the user out (Std:438,
                    // spec.md S10). It is also the only place CONCURRENT_SESSION_EXPIRED can be
                    // emitted from.
                    .expiredSessionStrategy(this::onConcurrentSessionExpired))
        // NO InvalidSessionStrategy. Std:90 prescribes a redirect that Std:438 forbids, and setting
        // one makes CsrfConfigurer insert an InvalidSessionAccessDeniedHandler ahead of the JSON
        // handler -- silently breaking the error contract on the expiry path (spec.md S4).
        .exceptionHandling(
            exceptions ->
                exceptions
                    // Empty body, and 401 rather than 403. Without an explicit entry point,
                    // createDefaultEntryPoint returns Http403ForbiddenEntryPoint and an
                    // unauthenticated GET /hello would answer 403 (spec.md S13, test 4).
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                    .accessDeniedHandler(
                        (request, response, denied) ->
                            problemDetailWriter.write(
                                response,
                                ApiErrorCode.ACCESS_DENIED,
                                "Access to this resource is denied.")))
        .logout(logout -> logout.disable())
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .anonymous(anonymous -> {});

    applyAuthorizationMatrix(http, roleHierarchy);

    http.addFilterBefore(
            rateLimitFilter(), org.springframework.security.web.csrf.CsrfFilter.class)
        .addFilterAt(authenticationFilter, UsernamePasswordAuthenticationFilter.class)
        // Registered HERE and not as a @Component: a component registration would also install it
        // in the plain servlet chain, where no authentication exists yet (spec.md S4, filter 6).
        .addFilterAfter(new MdcUserFilter(), AnonymousAuthenticationFilter.class)
        .addFilterAfter(
            new AbsoluteSessionTimeoutFilter(
                sessionPolicyProperties.absoluteTimeout(), auditLogger, clock),
            MdcUserFilter.class)
        // Tier 0: it PREEMPTS the matrix, so it must sit before AuthorizationFilter.
        .addFilterBefore(
            new PasswordChangeFilter(base, problemDetailWriter, auditLogger),
            org.springframework.security.web.access.intercept.AuthorizationFilter.class);

    return http.build();
  }

  /**
   * The response when a newer login has displaced this session.
   *
   * <p><strong>401 with an empty body</strong> — byte-identical to every other 401 this application
   * produces, because the SPA's only correct reaction is the same in every case: the session is gone,
   * log out. Distinguishing "displaced by a second login" from "expired" in the response would give
   * the client a state it cannot act on differently, and it would tell an attacker holding a stolen
   * cookie that the real user has just logged in.
   *
   * <p>Written directly rather than through {@code ProblemDetailWriter}: this is the 401 family, and
   * {@code HttpStatusEntryPoint} cannot write a body at all, so a JSON body here would make the two
   * paths distinguishable. {@code response.sendError} is banned chain-wide (ArchUnit rule 5).
   */
  private void onConcurrentSessionExpired(
      org.springframework.security.web.session.SessionInformationExpiredEvent event)
      throws java.io.IOException {

    AuditEvent.Builder audit =
        AuditEvent.of(
                com.assessment.auth.audit.AuditAction.SESSION_MANAGEMENT,
                com.assessment.auth.audit.AuditReason.CONCURRENT_SESSION_EXPIRED,
                org.slf4j.event.Level.INFO)
            .outcome("failure")
            .sourceIp(event.getRequest().getRemoteAddr());

    // The actor is resolved through the repository, and the indirection is forced.
    //
    // SpringSessionBackedSessionRegistry builds its SessionInformation from Spring Session's
    // PRINCIPAL_NAME index, so getPrincipal() returns the USERNAME STRING -- not the
    // AuthenticatedUser that the authentication filter registered. An `instanceof AuthenticatedUser`
    // check here compiles, reads correctly, and never matches: the event would ship with no actor and
    // nothing would fail. And the username cannot be logged instead, because `user.id` is the only
    // identity this application ever writes to a log line (spec.md S11) -- so a lookup is the only
    // way to name the account at all.
    //
    // A miss is not an error: an account deleted between its login and this request is possible, and
    // the event still records that a session was displaced and from where.
    if (event.getSessionInformation().getPrincipal() instanceof String username) {
      userRepository.findByUsername(username).ifPresent(user -> audit.actor(user.getId()));
    }
    auditLogger.emit(audit.build());

    HttpServletResponse response = event.getResponse();
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentLength(0);
    response.flushBuffer();
  }

  /**
   * Translates the configured matrix into chain rules, in order.
   *
   * <p>Order is the whole mechanism: first match wins, and the final row is a terminal {@code
   * denyAll} that closes {@code /actuator/**} and Swagger with no explicit row of their own.
   */
  private void applyAuthorizationMatrix(HttpSecurity http, RoleHierarchy roleHierarchy)
      throws Exception {
    http.authorizeHttpRequests(
        registry -> {
          for (AppSecurityProperties.MatrixRow row : securityProperties.authorizationMatrix()) {
            registry.requestMatchers(matcherFor(row)).access(managerFor(row, roleHierarchy));
          }
          // Belt and braces: if the configured matrix ever loses its terminal row, the chain still
          // denies rather than permits.
          registry.anyRequest().denyAll();
        });
  }

  private RequestMatcher matcherFor(AppSecurityProperties.MatrixRow row) {
    PathPatternRequestMatcher.Builder builder = PathPatternRequestMatcher.withDefaults();
    if ("ANY".equalsIgnoreCase(row.method())) {
      return builder.matcher(row.path());
    }
    return builder.matcher(HttpMethod.valueOf(row.method().toUpperCase()), row.path());
  }

  private AuthorizationManager<RequestAuthorizationContext> managerFor(
      AppSecurityProperties.MatrixRow row, RoleHierarchy roleHierarchy) {
    String access = row.access().trim();
    if ("permitAll".equalsIgnoreCase(access)) {
      return (authentication, context) ->
          new org.springframework.security.authorization.AuthorizationDecision(true);
    }
    if ("denyAll".equalsIgnoreCase(access)) {
      return (authentication, context) ->
          new org.springframework.security.authorization.AuthorizationDecision(false);
    }
    if ("authenticated".equalsIgnoreCase(access)) {
      return AuthenticatedAuthorizationManager.authenticated();
    }
    if (access.startsWith("hasRole(")) {
      String role = access.substring(access.indexOf('(') + 1, access.lastIndexOf(')')).trim();
      role = role.replace("'", "").replace("\"", "");
      AuthorityAuthorizationManager<RequestAuthorizationContext> manager =
          AuthorityAuthorizationManager.hasRole(role);
      // Without this the hierarchy is dead and a USER_MANAGER is refused GET /hello.
      manager.setRoleHierarchy(roleHierarchy);
      return manager;
    }
    throw new IllegalStateException(
        "Unsupported authorization-matrix access expression: " + row.access());
  }
}
