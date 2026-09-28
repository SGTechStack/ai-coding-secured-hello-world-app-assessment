package com.example.helloworldauth.auth;

import com.example.helloworldauth.user.PasswordResetToken;
import com.example.helloworldauth.user.PasswordResetTokenRepository;
import com.example.helloworldauth.user.User;
import com.example.helloworldauth.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Password reset request flow (Story 6).
 *
 * <p>Enumeration-resistant: the request always completes the same way whether
 * or not the email is registered. Only when it matches a real user is a token
 * minted, its SHA-256 hash persisted (never the plaintext), and the stubbed
 * {@link EmailService} invoked with the plaintext link. The plaintext token is
 * never stored and never written to the audit log.
 */
@Service
public class PasswordResetService {

    private static final Logger audit = LoggerFactory.getLogger("audit");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final EmailService emailService;
    private final Duration tokenTtl;

    public PasswordResetService(
        UserRepository users,
        PasswordResetTokenRepository tokens,
        EmailService emailService,
        @Value("${app.password-reset.token-ttl-minutes:30}") long tokenTtlMinutes) {
        this.users = users;
        this.tokens = tokens;
        this.emailService = emailService;
        this.tokenTtl = Duration.ofMinutes(tokenTtlMinutes);
    }

    /**
     * Processes a reset request. Returns nothing and reveals nothing: the caller
     * always sends the same generic response.
     */
    @Transactional
    public void requestReset(String email) {
        // Audit line carries no token and does not reveal whether the email existed.
        audit.info("password reset requested");

        users.findByEmail(email).ifPresent(user -> {
            String plaintextToken = generateToken();
            String tokenHash = sha256Hex(plaintextToken);
            Instant expiresAt = Instant.now().plus(tokenTtl);
            tokens.save(new PasswordResetToken(user, tokenHash, expiresAt));
            emailService.sendPasswordResetEmail(user, plaintextToken);
        });
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM; treat absence as fatal.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
