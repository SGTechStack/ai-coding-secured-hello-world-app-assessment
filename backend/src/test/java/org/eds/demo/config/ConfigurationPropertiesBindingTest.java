package org.eds.demo.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.eds.demo.auth.application.SignInThrottleProperties;
import org.eds.demo.user.application.TemporaryPasswordProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ConfigurationPropertiesBindingTest {

  @Configuration
  @EnableConfigurationProperties({
    RestClientProperties.class,
    SignInThrottleProperties.class,
    TemporaryPasswordProperties.class
  })
  static class Config {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(Config.class);

  @Test
  void defaultsApplyWhenNothingIsConfigured() {
    runner.run(
        context -> {
          var rest = context.getBean(RestClientProperties.class);
          assertThat(rest.connectTimeout()).isEqualTo(Duration.ofSeconds(30));
          assertThat(rest.readTimeout()).isEqualTo(Duration.ofSeconds(30));
          assertThat(context.getBean(TemporaryPasswordProperties.class).ttl())
              .isEqualTo(Duration.ofHours(24));
          assertThat(context.getBean(SignInThrottleProperties.class).backoffThreshold())
              .isEqualTo(3);
        });
  }

  @Test
  void configuredValuesOverrideTheDefaults() {
    runner
        .withPropertyValues("rest.client.read-timeout=2s", "app.temporary-password.ttl=2h")
        .run(
            context -> {
              assertThat(context.getBean(RestClientProperties.class).readTimeout())
                  .isEqualTo(Duration.ofSeconds(2));
              assertThat(context.getBean(TemporaryPasswordProperties.class).ttl())
                  .isEqualTo(Duration.ofHours(2));
            });
  }

  @Test
  void nonPositiveValuesFailStartup() {
    runner
        .withPropertyValues("app.temporary-password.ttl=0s")
        .run(context -> assertThat(context).hasFailed());
    runner
        .withPropertyValues("app.sign-in.backoff-threshold=0")
        .run(context -> assertThat(context).hasFailed());
    runner
        .withPropertyValues("rest.client.connect-timeout=-1s")
        .run(context -> assertThat(context).hasFailed());
  }
}
