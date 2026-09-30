package com.example.authapp.service;

import com.example.authapp.config.AppProperties;
import com.example.authapp.domain.PasswordResetToken;
import com.example.authapp.domain.PasswordResetTokenRepository;
import com.example.authapp.domain.User;
import com.example.authapp.domain.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final EmailService email;
    private final SessionService sessions;
    private final AuditLogger audit;
    private final Duration ttl;
    private final String frontendOrigin;

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens, PasswordEncoder encoder,
            PasswordPolicy policy, EmailService email, SessionService sessions, AuditLogger audit,
            AppProperties props) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.policy = policy;
        this.email = email;
        this.sessions = sessions;
        this.audit = audit;
        this.ttl = Duration.ofMinutes(props.passwordReset().tokenTtlMinutes());
        this.frontendOrigin = props.allowedOrigins().get(0);
    }

    /** Never signals whether the email exists; callers must give the same response either way. */
    @Transactional
    public void requestReset(String rawEmail) {
        Optional<User> found = users.findByEmail(rawEmail.trim().toLowerCase(Locale.ROOT));
        if (found.isEmpty()) {
            audit.log("password_reset_requested", "matched", false);
            return;
        }
        User user = found.get();
        tokens.deleteUnusedByUser(user); // only the newest link is valid

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tokens.save(new PasswordResetToken(user, hash(token), Instant.now().plus(ttl)));

        String link = UriComponentsBuilder.fromUriString(frontendOrigin)
                .path("/reset-password").queryParam("token", token).build().toUriString();
        email.sendPasswordResetEmail(user.getEmail(), link);
        audit.log("password_reset_requested", "matched", true, "user", user.getUsername());
    }

    @Transactional
    public void confirmReset(String token, String newPassword) {
        policy.validate(newPassword);
        PasswordResetToken t = tokens.findByTokenHash(hash(token))
                .filter(x -> x.getUsedAt() == null && x.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token"));
        if (tokens.markUsed(t.getId(), Instant.now()) != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token");
        }
        User user = users.findById(t.getUser().getId()).orElseThrow();
        user.setPasswordHash(encoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        users.save(user);
        sessions.invalidateAll(user.getUsername());
        audit.log("password_reset_completed", "user", user.getUsername());
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
