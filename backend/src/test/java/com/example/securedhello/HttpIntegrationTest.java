package com.example.securedhello;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/**
 * Shared integration-test harness exercising the real HTTP boundary over H2.
 *
 * <p>This is the single primary test seam for the application: tests run
 * against a full web environment with the real controller/service/repository
 * stack and the H2 database, asserting externally observable behaviour
 * (status codes, bodies, cookies, persisted state).
 *
 * <p>Time is driven by a controllable {@link Clock} so time-dependent
 * behaviour (lockout cooldown, token expiry) can be advanced deterministically
 * without {@code Thread.sleep}. Call {@link #advanceTime(Duration)} or
 * {@link #setTime(Instant)} to move the clock.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(HttpIntegrationTest.TestClockConfig.class)
public abstract class HttpIntegrationTest {

    /** Fixed reference instant used as the deterministic test "now". */
    protected static final Instant FIXED_NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static final AtomicReference<Instant> CURRENT = new AtomicReference<>(FIXED_NOW);

    @LocalServerPort
    protected int port;

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    /** Reset the test clock to the fixed reference instant before each usage. */
    protected void resetClock() {
        CURRENT.set(FIXED_NOW);
    }

    /** Advance the test clock by the given duration. */
    protected void advanceTime(Duration by) {
        CURRENT.updateAndGet(instant -> instant.plus(by));
    }

    /** Set the test clock to an explicit instant. */
    protected void setTime(Instant instant) {
        CURRENT.set(instant);
    }

    /**
     * Test-only clock that reads the mutable {@link #CURRENT} instant. Marked
     * {@link Primary} so it replaces the production system clock in tests.
     */
    @TestConfiguration
    static class TestClockConfig {

        @Bean
        @Primary
        Clock testClock() {
            return new Clock() {
                @Override
                public ZoneOffset getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(java.time.ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return CURRENT.get();
                }
            };
        }
    }
}
