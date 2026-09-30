package local.builderday.auth.session;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ends a session once it is older than the absolute lifetime, however active it has been. Spring Session only
 * enforces idle timeout. Runs before the security context is loaded, so an expired session is treated as anonymous.
 * Never creates a session.
 */
public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {
  private final Duration absoluteTimeout;
  private final Clock clock;

  public AbsoluteSessionTimeoutFilter(Duration absoluteTimeout, Clock clock) {
    this.absoluteTimeout = absoluteTimeout;
    this.clock = clock;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var session = request.getSession(false);
    if (session != null) {
      try {
        if (clock.millis() - session.getCreationTime() > absoluteTimeout.toMillis()) session.invalidate();
      } catch (IllegalStateException alreadyInvalidated) {
        // Nothing to end.
      }
    }
    chain.doFilter(request, response);
  }
}
