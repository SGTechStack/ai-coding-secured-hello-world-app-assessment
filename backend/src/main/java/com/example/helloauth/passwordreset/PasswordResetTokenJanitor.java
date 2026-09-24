package com.example.helloauth.passwordreset;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodic cleanup of the {@code password_reset_tokens} table (ticket 14
 * reviewer decision): the anonymous {@code /request} endpoint mints rows, so
 * consumed and expired tokens are deleted on a fixed cadence
 * ({@code app.password-reset.cleanup-interval}) rather than growing the table
 * forever. The delay is measured against the injected {@link Clock} like all
 * other time math.
 *
 * <p>First execution waits one full interval — a startup purge is pointless
 * on the in-memory store and keeps scheduled work out of the test suite's
 * way; tests call {@link #purgeDeadTokens()} directly.
 */
@Component
public class PasswordResetTokenJanitor {

    private static final Logger log =
        LoggerFactory.getLogger(PasswordResetTokenJanitor.class);

    private final PasswordResetTokenRepository tokens;
    private final Clock clock;

    public PasswordResetTokenJanitor(
            PasswordResetTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    /** Deletes every consumed or expired token row; returns the row count. */
    @Scheduled(
        fixedDelayString = "${app.password-reset.cleanup-interval:PT1H}",
        initialDelayString = "${app.password-reset.cleanup-interval:PT1H}")
    @Transactional
    public int purgeDeadTokens() {
        int deleted = tokens.deleteDeadTokens(clock.instant());
        if (deleted > 0) {
            log.info("Purged {} dead password-reset token(s).", deleted);
        }
        return deleted;
    }
}
