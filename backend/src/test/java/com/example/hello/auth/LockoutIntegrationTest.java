package com.example.hello.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.hello.support.IntegrationTestSupport;
import com.example.hello.user.User;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Story 3. Test profile: 3 account failures lock; 6 failures per IP throttle. */
class LockoutIntegrationTest extends IntegrationTestSupport {

  @Test
  @DisplayName("the Nth consecutive failure locks the account and sets locked_until")
  void locksAfterThreshold() throws Exception {
    String username = registerUser("lock");
    String ip = uniqueIp();

    for (int i = 0; i < 2; i++) {
      assertThat(attemptLogin(username, "bad-password-000", ip).getResponse().getStatus()).isEqualTo(401);
      assertThat(userRepository.findByUsername(username).orElseThrow().getLockedUntil()).isNull();
    }
    assertThat(attemptLogin(username, "bad-password-000", ip).getResponse().getStatus()).isEqualTo(401);

    User user = userRepository.findByUsername(username).orElseThrow();
    assertThat(user.getFailedLoginAttempts()).isEqualTo(3);
    assertThat(user.getLockedUntil()).isNotNull().isAfter(Instant.now());

    assertThat(attemptLogin(username, GOOD_PASSWORD, ip).getResponse().getStatus())
        .as("correct password while locked")
        .isEqualTo(401);
  }

  @Test
  @DisplayName("after the cooldown elapses the correct password logs in and resets the counter")
  void unlocksAfterCooldown() throws Exception {
    String username = registerUser("cool");
    User user = userRepository.findByUsername(username).orElseThrow();
    for (int i = 0; i < 3; i++) {
      user.recordFailedLogin();
    }
    user.lockUntil(Instant.now().minus(Duration.ofSeconds(1)));
    userRepository.save(user);

    ClientSession session = login(username, GOOD_PASSWORD);
    assertThat(session.cookie()).isNotNull();

    User after = userRepository.findByUsername(username).orElseThrow();
    assertThat(after.getFailedLoginAttempts()).isZero();
    assertThat(after.getLockedUntil()).isNull();
  }

  @Test
  @DisplayName("failures from one IP across many usernames are throttled independently of account lockout")
  void throttlesByIp() throws Exception {
    String victim = registerUser("victim");
    String attackerIp = uniqueIp();

    for (int i = 0; i < 6; i++) {
      MvcResult result = attemptLogin(uniqueName("nobody"), "whatever-password-1", attackerIp);
      assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }
    MvcResult throttled = attemptLogin(victim, GOOD_PASSWORD, attackerIp);
    assertThat(throttled.getResponse().getStatus()).isEqualTo(429);

    // The victim's account itself is untouched and usable from another address.
    assertThat(userRepository.findByUsername(victim).orElseThrow().getLockedUntil()).isNull();
    ClientSession session = login(victim, GOOD_PASSWORD, uniqueIp());
    assertThat(session.cookie()).isNotNull();
  }
}
