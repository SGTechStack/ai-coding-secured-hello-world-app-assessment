package org.eds.demo.user.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eds.demo.common.exception.BadRequestException;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lets an Account holder choose their own password, replacing a Temporary Password. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordChangeService {

  /** Audit event name, stable so log queries can filter on it. */
  private static final String EVENT_PASSWORD_CHANGED = "password_changed";

  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final AccountSessions accountSessions;

  /** True while the Account must replace its Temporary Password before doing anything else. */
  @Transactional(readOnly = true)
  public boolean isChangeRequired(String username) {
    return appUserRepository
        .findByUsername(username)
        .map(account -> account.isMustChangePassword())
        .orElse(false);
  }

  /**
   * Stores the new password hash, clears the forced-change state and ends the holder's other
   * sessions. The length policy is enforced by request validation; a mismatch is refused here so it
   * does not depend on the form.
   *
   * @throws BadRequestException when the two entries differ; nothing is changed
   */
  @Transactional
  public void changePassword(
      String username, String newPassword, String confirmPassword, String currentSessionId) {
    if (newPassword == null || !newPassword.equals(confirmPassword)) {
      throw new BadRequestException("Password confirmation does not match");
    }
    var account = appUserRepository.findByUsername(username).orElseThrow();
    account.changePassword(passwordEncoder.encode(newPassword));
    appUserRepository.save(account);
    accountSessions.endAllSessionsExcept(username, currentSessionId);
    log.atInfo()
        .addKeyValue("event", EVENT_PASSWORD_CHANGED)
        .addKeyValue("username", username)
        .log("Password changed: username={}", username);
  }
}
