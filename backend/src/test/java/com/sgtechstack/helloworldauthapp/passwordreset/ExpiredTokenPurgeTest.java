package com.sgtechstack.helloworldauthapp.passwordreset;

import com.sgtechstack.helloworldauthapp.user.Role;
import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reset-token rows must be removed once they can no longer be used for anything.
 *
 * <h2>Two separate reasons, neither of them access control</h2>
 *
 * An expired or consumed token is already unusable — the service rejects both. So
 * this is retention, not authorisation.
 *
 * <p>The privacy reason: each row is a dated record that a specific account asked
 * to reset its password. Kept indefinitely, the table becomes a behavioural
 * history of the user base that nothing in the product needs and no policy
 * covered.
 *
 * <p>The operational reason: nothing ever deleted a row, so the only bound on the
 * table was how many resets the system had ever served.
 *
 * <h2>Driven directly, not through the scheduler</h2>
 *
 * {@code app.retention.purge-enabled} is false in test configuration, and these
 * tests call the purge with an explicit cutoff. The behaviour worth pinning is
 * which rows it selects; whether Spring's scheduler fires is Spring's concern, and
 * a background thread deleting rows on its own timetable inside an integration
 * suite produces failures that reproduce about one run in twenty.
 */
@SpringBootTest
@ActiveProfiles("dev")
class ExpiredTokenPurgeTest {

    @Autowired
    private ExpiredTokenPurge purge;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        user = userRepository.save(new User("purge-user", "purge@example.com",
                passwordEncoder.encode("purge-password-1234"), Role.USER, true));
    }

    @Test
    void removesTokensWhoseExpiryIsPastTheCutoff() {
        tokenRepository.save(new PasswordResetToken(user, "long-expired-hash",
                Instant.now().minus(Duration.ofDays(30))));

        long removed = purge.purgeOlderThan(Instant.now().minus(Duration.ofDays(7)));

        assertThat(removed).isEqualTo(1);
        assertThat(tokenRepository.findAll()).isEmpty();
    }

    @Test
    void removesConsumedTokensEvenWhenTheirExpiryIsStillInTheFuture() {
        // The case a single expiry-based sweep would miss entirely, and the reason
        // there are two derived deletes rather than one. A token consumed shortly
        // after issue is spent, but its expires_at is still ahead of any cutoff —
        // so it would have lived forever.
        PasswordResetToken used = new PasswordResetToken(user, "used-hash",
                Instant.now().plus(Duration.ofDays(365)));
        used.markUsed();
        tokenRepository.save(used);

        long removed = purge.purgeOlderThan(Instant.now().plus(Duration.ofMinutes(1)));

        assertThat(removed).isEqualTo(1);
        assertThat(tokenRepository.findAll()).isEmpty();
    }

    @Test
    void keepsTokensInsideTheGracePeriod() {
        // Recently expired rows are kept on purpose. "Was a reset requested on
        // this account last week, and was the link followed" is a question that
        // arises exactly when an account turns out to have been compromised, so
        // purging on expiry would destroy the answer at the moment it became
        // useful.
        tokenRepository.save(new PasswordResetToken(user, "recently-expired-hash",
                Instant.now().minus(Duration.ofHours(1))));

        long removed = purge.purgeOlderThan(Instant.now().minus(Duration.ofDays(7)));

        assertThat(removed).isZero();
        assertThat(tokenRepository.findAll()).hasSize(1);
    }

    @Test
    void leavesLiveTokensAlone() {
        // The control case. A purge that removed a live token would silently break
        // every in-flight password reset, and every other assertion here would
        // still pass.
        tokenRepository.save(new PasswordResetToken(user, "live-hash",
                Instant.now().plus(PasswordResetService.TOKEN_EXPIRY)));

        long removed = purge.purgeOlderThan(Instant.now().minus(Duration.ofDays(7)));

        assertThat(removed).isZero();
        assertThat(tokenRepository.findAll()).hasSize(1);
    }

    @Test
    void isIdempotentSoARepeatSweepIsHarmless() {
        tokenRepository.save(new PasswordResetToken(user, "expired-hash",
                Instant.now().minus(Duration.ofDays(30))));
        Instant cutoff = Instant.now().minus(Duration.ofDays(7));

        assertThat(purge.purgeOlderThan(cutoff)).isEqualTo(1);
        // Matters because there is no distributed lock: two instances would each
        // run the sweep, and the second finding nothing to do is what makes that
        // wasteful rather than harmful.
        assertThat(purge.purgeOlderThan(cutoff)).isZero();
    }

    @Test
    void doesNotTouchTheAccountsTheTokensBelongTo() {
        tokenRepository.save(new PasswordResetToken(user, "expired-hash",
                Instant.now().minus(Duration.ofDays(30))));

        purge.purgeOlderThan(Instant.now().minus(Duration.ofDays(7)));

        // The tokens hold a foreign key to users, and a derived delete loads
        // entities before removing them. Pinned so a future cascade added to that
        // association cannot turn a retention sweep into account deletion.
        assertThat(userRepository.findById(user.getId())).isPresent();
    }
}
