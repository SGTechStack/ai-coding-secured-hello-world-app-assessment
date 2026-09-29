package sg.securedhello.security.lockout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.securedhello.testsupport.Proves;

/**
 * The ladder's arithmetic, its two lock rules and its startup floor (ADR-011; ADR-012). With t = 5, a 20-minute window,
 * the consecutive rule at 10, C = 100 and A = 50, the fastest attack of any pacing disables in 840 minutes with 580 of
 * warning: the steady attack and the best paced one tie.
 */
class LockoutLadderTest {

    private static final Duration WINDOW = Duration.ofMinutes(20);
    private static final List<Duration> RUNGS = minutes(20, 40, 60);

    private static List<Duration> minutes(long... values) {
        return Arrays.stream(values).mapToObj(Duration::ofMinutes).toList();
    }

    private static LockoutLadder ladder(int threshold, int consecutive, List<Duration> rungs, int cycles, int cap,
            int alert) {
        return new LockoutLadder(threshold, WINDOW, consecutive, rungs, cycles, cap, alert);
    }

    private static LockoutLadder ladder(List<Duration> rungs) {
        return ladder(5, 10, rungs, 5, 100, 50);
    }

    private static LockoutLadder defaults() {
        return ladder(RUNGS);
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
        LockoutLadder ladder = ladder(5, 10, RUNGS, 2, 100, 50);
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
    void theWindowedRuleLocksAtTheThresholdInsideTheWindow() {
        LockoutLadder ladder = defaults();
        assertThat(ladder.locksAt(4, 4)).isFalse();
        assertThat(ladder.locksAt(5, 5)).isTrue();
        assertThat(ladder.locksAt(7, 5)).isTrue();
    }

    @Test
    @Proves("T-LCK-021")
    void theConsecutiveRuleLocksTheTenthFailureSinceSuccessAndEveryFifthAfterItWhateverTheWindow() {
        LockoutLadder ladder = defaults();
        assertThat(ladder.locksAt(9, 1)).as("below the consecutive threshold").isFalse();
        assertThat(ladder.locksAt(10, 1)).as("the consecutive threshold, first in its window").isTrue();
        for (int sinceSuccess = 11; sinceSuccess < 15; sinceSuccess++) {
            assertThat(ladder.locksAt(sinceSuccess, 1)).as("failure %d", sinceSuccess).isFalse();
        }
        assertThat(ladder.locksAt(15, 1)).isTrue();
        assertThat(ladder.locksAt(20, 1)).isTrue();
        assertThat(ladder.locksAt(95, 2)).isTrue();
        assertThat(ladder.locksAt(96, 2)).isFalse();
        assertThat(ladder(5, 15, RUNGS, 5, 100, 50).locksAt(10, 1)).as("not before the consecutive threshold")
                .isFalse();
        assertThat(ladder(5, 15, RUNGS, 5, 100, 50).locksAt(15, 1)).isTrue();
    }

    @Test
    @Proves({"T-LCK-011", "T-LCK-021"})
    void theDefaultsReachTheCapIn840MinutesWith580OfWarningUnderAnyPacing() {
        LockoutLadder ladder = defaults();
        assertThat(ladder.timeToDisable()).isEqualTo(Duration.ofMinutes(840));
        assertThat(ladder.warning()).isEqualTo(Duration.ofMinutes(580));
        assertThat(ladder.threshold()).isEqualTo(5);
        assertThat(ladder.window()).isEqualTo(WINDOW);
        assertThat(ladder.consecutiveThreshold()).isEqualTo(10);
        assertThat(ladder.cap()).isEqualTo(100);
        assertThat(ladder.alertThreshold()).isEqualTo(50);
    }

    @Test
    @Proves("T-LCK-021")
    void withoutTheConsecutiveRuleAPacedAttackDisablesIn460MinutesAndTheFloorRefusesIt() {
        // Past the cap the consecutive rule never fires: four failures, wait out the window, repeat, taking locks only
        // where they are cheaper than the waits (review 11-13 H1).
        LockoutLadder unguarded = ladder(5, 1000, RUNGS, 5, 100, 50);
        assertThat(unguarded.timeToDisable()).isEqualTo(Duration.ofMinutes(460));
        assertThat(unguarded.warning()).isEqualTo(Duration.ofMinutes(240));
        assertThatIllegalArgumentException().isThrownBy(unguarded::requireFloor)
                .withMessageContaining("460")
                .withMessageContaining("840");
    }

    @Test
    @Proves({"T-LCK-011", "T-LCK-021"})
    void theFloorModelsTheConsecutiveThreshold() {
        assertThat(ladder(5, 5, RUNGS, 5, 100, 50).timeToDisable()).isEqualTo(Duration.ofMinutes(840));
        assertThat(ladder(5, 40, RUNGS, 5, 100, 50).timeToDisable()).isEqualTo(Duration.ofMinutes(820));
        assertThat(ladder(5, 35, RUNGS, 5, 100, 50).timeToDisable()).isEqualTo(Duration.ofMinutes(840));
        assertThatNoException().isThrownBy(ladder(5, 35, RUNGS, 5, 100, 50)::requireFloor);

        LockoutLadder raised = ladder(5, 50, RUNGS, 5, 100, 50);
        assertThat(raised.timeToDisable()).isEqualTo(Duration.ofMinutes(780));
        assertThatIllegalArgumentException().isThrownBy(raised::requireFloor).withMessageContaining("780");
    }

    @Test
    @Proves("T-LCK-011")
    void theFloorPassesTheDefaults() {
        assertThatNoException().isThrownBy(defaults()::requireFloor);
    }

    @Test
    @Proves("T-LCK-011")
    void theFloorFailsWhenOneRungIsAMinuteShorter() {
        assertThat(ladder(minutes(19, 40, 60)).timeToDisable()).isEqualTo(Duration.ofMinutes(835));
        assertThat(ladder(minutes(20, 39, 60)).warning()).isEqualTo(Duration.ofMinutes(579));
        assertThat(ladder(minutes(20, 40, 59)).timeToDisable()).isEqualTo(Duration.ofMinutes(831));
        for (List<Duration> rungs : List.of(minutes(19, 40, 60), minutes(20, 39, 60), minutes(20, 40, 59))) {
            assertThatIllegalArgumentException().as("rungs %s", rungs).isThrownBy(ladder(rungs)::requireFloor);
        }
    }

    @Test
    @Proves("T-LCK-011")
    void theFloorPassesRaisedRungs() {
        assertThat(ladder(minutes(30, 60, 90)).timeToDisable()).isEqualTo(Duration.ofMinutes(1260));
        assertThat(ladder(minutes(20, 40, 60, 120)).warning()).isEqualTo(Duration.ofMinutes(820));
        assertThatNoException().isThrownBy(ladder(minutes(30, 60, 90))::requireFloor);
        assertThatNoException().isThrownBy(ladder(minutes(20, 40, 60, 120))::requireFloor);
    }

    @Test
    @Proves("T-LCK-011")
    void theFloorFailsARaisedThresholdWithTheRungsUnchanged() {
        LockoutLadder ladder = ladder(6, 12, RUNGS, 5, 100, 50);
        assertThat(ladder.timeToDisable()).isEqualTo(Duration.ofMinutes(660));
        assertThatIllegalArgumentException().isThrownBy(ladder::requireFloor)
                .withMessageContaining("660")
                .withMessageContaining("840");
    }

    @Test
    @Proves("T-LCK-011")
    void theFloorFailsTheAlertMovedTo60() {
        LockoutLadder ladder = ladder(5, 10, RUNGS, 5, 100, 60);
        assertThat(ladder.timeToDisable()).isEqualTo(Duration.ofMinutes(840));
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
        LockoutLadder over = ladder(minutes(20, 40, 61));
        assertThat(over.timeToDisable()).isEqualTo(Duration.ofMinutes(849));
        assertThat(over.warning()).isEqualTo(Duration.ofMinutes(589));
        assertThatNoException().isThrownBy(over::requireFloor);
    }

    @Test
    void anAlertAtOrPastTheCapLeavesNoWarning() {
        assertThat(ladder(5, 10, RUNGS, 5, 100, 100).warning()).isZero();
        assertThat(ladder(5, 10, RUNGS, 5, 100, 101).warning()).isZero();
        assertThat(ladder(5, 10, RUNGS, 5, 100, 99).warning()).isZero();
        assertThat(ladder(5, 10, RUNGS, 5, 100, 94).warning()).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    void aCapOfOneDisablesAtTheFirstFailure() {
        assertThat(ladder(5, 10, RUNGS, 5, 1, 1).timeToDisable()).isZero();
        assertThat(ladder(1, 10, RUNGS, 5, 3, 1).timeToDisable()).as("threshold 1 locks every failure")
                .isEqualTo(Duration.ofMinutes(40));
    }

    @Test
    void aRungShorterThanTheObservationWindowIsRefused() {
        LockoutLadder ladder = ladder(minutes(19, 60, 90));
        assertThat(ladder.timeToDisable()).isGreaterThan(LockoutLadder.FLOOR_TO_DISABLE);
        assertThatIllegalArgumentException().isThrownBy(ladder::requireRungsOfAtLeastTheWindow)
                .withMessageContaining("19");
        assertThatNoException().isThrownBy(defaults()::requireRungsOfAtLeastTheWindow);
        assertThatNoException().isThrownBy(ladder(minutes(20))::requireRungsOfAtLeastTheWindow);
    }

    @Test
    void nonsenseLaddersAreRefusedOnConstruction() {
        assertThatIllegalArgumentException().isThrownBy(() -> ladder(0, 10, RUNGS, 5, 100, 50));
        assertThatIllegalArgumentException().isThrownBy(() -> ladder(5, 0, RUNGS, 5, 100, 50));
        assertThatIllegalArgumentException().isThrownBy(() -> ladder(5, 10, List.of(), 5, 100, 50));
        assertThatIllegalArgumentException().isThrownBy(() -> ladder(5, 10, RUNGS, 0, 100, 50));
        assertThatIllegalArgumentException().isThrownBy(() -> ladder(5, 10, RUNGS, 5, 0, 50));
        assertThatIllegalArgumentException().isThrownBy(() -> ladder(5, 10, RUNGS, 5, 100, 0));
        assertThatNoException().isThrownBy(() -> ladder(1, 1, minutes(1), 1, 1, 1));
    }

    @Test
    void aConsecutiveThresholdOffTheLockCadenceIsRefused() {
        assertThatIllegalArgumentException().isThrownBy(() -> ladder(5, 12, RUNGS, 5, 100, 50))
                .withMessageContaining("multiple");
        assertThatIllegalArgumentException().isThrownBy(() -> ladder(5, 3, RUNGS, 5, 100, 50));
        assertThatNoException().isThrownBy(() -> ladder(5, 5, RUNGS, 5, 100, 50));
    }
}
