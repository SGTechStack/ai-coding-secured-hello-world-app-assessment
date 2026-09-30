package com.assessment.auth.common;

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
 * Puts the authenticated {@code user.id} into MDC (spec.md S4, filter 6; Std_Logging:323).
 *
 * <p>Registered <em>in the {@code SecurityFilterChain} bean</em> via {@code addFilterAfter(...,
 * AnonymousAuthenticationFilter.class)} and deliberately <strong>not</strong> a {@code @Component}
 * — a component registration would also install it in the plain servlet chain, where no
 * authentication exists yet.
 *
 * <p>Two MDC traps are written into the contract and avoided here (ticket 12):
 *
 * <ul>
 *   <li>{@code putCloseable} is <strong>not</strong> used: it closes before a {@code catch} block
 *       runs and removes rather than restores a pre-existing key (MDC:233-235). Both bite precisely
 *       on the failed-login and lockout paths this contract exists for.
 *   <li>{@code MDC.clear()} is <strong>banned outright</strong>: it would strip Micrometer's
 *       {@code traceId}/{@code spanId}. This filter removes only the key it owns.
 * </ul>
 *
 * <p>The removal is in a {@code finally} block so it happens on every exit path including
 * authentication failure and access denial (story 1.22).
 */
public class MdcUserFilter extends OncePerRequestFilter {

  static final String USER_ID = "user.id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    boolean put = false;
    if (authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof AuthenticatedUser user) {
      MDC.put(USER_ID, user.id().toString());
      put = true;
    }
    try {
      chain.doFilter(request, response);
    } finally {
      if (put) {
        MDC.remove(USER_ID);
      }
    }
  }
}
