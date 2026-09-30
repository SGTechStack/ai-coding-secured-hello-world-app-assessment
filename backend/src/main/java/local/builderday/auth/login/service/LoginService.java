package local.builderday.auth.login.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import local.builderday.common.audit.SecurityAudit;
import local.builderday.common.ratelimit.RateLimitBuckets;
import local.builderday.auth.login.config.LoginRateLimitProperties;
import local.builderday.account.core.model.UserProfile;
import local.builderday.account.core.service.AccountRules;
import local.builderday.account.core.service.UserProfileService;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

/**
 * A Login attempt: one submission of credentials and its outcome. Owns the whole ordering so callers only map the
 * outcome. The order is the same for every attempt, so no rejection can be told apart by timing (ADR 0003):
 * normalise the username, apply the Login rate limit, check the password, then the account's Login lockout and status.
 * On success it loads the profile, then replaces the anonymous session (fixation protection, one session per user),
 * and persists the security context. Replacing the session discards the anonymous CSRF token; the client fetches the
 * authenticated session's token from {@code GET /csrf}. Nothing is persisted to the session until every lookup that
 * can fail has succeeded. Every outcome is audited with its real reason; the client only ever sees the outcome.
 */
@Service
public class LoginService {
  /** The result of a Login attempt. Unexpected failures are audited and rethrown instead. */
  public sealed interface Outcome {
    record Authenticated(UserProfile profile) implements Outcome {}
    record InvalidCredentials() implements Outcome {}
    record RateLimited() implements Outcome {}
  }

  private final AuthenticationManager authenticationManager;
  private final SessionAuthenticationStrategy loginSessionStrategy;
  private final SecurityContextRepository securityContextRepository;
  private final UserProfileService userProfileService;
  /** Why credentials did not authenticate; the client sees the same Authentication failure for every one. */
  private enum Rejection { INVALID_CREDENTIALS, DISABLED, LOCKED }

  private static final String RATE_LIMIT_PREFIX = "login-attempts:";

  private final RateLimitBuckets rateLimitBuckets;
  private final LoginRateLimitProperties rateLimit;
  private final Clock clock;

  LoginService(AuthenticationManager authenticationManager, SessionAuthenticationStrategy loginSessionStrategy,
      SecurityContextRepository securityContextRepository, UserProfileService userProfileService,
      RateLimitBuckets rateLimitBuckets, LoginRateLimitProperties rateLimit, Clock clock) {
    this.authenticationManager = authenticationManager;
    this.loginSessionStrategy = loginSessionStrategy;
    this.securityContextRepository = securityContextRepository;
    this.userProfileService = userProfileService;
    this.rateLimitBuckets = rateLimitBuckets;
    this.rateLimit = rateLimit;
    this.clock = clock;
  }

  public Outcome attempt(String submittedUsername, String password, HttpServletRequest request,
      HttpServletResponse response) {
    // Stored usernames are trimmed and lowercased at registration, so a case mismatch must still log in.
    String username = AccountRules.normalize(submittedUsername);
    try {
      // First, before any credential work, so a rate-limited attempt costs nothing and reveals nothing (ADR 0003).
      // The source IP is the remote address after Tomcat's RemoteIpValve, as for the Registration lockout. Every
      // attempt consumes, whatever its outcome, and nothing resets the fixed window early.
      if (!rateLimitBuckets.tryFixedWindow(
          RATE_LIMIT_PREFIX + request.getRemoteAddr(), rateLimit.attempts(), rateLimit.window())) {
        audit(request, SecurityAudit.Outcome.FAILURE, "rate_limited", userProfileService.findIdForAudit(username));
        return new Outcome.RateLimited();
      }
      // The password is always checked (BCrypt, or the dummy hash for an unknown username) before the lock or status.
      Authentication authentication = null;
      Rejection rejection = null;
      try {
        authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(username, password));
      } catch (AccountStatusException inactive) {
        rejection = Rejection.DISABLED;
      } catch (AuthenticationException wrongCredentials) {
        rejection = Rejection.INVALID_CREDENTIALS;
      }
      Instant now = clock.instant();
      if (userProfileService.isLocked(username, now)) {
        // Silent: even the correct password is a plain Authentication failure, and the attempt is not counted.
        rejection = Rejection.LOCKED;
      } else if (rejection == Rejection.INVALID_CREDENTIALS && userProfileService.recordFailedLogin(username, now)) {
        SecurityAudit.record(request, new SecurityAudit.Event("account-lockout", "iam", "change",
            SecurityAudit.Outcome.FAILURE, "consecutive_failures", userProfileService.findIdForAudit(username)));
      }
      if (rejection != null) {
        audit(request, SecurityAudit.Outcome.FAILURE, rejection.name().toLowerCase(Locale.ROOT),
            userProfileService.findIdForAudit(username));
        return new Outcome.InvalidCredentials();
      }
      var profile = userProfileService.findByUsername(username).orElseThrow();
      userProfileService.recordSuccessfulLogin(username, now);

      loginSessionStrategy.onAuthentication(authentication, request, response);
      var context = new SecurityContextImpl(authentication);
      SecurityContextHolder.setContext(context);
      securityContextRepository.saveContext(context, request, response);

      audit(request, SecurityAudit.Outcome.SUCCESS, "success", profile.id());
      return new Outcome.Authenticated(profile);
    } catch (RuntimeException failure) {
      audit(request, SecurityAudit.Outcome.ERROR, "system_error", userProfileService.findIdForAudit(username));
      throw failure;
    }
  }

  private static void audit(HttpServletRequest request, SecurityAudit.Outcome outcome, String reason, UUID userId) {
    SecurityAudit.record(request,
        new SecurityAudit.Event("user-login", "authentication", "start", outcome, reason, userId));
  }
}
