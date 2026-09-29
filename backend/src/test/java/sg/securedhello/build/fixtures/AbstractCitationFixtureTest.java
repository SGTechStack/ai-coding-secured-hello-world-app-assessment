package sg.securedhello.build.fixtures;

import org.junit.jupiter.api.Test;

import sg.securedhello.testsupport.Proves;

/** Traceability-gate fixture: an abstract class is never run, so its citations do not count. */
@Proves("T-FIXTURE-001")
public abstract class AbstractCitationFixtureTest {

    @Test
    @Proves("T-FIXTURE-002")
    void neverRunsOnItsOwn() {
    }
}
