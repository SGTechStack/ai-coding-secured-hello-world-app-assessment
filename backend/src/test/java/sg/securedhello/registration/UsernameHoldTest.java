package sg.securedhello.registration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/** A hold blocks its username up to, and not at, its expiry (ADR-032 amendment). */
class UsernameHoldTest {

    private static final Instant EXPIRY = Instant.parse("2026-09-30T00:00:00Z");

    @Test
    void aHoldIsLiveBeforeItsExpiryAndNotFromIt() {
        UsernameHold hold = new UsernameHold("alice", "alice@example.test", EXPIRY);

        assertThat(hold.liveAt(EXPIRY.minusNanos(1_000))).isTrue();
        assertThat(hold.liveAt(EXPIRY)).isFalse();
    }
}
