package com.example.demo_app.passwordreset;

import com.example.demo_app.audit.AuditEvent;
import com.example.demo_app.audit.AuditLog;
import com.example.demo_app.security.SessionExpiry;
import com.example.demo_app.user.UserAccount;
import com.example.demo_app.user.UserAccountRepository;
import com.example.demo_app.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Password reset by emailed link.
 *
 * <ul>
 *   <li>{@link #request}: for an <strong>enabled</strong> account with that email, marks its
 *       unused tokens used (only the newest link works), stores the SHA-256 of a new 256-bit
 *       {@link SecureRandom} token that expires after {@code token-ttl}, and emails {@code
 *       <frontend-url>/reset-password#token=<token>}. The token is in the fragment, which browsers
 *       never send to a server or in a {@code Referer}. Any other email does nothing, so the
 *       caller's answer can't reveal which emails have accounts.
 *   <li>{@link #confirm}: a known, unused, unexpired token sets the new BCrypt hash, is marked
 *       used, clears any lockout and signs the user out everywhere ({@link SessionExpiry}). Every
 *       other token gets the same {@code 400 INVALID_RESET_TOKEN} and changes nothing. The caller
 *       checks the password policy first, so a weak password never consumes the token.
 * </ul>
 *
 * <p>Both are audited. The submitted email is never logged: an unknown one is {@code
 * target=unknown}. The token is never logged here; the email stub is the only place it appears.
 */
@Service
@EnableConfigurationProperties(PasswordResetProperties.class)
public class PasswordReset {

  static final String INVALID_TOKEN_MESSAGE = "This reset link is invalid or has expired.";
  private static final String UNKNOWN = "unknown";
  private static final int TOKEN_BYTES = 32;

  private final UserAccountRepository accounts;
  private final PasswordResetTokenRepository tokens;
  private final EmailService emailService;
  private final PasswordEncoder passwordEncoder;
  private final SessionExpiry sessionExpiry;
  private final AuditLog auditLog;
  private final PasswordResetProperties properties;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  PasswordReset(
      UserAccountRepository accounts,
      PasswordResetTokenRepository tokens,
      EmailService emailService,
      PasswordEncoder passwordEncoder,
      SessionExpiry sessionExpiry,
      AuditLog auditLog,
      PasswordResetProperties properties,
      Clock clock) {
    this.accounts = accounts;
    this.tokens = tokens;
    this.emailService = emailService;
    this.passwordEncoder = passwordEncoder;
    this.sessionExpiry = sessionExpiry;
    this.auditLog = auditLog;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Sends a reset link if {@code email} (in any case, possibly malformed or {@code null}) belongs
   * to an enabled account; otherwise does nothing. Either way it returns normally.
   */
  @Transactional
  public void request(String email, HttpServletRequest request) {
    Optional<UserAccount> account =
        email == null ? Optional.empty() : accounts.findByEmail(UserAccount.normaliseEmail(email));
    auditLog.record(
        AuditEvent.PASSWORD_RESET_REQUESTED,
        null,
        request,
        Map.of("target", account.map(UserAccount::getUsername).orElse(UNKNOWN)));
    if (account.isEmpty() || !account.get().isEnabled()) {
      return;
    }

    UserAccount user = account.get();
    Instant now = clock.instant();
    tokens.invalidateUnusedTokens(user.getId(), now);
    String token = newToken();
    tokens.save(new PasswordResetToken(user, sha256(token), now, now.plus(properties.tokenTtl())));
    emailService.sendPasswordResetEmail(
        user.getEmail(), properties.frontendUrl() + "/reset-password#token=" + token);
  }

  /**
   * Sets {@code newPassword}, which must already meet the password policy, for the owner of
   * {@code token}, then signs them out everywhere.
   *
   * @throws ApiException {@code 400 INVALID_RESET_TOKEN} for an unknown, used, superseded or
   *     expired token
   */
  @Transactional
  public void confirm(String token, String newPassword, HttpServletRequest request) {
    Instant now = clock.instant();
    Optional<PasswordResetToken> found =
        token == null ? Optional.empty() : tokens.findByTokenHash(sha256(token));
    if (found.isEmpty()
        || !found.get().isUsableAt(now)
        || tokens.markUsed(found.get().getId(), now) == 0) {
      auditLog.record(
          AuditEvent.PASSWORD_RESET_REJECTED,
          null,
          request,
          Map.of("target", found.map(t -> t.getUser().getUsername()).orElse(UNKNOWN)));
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN", INVALID_TOKEN_MESSAGE, List.of());
    }

    UserAccount user = found.get().getUser();
    user.resetPassword(passwordEncoder.encode(newPassword));
    accounts.saveAndFlush(user);
    sessionExpiry.expireAllSessionsOf(user.getUsername());
    auditLog.record(AuditEvent.PASSWORD_RESET_COMPLETED, user.getUsername(), request);
  }

  /** 256 random bits, URL-safe Base64 without padding (43 characters). */
  private String newToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** The stored form of a token: lowercase SHA-256 hex (64 characters). */
  static String sha256(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required on every Java platform", e);
    }
  }
}
