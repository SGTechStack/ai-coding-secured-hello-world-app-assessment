package sg.securedhello.security.lockout;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import sg.securedhello.testsupport.RestartHarness;
import sg.securedhello.testsupport.RestartHarness.Boot;

/**
 * A ladder that would disable passwords sooner than the floor stops the whole application before its port opens
 * (ADR-011). The arithmetic's cases are {@code LockoutLadderTest}'s.
 */
class LockoutStartupFloorTest {

    @ParameterizedTest
    @ValueSource(strings = {"--app.security.lockout.ladder.rungs=19m,40m,60m", "--app.security.lockout.threshold=6",
            "--app.security.lockout.nist.alert-threshold=60", "--app.security.lockout.consecutive-threshold=50"})
    void aLadderUnderTheFloorStopsStartup(String misconfiguration) {
        Boot boot = RestartHarness.boot(builder -> builder.profiles("dev"), misconfiguration);

        assertThat(boot.portOpened()).isFalse();
        assertThat(boot.failureMessages()).contains("ADR-011");
    }
}
