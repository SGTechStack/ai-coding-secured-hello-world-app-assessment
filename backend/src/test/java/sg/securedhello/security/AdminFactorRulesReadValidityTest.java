package sg.securedhello.security;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import sg.securedhello.testsupport.MutableClock;
import sg.securedhello.testsupport.Proves;

/**
 * The admin read rule's factor validity is never shorter than the absolute session lifetime (ADR-021; R-MFA-012): the
 * rules refuse to be built otherwise, and the application builds them with the two equal.
 */
class AdminFactorRulesReadValidityTest {

    private static final Duration LIFETIME = Duration.ofHours(8);

    @Test
    @Proves("T-CFG-019")
    void aReadValidityShorterThanTheSessionLifetimeIsRefused() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new AdminFactorRules(LIFETIME.minusSeconds(1), LIFETIME, MutableClock.startingNow()))
                .withMessageContaining("shorter than the absolute session lifetime");
    }

    @Test
    @Proves("T-CFG-019")
    void aReadValidityEqualToOrLongerThanTheLifetimeIsAcceptedAndTheApplicationUsesTheLifetime() {
        assertThatNoException().isThrownBy(() -> new AdminFactorRules(LIFETIME, LIFETIME, MutableClock.startingNow()));
        assertThatNoException().isThrownBy(() -> new AdminFactorRules(LIFETIME.plusHours(1), LIFETIME,
                MutableClock.startingNow()));
        assertThatNoException().isThrownBy(() -> new AdminFactorRules(LIFETIME, MutableClock.startingNow()));
    }
}
