package sg.securedhello.security.lockout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.securedhello.testsupport.Proves;

/**
 * The ladder's arithmetic and its startup floor (ADR-011): with t = 5, C = 100 and A = 50, 19 locks come before the cap
 * and 9 before the alert, so the disable is 840 minutes away and the warning 580.
 */
class LockoutLadderTest {

    private static final List<Duration> RUNGS = minutes(20, 40, 60);

    private static List<Duration> minutes(long... values) {
        return java.util.Arrays.stream(values).mapToObj(Duration::ofMinutes).toList();
    }

    private static LockoutLadder ladder(int threshold, List<Duration> rungs, int cycles, int cap, int alert) {
        return new LockoutLadder(threshold, rungs, cycles, cap, alert);
    }

    private static LockoutLadder defaults() {
        return ladder(5, RUNGS, 5, 100, 50);
    }

    @Test
    void eachRungLastsItsCyclesThenTheLastRungRepeats() {
        LockoutLadder ladder = defaults();
        assertThat(ladder.lockDuration(1)).isEqualTo(Duration.ofMinutes(20));
        assertThat(ladder.lockDuration(5)).isEqualTo(Duration.ofMinutes(20));
        assertThat(ladder.lockDuration(6)).isEqualTo(Duration.ofMinutes(40));
        assertThat(ladder.lockDuration(10)).isEqualTo(Duration.ofMinutes(40));
        assertThat(ladder.lockDuration(11)).isEqualTo(Duration.ofMinutes(60));
        assertThat(ladder.lockDuration(19)).isEqualTo(Duration.ofMinutes(60));
        assertThat(ladder.lockDuration(40)).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    void theCyclesPerRungDecideWhereTheRungChanges() {
        LockoutLadder ladder = ladder(5, RUNGS, 2, 100, 50);
        assertThat(ladder.lockDuration(2)).isEqualTo(Duration.ofMinutes(20));
        assertThat(ladder.lockDuration(3)).isEqualTo(Duration.ofMinutes(40));
        assertThat(ladder.lockDuration(5)).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    void theLockNumberIsReadFromTheCapCounter() {
        LockoutLadder ladder = defaults();
        assertThat(ladder.lockNumber(5)).isEqualTo(1);
        assertThat(ladder.lockNumber(6)).isEqualTo(2);
        assertThat(ladder.lockNumber(10)).isEqualTo(2);
        assertThat(ladder.lockNumber(11)).isEqualTo(3);
        assertThat(ladder.lockNumber(95)).isEqualTo(19);
    }

    @Test
    void theDefaultsReachTheCapIn840MinutesWith580OfWarning() {
        LockoutLadder ladder = defaults();
        assertThat(ladder.locksBeforeCap()).isEqualTo(19);
        assertThat(ladder.locksBeforeAlert()).isEqualTo(9);
        assertThat(ladder.timeToDisable()).isEqualTo(Duration.ofMinutes(840));
        assertThat(ladder.timeToAlert()).isEqualTo(Duration.ofMinutes(260));
        assertThat(ladder.warning()).isEqualTo(Duration.ofMinutes(580));
        assertThat(ladder.threshold()).isEqualTo(5);
        assertThat(ladder.cap()).isEqualTo(100);
        assertThat(ladder.alertThreshold()).isEqualTo(50);
    }

    @Test
    @Proves("T-LCK-011")
    void theFloorPassesTheDefaults() {
        assertThatNoException().isThrownBy(defaults()::requireFloor);
    }

    @Test
    @Proves("T-LCK-011")
    void theFloorFailsWhenOneRungIsAMinuteShorter() {
        for (List<Duration> rungs : List.of(minutes(19, 40, 60), minutes(20, 39, 60), minutes(20, 40, 59))) {
            assertThatIllegalArgumentException().as("rungs %s", rungs)
                    .isThrownBy(ladder(5, rungs, 5, 100, 50)::requireFloor);
        }
    }

    @Test
    @Proves("T-LCK-011")
    void theFloorPassesRaisedRungs() {
        assertThatNoException().isThrownBy(ladder(5, minutes(30, 60, 90), 5, 100, 50)::requireFloor);
        assertThatNoException().isThrownBy(ladder(5, minutes(20, 40, 60, 120), 5, 100, 50)::requireFloor);
    }

    @Test
    @Proves("T-LCK-011")
    void theFloorFailsARaisedThresholdWithTheRungsUnchanged() {
        LockoutLadder ladder = ladder(6, RUNGS, 5, 100, 50);
        assertThat(ladder.locksBeforeCap()).isEqualTo(16);
        assertThat(ladder.timeToDisable()).isEqualTo(Duration.ofMinutes(660));
        assertThatIllegalArgumentException().isThrownBy(ladder::requireFloor)
                .withMessageContaining("660")
                .withMessageContaining("840");
    }

    @Test
    @Proves("T-LCK-011")
    void theFloorFailsTheAlertMovedTo60() {
        LockoutLadder ladder = ladder(5, RUNGS, 5, 100, 60);
        assertThat(ladder.warning()).isEqualTo(Duration.ofMinutes(480));
        assertThatIllegalArgumentException().isThrownBy(ladder::requireFloor)
                .withMessageContaining("480")
                .withMessageContaining("580");
    }

    @Test
    void theFloorsAreInclusive() {
        // 840 to disable exactly, with the alert placed for 580 exactly: both pass.
        assertThatNoException().isThrownBy(defaults()::requireFloor);
        // One minute over each floor also passes; one under fails (above).
        assertThatNoException().isThrownBy(ladder(5, minutes(20, 40, 61), 5, 100, 50)::requireFloor);
    }

    @Test
    void aRungShorterThanTheObservationWindowIsRefused() {
        LockoutLadder ladder = ladder(5, minutes(19, 60, 90), 5, 100, 50);
        assertThat(ladder.timeToDisable()).isGreaterThan(LockoutLadder.FLOOR_TO_DISABLE);
        assertThatIllegalArgumentException().isThrownBy(() -> ladder.requireRungsOfAtLeast(Duration.ofMinutes(20)))
                .withMessageContaining("19");
        assertThatNoException().isThrownBy(() -> defaults().requireRungsOfAtLeast(Duration.ofMinutes(20)));
    }

    @Test
    void nonsenseLaddersAreRefusedOnConstruction() {
        assertThatIllegalArgumentException().isThrownBy(() -> ladder(0, RUNGS, 5, 100, 50));
        assertThatIllegalArgumentException().isThrownBy(() -> ladder(5, List.of(), 5, 100, 50));
        assertThatIllegalArgumentException().isThrownBy(() -> ladder(5, RUNGS, 0, 100, 50));
        assertThatIllegalArgumentException().isThrownBy(() -> ladder(5, RUNGS, 5, 0, 50));
        assertThatIllegalArgumentException().isThrownBy(() -> ladder(5, RUNGS, 5, 100, 0));
        assertThatNoException().isThrownBy(() -> ladder(1, minutes(1), 1, 1, 1));
    }
}
