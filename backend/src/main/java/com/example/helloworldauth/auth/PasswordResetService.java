package com.example.helloworldauth.auth;

import com.example.helloworldauth.user.PasswordResetToken;
import com.example.helloworldauth.user.PasswordResetTokenRepository;
import com.example.helloworldauth.user.User;
import com.example.helloworldauth.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
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
import java.util.Map;

/**
 * Password reset request (Story 6) and confirm (Story 7) flows.
 *
 * <p>Request side is enumeration-resistant: it always completes the same way
 * whether or not the email is registered. Only when it matches a real user is a
 * token minted, its SHA-256 hash persisted (never the plaintext), and the
 * stubbed {@link EmailService} invoked with the plaintext link.
 *
 * <p>Confirm side looks the incoming plaintext token up by its SHA-256 hash
 * (the same hashing used when it was minted), rejects any token that is unknown,
 * expired, or already used, updates the password hash, marks the token used
 * (single-use), and invalidates every existing session for that user.
 */
@Service
public class PasswordResetService {

    private static final Logger audit = LoggerFactory.getLogger("audit");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final Duration tokenTtl;

    public PasswordResetService(
        UserRepository users,
        PasswordResetTokenRepository tokens,
        EmailService emailService,
        PasswordEncoder passwordEncoder,
        FindByIndexNameSessionRepository<? extends Session> sessionRepository,
        @Value("${app.password-reset.token-ttl-minutes:30}") long tokenTtlMinutes) {
        this.users = users;
        this.tokens = tokens;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.sessionRepository = sessionRepository;
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

    /**
     * Confirms a reset: validates the token, sets the new password, marks the
     * token used, and invalidates the user's existing sessions.
     *
     * @throws InvalidResetTokenException when the token is unknown, expired, or
     *     already used. The password is left unchanged in every rejection case.
     */
    @Transactional
    public void confirmReset(String plaintextToken, String newPassword) {
        String tokenHash = sha256Hex(plaintextToken);
        PasswordResetToken token = tokens.findByTokenHash(tokenHash)
            .orElseThrow(InvalidResetTokenException::new);

        Instant now = Instant.now();
        if (token.isUsed() || token.isExpired(now)) {
            // Single generic rejection — never reveal which condition tripped.
            throw new InvalidResetTokenException();
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(user);

        // Single-use: stamp the token so any further use is rejected above.
        token.setUsedAt(now);
        tokens.save(token);

        // Invalidate every existing session for this user so a stolen/active
        // session cannot survive a password reset.
        invalidateSessions(user.getUsername());

        audit.info("password reset completed username={}", user.getUsername());
    }

    /**
     * Deletes all Spring Session entries indexed under the given principal name.
     * The principal-name index is populated when a logged-in session persists a
     * SPRING_SECURITY_CONTEXT (see {@code LoginController}); with the in-memory
     * {@code MapSessionRepository} configured for this demo the lookup is exact.
     */
    private void invalidateSessions(String username) {
        Map<String, ? extends Session> sessions =
            sessionRepository.findByPrincipalName(username);
        for (String sessionId : sessions.keySet()) {
            sessionRepository.deleteById(sessionId);
        }
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
