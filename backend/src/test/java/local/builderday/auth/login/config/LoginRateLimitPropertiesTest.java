package local.builderday.auth.login.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class LoginRateLimitPropertiesTest {
  private final ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(Bind.class);

  @Test
  void should_failStartup_when_aValueIsNotPositive() {
    context.withPropertyValues("app.security.login.rate-limit.attempts=0", "app.security.login.rate-limit.window=1m")
        .run(started -> assertThat(started).hasFailed());
    context.withPropertyValues("app.security.login.rate-limit.attempts=10", "app.security.login.rate-limit.window=0s")
        .run(started -> assertThat(started).hasFailed());
    context.withPropertyValues("app.security.login.rate-limit.attempts=10", "app.security.login.rate-limit.window=1m")
        .run(started -> assertThat(started).hasNotFailed());
  }

  @Configuration
  @EnableConfigurationProperties(LoginRateLimitProperties.class)
  static class Bind {}
}
