package com.eitri.passwordreset;

import com.eitri.audit.AuditAccount;
import com.eitri.audit.AuditLogger;
import com.eitri.auth.AccountService;
import com.eitri.auth.AccountView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and redeems single-use password reset tokens. A token is 256 bits from {@link SecureRandom};
 * only its SHA-256 hash is stored, so a database leak reveals no usable token.
 */
@Service
@EnableConfigurationProperties(PasswordResetProperties.class)
class PasswordResetService {

    private static final int TOKEN_BYTES = 32;

    private final PasswordResetTokenRepository tokens;
    private final AccountService accounts;
    private final EmailService emailService;
    private final AuditLogger auditLogger;
    private final Clock clock;
    private final PasswordResetProperties properties;
    private final SecureRandom random = new SecureRandom();

    PasswordResetService(
            PasswordResetTokenRepository tokens,
            AccountService accounts,
            EmailService emailService,
            AuditLogger auditLogger,
            Clock clock,
            PasswordResetProperties properties) {
        this.tokens = tokens;
        this.accounts = accounts;
        this.emailService = emailService;
        this.auditLogger = auditLogger;
        this.clock = clock;
        this.properties = properties;
    }

    /**
     * For a registered email, replaces the account's unused tokens with a new one and emails its link.
     * For any other email nothing is stored, sent or audited.
     */
    @Transactional
    public void request(String email) {
        Optional<AccountView> account = accounts.findByEmail(email);
        if (account.isEmpty()) {
            return;
        }
        UUID userId = account.get().id();
        tokens.deleteUnusedByUserId(userId);
        String token = newToken();
        tokens.save(new PasswordResetToken(userId, hash(token), clock.instant().plus(properties.tokenLifetime())));
        emailService.sendPasswordResetEmail(
                account.get().email(), properties.linkBaseUrl() + "/reset-password?token=" + token);
        auditLogger.passwordResetRequested(new AuditAccount(userId, account.get().username()));
    }

    /**
     * Sets a new password with a valid token and consumes it, in one transaction.
     *
     * @return the account whose password changed, or empty for an unknown, expired or used token
     */
    @Transactional
    public Optional<AccountView> confirm(String token, String newPassword) {
        Instant now = clock.instant();
        Optional<PasswordResetToken> stored = tokens.findByTokenHash(hash(token));
        if (stored.isEmpty() || !stored.get().isUsableAt(now)) {
            return Optional.empty();
        }
        UUID userId = stored.get().getUserId();
        if (!accounts.changePassword(userId, newPassword)) {
            return Optional.empty();
        }
        stored.get().markUsed(now);
        return accounts.find(userId);
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }
}
