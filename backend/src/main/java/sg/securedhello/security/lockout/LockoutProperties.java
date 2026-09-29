package sg.securedhello.security.lockout;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The password lockout and NIST cap under {@code app.security.lockout} (ADR-011; ADR-012; ADR-013). The values live in
 * {@code application.yml}; there are no defaults here.
 *
 * <p>Binding checks the startup floor: the context refresh fails if the ladder disables a password in under 840
 * minutes of steady attack, warns for under 580, or has a rung shorter than the observation window (T-LCK-011).
 *
 * @param threshold         failures inside the window that lock the account (5)
 * @param observationWindow how close together failures must be to count together (20m)
 * @param ladder            the lock durations
 * @param nist              the NIST cap and its alert
 */
@Validated
@ConfigurationProperties("app.security.lockout")
public record LockoutProperties(@Positive int threshold, @NotNull Duration observationWindow,
        @NotNull @Valid Ladder ladder, @NotNull @Valid Nist nist) {

    public LockoutProperties {
        if (threshold > 0 && observationWindow != null && ladder != null && nist != null && ladder.valid()
                && nist.valid()) {
            LockoutLadder built = ladder(threshold, ladder, nist);
            built.requireFloor();
            built.requireRungsOfAtLeast(observationWindow);
        }
    }

    /** The ladder these values describe. */
    public LockoutLadder toLadder() {
        return ladder(threshold, ladder, nist);
    }

    private static LockoutLadder ladder(int threshold, Ladder ladder, Nist nist) {
        return new LockoutLadder(threshold, ladder.rungs(), ladder.cyclesPerRung(), nist.cap(), nist.alertThreshold());
    }

    /**
     * @param rungs         the lock durations, shortest first (20m, 40m, 60m)
     * @param cyclesPerRung how many locks each rung lasts for (5)
     */
    public record Ladder(@NotEmpty List<Duration> rungs, @Positive int cyclesPerRung) {

        boolean valid() {
            return rungs != null && !rungs.isEmpty() && cyclesPerRung > 0;
        }
    }

    /**
     * @param cap            failures since success that disable the password authenticator (100)
     * @param alertThreshold failures since success that raise the alert (50)
     */
    public record Nist(@Positive int cap, @Positive int alertThreshold) {

        boolean valid() {
            return cap > 0 && alertThreshold > 0;
        }
    }
}
