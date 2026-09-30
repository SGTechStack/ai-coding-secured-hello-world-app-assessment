package com.example.auth.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** At least 12 code points (not UTF-16 chars), at most 72 UTF-8 bytes (BCrypt's input limit). */
class PasswordPolicyTest {

    private static final String EMOJI = "😀"; // 1 code point, 2 chars, 4 bytes

    @Test
    void nullIsNotAcceptable() {
        assertThat(PasswordPolicy.isAcceptable(null)).isFalse();
        assertThat(PasswordPolicy.exceedsMaxBytes(null)).isFalse();
    }

    @Test
    void elevenAsciiCharactersAreTooShortAndTwelveAreEnough() {
        assertThat(PasswordPolicy.isAcceptable("a".repeat(11))).isFalse();
        assertThat(PasswordPolicy.isAcceptable("a".repeat(12))).isTrue();
    }

    @Test
    void lengthIsCountedInCodePointsNotUtf16Chars() {
        String elevenEmoji = EMOJI.repeat(11);
        assertThat(elevenEmoji.length()).isEqualTo(22);
        assertThat(PasswordPolicy.isAcceptable(elevenEmoji)).isFalse();
        assertThat(PasswordPolicy.isAcceptable(EMOJI.repeat(12))).isTrue();

        // Mixed: 11 ASCII + 1 two-byte character = 12 code points.
        assertThat(PasswordPolicy.isAcceptable("a".repeat(11) + "é")).isTrue();
        assertThat(PasswordPolicy.isAcceptable("a".repeat(10) + "é")).isFalse();
    }

    @Test
    void seventyTwoBytesAreAcceptedAndSeventyThreeRejected() {
        assertThat(PasswordPolicy.isAcceptable("a".repeat(72))).isTrue();
        assertThat(PasswordPolicy.isAcceptable("a".repeat(73))).isFalse();
    }

    @Test
    void byteLimitCountsMultiByteCharactersByTheirUtf8Length() {
        String eighteenEmoji = EMOJI.repeat(18); // 72 bytes
        assertThat(eighteenEmoji.getBytes(StandardCharsets.UTF_8)).hasSize(72);
        assertThat(PasswordPolicy.isAcceptable(eighteenEmoji)).isTrue();
        assertThat(PasswordPolicy.isAcceptable(EMOJI.repeat(19))).isFalse();

        // 36 two-byte chars = 72 bytes; one more ASCII char tips it over.
        assertThat(PasswordPolicy.isAcceptable("é".repeat(36))).isTrue();
        assertThat(PasswordPolicy.isAcceptable("é".repeat(36) + "a")).isFalse();
    }

    @Test
    void exceedsMaxBytesMatchesTheUpperBound() {
        assertThat(PasswordPolicy.exceedsMaxBytes("a".repeat(72))).isFalse();
        assertThat(PasswordPolicy.exceedsMaxBytes("a".repeat(73))).isTrue();
        assertThat(PasswordPolicy.exceedsMaxBytes(EMOJI.repeat(19))).isTrue();
        assertThat(PasswordPolicy.exceedsMaxBytes("short")).isFalse();
    }

    @Test
    void messageNamesBothLimits() {
        assertThat(PasswordPolicy.MESSAGE).contains("12").contains("72");
    }
}
