package com.assessment.hello.service;

import com.assessment.hello.config.AppProperties;
import com.assessment.hello.domain.PasswordResetToken;
import com.assessment.hello.domain.Role;
import com.assessment.hello.domain.User;
import com.assessment.hello.dto.RegisterRequest;
import com.assessment.hello.repository.PasswordResetTokenRepository;
import com.assessment.hello.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AppProperties appProperties;
    private final LoginAttemptService loginAttemptService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       PasswordResetTokenRepository tokenRepository,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService,
                       AppProperties appProperties,
                       LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.appProperties = appProperties;
        this.loginAttemptService = loginAttemptService;
    }

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "Username or email already in use");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "Username or email already in use");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        user.setEnabled(true);
        User saved = userRepository.save(user);
        log.info("Registration succeeded for username={}", saved.getUsername());
        return saved;
    }

    /**
     * Verifies credentials and enforces account lockout. Returns the authenticated
     * user on success. Throws a generic ApiException on any failure so callers cannot
     * distinguish "unknown username" from "wrong password".
     */
    public User authenticate(String username, String rawPassword) {
        Optional<User> maybeUser = userRepository.findByUsername(username);

        if (maybeUser.isEmpty()) {
            // Run a dummy hash to keep timing roughly constant and avoid user enumeration.
            passwordEncoder.matches(rawPassword, "$2a$10$7EqJtq98hPqEX7fNZaFWoOa7Z3l8Vp0v1kK8n9wJ0mR5tS2uW3xY");
            log.info("Login failed (unknown user) username={}", username);
            throw genericAuthFailure();
        }

        User user = maybeUser.get();

        if (user.isLocked()) {
            log.info("Login rejected (account locked) username={}", username);
            throw genericAuthFailure();
        }

        if (!user.isEnabled()) {
            log.info("Login rejected (account disabled) username={}", username);
            throw genericAuthFailure();
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            // Record in its own committed transaction so it survives the failure below.
            loginAttemptService.recordFailure(user.getId());
            log.info("Login failed (bad password) username={}", username);
            throw genericAuthFailure();
        }

        // Success: reset counters.
        loginAttemptService.recordSuccess(user.getId());
        log.info("Login succeeded username={}", username);
        // Re-read so the returned user reflects the reset counters.
        return userRepository.findById(user.getId()).orElse(user);
    }

    private ApiException genericAuthFailure() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    // ---- Password reset ----

    @Transactional
    public void requestPasswordReset(String email) {
        Optional<User> maybeUser = userRepository.findByEmail(email);
        // Always respond generically; only do work if the user actually exists.
        if (maybeUser.isEmpty()) {
            log.info("Password reset requested for unregistered email (no-op)");
            return;
        }
        User user = maybeUser.get();

        // Invalidate any prior tokens for this user.
        tokenRepository.deleteByUser(user);

        String rawToken = generateRawToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(hashToken(rawToken));
        int expiry = appProperties.getSecurity().getResetToken().getExpiryMinutes();
        token.setExpiresAt(Instant.now().plusSeconds(expiry * 60L));
        tokenRepository.save(token);

        log.info("Password reset requested username={}", user.getUsername());
        emailService.sendPasswordResetEmail(user.getEmail(), rawToken);
    }

    /**
     * Confirms a reset. Returns the user whose password was changed so the caller
     * can invalidate that user's sessions.
     */
    @Transactional
    public User confirmPasswordReset(String rawToken, String newPassword) {
        String hash = hashToken(rawToken);
        PasswordResetToken token = tokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired token"));

        if (token.isUsed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
        }
        if (token.isExpired()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // A password reset should also clear any lockout.
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        // Invalidate every existing session for this user: any session established
        // before "now" will be rejected by SessionValidityFilter.
        user.setSessionsValidFrom(Instant.now());
        userRepository.save(user);

        token.setUsedAt(Instant.now());
        tokenRepository.save(token);

        log.info("Password reset completed username={}", user.getUsername());
        return user;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
