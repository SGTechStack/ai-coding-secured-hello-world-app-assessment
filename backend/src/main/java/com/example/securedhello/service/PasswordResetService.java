package com.example.securedhello.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.securedhello.entity.PasswordResetToken;
import com.example.securedhello.entity.User;
import com.example.securedhello.repository.PasswordResetTokenRepository;
import com.example.securedhello.repository.UserRepository;

/**
 * Handles password-reset requests. Always behaves the same to the caller
 * (Enumeration Resistance): whether or not the Email is registered, the
 * controller returns a generic success. When the Email matches a User, a
 * single-use Reset Token is generated (32 random bytes, Base64URL), only its
 * SHA-256 hash is stored with a 30-minute expiry, any existing live token for
 * that user is invalidated, and the stub {@link EmailService} logs the link.
 */
@Service
public class PasswordResetService {

    /** Token lifetime per ADR-0003. */
    static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final AuditService auditService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                EmailService emailService,
                                AuditService auditService,
                                Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Processes a reset request for the given Email. Never reveals whether the
     * Email exists; the caller always reports generic success.
     */
    @Transactional
    public void requestReset(String email) {
        Optional<User> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isEmpty()) {
            // Enumeration Resistance: do nothing observable.
            auditService.record("PASSWORD_RESET_REQUESTED", email, null, "SUCCESS");
            return;
        }
        User user = maybeUser.get();

        // At most one live token per user: invalidate any existing unused tokens.
        List<PasswordResetToken> existing = tokenRepository.findByUserIdAndUsedFalse(user.getId());
        existing.forEach(PasswordResetToken::markUsed);
        tokenRepository.saveAll(existing);

        byte[] raw = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(raw);
        String plaintextToken = urlEncoder.encodeToString(raw);
        String tokenHash = TokenHasher.sha256Hex(plaintextToken);

        PasswordResetToken token = new PasswordResetToken(
                user.getId(), tokenHash, clock.instant().plus(TOKEN_TTL), clock.instant());
        tokenRepository.save(token);

        emailService.sendPasswordResetEmail(user.getEmail(),
                "/reset-password?token=" + plaintextToken);
        auditService.record("PASSWORD_RESET_REQUESTED", user.getUsername(), null, "SUCCESS");
    }
}
