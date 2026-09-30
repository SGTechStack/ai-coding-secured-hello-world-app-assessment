package com.assessment.auth.password;

import com.assessment.auth.audit.AuditAction;
import com.assessment.auth.audit.AuditEvent;
import com.assessment.auth.audit.AuditLogger;
import com.assessment.auth.audit.AuditReason;
import com.assessment.auth.common.ApiErrorCode;
import com.assessment.auth.common.ApiException;
import com.assessment.auth.security.SessionRevocationService;
import com.assessment.auth.user.User;
import com.assessment.auth.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.event.Level;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Password reset, self-service and administrator-initiated on <strong>one token model</strong>
 * (spec.md S7, ticket 11).
 *
 * <p><strong>The administrator issues a token, not a password.</strong> That overturns
 * Priv:414-463 on the strength of Std:401, an enforced constraint: the user chooses their own
 * password at confirm. It also means the admin-generated-password composition rules are
 * inapplicable, which is why the policy has none.
 *
 * <p><strong>A reset does not clear an account lock.</strong> Std:131 beats Priv:441-442; the
 * dedicated unlock endpoint is the only remedy.
 */
@Service
public class PasswordResetService {

  private static final String ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

  private final PasswordResetTokenRepository tokenRepository;
  private final UserRepository userRepository;
  private final PasswordPolicy passwordPolicy;
  private final PasswordHistoryService passwordHistoryService;
  private final PasswordEncoder passwordEncoder;
  private final SessionRevocationService sessionRevocationService;
  private final EmailService emailService;
  private final AuditLogger auditLogger;
  private final ResetProperties properties;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public PasswordResetService(
      PasswordResetTokenRepository tokenRepository,
      UserRepository userRepository,
      PasswordPolicy passwordPolicy,
      PasswordHistoryService passwordHistoryService,
      PasswordEncoder passwordEncoder,
      SessionRevocationService sessionRevocationService,
      EmailService emailService,
      AuditLogger auditLogger,
      ResetProperties properties,
      Clock clock) {
    this.tokenRepository = tokenRepository;
    this.userRepository = userRepository;
    this.passwordPolicy = passwordPolicy;
    this.passwordHistoryService = passwordHistoryService;
    this.passwordEncoder = passwordEncoder;
    this.sessionRevocationService = sessionRevocationService;
    this.emailService = emailService;
    this.auditLogger = auditLogger;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Self-service request by email.
   *
   * <p>Enumeration resistance covers <strong>body, timing and the audit log</strong>. The caller
   * always gets the same generic success; the controller applies the response-time floor; and the
   * audit event below is emitted identically whether or not the email resolved, carrying no field
   * that could distinguish the two.
   */
  @Transactional
  public void requestReset(String email) {
    Optional<User> user = userRepository.findByEmail(email);
    user.ifPresent(found -> emailService.sendPasswordResetLink(email, issueTokenFor(found), found.getId()));

    // Emitted OUTSIDE the ifPresent, with no subject and no outcome difference. One identical event
    // for known and unknown email (spec.md S7).
    auditLogger.emit(
        AuditEvent.of(
                AuditAction.CREDENTIAL_MANAGEMENT, AuditReason.PASSWORD_RESET_REQUESTED, Level.INFO)
            .build());
  }

  /**
   * Issues a token, superseding any prior unused one.
   *
   * @return the plaintext token. It is returned rather than stored, and the caller must not log it.
   */
  @Transactional
  public String issueTokenFor(User user) {
    // Std:112: issuing deletes any prior unused row, so `used_at` means exactly "redeemed" and only
    // the most recently issued token is ever valid.
    tokenRepository.deleteByUserIdAndUsedAtIsNull(user.getId());
    String token = generateToken();
    tokenRepository.save(
        new PasswordResetToken(
            UUID.randomUUID(),
            user.getId(),
            sha256(token),
            clock.instant().plus(properties.tokenTtl())));
    return token;
  }

  /** Redeems a token and writes the new credential. */
  @Transactional
  public void confirmReset(String token, String newPassword) {
    Instant now = clock.instant();
    PasswordResetToken stored =
        tokenRepository
            .findByTokenHash(sha256(token))
            // A merged error. Distinguishing "no such token" from "expired" from "already used"
            // tells an attacker they guessed a real one (Std:261).
            .filter(candidate -> candidate.isRedeemableAt(now))
            .orElseThrow(
                () ->
                    new ApiException(
                        ApiErrorCode.RESET_TOKEN_INVALID,
                        "That reset link is no longer valid. Request a new one."));

    User user =
        userRepository
            .findById(stored.getUserId())
            .orElseThrow(
                () ->
                    new ApiException(
                        ApiErrorCode.RESET_TOKEN_INVALID,
                        "That reset link is no longer valid. Request a new one."));

    applyNewPassword(user, newPassword, now);
    stored.markUsed(now);
    tokenRepository.save(stored);

    auditLogger.emit(
        AuditEvent.of(
                AuditAction.CREDENTIAL_MANAGEMENT, AuditReason.PASSWORD_RESET_COMPLETED, Level.INFO)
            .actor(user.getId())
            .target(user.getId())
            .build());
  }

  /**
   * The shared tail of every credential write: policy, history, hash, persist, revoke, clear flag,
   * notify. Used by reset-confirm and by the self-service change.
   *
   * <p>The lock is deliberately <em>not</em> touched here (Std:131).
   */
  public void applyNewPassword(User user, String newPassword, Instant now) {
    passwordPolicy.validate(newPassword, user.getUsername(), user.getEmail());
    passwordHistoryService.assertNotReused(user.getId(), newPassword);

    String hash = passwordEncoder.encode(newPassword);
    user.setPasswordHash(hash);
    // Cleared by reset-confirm and self-service change; NOT by an administrator-initiated reset,
    // because the user picks their own password at confirm (spec.md S5).
    user.setRequirePasswordChange(false);
    userRepository.save(user);
    passwordHistoryService.record(user.getId(), hash);

    // Every session, including the caller's own. This is why the first boot is three steps.
    sessionRevocationService.revokeAllFor(user.getUsername());
    emailService.sendPasswordChangedEmail(user.getId());
  }

  /**
   * Discards any pending unused reset token for the account (story 1.11).
   *
   * <p>Called by the <em>self-service change</em> path only, and deliberately <strong>not</strong>
   * from {@link #applyNewPassword}: on the reset-confirm path the token being redeemed is itself
   * still unused at that moment, so a blanket delete there would remove the row that is about to
   * be marked {@code used_at} — and re-saving it would resurrect a redeemed token.
   *
   * <p>There is at most one such row to begin with, because issuing always supersedes (Std:112).
   */
  @Transactional
  public void invalidatePendingTokens(UUID userId) {
    tokenRepository.deleteByUserIdAndUsedAtIsNull(userId);
  }

  private String generateToken() {
    StringBuilder builder = new StringBuilder(properties.tokenLength());
    for (int i = 0; i < properties.tokenLength(); i++) {
      builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    }
    return builder.toString();
  }

  /**
   * Unsalted SHA-256. Unsalted deliberately: the token carries ~190 bits of entropy already
   * (Std:66), and an unsalted digest is what lets the hash <em>be</em> the unique lookup key.
   */
  public static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required and must be available", ex);
    }
  }
}
