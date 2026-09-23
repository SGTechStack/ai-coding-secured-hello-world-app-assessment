package com.assessment.securedhelloworld.passwordreset;

import com.assessment.securedhelloworld.user.User;
import com.assessment.securedhelloworld.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SessionRegistry sessionRegistry;
    private final Duration tokenExpiry;
    private final String frontendOrigin;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            SessionRegistry sessionRegistry,
            @Value("${app.security.password-reset.token-expiry-minutes}") long tokenExpiryMinutes,
            @Value("${app.frontend.origin}") String frontendOrigin) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.sessionRegistry = sessionRegistry;
        this.tokenExpiry = Duration.ofMinutes(tokenExpiryMinutes);
        this.frontendOrigin = frontendOrigin;
    }

    /**
     * Always completes successfully from the caller's perspective,
     * whether or not the email is registered (enumeration resistance).
     */
    @Transactional
    public void requestReset(PasswordResetRequestRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            String plaintextToken = generatePlaintextToken();
            String tokenHash = hashToken(plaintextToken);

            PasswordResetToken token = new PasswordResetToken(
                    user.getId(), tokenHash, Instant.now().plus(tokenExpiry));
            tokenRepository.save(token);

            String resetLink = frontendOrigin + "/reset-password?token=" + plaintextToken;
            emailService.sendPasswordResetEmail(user.getEmail(), resetLink);

            log.info("Password reset requested username={}", user.getUsername());
        });
    }

    @Transactional
    public void confirmReset(PasswordResetConfirmRequest request) {
        PasswordResetToken token = tokenRepository.findAll().stream()
                .filter(candidate -> matchesToken(request.getToken(), candidate.getTokenHash()))
                .findFirst()
                .orElseThrow(() -> new InvalidResetTokenException("Invalid or expired reset token"));

        Instant now = Instant.now();
        if (token.isUsed()) {
            throw new InvalidResetTokenException("Reset token has already been used");
        }
        if (token.isExpired(now)) {
            throw new InvalidResetTokenException("Reset token has expired");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new InvalidResetTokenException("Invalid or expired reset token"));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        token.setUsedAt(now);
        tokenRepository.save(token);

        invalidateAllSessionsFor(user.getUsername());

        log.info("Password reset completed username={}", user.getUsername());
    }

    private void invalidateAllSessionsFor(String username) {
        sessionRegistry.getAllPrincipals().stream()
                .filter(principal -> principal instanceof com.assessment.securedhelloworld.auth.AppUserDetails
                        && ((com.assessment.securedhelloworld.auth.AppUserDetails) principal).getUsername().equals(username))
                .flatMap(principal -> sessionRegistry.getAllSessions(principal, false).stream())
                .forEach(sessionInformation -> sessionInformation.expireNow());
    }

    private static String generatePlaintextToken() {
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
