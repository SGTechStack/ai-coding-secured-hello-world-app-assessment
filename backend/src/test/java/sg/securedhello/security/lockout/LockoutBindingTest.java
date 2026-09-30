package sg.securedhello.security.lockout;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.securedhello.security.ratelimit.LockoutCardinalityProperties;
import sg.securedhello.testsupport.CtxNondevTest;
import sg.securedhello.testsupport.Proves;

/**
 * The production values of the lockout, the NIST cap and the cardinality axis, read off {@code application.yml} with
 * no profile (ADR-067). What each value drives is shown against the bound properties in {@code LockoutTest} and
 * {@code LockoutCardinalityTest}.
 */
class LockoutBindingTest extends CtxNondevTest {

    private static final LockoutProperties LOCKOUT = productionProperty("app.security.lockout",
            LockoutProperties.class);

    @Test
    @Proves("T-LCK-010")
    void theLadderRungsAre20And40And60Minutes() {
        assertThat(LOCKOUT.ladder().rungs())
                .isEqualTo(List.of(Duration.ofMinutes(20), Duration.ofMinutes(40), Duration.ofMinutes(60)));
    }

    @Test
    @Proves("T-LCK-012")
    void theLockThresholdIs5() {
        assertThat(LOCKOUT.threshold()).isEqualTo(5);
        assertThat(LOCKOUT.toLadder().threshold()).as("the value the counter compares against").isEqualTo(5);
    }

    @Test
    @Proves("T-LCK-013")
    void theObservationWindowIs20Minutes() {
        assertThat(LOCKOUT.observationWindow()).isEqualTo(Duration.ofMinutes(20));
    }

    @Test
    @Proves("T-LCK-022")
    void theConsecutiveThresholdIs10() {
        assertThat(LOCKOUT.consecutiveThreshold()).isEqualTo(10);
        assertThat(LOCKOUT.toLadder().consecutiveThreshold()).as("the value the counter compares against")
                .isEqualTo(10);
    }

    @Test
    @Proves("T-LCK-014")
    void eachRungLastsFiveCycles() {
        assertThat(LOCKOUT.ladder().cyclesPerRung()).isEqualTo(5);
    }

    @Test
    @Proves("T-LCK-015")
    void theNistAlertIsAt50() {
        assertThat(LOCKOUT.nist().alertThreshold()).isEqualTo(50);
        assertThat(LOCKOUT.toLadder().alertThreshold()).isEqualTo(50);
    }

    @Test
    @Proves("T-LCK-032")
    void aTrustedDeviceStaysTrustedFor30DaysAndLocksAt5() {
        assertThat(LOCKOUT.device().ttl()).isEqualTo(Duration.ofDays(30));
        assertThat(LOCKOUT.device().threshold()).isEqualTo(5);
        assertThat(LOCKOUT.toDeviceLadder().threshold()).as("the value the device lane compares against")
                .isEqualTo(5);
    }

    @Test
    @Proves("T-LCK-016")
    void theNistCapIs100() {
        assertThat(LOCKOUT.nist().cap()).isEqualTo(100);
        assertThat(LOCKOUT.toLadder().cap()).isEqualTo(100);
    }

    @Test
    @Proves("T-LCK-019")
    void theProductionJdbcUrlPinsTheLockTimeout() {
        assertThat(productionProperty("spring.datasource.url", String.class)).contains(";LOCK_TIMEOUT=1000");
    }

    @Test
    @Proves("T-RL-008")
    void theCardinalityAxisHoldsFiveAccountsPerSource() {
        assertThat(productionProperty("app.security.rate-limit.lockout-cardinality",
                LockoutCardinalityProperties.class).k()).isEqualTo(5);
    }

    @Test
    @Proves("T-RL-009")
    void theCardinalityWindowIsOneHour() {
        assertThat(productionProperty("app.security.rate-limit.lockout-cardinality",
                LockoutCardinalityProperties.class).window()).isEqualTo(Duration.ofHours(1));
    }
}
