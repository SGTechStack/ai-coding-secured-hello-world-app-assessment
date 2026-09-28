package com.example.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure expiry check backing {@link
 * AbsoluteSessionTimeoutFilter}. Deliberately not a {@code @SpringBootTest}
 * + MockMvc test: {@link jakarta.servlet.http.HttpSession#getCreationTime()}
 * isn't settable on a mock session, so the filter's decision logic is kept
 * as a plain static method precisely so it can be tested directly.
 */
class AbsoluteSessionTimeoutFilterTest {

    private static final Duration EIGHT_HOURS = Duration.ofHours(8);

    @Test
    void sessionYoungerThanMaxAgeIsNotExpired() {
        Instant createdAt = Instant.parse("2026-09-23T00:00:00Z");
        Instant now = createdAt.plus(Duration.ofHours(7).plusMinutes(59));

        assertThat(AbsoluteSessionTimeoutFilter.isExpired(createdAt.toEpochMilli(), now.toEpochMilli(), EIGHT_HOURS))
                .isFalse();
    }

    @Test
    void sessionOlderThanMaxAgeIsExpired() {
        Instant createdAt = Instant.parse("2026-09-23T00:00:00Z");
        Instant now = createdAt.plus(Duration.ofHours(8).plusMinutes(1));

        assertThat(AbsoluteSessionTimeoutFilter.isExpired(createdAt.toEpochMilli(), now.toEpochMilli(), EIGHT_HOURS))
                .isTrue();
    }

    @Test
    void sessionExactlyAtMaxAgeIsNotYetExpired() {
        Instant createdAt = Instant.parse("2026-09-23T00:00:00Z");
        Instant now = createdAt.plus(EIGHT_HOURS);

        assertThat(AbsoluteSessionTimeoutFilter.isExpired(createdAt.toEpochMilli(), now.toEpochMilli(), EIGHT_HOURS))
                .isFalse();
    }
}
