package com.sgtechstack.helloworldauthapp.passwordreset;

import com.sgtechstack.helloworldauthapp.auth.PasswordPolicy;
import com.sgtechstack.helloworldauthapp.auth.UserPrincipal;
import com.sgtechstack.helloworldauthapp.auth.WeakPasswordException;
import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Password reset: request a token via email, then confirm a new password
 * with it. Never reveals whether an email is registered (the request
 * endpoint's response is identical either way), and never persists the
 * plaintext token — only its hash, so a database read alone can't be used
 * to forge a valid reset link.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    /** Per spec: short-lived, 15–30 minutes. */
    public static final Duration TOKEN_EXPIRY = Duration.ofMinutes(30);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final EmailService emailService;
    private final SessionRegistry sessionRegistry;
    private final String frontendBaseUrl;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            EmailService emailService,
            SessionRegistry sessionRegistry,
            @Value("${app.frontend.base-url}") String frontendBaseUrl
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.emailService = emailService;
        this.sessionRegistry = sessionRegistry;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /**
     * Always succeeds from the caller's point of view, regardless of
     * whether the email is registered — that's the enumeration-resistance
     * requirement. Only does real work (issuing a token, "sending" the
     * email) when the email does match an account.
     */
    @Transactional
    public void requestReset(String email) {
        Optional<User> user = userRepository.findByEmailIgnoreCase(email);

        if (user.isEmpty()) {
            log.info("Password reset requested for unregistered email");
            return;
        }

        String plaintextToken = generateToken();
        String tokenHash = hash(plaintextToken);
        Instant expiresAt = Instant.now().plus(TOKEN_EXPIRY);

        tokenRepository.save(new PasswordResetToken(user.get(), tokenHash, expiresAt));

        String resetLink = frontendBaseUrl + "/?token=" + plaintextToken;
        emailService.sendPasswordResetEmail(user.get().getEmail(), resetLink);

        log.info("Password reset token issued username={} expiresAt={}", user.get().getUsername(), expiresAt);
    }

    /**
     * Validates the token (exists, unexpired, unused), updates the
     * password, marks the token used, and invalidates every existing
     * session for that user.
     *
     * @throws InvalidResetTokenException if the token is unknown, expired, or already used
     * @throws WeakPasswordException      if the new password fails the strength policy
     */
    @Transactional
    public void confirmReset(String plaintextToken, String newPassword) {
        if (!passwordPolicy.isSatisfiedBy(newPassword)) {
            throw new WeakPasswordException(
                    "Password must be at least " + PasswordPolicy.MIN_LENGTH + " characters long");
        }

        String tokenHash = hash(plaintextToken);
        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidResetTokenException("Invalid or expired reset token"));

        if (token.isUsed()) {
            throw new InvalidResetTokenException("This reset token has already been used");
        }

        if (token.isExpired()) {
            throw new InvalidResetTokenException("This reset token has expired");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.markUsed();
        tokenRepository.save(token);

        invalidateAllSessionsFor(user);

        log.info("Password reset completed username={}", user.getUsername());
    }

    private void invalidateAllSessionsFor(User user) {
        List<Object> principals = sessionRegistry.getAllPrincipals();

        principals.stream()
                .filter(principal -> principal instanceof UserPrincipal userPrincipal
                        && userPrincipal.getId().equals(user.getId()))
                .flatMap(principal -> sessionRegistry.getAllSessions(principal, false).stream())
                .forEach(SessionInformation::expireNow);
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
