package com.eitri.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void missingOrEmptyPasswordIsRequired() {
        assertThat(PasswordPolicy.violation(null)).contains("Password is required");
        assertThat(PasswordPolicy.violation("")).contains("Password is required");
    }

    @Test
    void minimumIsTwelveCharactersInclusive() {
        assertThat(PasswordPolicy.violation("a".repeat(11))).contains("Password must be at least 12 characters");
        assertThat(PasswordPolicy.violation("a".repeat(12))).isEmpty();
    }

    @Test
    void maximumIsSeventyTwoUtf8BytesInclusive() {
        assertThat(PasswordPolicy.violation("a".repeat(72))).isEmpty();
        assertThat(PasswordPolicy.violation("a".repeat(73))).contains("Password must be at most 72 bytes");
    }

    @Test
    void lengthCountsCodePointsWhileTheLimitCountsBytes() {
        // U+1F511 is one code point, two UTF-16 chars and four UTF-8 bytes.
        String key = "\uD83D\uDD11";
        assertThat(PasswordPolicy.violation(key.repeat(11))).contains("Password must be at least 12 characters");
        assertThat(PasswordPolicy.violation(key.repeat(12))).isEmpty();
        assertThat(key.repeat(18).getBytes(StandardCharsets.UTF_8)).hasSize(72);
        assertThat(PasswordPolicy.violation(key.repeat(18))).isEmpty();
        assertThat(PasswordPolicy.violation(key.repeat(18) + "a")).contains("Password must be at most 72 bytes");
        // Two-byte characters: 36 of them are exactly 72 bytes.
        assertThat(PasswordPolicy.violation("é".repeat(36))).isEmpty();
        assertThat(PasswordPolicy.violation("é".repeat(37))).contains("Password must be at most 72 bytes");
    }

    @Test
    void whitespaceIsAnOrdinaryCharacter() {
        assertThat(PasswordPolicy.violation(" ".repeat(12))).isEmpty();
    }
}
