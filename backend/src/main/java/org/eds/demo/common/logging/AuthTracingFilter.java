package org.eds.demo.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.eds.demo.user.domain.AppUserDetails;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
// Spring Security's filter chain runs at -100; this runs immediately after to read the
// SecurityContext
@Order(-99)
public class AuthTracingFilter extends OncePerRequestFilter {

  private static final String MDC_USER_ID = "user.id";
  private static final String MDC_USER_NAME = "user.name";
  private static final String ANONYMOUS = "anonymous";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof AppUserDetails details) {
      MDC.put(MDC_USER_ID, details.getUserId().value().toString());
      MDC.put(MDC_USER_NAME, details.getUsername());
    } else {
      MDC.put(MDC_USER_ID, ANONYMOUS);
      MDC.put(MDC_USER_NAME, ANONYMOUS);
    }

    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_USER_ID);
      MDC.remove(MDC_USER_NAME);
    }
  }
}
