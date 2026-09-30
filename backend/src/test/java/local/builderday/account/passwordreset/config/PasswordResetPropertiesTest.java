package local.builderday.account.passwordreset.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class PasswordResetPropertiesTest {
  private static final List<String> VALID = List.of(
      "app.security.password-reset.token-lifetime=30m",
      "app.security.password-reset.request-rate-limit.attempts=5",
      "app.security.password-reset.request-rate-limit.window=15m",
      "app.security.password-reset.confirm-rate-limit.attempts=10",
      "app.security.password-reset.confirm-rate-limit.window=15m",
      "app.security.password-reset.email-cap.attempts=3",
      "app.security.password-reset.email-cap.window=1h");

  private final ApplicationContextRunner context = new ApplicationContextRunner()
      .withUserConfiguration(Bind.class).withPropertyValues(VALID.toArray(String[]::new));

  @ParameterizedTest
  @ValueSource(strings = {"token-lifetime=0s", "request-rate-limit.attempts=0", "confirm-rate-limit.window=0s",
      "email-cap.attempts=-1"})
  void should_failStartup_when_aValueIsNotPositive(String override) {
    context.withPropertyValues("app.security.password-reset." + override)
        .run(started -> assertThat(started).hasFailed());
  }

  @ParameterizedTest
  @ValueSource(strings = {"request-rate-limit", "email-cap"})
  void should_failStartup_when_aLimitIsMissing(String limit) {
    var partial = new ArrayList<>(VALID);
    partial.removeIf(property -> property.contains("." + limit + "."));
    new ApplicationContextRunner().withUserConfiguration(Bind.class).withPropertyValues(partial.toArray(String[]::new))
        .run(started -> assertThat(started).hasFailed());
  }

  @ParameterizedTest
  @ValueSource(strings = {"token-lifetime=45s"})
  void should_start_withValidValues(String override) {
    context.withPropertyValues("app.security.password-reset." + override)
        .run(started -> assertThat(started).hasNotFailed());
  }

  @Configuration
  @EnableConfigurationProperties(PasswordResetProperties.class)
  static class Bind {}
}
