package com.sgtechstack.helloworldauthapp.passwordreset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Deletes reset-token rows once they can no longer be used for anything.
 *
 * <h2>Why rows that are already dead still need removing</h2>
 *
 * An expired or consumed token cannot be redeemed — {@code PasswordResetService}
 * rejects both. So this is not an access-control fix; it is a retention one, and
 * two distinct problems follow from keeping the rows.
 *
 * <p>The first is privacy. Each row is a dated record that a specific account
 * asked to reset its password. Accumulated indefinitely, the table becomes a
 * behavioural history of the user base that nothing in the product needs and no
 * policy covers — precisely the "no retention limit" finding, and the reason
 * this class exists in the privacy remediation rather than the hardening one.
 *
 * <p>The second is that unbounded growth is its own slow failure. The table has
 * a unique index on the token hash; nothing ever removed a row; the only bound
 * was how many resets the system had ever served.
 *
 * <h2>The grace period</h2>
 *
 * Rows are kept for {@code app.retention.reset-token-grace} past expiry or use,
 * rather than removed the moment they go stale. That window is what lets an
 * investigation answer "did a reset happen on this account last week, and was
 * the link consumed" — a question that arises specifically when an account
 * turns out to have been taken over. Purging on expiry would destroy the
 * evidence at the exact moment it became interesting.
 *
 * <h2>Single-instance assumption</h2>
 *
 * No distributed lock. Two instances would each run the sweep, and the second
 * would find nothing to delete — wasteful, not harmful, because deletion is
 * idempotent. Worth naming because the same cannot be said of the session
 * registry or the throttle map, which are the genuine multi-instance blockers.
 *
 * <h2>Always a bean, not always scheduled</h2>
 *
 * This component is registered unconditionally; what {@code
 * app.retention.purge-enabled} gates is {@code SchedulingConfig}, and therefore
 * whether {@link #purge()} is ever invoked on a timer. The two were briefly the
 * same switch, which meant turning the scheduler off in tests also removed the
 * thing under test. Separating them lets the test suite disable the timer and
 * still drive the sweep directly — which is the right shape anyway, since the
 * behaviour worth asserting is which rows are selected.
 */
@Component
public class ExpiredTokenPurge {

    private static final Logger log = LoggerFactory.getLogger(ExpiredTokenPurge.class);

    private final PasswordResetTokenRepository tokenRepository;
    private final Duration grace;

    public ExpiredTokenPurge(
            PasswordResetTokenRepository tokenRepository,
            @Value("${app.retention.reset-token-grace}") Duration grace
    ) {
        this.tokenRepository = tokenRepository;
        this.grace = grace;
    }

    /**
     * Sweeps on a fixed delay from the end of the previous run, so a slow sweep
     * cannot overlap itself the way {@code fixedRate} would.
     *
     * <p>{@code initialDelay} keeps it off the startup path: a purge competing
     * with schema creation and the admin bootstrap runner for the same
     * connection pool buys nothing, since nothing expires in the first minute of
     * a process's life.
     */
    @Scheduled(
            initialDelayString = "${app.retention.purge-initial-delay}",
            fixedDelayString = "${app.retention.purge-interval}")
    public void purge() {
        long removed = purgeOlderThan(Instant.now().minus(grace));

        if (removed > 0) {
            log.info("Purged spent password reset tokens count={} graceperiod={}", removed, grace);
        }
    }

    /**
     * Package-private and cutoff-parameterised so a test can drive it directly
     * against rows it just created, instead of waiting for a schedule or
     * manipulating the clock.
     *
     * @return how many rows were removed
     */
    @Transactional
    long purgeOlderThan(Instant cutoff) {
        return tokenRepository.deleteAllByExpiresAtBefore(cutoff)
                + tokenRepository.deleteAllByUsedAtBefore(cutoff);
    }
}
