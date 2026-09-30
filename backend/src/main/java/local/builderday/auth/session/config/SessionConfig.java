package local.builderday.auth.session.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import local.builderday.auth.session.AbsoluteSessionTimeoutFilter;
import local.builderday.common.exception.ApiError;
import local.builderday.common.exception.ProblemDetails;
import local.builderday.common.web.SpaDocumentRequestFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;

/** The Session rules: one Session per User, fixation protection and the absolute lifetime. */
@Configuration
public class SessionConfig {
  private static final int MAXIMUM_SESSIONS = 1;

  /**
   * Session registry backed by the shared Spring Session JDBC store, so one-session-per-user holds across instances.
   */
  @Bean
  <S extends Session> SessionRegistry sessionRegistry(FindByIndexNameSessionRepository<S> sessions) {
    return new SpringSessionBackedSessionRegistry<>(sessions);
  }

  /**
   * Applied by the login endpoint on success, in the order Spring Security's own login filters use: expire the user's
   * earlier session, replace the anonymous session with a fresh one (fixation protection, like {@code newSession()}:
   * no application attributes, so the anonymous CSRF token is dropped), then register the new session.
   */
  @Bean
  SessionAuthenticationStrategy loginSessionStrategy(SessionRegistry sessionRegistry) {
    var concurrentSessionControl = new ConcurrentSessionControlAuthenticationStrategy(sessionRegistry);
    concurrentSessionControl.setMaximumSessions(MAXIMUM_SESSIONS);
    var fixationProtection = new SessionFixationProtectionStrategy();
    fixationProtection.setMigrateSessionAttributes(false);
    return new CompositeSessionAuthenticationStrategy(List.of(concurrentSessionControl, fixationProtection,
        new RegisterSessionAuthenticationStrategy(sessionRegistry)));
  }

  /** @param absoluteTimeout hard session lifetime from login, regardless of activity */
  @Bean
  Customizer<HttpSecurity> sessionSecurity(SessionRegistry sessionRegistry, Clock clock,
      @Value("${app.security.session.absolute-timeout}") Duration absoluteTimeout) {
    return http -> http
        .addFilterBefore(new AbsoluteSessionTimeoutFilter(absoluteTimeout, clock), SecurityContextHolderFilter.class)
        // Login is a controller, not a Spring Security auth filter, so the chain's own session authentication strategy
        // never runs; LoginService applies loginSessionStrategy instead. This block only installs
        // ConcurrentSessionFilter, which rejects sessions that strategy expired.
        .sessionManagement(sessions -> sessions.maximumSessions(MAXIMUM_SESSIONS)
            .expiredSessionStrategy(event -> sessionExpired(event.getRequest(), event.getResponse()))
            .sessionRegistry(sessionRegistry));
  }

  /**
   * A Session ended by a later login: a page load goes to the login page, since this answer replaces the SPA document;
   * everything else gets a 401 Problem Detail.
   */
  private static void sessionExpired(HttpServletRequest request, HttpServletResponse response) throws IOException {
    if (SpaDocumentRequestFilter.isFrontendPath(request) && "GET".equals(request.getMethod())) {
      response.sendRedirect(request.getContextPath() + "/login");
    } else {
      ProblemDetails.write(request, response, ApiError.AUTHENTICATION_REQUIRED);
    }
  }
}
