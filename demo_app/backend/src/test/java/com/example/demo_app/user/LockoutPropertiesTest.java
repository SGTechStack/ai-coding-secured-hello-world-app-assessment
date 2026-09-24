package com.example.demo_app.user;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Lockout can't be configured off: a missing or non-positive setting stops startup. */
class LockoutPropertiesTest {

  @Test
  void acceptsPositiveValues() {
    assertThatNoException().isThrownBy(() -> new LockoutProperties(5, Duration.ofMinutes(15)));
  }

  @Test
  void rejectsMissingOrNonPositiveValues() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new LockoutProperties(0, Duration.ofMinutes(15)));
    assertThatIllegalArgumentException().isThrownBy(() -> new LockoutProperties(5, null));
    assertThatIllegalArgumentException().isThrownBy(() -> new LockoutProperties(5, Duration.ZERO));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new LockoutProperties(5, Duration.ofMinutes(-1)));
  }
}
