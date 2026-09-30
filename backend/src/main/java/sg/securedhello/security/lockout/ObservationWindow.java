package sg.securedhello.security.lockout;

import java.time.Duration;
import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * The observation window's chained rule (ADR-012), shared by the password lockout and the TOTP factor's tier 1
 * (ADR-027): a failure counts on the one before it only if it follows that failure by less than the window, and
 * otherwise restarts the count at 1. The window is chained, not fixed: each failure moves the anchor, so a steady
 * attack below the window's pace never goes stale. Pure arithmetic, no state.
 */
public final class ObservationWindow {

    private ObservationWindow() {
    }

    /**
     * The windowed count after a failure at {@code now}.
     *
     * @param counted      the windowed count before this failure
     * @param lastFailedAt when the previous failure happened, the window's anchor, or {@code null} if none is counted
     * @param now          when this failure happened
     * @param window       the observation window
     * @return {@code counted + 1} if {@code now} is less than {@code window} after {@code lastFailedAt}, otherwise 1
     */
    public static int count(int counted, @Nullable Instant lastFailedAt, Instant now, Duration window) {
        return lastFailedAt != null && now.isBefore(lastFailedAt.plus(window)) ? counted + 1 : 1;
    }
}
