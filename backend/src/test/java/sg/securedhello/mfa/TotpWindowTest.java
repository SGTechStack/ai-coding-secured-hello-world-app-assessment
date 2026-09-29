package sg.securedhello.mfa;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import sg.securedhello.testsupport.Proves;

/** The pure TOTP code check (level U): RFC 6238 vectors, the ±1 step window and the replay floor. */
class TotpWindowTest {

    /** RFC 6238 Appendix B's SHA-1 seed. */
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    /** A fixed instant in the middle of a step, and its counter. */
    private static final Instant NOW = Instant.parse("2026-09-29T10:00:15Z");
    private static final long C = TotpWindow.counter(NOW);

    @Test
    @Proves("T-MFA-012")
    void theRfc6238ConformanceVectorGenerates() {
        assertThat(TotpWindow.generate(RFC_SECRET, 1L, 8)).isEqualTo("94287082");
    }

    @ParameterizedTest
    @CsvSource({"59, 287082", "1111111109, 081804", "1111111111, 050471", "1234567890, 005924",
        "2000000000, 279037", "20000000000, 353130"})
    @Proves("T-MFA-013")
    void theSixDigitTruncationOfEachRfcVectorIsZeroPadded(long epochSecond, String code) {
        long counter = TotpWindow.counter(Instant.ofEpochSecond(epochSecond));

        assertThat(TotpWindow.generate(RFC_SECRET, counter, TotpWindow.DIGITS)).isEqualTo(code);
        assertThat(TotpWindow.match(RFC_SECRET, code, Instant.ofEpochSecond(epochSecond), TotpWindow.NEVER_USED))
                .hasValue(counter);
    }

    @Test
    void theCounterIsWholeThirtySecondStepsSinceTheEpoch() {
        assertThat(TotpWindow.counter(Instant.ofEpochSecond(0))).isZero();
        assertThat(TotpWindow.counter(Instant.ofEpochSecond(29))).isZero();
        assertThat(TotpWindow.counter(Instant.ofEpochSecond(30))).isEqualTo(1);
        assertThat(TotpWindow.counter(Instant.ofEpochSecond(59))).isEqualTo(1);
    }

    @Test
    @Proves("T-MFA-010")
    void onAPeriodBoundaryThePreviousWindowsCodeVerifies() {
        Instant boundary = Instant.ofEpochSecond(C * TotpWindow.STEP_SECONDS);
        String previous = TotpWindow.generate(RFC_SECRET, C - 1, TotpWindow.DIGITS);

        assertThat(TotpWindow.match(RFC_SECRET, previous, boundary, TotpWindow.NEVER_USED)).hasValue(C - 1);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1})
    @Proves("T-MFA-011")
    void codesOneStepEitherSideVerifyOnAFreshPrincipal(long offset) {
        String code = TotpWindow.generate(RFC_SECRET, C + offset, TotpWindow.DIGITS);

        assertThat(TotpWindow.match(RFC_SECRET, code, NOW, TotpWindow.NEVER_USED)).hasValue(C + offset);
    }

    @ParameterizedTest
    @ValueSource(longs = {-2, 2})
    @Proves("T-MFA-011")
    void codesTwoStepsAwayAreRejected(long offset) {
        String code = TotpWindow.generate(RFC_SECRET, C + offset, TotpWindow.DIGITS);
        assertThat(code).as("distinct from every in-window code")
                .isNotIn(TotpWindow.generate(RFC_SECRET, C - 1, 6), TotpWindow.generate(RFC_SECRET, C, 6),
                        TotpWindow.generate(RFC_SECRET, C + 1, 6));

        assertThat(TotpWindow.match(RFC_SECRET, code, NOW, TotpWindow.NEVER_USED)).isEmpty();
    }

    @Test
    void aCodeAtOrBeforeTheLastUsedCounterIsRejectedAndALaterOneAccepted() {
        String previous = TotpWindow.generate(RFC_SECRET, C - 1, TotpWindow.DIGITS);
        String current = TotpWindow.generate(RFC_SECRET, C, TotpWindow.DIGITS);
        String next = TotpWindow.generate(RFC_SECRET, C + 1, TotpWindow.DIGITS);

        assertThat(TotpWindow.match(RFC_SECRET, previous, NOW, C)).isEmpty();
        assertThat(TotpWindow.match(RFC_SECRET, current, NOW, C)).isEmpty();
        assertThat(TotpWindow.match(RFC_SECRET, next, NOW, C)).hasValue(C + 1);
        assertThat(TotpWindow.match(RFC_SECRET, current, NOW, C - 1)).hasValue(C);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "12345", "1234567", "12345a", " 12345", "１２３４５６", "-12345"})
    void anythingButExactlySixAsciiDigitsNeverMatches(String code) {
        assertThat(TotpWindow.match(RFC_SECRET, code, NOW, TotpWindow.NEVER_USED)).isEmpty();
    }

    @Test
    void anotherSecretsCodeDoesNotMatch() {
        byte[] other = "09876543210987654321".getBytes(StandardCharsets.US_ASCII);
        String code = TotpWindow.generate(other, C, TotpWindow.DIGITS);
        assertThat(code).isNotEqualTo(TotpWindow.generate(RFC_SECRET, C, TotpWindow.DIGITS));

        assertThat(TotpWindow.match(RFC_SECRET, code, NOW, TotpWindow.NEVER_USED)).isEmpty();
    }
}
