package com.sgtechstack.helloworldauthapp.auth;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Account-lockout policy: {@link #MAX_FAILED_ATTEMPTS} failures
 * <em>within {@link #FAILURE_WINDOW}</em> lock the account for
 * {@link #LOCKOUT_DURATION}.
 *
 * <p>The window is the important part, and was previously missing. Without it
 * {@code failed_login_attempts} is a lifetime tally that only ever resets on a
 * successful login, so five failures spread across months still lock the
 * account. That makes targeted lockout trivial: an attacker who knows a
 * username spends five cheap requests to deny that user access, then repeats
 * whenever the cooldown lapses — and five attempts stays under the per-IP
 * threshold, so IP throttling never engages. The PRD asks for failures "within
 * a window" precisely to avoid this.
 */
@Component
public class LockoutPolicy {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    /**
     * How long a failure keeps counting towards the threshold. A failure older
     * than this starts a new streak rather than adding to the old one.
     */
    public static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);

    public boolean shouldLock(int failedAttempts) {
        return failedAttempts >= MAX_FAILED_ATTEMPTS;
    }

    /**
     * @param lastFailureAt when this account last failed a login, or null if it
     *                      never has
     * @return whether a failure now continues an existing streak, as opposed to
     *         starting a fresh one
     */
    public boolean continuesStreak(Instant lastFailureAt, Instant now) {
        return lastFailureAt != null && !now.isAfter(lastFailureAt.plus(FAILURE_WINDOW));
    }
}
