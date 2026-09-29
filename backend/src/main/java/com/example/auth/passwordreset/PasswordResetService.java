package com.example.auth.passwordreset;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.example.auth.audit.AuditService;
import com.example.auth.config.FrontendProperties;
import com.example.auth.config.PasswordResetProperties;
import com.example.auth.session.SessionInvalidator;
import com.example.auth.user.User;
import com.example.auth.user.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final ResetTokenService resetTokenService;
    private final ResetRequestRateLimiter rateLimiter;
    private final EmailService emailService;
    private final SessionInvalidator sessionInvalidator;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final Clock clock;
    private final int tokenTtlMinutes;
    private final String frontendBaseUrl;

    public PasswordResetService(UserRepository users,
                                PasswordResetTokenRepository tokens,
                                ResetTokenService resetTokenService,
                                ResetRequestRateLimiter rateLimiter,
                                EmailService emailService,
                                SessionInvalidator sessionInvalidator,
                                PasswordEncoder passwordEncoder,
                                AuditService audit,
                                Clock clock,
                                PasswordResetProperties resetProps,
                                FrontendProperties frontendProps) {
        this.users = users;
        this.tokens = tokens;
        this.resetTokenService = resetTokenService;
        this.rateLimiter = rateLimiter;
        this.emailService = emailService;
        this.sessionInvalidator = sessionInvalidator;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.clock = clock;
        this.tokenTtlMinutes = resetProps.tokenTtlMinutes();
        this.frontendBaseUrl = frontendProps.baseUrl();
    }

    /**
     * Requests a password reset. Always completes without revealing whether the
     * email is registered (Story 18). If the IP exceeds the rate limit, throws
     * ResetRequestThrottledException (Story 47) — an IP-keyed signal that leaks
     * no account existence.
     */
    @Transactional
    public void requestReset(String email, String ip) {
        if (rateLimiter.isThrottled(ip)) {
            throw new ResetRequestThrottledException();
        }
        rateLimiter.recordRequest(ip);

        users.findByEmail(email).ifPresent(user -> {
            // Invalidate any prior outstanding tokens so only the latest is valid.
            tokens.deleteByUser(user);

            String plaintext = resetTokenService.generateToken();
            Instant expiresAt = clock.instant().plus(tokenTtlMinutes, ChronoUnit.MINUTES);
            tokens.save(new PasswordResetToken(user, resetTokenService.hash(plaintext), expiresAt));

            String resetLink = frontendBaseUrl + "/reset-password?token=" + plaintext;
            emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
            audit.passwordResetRequested(user.getUsername());
        });
    }

    /**
     * Confirms a reset: validates the token, sets the new password, marks the
     * token used, and invalidates all of the user's existing sessions (Story 7).
     * Does NOT authenticate the user — they must log in fresh (Story 24).
     *
     * @throws InvalidResetTokenException if the token is unknown, expired, or used
     */
    @Transactional
    public void confirmReset(String presentedToken, String newPassword) {
        String hash = resetTokenService.hash(presentedToken);
        PasswordResetToken token = tokens.findByTokenHash(hash)
                .orElseThrow(InvalidResetTokenException::new);

        Instant now = clock.instant();
        if (token.isUsed() || token.isExpired(now)) {
            throw new InvalidResetTokenException();
        }

        User user = token.getUser();
        user.changePassword(passwordEncoder.encode(newPassword));
        token.markUsed(now);

        sessionInvalidator.invalidateAllSessions(user.getUsername());
        audit.passwordResetCompleted(user.getUsername());
    }
}
