package com.example.auth.passwordreset;

import com.example.auth.audit.AuditLogger;
import com.example.auth.auth.ApiException;
import com.example.auth.user.User;
import com.example.auth.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Story 6 (request a reset) + Story 7 (confirm a reset, invalidating every
 * existing session). {@code requestReset} always returns normally, whether
 * or not the email matches a real account -- callers must not be able to
 * distinguish the two cases (anti-enumeration, same rationale as {@code
 * DaoAuthenticationProvider#hideUserNotFoundExceptions} in {@code
 * SecurityConfig}).
 */
@Service
public class PasswordResetService {

    private static final int MINIMUM_PASSWORD_LENGTH = 12;
    private static final int TOKEN_BYTES = 32;
    private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired reset token";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditLogger auditLogger;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String frontendBaseUrl;
    private final Duration tokenTtl;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            AuditLogger auditLogger,
            @Value("${app.frontend.base-url}") String frontendBaseUrl,
            @Value("${app.security.password-reset.token-ttl}") Duration tokenTtl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.auditLogger = auditLogger;
        this.frontendBaseUrl = frontendBaseUrl;
        this.tokenTtl = tokenTtl;
    }

    /**
     * Deliberately not {@code @Transactional}: sending the email is external
     * I/O, and a DB transaction must never be held open across that call (a
     * slow/stuck mail transport would otherwise pin a connection from the
     * pool for the duration of the send). {@code tokenRepository.save(...)}
     * still gets its own transaction -- every {@code JpaRepository} method is
     * transactional on its own via {@code SimpleJpaRepository} -- it's just
     * scoped to that one call rather than wrapping the email send too.
     */
    public void requestReset(String email) {
        if (email == null) {
            return;
        }
        userRepository.findByEmail(email.toLowerCase(Locale.ROOT)).ifPresent(this::issueResetToken);
    }

    private void issueResetToken(User user) {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        tokenRepository.save(new PasswordResetToken(user.getId(), hash(rawToken), Instant.now().plus(tokenTtl)));

        String resetLink = frontendBaseUrl + "/reset-password/confirm?token=" + rawToken;
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
        auditLogger.passwordResetRequested(user.getUsername());
    }

    /**
     * Returns the username whose password was just changed, so the caller
     * can sweep sessions via {@code SessionTerminationService} only after
     * this method (and its transaction) has actually returned -- see that
     * class's javadoc for why the sweep must not happen from inside this
     * transaction.
     */
    @Transactional
    public String confirmReset(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_TOKEN_MESSAGE);
        }
        if (newPassword == null || newPassword.length() < MINIMUM_PASSWORD_LENGTH) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Password must be at least " + MINIMUM_PASSWORD_LENGTH + " characters long");
        }

        PasswordResetToken token = tokenRepository
                .findByTokenHash(hash(rawToken))
                .filter(t -> t.isUsable(Instant.now()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, INVALID_TOKEN_MESSAGE));

        // Atomic claim, not read-then-write: two concurrent requests racing
        // the same raw token could otherwise both pass the isUsable() check
        // above before either commits. Only the request whose UPDATE
        // actually flips the row proceeds past this point.
        if (tokenRepository.markUsed(token.getId(), Instant.now()) == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_TOKEN_MESSAGE);
        }

        User user = userRepository
                .findById(token.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, INVALID_TOKEN_MESSAGE));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        auditLogger.passwordResetCompleted(user.getUsername());

        return user.getUsername();
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
