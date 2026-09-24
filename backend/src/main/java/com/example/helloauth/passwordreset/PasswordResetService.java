package com.example.helloauth.passwordreset;

import com.example.helloauth.audit.AuditLogger;
import com.example.helloauth.config.AppProperties;
import com.example.helloauth.session.SessionInvalidationService;
import com.example.helloauth.user.RegistrationException;
import com.example.helloauth.user.User;
import com.example.helloauth.user.UserRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single-use reset flow (ticket 12, mechanics ratified in ticket 07):
 *
 * <ul>
 *   <li><b>Request</b> — looks the email up silently; the response is always
 *       the same generic message whether or not an account exists, so the
 *       endpoint can't be used for enumeration. On a match it mints a 32-byte
 *       {@link SecureRandom} token, stores only its SHA-256 hash with a short
 *       expiry, and hands the plaintext to the (stubbed) email adapter.</li>
 *   <li><b>Confirm</b> — hashes the presented token, requires unexpired +
 *       unused, re-BCrypts the password, stamps {@code used_at}, and deletes
 *       every session the user holds — a stolen session dies with the old
 *       password.</li>
 * </ul>
 *
 * High-entropy tokens get SHA-256 (not BCrypt): the hash exists only to keep
 * a database read from yielding a replayable credential, and the 256-bit
 * search space makes offline guessing pointless.
 */
@Service
public class PasswordResetService {

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final EmailService emailService;
    private final SessionInvalidationService sessionInvalidation;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final AppProperties properties;
    private final AuditLogger audit;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(UserRepository users,
            PasswordResetTokenRepository tokens, EmailService emailService,
            SessionInvalidationService sessionInvalidation,
            PasswordEncoder passwordEncoder, Clock clock, AppProperties properties,
            AuditLogger audit) {
        this.users = users;
        this.tokens = tokens;
        this.emailService = emailService;
        this.sessionInvalidation = sessionInvalidation;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.properties = properties;
        this.audit = audit;
    }

    /**
     * Mints a token only when the email is registered — the caller sees the
     * identical generic response either way (enumeration resistance lives in
     * the controller's fixed message; the side-effects simply don't happen).
     *
     * <p>{@code @Transactional}: the outstanding-token invalidation (ticket 14
     * — a new request kills every earlier link, leaving one live token) and
     * the mint are one change. The request itself is audited either way; the
     * plaintext token's only egress stays the stubbed email.
     */
    @Transactional
    public void requestReset(String email) {
        audit.passwordResetRequested(email);
        users.findByEmail(email).ifPresent(user -> {
            tokens.invalidateOutstandingForUser(user, clock.instant());
            String token = generateToken();
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setTokenHash(hashToken(token));
            resetToken.setExpiresAt(
                clock.instant().plus(properties.getPasswordReset().getTokenTtl()));
            tokens.save(resetToken);
            emailService.sendPasswordResetLink(user.getEmail(), token);
        });
    }

    /**
     * Consumes a token: re-BCrypts the password, stamps {@code used_at}, and
     * deletes every session the account holds (via
     * {@link SessionInvalidationService} — a stolen session dies with the
     * old password).
     *
     * <p>{@code @Transactional}: token consumption and the password update are
     * one atomic change, and the Spring Session JDBC deletes join the same
     * datasource transaction — either the whole reset lands or nothing does.
     *
     * <p>The spend is an atomic {@code UPDATE … WHERE used_at IS NULL}
     * (ticket 14): two concurrent confirms of the same token both pass the
     * read-time checks, but only one UPDATE matches — the loser gets the same
     * generic rejection as an already-spent token, so a token can never be
     * double-consumed.
     */
    @Transactional
    public void confirmReset(String token, String newPassword) {
        String tokenHash = hashToken(token);
        PasswordResetToken resetToken = tokens.findByTokenHash(tokenHash)
            .orElseThrow(InvalidResetTokenException::new);

        // Unknown, spent, or expired — one generic rejection for all three
        // (the token is a credential; its state is not public information).
        Instant now = clock.instant();
        if (resetToken.getUsedAt() != null
                || !resetToken.getExpiresAt().isAfter(now)) {
            throw new InvalidResetTokenException();
        }

        // Same length-only policy as registration — enforced before the
        // token is spent, so a typo'd password doesn't burn the link.
        int minLength = properties.getPasswordMinLength();
        if (newPassword == null || newPassword.length() < minLength) {
            throw new RegistrationException.PasswordTooShort(minLength);
        }

        // The atomic claim — the row the read just validated may already be
        // spent by a racing confirm; a 0-row update is the generic rejection.
        if (tokens.consumeIfUnused(tokenHash, now) == 0) {
            throw new InvalidResetTokenException();
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(user);

        // One live token: links minted alongside the consumed one die too —
        // otherwise an attacker who requested a second link before the
        // victim reset could still pivot through it afterwards.
        tokens.invalidateOutstandingForUser(user, now);

        sessionInvalidation.invalidateAllFor(user.getUsername());
        audit.passwordResetCompleted(user.getUsername());
    }

    /** 32 bytes of {@link SecureRandom}, base64url — URL-safe, no padding. */
    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 hex of a plaintext token — the only form that is persisted or
     * queried. Public so tests can seed token rows through the repository
     * (the ratified fixture seam) and verify stored-vs-plaintext separation.
     */
    public static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                    .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is guaranteed by the JCA spec — unreachable on any JDK.
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
