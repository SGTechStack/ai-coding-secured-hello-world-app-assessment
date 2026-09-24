package com.example.demo_app.security;

import com.example.demo_app.audit.AuditEvent;
import com.example.demo_app.audit.AuditLog;
import com.example.demo_app.web.RequestBodyLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
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
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Session-based JSON API security.
 *
 * <ul>
 *   <li>CORS: the SPA runs on its own origin and calls the API with credentials. Only the exact
 *       origins in {@code app.cors.allowed-origins} ({@link CorsProperties}) get {@code
 *       Access-Control-Allow-Origin}; a preflight from any other origin is refused with {@code
 *       403}. <strong>This allow-list is what protects the CSRF token that {@code GET
 *       /api/v1/auth/csrf} returns in its body</strong>: a page on a hostile origin can send that
 *       request with the victim's cookies, and only the missing allow-origin header stops it
 *       reading the token. Never add wildcards, patterns or {@code null} to the list.
 *   <li>Login is a REST endpoint ({@code AuthController}), not form login; it uses the {@link
 *       AuthenticationManager}, {@link SessionAuthenticationStrategy} and {@link
 *       SecurityContextRepository} beans defined here.
 *   <li>CSRF: double-submit cookie ({@code XSRF-TOKEN}) checked against the {@code X-XSRF-TOKEN}
 *       header. The SPA cannot read the API's cookie across origins, so it takes the raw token
 *       from the {@code /csrf} response body and keeps it in memory. The plain request handler is
 *       used because the SPA sends that raw value, not a BREACH-masked one.
 *   <li>Failures render as JSON via {@link JsonSecurityErrorHandler}; nothing redirects.
 *   <li>Request bodies over 16 KB are rejected ({@link RequestBodyLimitFilter}) before the CSRF
 *       check, inside the chain so the rejection still carries the security headers.
 *   <li>Registration ({@code POST /api/v1/auth/register}) is anonymous but, like login, needs the
 *       CSRF token; it never creates a session.
 *   <li>Login and registration are throttled per client IP ({@link IpThrottle}, limits in {@link
 *       ThrottleProperties}); a throttled request answers {@code 429} with {@code Retry-After},
 *       which CORS exposes to the SPA.
 *   <li>Admin: everything under {@code /api/v1/admin/**} needs {@code ROLE_ADMIN} ({@code 403}
 *       for a {@code USER}, {@code 401} anonymous). Method security ({@code @PreAuthorize}) is on
 *       too, so the admin service enforces the role again; {@code ApiExceptionHandler} hands its
 *       denials back to Spring Security, so they also end as {@code 401}/{@code 403}.
 *   <li>Session fixation: login replaces the session with a new one ({@code newSession}).
 *   <li>Logout is Spring Security's logout filter on {@code POST /api/v1/auth/logout}. It runs
 *       after the CSRF check and before authorization, so it needs a valid CSRF token but no
 *       session. It invalidates the session, expires {@code JSESSIONID}, clears the CSRF cookie
 *       (the built-in CSRF logout handler), audits {@code LOGOUT} (actor {@code anonymous} without
 *       a session) and answers an empty {@code 204}.
 *   <li>Headers: a restrictive Content-Security-Policy (the API serves JSON only),
 *       Referrer-Policy and Permissions-Policy, on top of Spring Security's defaults (which include
 *       HSTS on secure requests).
 *   <li>The {@code prod} profile redirects plain HTTP to HTTPS; local dev and the e2e suite run
 *       over HTTP without it.
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(CorsProperties.class)
class SecurityConfig {

  static final String CONTENT_SECURITY_POLICY = "default-src 'self'; frame-ancestors 'none'";
  static final String PERMISSIONS_POLICY = "camera=(), geolocation=(), microphone=()";

  @Bean
  SecurityFilterChain apiSecurity(
      HttpSecurity http,
      CsrfTokenRepository csrfTokenRepository,
      SecurityContextRepository securityContextRepository,
      JsonSecurityErrorHandler errorHandler,
      ServerProperties serverProperties,
      ObjectMapper objectMapper,
      Environment environment,
      AuditLog auditLog)
      throws Exception {
    if (environment.acceptsProfiles(Profiles.of("prod"))) {
      // Replaces the deprecated requiresChannel().anyRequest().requiresSecure().
      http.redirectToHttps(Customizer.withDefaults());
    }
    return http.cors(Customizer.withDefaults())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/register")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf")
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .addFilterBefore(new RequestBodyLimitFilter(objectMapper), CsrfFilter.class)
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
        .logout(
            logout ->
                logout
                    .logoutRequestMatcher(
                        PathPatternRequestMatcher.withDefaults()
                            .matcher(HttpMethod.POST, "/api/v1/auth/logout"))
                    .addLogoutHandler(
                        (request, response, authentication) ->
                            auditLog.record(
                                AuditEvent.LOGOUT,
                                authentication == null ? null : authentication.getName(),
                                request))
                    .addLogoutHandler(
                        new CookieClearingLogoutHandler(
                            expiredSessionCookie(
                                serverProperties.getServlet().getSession().getCookie())))
                    .logoutSuccessHandler(
                        new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
        .build();
  }

  /**
   * A {@code Max-Age=0} copy of the session cookie as configured under {@code
   * server.servlet.session.cookie} (including its domain, if one is set), so the browser replaces
   * the cookie it holds. Spring Security's {@code deleteCookies} would drop {@code HttpOnly} and
   * take {@code Secure} from the request.
   */
  static Cookie expiredSessionCookie(org.springframework.boot.web.server.Cookie config) {
    Cookie cookie = new Cookie(Objects.requireNonNullElse(config.getName(), "JSESSIONID"), null);
    cookie.setPath(Objects.requireNonNullElse(config.getPath(), "/"));
    if (config.getDomain() != null) {
      cookie.setDomain(config.getDomain());
    }
    cookie.setMaxAge(0);
    cookie.setHttpOnly(!Boolean.FALSE.equals(config.getHttpOnly()));
    cookie.setSecure(Boolean.TRUE.equals(config.getSecure()));
    if (config.getSameSite() != null) {
      cookie.setAttribute("SameSite", config.getSameSite().attributeValue());
    }
    return cookie;
  }

  /** Exact-origin allow-list for the SPA; see the class comment for why it matters. */
  @Bean
  CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(properties.allowedOrigins());
    cors.setAllowCredentials(true);
    cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE"));
    cors.setAllowedHeaders(List.of("Content-Type", "Accept", "X-XSRF-TOKEN"));
    // Not a CORS-safelisted response header: without this the SPA can't read a 429's wait time.
    cors.setExposedHeaders(List.of(HttpHeaders.RETRY_AFTER));
    cors.setMaxAge(Duration.ofHours(1));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);
    return source;
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

  /**
   * BCrypt at cost 12 for new hashes. The cost is read from each stored hash, so older cost-10
   * hashes (such as the demo seed) keep verifying.
   */
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  AuthenticationManager authenticationManager(
      UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }
}
