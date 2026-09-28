package com.assessment.securedhelloworld.passwordreset;

import com.assessment.securedhelloworld.user.User;
import com.assessment.securedhelloworld.user.UserRepository;
import com.assessment.securedhelloworld.logging.LogSanitizer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Single-use, hashed, time-boxed password reset tokens. Only the token
 * hash is ever persisted; the plaintext token is handed to
 * {@link EmailService} for delivery and never logged or stored
 * elsewhere. Resetting a password invalidates every existing session for
 * that account.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SessionRegistry sessionRegistry;
    private final Clock clock;
    private final Duration tokenExpiry;
    private final String frontendOrigin;
    private final Counter resetRequestedCounter;
    private final Counter resetCompletedCounter;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            SessionRegistry sessionRegistry,
            Clock clock,
            MeterRegistry meterRegistry,
            @Value("${app.security.password-reset.token-expiry-minutes}") long tokenExpiryMinutes,
            @Value("${app.frontend.origin}") String frontendOrigin) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.sessionRegistry = sessionRegistry;
        this.clock = clock;
        this.tokenExpiry = Duration.ofMinutes(tokenExpiryMinutes);
        this.frontendOrigin = frontendOrigin;
        this.resetRequestedCounter = Counter.builder("app.password_reset.requested")
                .description("Password reset requests for a registered email")
                .register(meterRegistry);
        this.resetCompletedCounter = Counter.builder("app.password_reset.completed")
                .description("Password resets completed via a valid token")
                .register(meterRegistry);
    }

    /**
     * Always completes successfully from the caller's perspective,
     * whether or not the email is registered (enumeration resistance).
     */
    @Transactional
    public void requestReset(PasswordResetRequestRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            String selector = generateUrlSafeRandomToken();
            String verifier = generateUrlSafeRandomToken();
            String verifierHash = hashToken(verifier);

            PasswordResetToken token = new PasswordResetToken(
                    user.getId(), selector, verifierHash, clock.instant().plus(tokenExpiry));
            tokenRepository.save(token);

            String plaintextToken = selector + "." + verifier;
            String resetLink = frontendOrigin + "/reset-password?token=" + plaintextToken;
            emailService.sendPasswordResetEmail(user.getEmail(), resetLink);

            resetRequestedCounter.increment();
            log.info("Password reset requested username={}", LogSanitizer.sanitize(user.getUsername()));
        });
    }

    @Transactional
    public void confirmReset(PasswordResetConfirmRequest request) {
        String[] parts = request.getToken().split("\\.", 2);
        if (parts.length != 2) {
            throw new InvalidResetTokenException("Invalid or expired reset token");
        }
        String selector = parts[0];
        String verifier = parts[1];

        // A single indexed lookup by selector, then exactly one BCrypt
        // comparison against that row's hash — not a linear scan
        // comparing the presented token against every issued token's
        // hash (see PasswordResetToken's javadoc for why that matters).
        PasswordResetToken token = tokenRepository.findBySelector(selector)
                .filter(candidate -> matchesToken(verifier, candidate.getTokenHash()))
                .orElseThrow(() -> new InvalidResetTokenException("Invalid or expired reset token"));

        Instant now = clock.instant();
        if (token.isUsed()) {
            throw new InvalidResetTokenException("Reset token has already been used");
        }
        if (token.isExpired(now)) {
            throw new InvalidResetTokenException("Reset token has expired");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new InvalidResetTokenException("Invalid or expired reset token"));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.clearLockout();
        userRepository.save(user);

        token.setUsedAt(now);
        tokenRepository.save(token);

        invalidateAllSessionsFor(user.getUsername());

        resetCompletedCounter.increment();
        log.info("Password reset completed username={}", LogSanitizer.sanitize(user.getUsername()));
    }

    private void invalidateAllSessionsFor(String username) {
        sessionRegistry.getAllPrincipals().stream()
                .filter(principal -> principal instanceof com.assessment.securedhelloworld.auth.AppUserDetails
                        && ((com.assessment.securedhelloworld.auth.AppUserDetails) principal).getUsername().equals(username))
                .flatMap(principal -> sessionRegistry.getAllSessions(principal, false).stream())
                .forEach(sessionInformation -> sessionInformation.expireNow());
    }

    private static String generateUrlSafeRandomToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private static String hashToken(String plaintextToken) {
        return BCrypt.hashpw(plaintextToken, BCrypt.gensalt());
    }

    private static boolean matchesToken(String plaintextToken, String tokenHash) {
        try {
            return BCrypt.checkpw(plaintextToken, tokenHash);
        } catch (IllegalArgumentException malformedHash) {
            return false;
        }
    }
}
