package local.builderday.auth.logout.config;

import jakarta.servlet.http.HttpServletResponse;
import local.builderday.auth.core.service.Authentications;
import local.builderday.auth.logout.service.LogoutAuditHandler;
import local.builderday.common.exception.ApiError;
import local.builderday.common.exception.ProblemDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/** Adds Logout to the security filter chain built in {@code auth.core}. */
@Configuration
public class LogoutConfig {

  /**
   * Logout (POST only, CSRF-checked first). This filter answers before the authorization matrix is consulted, so the
   * logout path is deliberately in neither public-access nor grants (IM8 ac-1): the success handler is its guard. It
   * replaces the framework default /logout. LogoutAuditHandler runs before the Session is invalidated.
   */
  @Bean
  Customizer<HttpSecurity> logoutSecurity(LogoutAuditHandler logoutAuditHandler) {
    return http -> http.logout(logout -> logout
        .logoutUrl("/api/auth/logout")
        .addLogoutHandler(logoutAuditHandler)
        .logoutSuccessHandler((request, response, authentication) -> {
          if (Authentications.isAuthenticatedUser(authentication)) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
          } else {
            ProblemDetails.write(request, response, ApiError.AUTHENTICATION_REQUIRED);
          }
        }));
  }
}
