package com.example.demo_app;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the application {@link java.time.Clock} with a {@link MutableClock}. Add it to a test
 * with {@code @Import(TestClockConfig.class)} and autowire {@link MutableClock} to move time.
 * Each test class gets its own context (and clock), so reset the clock in {@code @BeforeEach}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

  public static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

  @Bean
  @Primary
  MutableClock mutableClock() {
    return new MutableClock(START);
  }
}
