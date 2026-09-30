package com.example.hello.passwordreset;

import com.example.hello.auth.SessionInvalidationService;
import com.example.hello.common.AuditLogger;
import com.example.hello.common.InvalidResetTokenException;
import com.example.hello.config.PasswordResetProperties;
import com.example.hello.user.PasswordPolicy;
import com.example.hello.user.User;
import com.example.hello.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stories 6 and 7: request a reset link, then redeem it. */
@Service
public class PasswordResetService {

  private static final Logger LOG = LoggerFactory.getLogger(PasswordResetService.class);

  private final UserRepository userRepository;
  private final PasswordResetTokenRepository tokenRepository;
  private final EmailService emailService;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicy passwordPolicy;
  private final SessionInvalidationService sessionInvalidation;
  private final PasswordResetProperties props;
  private final AuditLogger audit;
  private final Clock clock;

  public PasswordResetService(
      UserRepository userRepository,
      PasswordResetTokenRepository tokenRepository,
      EmailService emailService,
      PasswordEncoder passwordEncoder,
      PasswordPolicy passwordPolicy,
      SessionInvalidationService sessionInvalidation,
      PasswordResetProperties props,
      AuditLogger audit,
      Clock clock) {
    this.userRepository = userRepository;
    this.tokenRepository = tokenRepository;
    this.emailService = emailService;
    this.passwordEncoder = passwordEncoder;
    this.passwordPolicy = passwordPolicy;
    this.sessionInvalidation = sessionInvalidation;
    this.props = props;
    this.audit = audit;
    this.clock = clock;
  }

  /**
   * Always completes silently. Whether the email exists is deliberately not observable from
   * the outside (same response, and the work done is small either way).
   */
  @Transactional
  public void requestReset(String email) {
    Optional<User> match = userRepository.findByEmailIgnoreCase(email.trim());
    if (match.isEmpty()) {
      LOG.debug("Password reset requested for an unregistered email; ignoring");
      return;
    }
    User user = match.get();
    tokenRepository.deleteByUser(user);

    String token = TokenHasher.generateToken();
    Instant expiresAt = clock.instant().plus(props.tokenTtl());
    tokenRepository.save(new PasswordResetToken(user, TokenHasher.hash(token), expiresAt));

    emailService.sendPasswordResetEmail(user.getEmail(), props.resetUrlBase() + "?token=" + token);
    audit.event("PASSWORD_RESET_REQUESTED", "username", user.getUsername());
  }

  @Transactional
  public void confirmReset(String token, String newPassword) {
    PasswordResetToken resetToken =
        tokenRepository.findByTokenHash(TokenHasher.hash(token)).orElseThrow(InvalidResetTokenException::new);
    Instant now = clock.instant();
    if (resetToken.isUsed() || resetToken.isExpired(now)) {
      throw new InvalidResetTokenException();
    }

    User user = resetToken.getUser();
    passwordPolicy.validate(newPassword, user.getUsername());

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    user.resetFailedLogins();
    resetToken.markUsed(now);
    int invalidated = sessionInvalidation.invalidateAllForUser(user.getUsername());

    audit.event(
        "PASSWORD_RESET_COMPLETED",
        "username", user.getUsername(),
        "sessions_invalidated", String.valueOf(invalidated));
  }
}
