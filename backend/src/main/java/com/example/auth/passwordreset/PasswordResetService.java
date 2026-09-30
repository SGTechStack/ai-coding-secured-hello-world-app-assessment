package com.example.auth.passwordreset;

import com.example.auth.audit.AuditLogger;
import com.example.auth.auth.ApiException;
import com.example.auth.auth.ErrorCode;
import com.example.auth.security.SessionTerminationService;
import com.example.auth.security.ratelimit.RateLimiters;
import com.example.auth.user.PasswordPolicy;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Story 6 (request a reset) + Story 7 (confirm a reset, invalidating every existing session).
 *
 * <p>{@code requestReset} always returns normally, whether or not the email matches an account,
 * and sends the email asynchronously after commit ({@link PasswordResetEmailSender}), so neither
 * the response nor its timing distinguishes the two cases (anti-enumeration). Issuing a new token
 * invalidates the user's earlier ones; a successful confirm invalidates all remaining tokens,
 * clears any lockout (the user just proved control of the mailbox) and ends every session.
 */
@Service
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;
    private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired reset token";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final SessionTerminationService sessionTerminationService;
    private final RateLimiters rateLimiters;
    private final AuditLogger auditLogger;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String frontendBaseUrl;
    private final Duration tokenTtl;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher,
            SessionTerminationService sessionTerminationService,
            RateLimiters rateLimiters,
            AuditLogger auditLogger,
            @Value("${app.frontend.base-url}") String frontendBaseUrl,
            @Value("${app.security.password-reset.token-ttl}") Duration tokenTtl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.sessionTerminationService = sessionTerminationService;
        this.rateLimiters = rateLimiters;
        this.auditLogger = auditLogger;
        this.frontendBaseUrl = frontendBaseUrl;
        this.tokenTtl = tokenTtl;
    }

    /**
     * Past the per-email limit the request is silently dropped (still 200): a 429 here would tell
     * the caller the address is being targeted, and the limit exists to stop mailbox flooding.
     */
    @Transactional
    public void requestReset(String email) {
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        if (rateLimiters.resetRequestEmail().tryAcquire(normalizedEmail).blocked()) {
            auditLogger.rateLimited(rateLimiters.resetRequestEmail().name());
            return;
        }
        userRepository.findByEmail(normalizedEmail).ifPresent(this::issueResetToken);
    }

    private void issueResetToken(User user) {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        Instant now = Instant.now();

        tokenRepository.invalidateUnusedTokens(user.getId(), now);
        tokenRepository.save(new PasswordResetToken(user, hash(rawToken), now.plus(tokenTtl)));
        auditLogger.passwordResetRequested(user.getPublicId());

        String resetLink = frontendBaseUrl + "/reset-password/confirm?token=" + rawToken;
        eventPublisher.publishEvent(new PasswordResetEmailRequested(user.getEmail(), resetLink));
    }

    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        if (!PasswordPolicy.isAcceptable(newPassword)) {
            throw ApiException.validation(PasswordPolicy.MESSAGE);
        }

        Instant now = Instant.now();
        PasswordResetToken token = tokenRepository
                .findByTokenHash(hash(rawToken))
                .filter(t -> t.isUsable(now))
                .orElseThrow(this::invalidToken);
        Long userId = token.getUser().getId();

        // Atomic claim, not read-then-write: two concurrent requests racing
        // the same raw token could otherwise both pass the isUsable() check
        // above before either commits. Only the request whose UPDATE
        // actually flips the row proceeds past this point.
        if (tokenRepository.markUsed(token.getId(), now) == 0) {
            throw invalidToken();
        }
        tokenRepository.invalidateUnusedTokens(userId, now);

        User user = userRepository.findById(userId).orElseThrow(this::invalidToken);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        auditLogger.passwordResetCompleted(user.getPublicId());

        // Runs after commit (see SessionTerminationService).
        sessionTerminationService.terminateAllSessions(user.getUsername(), user.getPublicId(), "password_reset");
    }

    private ApiException invalidToken() {
        auditLogger.passwordResetRejected("invalid_token");
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_TOKEN, INVALID_TOKEN_MESSAGE);
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
