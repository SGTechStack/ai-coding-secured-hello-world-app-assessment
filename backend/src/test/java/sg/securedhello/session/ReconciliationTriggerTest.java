package sg.securedhello.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/** Which durable state, if any, says an account's sessions should already have ended (ADR-039). */
class ReconciliationTriggerTest {

    private static final Instant NOW = Instant.parse("2026-09-30T00:00:00Z");

    private static ReconciliationTrigger.Standing standing() {
        return new ReconciliationTrigger.Standing(true, true, false, null, false);
    }

    @Test
    void anAccountInGoodStandingHasNoTrigger() {
        assertThat(ReconciliationTrigger.of(standing(), NOW)).isEmpty();
    }

    @Test
    void aMissingAccountIsDeleted() {
        assertThat(ReconciliationTrigger.of(new ReconciliationTrigger.Standing(false, false, false, null, false), NOW))
                .contains(ReconciliationTrigger.DELETED);
    }

    @Test
    void eachDurableStateIsItsTrigger() {
        assertThat(ReconciliationTrigger.of(new ReconciliationTrigger.Standing(true, false, false, null, false), NOW))
                .contains(ReconciliationTrigger.DISABLED);
        assertThat(ReconciliationTrigger.of(new ReconciliationTrigger.Standing(true, true, true, null, false), NOW))
                .contains(ReconciliationTrigger.CAPPED);
        assertThat(ReconciliationTrigger.of(
                new ReconciliationTrigger.Standing(true, true, false, NOW.plusSeconds(1), false), NOW))
                .contains(ReconciliationTrigger.LOCKED);
        assertThat(ReconciliationTrigger.of(new ReconciliationTrigger.Standing(true, true, false, null, true), NOW))
                .contains(ReconciliationTrigger.FACTOR_DISABLED);
    }

    /** A lock is in force strictly before {@code locked_until}; at that instant it has lifted, as sign-in sees it. */
    @Test
    void aLockThatHasRunOutIsNoTrigger() {
        assertThat(ReconciliationTrigger.of(new ReconciliationTrigger.Standing(true, true, false, NOW, false), NOW))
                .isEmpty();
        assertThat(ReconciliationTrigger.of(
                new ReconciliationTrigger.Standing(true, true, false, NOW.minus(Duration.ofMinutes(1)), false), NOW))
                .isEmpty();
    }

    /** One account is counted once, under the first trigger in declaration order. */
    @Test
    void theFirstTriggerInOrderWins() {
        assertThat(ReconciliationTrigger.of(
                new ReconciliationTrigger.Standing(false, false, true, NOW.plusSeconds(60), true), NOW))
                .contains(ReconciliationTrigger.DELETED);
        assertThat(ReconciliationTrigger.of(
                new ReconciliationTrigger.Standing(true, false, true, NOW.plusSeconds(60), true), NOW))
                .contains(ReconciliationTrigger.DISABLED);
        assertThat(ReconciliationTrigger.of(
                new ReconciliationTrigger.Standing(true, true, true, NOW.plusSeconds(60), true), NOW))
                .contains(ReconciliationTrigger.CAPPED);
        assertThat(ReconciliationTrigger.of(
                new ReconciliationTrigger.Standing(true, true, false, NOW.plusSeconds(60), true), NOW))
                .contains(ReconciliationTrigger.LOCKED);
    }
}
