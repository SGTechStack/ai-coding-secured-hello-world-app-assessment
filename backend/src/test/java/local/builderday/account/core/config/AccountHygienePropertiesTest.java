package local.builderday.account.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AccountHygienePropertiesTest {
  private final ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(Bind.class);

  @Test
  void should_bindEnvironmentThresholds() {
    context.withPropertyValues("app.security.account-hygiene.disable-after=30d",
            "app.security.account-hygiene.delete-after=365d")
        .run(started -> assertThat(started.getBean(AccountHygieneProperties.class))
            .isEqualTo(new AccountHygieneProperties(Duration.ofDays(30), Duration.ofDays(365))));
  }

  @Test
  void should_failStartup_whenDeletionWouldNotComeAfterDisablement() {
    context.withPropertyValues("app.security.account-hygiene.disable-after=180d",
            "app.security.account-hygiene.delete-after=90d")
        .run(started -> assertThat(started).hasFailed());
  }

  @Configuration
  @EnableConfigurationProperties(AccountHygieneProperties.class)
  static class Bind {}
}
