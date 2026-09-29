package sg.securedhello.security.lockout;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import sg.securedhello.security.source.SourceKey;
import sg.securedhello.security.source.SourceKeyAuthenticationDetails;
import sg.securedhello.testsupport.Proves;

/**
 * The lockout-cardinality axis fails closed (ADR-015): a lockout whose token lacks the converter's source details is
 * attributed to the one {@link SourceKey#UNPARSEABLE} key, whose set then fills and refuses, rather than going
 * unrecorded.
 */
class LockoutRecorderSourceTest {

    @Test
    @Proves("T-RL-032")
    void missingOrForeignDetailsAreAttributedToTheUnparseableKey() {
        assertThat(LockoutRecorder.sourceOf(null)).isSameAs(SourceKey.UNPARSEABLE);
        assertThat(LockoutRecorder.sourceOf("web details of another kind")).isSameAs(SourceKey.UNPARSEABLE);
    }

    @Test
    @Proves("T-RL-032")
    void theConvertersDetailsNameTheirSource() {
        SourceKey source = new SourceKey("198.51.100.7");
        assertThat(LockoutRecorder.sourceOf(new SourceKeyAuthenticationDetails(source))).isEqualTo(source);
    }
}
