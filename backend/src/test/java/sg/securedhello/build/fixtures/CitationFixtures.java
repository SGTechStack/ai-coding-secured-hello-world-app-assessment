package sg.securedhello.build.fixtures;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import sg.securedhello.testsupport.Proves;

/**
 * Traceability-gate fixture: no Surefire or Failsafe include matches this name, so nothing here runs and no citation
 * counts. Its methods also show which ones the gate treats as tests.
 */
@Proves("T-FIXTURE-005")
public class CitationFixtures {

    @Test
    @Proves("T-FIXTURE-006")
    void aTest() {
    }

    @ParameterizedTest
    @ValueSource(ints = 1)
    void aParameterizedTest(int value) {
    }

    @Test
    @Disabled("traceability-gate fixture")
    @Proves("T-FIXTURE-007")
    void aDisabledTest() {
    }

    @Proves("T-FIXTURE-008")
    void aHelper() {
    }

    @Nested
    public class InsideAClassThatNeverRuns {

        @Test
        @Proves("T-FIXTURE-009")
        void aNestedTest() {
        }
    }
}
