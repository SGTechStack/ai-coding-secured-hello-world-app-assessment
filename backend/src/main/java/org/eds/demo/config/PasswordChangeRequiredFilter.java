package org.eds.demo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.eds.demo.common.WebSpaController;
import org.eds.demo.user.api.ChangePasswordController;
import org.eds.demo.user.application.PasswordChangeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses every request from an Account that still holds a Temporary Password, except
 * change-password and sign-out. Runs ahead of authorization on the API, admin API and application
 * chains, so an admin is held to the same rule. Read-only GETs for the SPA shell and its built
 * assets stay open, or the change-password page itself could not load; they carry no data.
 */
@RequiredArgsConstructor
final class PasswordChangeRequiredFilter extends OncePerRequestFilter {

  static final String TITLE = "Password change required";
  private static final String DETAIL = "You must choose a new password before continuing";

  private static final List<String> SPA_SHELL_PREFIXES =
      List.of(WebSpaController.SPA_ROOT, "/assets", WebSpaController.SIGN_IN_PATH, "/error");
  private static final List<String> SPA_SHELL_EXACT = List.of("/", "/index.html");

  private final PasswordChangeService passwordChangeService;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || authentication instanceof AnonymousAuthenticationToken
        || !authentication.isAuthenticated()
        || isAllowedWhileChangeRequired(request)
        || !passwordChangeService.isChangeRequired(authentication.getName())) {
      chain.doFilter(request, response);
      return;
    }
    SecurityProblemDetailHandlers.writeProblemDetail(response, HttpStatus.FORBIDDEN, TITLE, DETAIL);
  }

  private static boolean isAllowedWhileChangeRequired(HttpServletRequest request) {
    var path = request.getRequestURI().substring(request.getContextPath().length());
    if (path.equals(ChangePasswordController.CHANGE_PASSWORD_PATH)
        || path.equals(SecurityConfiguration.LOGOUT_URL)) {
      return true;
    }
    var readOnly = "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
    return readOnly
        && (SPA_SHELL_EXACT.contains(path)
            || SPA_SHELL_PREFIXES.stream()
                .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/")));
  }
}
