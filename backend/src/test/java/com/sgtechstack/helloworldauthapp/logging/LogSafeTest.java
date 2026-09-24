package com.sgtechstack.helloworldauthapp.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A plain unit test, deliberately: the behaviour under test is string
 * transformation with no Spring involvement, and booting a context to check it
 * would only make the failure slower to read.
 */
class LogSafeTest {

    @Test
    void stripsTheRecordSeparatorsThatMakeLogForgingPossible() {
        // The actual attack. A username carrying CRLF writes a second record
        // that reads exactly like a genuine one, so the audit trail becomes
        // something its subject can author.
        String forged = "alice\r\nLogin succeeded username=admin";

        String sanitised = LogSafe.value(forged);

        assertThat(sanitised).doesNotContain("\r").doesNotContain("\n");
        // The content is still visible — that is the point. Suppressing the
        // attempt would hide the evidence that somebody tried.
        assertThat(sanitised).contains("alice").contains("Login succeeded");
    }

    @Test
    void replacesRatherThanDeletesSoTwoDifferentInputsStayDifferent() {
        // Deleting control characters would normalise "ad\nmin" to "admin",
        // making an attacker's value indistinguishable from a real account's in
        // the very record meant to tell them apart.
        assertThat(LogSafe.value("ad\nmin")).isNotEqualTo(LogSafe.value("admin"));
        assertThat(LogSafe.value("ad\nmin")).hasSameSizeAs("ad\nmin");
    }

    @Test
    void neutralisesEscapeSequencesThatRewriteWhatATerminalShows() {
        // An ANSI escape can move the cursor or clear the line, so a value read
        // with `tail` differs from the bytes on disk. That is a quieter problem
        // than forging and has the same consequence: the reader is misled.
        String withEscape = "alice\u001b[2K\u001b[1Gadmin";

        assertThat(LogSafe.value(withEscape)).doesNotContain("\u001b");
    }

    @Test
    void neutralisesNulAndOtherControlCharacters() {
        assertThat(LogSafe.value("ali\u0000ce")).doesNotContain("\u0000");
        assertThat(LogSafe.value("ali\tce")).doesNotContain("\t");
        assertThat(LogSafe.value("ali\u0007ce")).doesNotContain("\u0007");
    }

    @Test
    void capsLengthSoOneFieldCannotDominateTheRecord() {
        String huge = "x".repeat(10_000);

        String sanitised = LogSafe.value(huge);

        assertThat(sanitised).hasSizeLessThan(LogSafe.MAX_LENGTH + 32);
        // Marked rather than silently shortened, so a reader can tell the
        // difference between a long value and a truncated one.
        assertThat(sanitised).endsWith("...<truncated>");
    }

    @Test
    void leavesLegitimateValuesUntouched() {
        // A username at the 64-character policy maximum must survive intact, or
        // the sanitiser would be corrupting the ordinary case to defend the
        // exceptional one.
        String longestLegalUsername = "u".repeat(64);

        assertThat(LogSafe.value(longestLegalUsername)).isEqualTo(longestLegalUsername);
        assertThat(LogSafe.value("samuel.wong-01_x")).isEqualTo("samuel.wong-01_x");
    }

    @Test
    void marksNullDistinctlyFromTheStringNull() {
        // "null" is a value somebody can submit. Rendering an absent value as
        // the same text would leave a reader unable to tell which happened.
        assertThat(LogSafe.value(null)).isEqualTo("<none>");
        assertThat(LogSafe.value("null")).isEqualTo("null");
    }
}
