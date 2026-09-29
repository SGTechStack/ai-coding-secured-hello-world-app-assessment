package com.example.hello.auth;

import com.example.hello.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class CurrentAccountFilter extends OncePerRequestFilter {
  private final UserRepository users;

  public CurrentAccountFilter(UserRepository users) {
    this.users = users;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
      boolean current =
          users
              .findById(principal.id())
              .filter(
                  user ->
                      user.isEnabled() && user.getSecurityVersion() == principal.securityVersion())
              .isPresent();
      if (!current) {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) request.getSession(false).invalidate();
      }
    }
    chain.doFilter(request, response);
  }
}
