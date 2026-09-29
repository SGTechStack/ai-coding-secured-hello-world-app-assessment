package com.eitri.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SessionHasherTest {

    @Test
    void hashesSessionIdsWithHmacSha256AsLowerCaseHex() {
        SessionHasher hasher = new SessionHasher("key");

        assertThat(hasher.hash("The quick brown fox jumps over the lazy dog"))
                .isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }

    @Test
    void omitsTheHashWhenThereIsNoSession() {
        assertThat(new SessionHasher("key").hash((jakarta.servlet.http.HttpSession) null)).isEmpty();
    }

    @Test
    void rejectsABlankKey() {
        assertThatThrownBy(() -> new SessionHasher("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("session-hash-key");
    }
}
