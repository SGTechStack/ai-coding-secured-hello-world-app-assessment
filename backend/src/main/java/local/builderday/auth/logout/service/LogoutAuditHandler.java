package local.builderday.auth.logout.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import local.builderday.account.core.service.UserProfileService;
import local.builderday.auth.core.service.Authentications;
import local.builderday.common.audit.SecurityAudit;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

/**
 * Audits a Logout of an authenticated Session. It runs before the Session is invalidated, so {@code session.hash}
 * names the Session being ended. A refused logout (no Session) is not an event and records nothing.
 */
@Component
public class LogoutAuditHandler implements LogoutHandler {
  private final UserProfileService userProfileService;

  LogoutAuditHandler(UserProfileService userProfileService) { this.userProfileService = userProfileService; }

  @Override
  public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    if (!Authentications.isAuthenticatedUser(authentication)) return;
    SecurityAudit.record(request, new SecurityAudit.Event("user-logout", "authentication", "end",
        SecurityAudit.Outcome.SUCCESS, "success", userProfileService.findIdForAudit(authentication.getName())));
  }
}
