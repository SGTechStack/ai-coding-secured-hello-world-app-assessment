package sg.example.helloauth.passwordreset;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import sg.example.helloauth.account.Account;
import sg.example.helloauth.account.AccountService;
import sg.example.helloauth.api.ApiException;
import sg.example.helloauth.api.ErrorCode;
import sg.example.helloauth.audit.AuditLogger;
import sg.example.helloauth.email.EmailService;
import sg.example.helloauth.email.EmailService.Recipient;
import sg.example.helloauth.session.SessionControl;

/**
 * The Password reset module (ADR-0006): self-service only. A request is answered at once, the
 * same way whether or not the email is registered, and the token is issued in the background. A
 * token is 32 random bytes, stored only as its SHA-256 hash, works once, and expires. A reset
 * never clears a lock or the Failed-login counter.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private static final int TOKEN_BYTES = 32;

    private final PasswordResetTokenRepository tokens;
    private final AccountService accounts;
    private final EmailService emails;
    private final SessionControl sessionControl;
    private final AuditLogger audit;
    private final Executor background;
    private final TransactionTemplate transaction;
    private final PasswordResetProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    PasswordResetService(PasswordResetTokenRepository tokens, AccountService accounts, EmailService emails,
            SessionControl sessionControl, AuditLogger audit,
            @Qualifier(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME) Executor background,
            PlatformTransactionManager transactionManager, PasswordResetProperties properties, Clock clock) {
        this.tokens = tokens;
        this.accounts = accounts;
        this.emails = emails;
        this.sessionControl = sessionControl;
        this.audit = audit;
        this.background = background;
        this.transaction = new TransactionTemplate(transactionManager);
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Hands the request to the background and returns at once, before even looking the email
     * up, so neither the response nor its timing says whether the email is registered.
     */
    public void requestReset(String email) {
        background.execute(() -> {
            try {
                issueToken(email);
            } catch (RuntimeException ex) {
                log.error("Password reset request could not be completed", ex);
            }
        });
    }

    /**
     * Sets the new password of the Account the token was issued to, and uses the token up. Then
     * every session of the Account ends, so whoever had the old password or a session is out,
     * and the owner is told. The caller has already checked the new password against the
     * Password policy.
     *
     * @return the Account whose password changed
     * @throws ApiException 400 {@code password reset token expired or invalid} if the token is
     *         unknown, used or expired; the password is then unchanged
     */
    public Account confirmReset(String token, String newPassword) {
        Account account = transaction.execute(status -> {
            PasswordResetToken stored = tokens.findForUpdateByTokenHash(hash(token))
                    .filter(found -> found.isRedeemableAt(clock.instant()))
                    .orElseThrow(PasswordResetService::invalidToken);
            Account changed = accounts.changePassword(stored.userId(), newPassword)
                    .orElseThrow(PasswordResetService::invalidToken);
            stored.markUsed(clock.instant());
            return changed;
        });
        // After the commit: a session that ended before it could still be using the old password.
        sessionControl.endAllSessions(account.getUsername());
        emails.sendPasswordChangedNotification(recipient(account));
        return account;
    }

    private static Recipient recipient(Account account) {
        return new Recipient(account.getId(), account.getEmail());
    }

    /** Removes every Password reset token of this Account, as when it becomes a Tombstone. */
    public void removeTokens(UUID accountId) {
        transaction.executeWithoutResult(status -> tokens.deleteByUserId(accountId));
    }

    private void issueToken(String email) {
        String token = newToken();
        Optional<Account> issuedTo = transaction.execute(status -> {
            // Locked until the commit, so two requests at once can't each leave a pending token.
            Optional<Account> account = accounts.lockActiveByEmail(email);
            account.ifPresent(found -> {
                tokens.deleteByUserIdAndUsedAtIsNull(found.getId());
                tokens.save(PasswordResetToken.issue(found.getId(), hash(token), clock.instant(),
                        properties.tokenValidity()));
            });
            return account;
        });
        // Only once the token is stored, so the link in the email works.
        issuedTo.ifPresent(account -> {
            audit.passwordResetTokenIssued(account.getId());
            emails.sendPasswordResetEmail(recipient(account), resetLink(token));
        });
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String resetLink(String token) {
        return UriComponentsBuilder.fromUriString(properties.resetPageUrl()).queryParam("token", token)
                .build().toUriString();
    }

    private static String hash(String token) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.PASSWORD_RESET_TOKEN_INVALID,
                "The password reset link has expired or is invalid. Please request a new one.");
    }
}
