package local.builderday.account.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class LoginLockoutPropertiesTest {
  private final ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(Bind.class);

  @Test
  void should_failStartup_when_aValueIsNotPositive() {
    context.withPropertyValues("app.security.login.lockout.threshold=0", "app.security.login.lockout.duration=20m")
        .run(started -> assertThat(started).hasFailed());
    context.withPropertyValues("app.security.login.lockout.threshold=5", "app.security.login.lockout.duration=0s")
        .run(started -> assertThat(started).hasFailed());
    context.withPropertyValues("app.security.login.lockout.threshold=5", "app.security.login.lockout.duration=20m")
        .run(started -> assertThat(started).hasNotFailed());
  }

  @Configuration
  @EnableConfigurationProperties(LoginLockoutProperties.class)
  static class Bind {}
}
