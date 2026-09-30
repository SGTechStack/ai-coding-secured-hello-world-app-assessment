package org.eds.demo.auth.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks a username and password against the stored Account hash. Every failure surfaces as the
 * same {@link BadCredentialsException} so callers cannot tell why sign-in was refused; the real
 * reason goes only to the audit log. Passwords are never logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignInService {

  private static final String EVENT_KEY = "event";
  private static final String USERNAME_KEY = "username";
  private static final String REASON_KEY = "reason";

  /** Audit event names, stable so log queries can filter on them. */
  private static final String EVENT_SUCCESS = "sign_in_success";

  private static final String EVENT_FAILURE = "sign_in_failure";

  private static final String REASON_BLANK_CREDENTIALS = "blank_credentials";
  private static final String REASON_ACCOUNT_DISABLED = "account_disabled";
  private static final String REASON_BAD_CREDENTIALS = "bad_credentials";

  private final AuthenticationManager authenticationManager;
  private final AppUserRepository appUserRepository;

  @Transactional
  public Authentication signIn(String username, String password) {
    if (username == null || username.isBlank() || password == null || password.isEmpty()) {
      throw refused(username, REASON_BLANK_CREDENTIALS);
    }
    Authentication authentication;
    try {
      authentication =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(username, password));
    } catch (DisabledException e) {
      throw refused(username, REASON_ACCOUNT_DISABLED);
    } catch (AuthenticationException e) {
      throw refused(username, REASON_BAD_CREDENTIALS);
    }
    appUserRepository
        .findByUsername(username)
        .ifPresent(account -> account.recordSuccessfulSignIn());
    log.atInfo()
        .addKeyValue(EVENT_KEY, EVENT_SUCCESS)
        .addKeyValue(USERNAME_KEY, username)
        .log("Sign-in succeeded: username={}", username);
    return authentication;
  }

  private BadCredentialsException refused(String username, String reason) {
    log.atWarn()
        .addKeyValue(EVENT_KEY, EVENT_FAILURE)
        .addKeyValue(USERNAME_KEY, username)
        .addKeyValue(REASON_KEY, reason)
        .log("Sign-in failed: username={}, reason={}", username, reason);
    return new BadCredentialsException("Invalid username or password");
  }
}
