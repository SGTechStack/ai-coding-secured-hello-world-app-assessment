package local.builderday.auth.core.config;

import java.time.Clock;
import local.builderday.account.core.service.UserProfileService;
import local.builderday.account.core.service.UserService;
import local.builderday.common.exception.ApiError;
import local.builderday.common.exception.ProblemDetails;
import local.builderday.common.web.RequestUserFilter;
import local.builderday.common.web.SpaDocumentRequestFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.HttpStatusRequestRejectedHandler;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The skeleton of the one security filter chain: authentication, the authorization matrix and the chain's error
 * responses. The auth use cases (login, logout, session, csrf) add their own parts as
 * {@code Customizer<HttpSecurity>} beans, which Spring Security applies to {@link HttpSecurity} before this chain is
 * built, so core imports none of them.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  AuthenticationManager authenticationManager(UserService users, PasswordEncoder encoder) {
    var provider = new DaoAuthenticationProvider(users);
    provider.setPasswordEncoder(encoder);
    // ADR 0003: check the password first for every account, then its status, so a disabled account costs the same
    // BCrypt work as any other rejection, and its status is only considered (and audited) once the password is right.
    // Unknown usernames are checked against the provider's own dummy hash, made by this same encoder and strength.
    provider.setPreAuthenticationChecks(user -> {});
    provider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
    return provider::authenticate;
  }

  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  /**
   * A URL the request firewall rejects (e.g. {@code //} or {@code ;}) answers 400 Bad Request. The framework installs this only
   * with an observation registry, which this application has none of, so a scanner probe would otherwise be a 500.
   */
  @Bean
  RequestRejectedHandler requestRejectedHandler() {
    return new HttpStatusRequestRejectedHandler();
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContexts,
      AuthorizationProperties authorization, UserProfileService userProfileService,
      @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping requestMappingHandlerMapping) {
    return http
        // Explicit save is the default.
        .securityContext(context -> context.securityContextRepository(securityContexts))
        // Publishes the User's id to MDC and the request log. Created here, never a bean, so it runs once.
        .addFilterAfter(new RequestUserFilter(), SecurityContextHolderFilter.class)
        // Synchronizer Token Pattern: the session-backed HttpSessionCsrfTokenRepository and the XOR request handler are
        // Spring Security's defaults. Never CookieCsrfTokenRepository or csrf.spa() (Double Submit Cookie).
        .csrf(Customizer.withDefaults())
        .authorizeHttpRequests(requests -> {
          // A null method matches every HTTP method.
          for (var rule : authorization.publicAccess()) {
            requests.requestMatchers(rule.httpMethod(), rule.pattern()).permitAll();
          }
          for (var grant : authorization.grants()) {
            requests.requestMatchers(grant.httpMethod(), grant.pattern())
                .hasAnyRole(grant.roles().toArray(String[]::new));
          }
          // ADR 0007: the client router decides what a frontend path shows, so every method is open to everyone here;
          // SpaDocumentRequestFilter answers each one after the CSRF check.
          requests.requestMatchers(SpaDocumentRequestFilter::isFrontendPath).permitAll();
          requests.anyRequest().denyAll();
        })
        // ADR 0001: these run before any controller, so they write Problem Details directly rather than via MVC
        // advice.
        // ADR 0007: a caller without a Session always gets Authentication-required, even for a path that does not
        // exist.
        .exceptionHandling(errors -> errors
            .authenticationEntryPoint((request, response, exception) ->
                ProblemDetails.write(request, response, ApiError.AUTHENTICATION_REQUIRED))
            .accessDeniedHandler(new DeniedRequestHandler(userProfileService, requestMappingHandlerMapping)))
        // The SPA never uses saved-request redirects; the default cache would create a (JDBC-stored) session for every
        // anonymous request to a protected or unlisted path just to remember it.
        .requestCache(cache -> cache.requestCache(new NullRequestCache()))
        .build();
  }
}
