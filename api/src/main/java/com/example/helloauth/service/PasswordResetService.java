package com.example.helloauth.service;

import com.example.helloauth.settings.AppProperties;
import com.example.helloauth.domain.Account;
import com.example.helloauth.domain.PasswordResetToken;
import com.example.helloauth.repository.AccountRepository;
import com.example.helloauth.repository.PasswordResetTokenRepository;
import com.example.helloauth.service.exception.AuthExceptions.InvalidResetTokenException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;

    private final AccountRepository accounts;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicy;
    private final EmailService emailService;
    private final SessionService sessions;
    private final IpThrottleService throttle;
    private final AuditLog audit;
    private final AppProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(
            AccountRepository accounts,
            PasswordResetTokenRepository tokens,
            PasswordEncoder passwordEncoder,
            PasswordPolicyValidator passwordPolicy,
            EmailService emailService,
            SessionService sessions,
            IpThrottleService throttle,
            AuditLog audit,
            AppProperties properties,
            Clock clock) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.emailService = emailService;
        this.sessions = sessions;
        this.throttle = throttle;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Issues a reset token if the email belongs to an account, and says nothing either way.
     *
     * <p>The caller gets no signal at all, which is the point: a response that differed for a
     * registered address would turn this endpoint into an account-existence oracle.
     *
     * <p>Every call counts against the caller's IP allowance, not just the ones that find an
     * account. Because the endpoint deliberately cannot tell the caller whether it matched, there is
     * no "failure" to count — so counting only failures would leave it entirely unmetered, and hand
     * an attacker both a free oracle and a way to spam a victim's inbox.
     */
    @Transactional
    public void requestReset(String email, String clientIp) {
        throttle.assertNotThrottled(clientIp);
        throttle.recordAttempt(clientIp);

        Optional<Account> found = accounts.findByEmail(RegistrationService.normalise(email));
        if (found.isEmpty()) {
            return;
        }
        Account account = found.get();

        String plaintextToken = generateToken();
        tokens.save(
                new PasswordResetToken(
                        account,
                        hash(plaintextToken),
                        clock.instant().plus(properties.passwordReset().tokenTtl())));

        emailService.sendPasswordResetEmail(
                account.getEmail(), plaintextToken, properties.passwordReset().tokenTtl());
        audit.passwordResetRequested(account.getUsername());
    }

    /**
     * Redeems a token and sets a new password.
     *
     * <p>The new password is validated before the token is examined, so a rejected password does not
     * burn the user's only token and force them to start over.
     */
    @Transactional
    public void confirmReset(String plaintextToken, String newPassword) {
        passwordPolicy.validate(newPassword);

        Instant now = clock.instant();
        PasswordResetToken token =
                tokens.findByTokenHash(hash(plaintextToken))
                        .orElseThrow(InvalidResetTokenException::new);

        // Unknown, expired and already-used all land here, and all produce the same response.
        if (!token.isRedeemable(now)) {
            throw new InvalidResetTokenException();
        }

        Account account = token.getAccount();
        account.setPasswordHash(passwordEncoder.encode(newPassword));

        // A user resetting their password has almost certainly been failing to log in. Leaving them
        // locked out with a password that now works would be a confusing dead end.
        account.clearFailedLogins();

        token.markUsed(now);

        // Beyond the PRD's single-use rule, and deliberately: any *other* outstanding token for this
        // account is retired too. Otherwise someone who requested a reset before the real owner did
        // would still hold a working key afterwards.
        List<PasswordResetToken> siblings = tokens.findByAccountAndUsedAtIsNull(account);
        siblings.forEach(sibling -> sibling.markUsed(now));

        // The reason the PRD requires this: a reset is what someone does when they suspect their
        // password is compromised, and an attacker holding a live session would otherwise keep it.
        int ended = sessions.endAllSessionsFor(account.getUsername());
        audit.passwordResetCompleted(account.getUsername(), ended);
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256, hex encoded. Deterministic on purpose — the token has to be found by value — and
     * adequate because the token is 256 bits of randomness rather than a guessable human password.
     */
    private String hash(String plaintextToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(plaintextToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required of every Java platform; this cannot happen.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
