package com.example.hello.reset;

import com.example.hello.auth.PasswordPolicy;
import com.example.hello.auth.SessionRevocation;
import com.example.hello.shared.ApiException;
import com.example.hello.shared.AuditLog;
import com.example.hello.user.UserAccount;
import com.example.hello.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {
    private final UserRepository users;
    private final ResetTokenRepository tokens;
    private final PasswordPolicy policy;
    private final PasswordEncoder passwords;
    private final SessionRevocation sessions;
    private final EmailService emails;
    private final AuditLog audit;
    private final Clock clock;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository users, ResetTokenRepository tokens, PasswordPolicy policy,
                                PasswordEncoder passwords, SessionRevocation sessions, EmailService emails,
                                AuditLog audit, Clock clock, @Value("${app.frontend-url}") String frontendUrl) {
        this.users = users; this.tokens = tokens; this.policy = policy; this.passwords = passwords;
        this.sessions = sessions; this.emails = emails; this.audit = audit; this.clock = clock;
        this.frontendUrl = frontendUrl;
    }

    @Transactional
    public void request(String email) {
        audit.record("password_reset_requested", "anonymous", "redacted", "accepted");
        users.findByEmail(email).ifPresent(account -> {
            // All reset operations lock the user first, then tokens, to avoid cross-token deadlocks.
            UserAccount user = users.lockById(account.getId()).orElseThrow(this::invalidToken);
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            tokens.save(new PasswordResetToken(user.getId(), hash(token), clock.instant().plusSeconds(1200)));
            emails.sendPasswordResetEmail(user.getEmail(), frontendUrl + "/reset-password#token=" + token);
        });
    }

    @Transactional
    public void confirm(String plaintext, String password) {
        policy.validate(password);
        String tokenHash = hash(plaintext);
        // Project only the owner id so an unlocked token is never cached in this persistence context.
        var userId = tokens.findUserIdByTokenHash(tokenHash).orElseThrow(this::invalidToken);
        UserAccount user = users.lockById(userId).orElseThrow(this::invalidToken);
        PasswordResetToken token = tokens.lockByHash(tokenHash).orElseThrow(this::invalidToken);
        // The first entity read occurs under the lock, after any competing redemption commits.
        if (!token.isUsable(clock.instant())) throw invalidToken();
        user.changePassword(passwords.encode(password));
        token.consume(clock.instant());
        tokens.consumeAll(user.getId(), clock.instant());
        sessions.revoke(user.getUsername());
        audit.record("password_reset_completed", user.getUsername(), user.getUsername(), "success");
    }

    private ApiException invalidToken() { return new ApiException(HttpStatus.BAD_REQUEST, "Reset link is invalid or expired. Request a new link."); }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime.", error);
        }
    }
}
