package local.builderday.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Publishes the authenticated User's id for the request: as MDC {@code user.id} on every log line written inside the
 * security chain, and as {@link RequestLogFilter#USER_ID_ATTRIBUTE} for the completion Request log entry. Placed
 * directly after the security-context holder filter. A User who logs in during the request replaces the id; one who
 * logs out keeps it. Writes no log lines. Created by the security configuration, never a bean, so it is not also
 * registered as a servlet filter.
 */
public class RequestUserFilter extends OncePerRequestFilter {
  public static final String MDC_KEY = "user.id";

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String entryUserId = currentUserId();
    if (entryUserId != null) {
      request.setAttribute(RequestLogFilter.USER_ID_ATTRIBUTE, entryUserId);
      MDC.put(MDC_KEY, entryUserId);
    }
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
      String exitUserId = currentUserId();
      if (exitUserId != null) request.setAttribute(RequestLogFilter.USER_ID_ATTRIBUTE, exitUserId);
    }
  }

  /** @return the id of an {@link AuthenticatedUser} principal; {@code null} for anonymous or any other principal */
  private static String currentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
        ? user.userId().toString() : null;
  }
}
