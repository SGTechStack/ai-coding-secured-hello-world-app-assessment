package com.assessment.securedhelloworld.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void stripsCarriageReturnAndLineFeed() {
        assertThat(LogSanitizer.sanitize("alice\r\nlog.info(\"forged\")"))
                .isEqualTo("alicelog.info(\"forged\")");
    }

    @Test
    void stripsOtherControlCharacters() {
        assertThat(LogSanitizer.sanitize("alice\u0000\u0007\u001bbob"))
                .isEqualTo("alicebob");
    }

    @Test
    void leavesOrdinaryTextUnchanged() {
        assertThat(LogSanitizer.sanitize("alice-user_1.2")).isEqualTo("alice-user_1.2");
    }

    @Test
    void returnsLiteralNullStringForNullInput() {
        assertThat(LogSanitizer.sanitize(null)).isEqualTo("null");
    }
}
