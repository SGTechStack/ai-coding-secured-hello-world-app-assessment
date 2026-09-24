package com.example.demo_app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** A test can swap the application clock for one it controls. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class TestClockConfigTest {

  @Autowired private Clock clock;

  @Autowired private MutableClock mutableClock;

  @Test
  void theInjectedClockIsTheControllableOne() {
    mutableClock.set(TestClockConfig.START);

    mutableClock.advance(Duration.ofMinutes(16));

    assertThat(clock).isSameAs(mutableClock);
    assertThat(clock.instant()).isEqualTo(TestClockConfig.START.plus(Duration.ofMinutes(16)));
  }
}
