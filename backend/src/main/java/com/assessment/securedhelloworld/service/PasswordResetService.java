package com.assessment.securedhelloworld.service;

import com.assessment.securedhelloworld.domain.PasswordResetToken;
import com.assessment.securedhelloworld.domain.User;
import com.assessment.securedhelloworld.exception.ApiException;
import com.assessment.securedhelloworld.repository.PasswordResetTokenRepository;
import com.assessment.securedhelloworld.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * Implements PRD Stories 6-7 / ticket 04. Two deliberate enumeration-resistance seams live here
 * rather than in the controller: {@link #requestReset} never throws and never reveals whether the
 * email matched a user (the controller returns the identical generic body either way), and only a
 * SHA-256 hash of the reset token is ever persisted or logged — the plaintext token exists only in
 * the (stub) emailed link (IM8 as-3, as-15, lm-19).
 */
@Service
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final int expiryMinutes;
    private final int passwordMinLength;

    public PasswordResetService(UserRepository userRepository,
                                 PasswordResetTokenRepository passwordResetTokenRepository,
                                 EmailService emailService,
                                 PasswordEncoder passwordEncoder,
                                 AuditLogService auditLogService,
                                 FindByIndexNameSessionRepository<? extends Session> sessionRepository,
                                 @Value("${app.reset-token.expiry-minutes}") int expiryMinutes,
                                 @Value("${app.security.password-min-length}") int passwordMinLength) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.sessionRepository = sessionRepository;
        this.expiryMinutes = expiryMinutes;
        this.passwordMinLength = passwordMinLength;
    }

    /**
     * Never throws and never returns any signal distinguishing "email matched a user" from "email
     * did not match" — the controller always sends back the same generic response regardless (PRD
     * Story 6, enumeration resistance).
     */
    @Transactional
    public void requestReset(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            // Deliberately no audit event distinguishing this from the success path by outcome
            // detail beyond "failure" with no target — logging "email not found" at the same
            // level with the same fields would be fine, but we simply do nothing further, which
            // also avoids any timing-observable branch beyond the DB lookup itself.
            auditLogService.event("password_reset_requested", "unknown_email", null, null);
            return;
        }

        User user = userOpt.get();

        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String plaintextToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String tokenHash = hashToken(plaintextToken);

        PasswordResetToken resetToken = new PasswordResetToken(user, tokenHash, Instant.now().plus(Duration.ofMinutes(expiryMinutes)));
        passwordResetTokenRepository.save(resetToken);

        String resetLink = "http://localhost:3000/reset-password?token=" + plaintextToken;
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);

        auditLogService.event("password_reset_requested", "success", user.getUsername(), user.getUsername());
    }

    @Transactional
    public void confirmReset(String plaintextToken, String newPassword) {
        String tokenHash = hashToken(plaintextToken);
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    auditLogService.event("password_reset_confirmed", "failure", null, null, Map.of("reason", "invalid_token"));
                    return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "This reset link is invalid");
                });

        User user = resetToken.getUser();

        if (resetToken.isUsed()) {
            auditLogService.event("password_reset_confirmed", "failure", user.getUsername(), user.getUsername(), Map.of("reason", "token_already_used"));
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOKEN_ALREADY_USED", "This reset link has already been used");
        }
        if (resetToken.isExpired()) {
            auditLogService.event("password_reset_confirmed", "failure", user.getUsername(), user.getUsername(), Map.of("reason", "token_expired"));
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED", "This reset link has expired");
        }
        if (newPassword.length() < passwordMinLength) {
            auditLogService.event("password_reset_confirmed", "failure", user.getUsername(), user.getUsername(), Map.of("reason", "weak_password"));
            throw new ApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD",
                    "Password must be at least " + passwordMinLength + " characters long");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setForcePasswordChange(false);
        userRepository.save(user);

        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);

        invalidateAllSessionsFor(user.getUsername());

        auditLogService.event("password_reset_confirmed", "success", user.getUsername(), user.getUsername());
    }

    private void invalidateAllSessionsFor(String username) {
        Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(username);
        sessions.keySet().forEach(sessionRepository::deleteById);
    }

    private static String hashToken(String plaintextToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plaintextToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
