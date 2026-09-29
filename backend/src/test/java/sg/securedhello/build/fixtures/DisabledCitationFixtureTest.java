package sg.securedhello.build.fixtures;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import sg.securedhello.testsupport.Proves;

/** Traceability-gate fixture: a disabled class is skipped by the runner, so its citations do not count. */
@Disabled("traceability-gate fixture: a disabled test proves nothing")
@Proves("T-FIXTURE-003")
public class DisabledCitationFixtureTest {

    @Test
    @Proves("T-FIXTURE-004")
    void skipped() {
    }
}
