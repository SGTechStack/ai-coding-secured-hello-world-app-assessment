package com.example.helloauth.service;

import com.example.helloauth.config.AppProperties;
import com.example.helloauth.domain.PasswordResetToken;
import com.example.helloauth.domain.User;
import com.example.helloauth.repo.PasswordResetTokenRepository;
import com.example.helloauth.repo.UserRepository;
import com.example.helloauth.security.AuditLogger;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final EmailService emailService;
    private final SessionRegistryService sessionRegistry;
    private final AuditLogger audit;
    private final AppProperties props;

    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                                EmailService emailService, SessionRegistryService sessionRegistry,
                                AuditLogger audit, AppProperties props) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.emailService = emailService;
        this.sessionRegistry = sessionRegistry;
        this.audit = audit;
        this.props = props;
    }

    /**
     * Always behaves identically from the caller's perspective (generic success) regardless of
     * whether the email is registered (IM8 enumeration resistance). Only when a user matches is a
     * token generated, its hash stored, and the stub email sent.
     */
    @Transactional
    public void requestReset(String email, String resetBaseUrl) {
        audit.passwordResetRequested(email);
        Optional<User> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isEmpty()) {
            return;
        }
        User user = maybeUser.get();

        String plaintextToken = generateToken();
        String tokenHash = sha256(plaintextToken);
        Instant expiresAt = Instant.now().plus(props.getReset().getTokenTtlMinutes(), ChronoUnit.MINUTES);

        tokenRepository.save(new PasswordResetToken(user.getId(), tokenHash, expiresAt));

        String link = resetBaseUrl + "?token=" + plaintextToken;
        emailService.sendPasswordResetEmail(email, link);
    }

    /**
     * Confirms a reset. Rejects expired or already-used tokens. On success updates the password,
     * marks the token used (single-use), and invalidates all existing sessions for the user.
     */
    @Transactional
    public void confirmReset(String plaintextToken, String newPassword) {
        if (!passwordPolicy.isValid(newPassword)) {
            throw new ServiceExceptions.ValidationException(passwordPolicy.requirementMessage());
        }

        String tokenHash = sha256(plaintextToken);
        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ServiceExceptions.ValidationException("Invalid or expired reset token."));

        if (token.isUsed() || token.isExpired()) {
            throw new ServiceExceptions.ValidationException("Invalid or expired reset token.");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new ServiceExceptions.ValidationException("Invalid or expired reset token."));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);

        token.setUsedAt(Instant.now());
        tokenRepository.save(token);

        // Invalidate all existing sessions for this user (Story 7).
        sessionRegistry.invalidateSessionsForUser(user.getId());

        audit.passwordResetCompleted(user.getUsername());
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
