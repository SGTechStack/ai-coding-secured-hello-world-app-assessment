package org.eds.demo.auth.application;

import java.time.Clock;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eds.demo.common.logging.LogSanitizer;
import org.eds.demo.user.domain.AppUser;
import org.eds.demo.user.domain.BackoffPolicy;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Checks a username and password against the stored Account hash. Every failure surfaces as the
 * same {@link BadCredentialsException} so callers cannot tell why sign-in was refused; the real
 * reason goes only to the audit log. Passwords are never logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignInService {

  /** The one message behind every refusal; also the API response detail. */
  public static final String INVALID_CREDENTIALS_MESSAGE = "Invalid username or password";

  private static final String EVENT_KEY = "event";
  private static final String USERNAME_KEY = "username";
  private static final String REASON_KEY = "reason";

  /** Audit event names, stable so log queries can filter on them. */
  private static final String EVENT_SUCCESS = "sign_in_success";

  private static final String EVENT_FAILURE = "sign_in_failure";
  private static final String EVENT_DELAY = "sign_in_delay";
  private static final String EVENT_IP_THROTTLED = "sign_in_ip_throttled";
  private static final String REMOTE_ADDRESS_KEY = "remoteAddress";
  private static final String DELAY_KEY = "delay";

  private static final String REASON_BLANK_CREDENTIALS = "blank_credentials";
  private static final String REASON_ACCOUNT_DISABLED = "account_disabled";
  private static final String REASON_DELAYED = "delayed";
  private static final String REASON_TEMP_PASSWORD_EXPIRED = "temporary_password_expired";
  private static final String REASON_BAD_CREDENTIALS = "bad_credentials";

  private final AuthenticationManager authenticationManager;
  private final AppUserRepository appUserRepository;
  private final SignInThrottleProperties throttle;
  private final SignInIpThrottle ipThrottle;
  private final TimingEqualizer timingEqualizer;
  private final Clock clock;
  private final TransactionTemplate transactions;

  /**
   * Deliberately not {@code @Transactional}: a refusal is an exception, and one thrown through the
   * user-details lookup's transaction would mark an outer one rollback-only and discard the failure
   * counters. Each state change runs in its own short transaction instead.
   */
  public Authentication signIn(String username, String password, String remoteAddress) {
    ipThrottle
        .retryAfter(remoteAddress)
        .ifPresent(
            retryAfter -> {
              log.atWarn()
                  .addKeyValue(EVENT_KEY, EVENT_IP_THROTTLED)
                  .addKeyValue(REMOTE_ADDRESS_KEY, remoteAddress)
                  .log(
                      "Sign-in throttled: remoteAddress={}, retryAfter={}",
                      remoteAddress,
                      retryAfter);
              throw new SignInThrottledException(retryAfter);
            });
    try {
      return attempt(username, password);
    } catch (BadCredentialsException e) {
      ipThrottle.recordFailure(remoteAddress);
      throw e;
    }
  }

  private Authentication attempt(String username, String password) {
    if (username == null || username.isBlank() || password == null || password.isEmpty()) {
      throw refused(username, REASON_BLANK_CREDENTIALS);
    }
    var account = appUserRepository.findByUsername(username);
    if (account.filter(found -> found.isSignInDelayed(clock.instant())).isPresent()) {
      // The password is not even checked, so a correct one cannot end the delay early.
      timingEqualizer.spendPasswordCheck(password);
      throw refused(username, REASON_DELAYED);
    }
    if (account.filter(found -> found.isTemporaryPasswordExpired(clock.instant())).isPresent()) {
      timingEqualizer.spendPasswordCheck(password);
      throw refused(username, REASON_TEMP_PASSWORD_EXPIRED);
    }
    Authentication authentication;
    try {
      authentication =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(username, password));
    } catch (DisabledException e) {
      throw refused(username, REASON_ACCOUNT_DISABLED);
    } catch (AuthenticationException e) {
      account.ifPresent(found -> inTransaction(found, this::recordFailure));
      throw refused(username, REASON_BAD_CREDENTIALS);
    }
    account.ifPresent(found -> inTransaction(found, AppUser::clearSignInBackoff));
    log.atInfo()
        .addKeyValue(EVENT_KEY, EVENT_SUCCESS)
        .addKeyValue(USERNAME_KEY, LogSanitizer.sanitize(username))
        .log("Sign-in succeeded: username={}", LogSanitizer.sanitize(username));
    return authentication;
  }

  private void inTransaction(AppUser found, Consumer<AppUser> change) {
    transactions.executeWithoutResult(
        status -> appUserRepository.findByUsername(found.getUsername()).ifPresent(change));
  }

  private void recordFailure(AppUser account) {
    var delay =
        account.recordFailedSignIn(
            clock.instant(),
            new BackoffPolicy(
                throttle.backoffThreshold(), throttle.baseDelay(), throttle.maxDelay()));
    if (!delay.isZero()) {
      log.atWarn()
          .addKeyValue(EVENT_KEY, EVENT_DELAY)
          .addKeyValue(USERNAME_KEY, account.getUsername())
          .addKeyValue(DELAY_KEY, delay.toString())
          .log(
              "Sign-in delay applied: username={}, delay={}, failedAttempts={}",
              account.getUsername(),
              delay,
              account.getFailedLoginAttempts());
    }
  }

  private BadCredentialsException refused(String username, String reason) {
    var safeUsername = LogSanitizer.sanitize(username);
    log.atWarn()
        .addKeyValue(EVENT_KEY, EVENT_FAILURE)
        .addKeyValue(USERNAME_KEY, safeUsername)
        .addKeyValue(REASON_KEY, reason)
        .log("Sign-in failed: username={}, reason={}", safeUsername, reason);
    return new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
  }
}
