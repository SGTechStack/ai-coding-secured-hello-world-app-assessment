package com.eitri.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Adjustable UTC clock that replaces the application {@link Clock}; import {@link Config} to use it. */
public final class MutableClock extends Clock {

    private final AtomicReference<Instant> instant;

    public MutableClock(Instant initial) {
        instant = new AtomicReference<>(initial);
    }

    public void set(Instant value) {
        instant.set(value);
    }

    public void advance(Duration duration) {
        instant.updateAndGet(value -> value.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return zone.equals(ZoneOffset.UTC) ? this : Clock.fixed(instant(), zone);
    }

    @Override
    public Instant instant() {
        return instant.get();
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        MutableClock mutableTestClock() {
            return new MutableClock(Instant.now());
        }
    }
}
