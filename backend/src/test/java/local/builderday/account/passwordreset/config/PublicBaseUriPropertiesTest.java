package local.builderday.account.passwordreset.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class PublicBaseUriPropertiesTest {
  private final ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(Bind.class);

  @ParameterizedTest
  @ValueSource(strings = {
      "http://app.example", "app.example", "/relative", "https://app.example/?q=1", "https://app.example/#x"})
  void should_failStartup_when_theBaseUriIsNotAnAbsoluteHttpsUri(String uri) {
    context.withPropertyValues("app.public-base-uri=" + uri).run(started -> assertThat(started).hasFailed());
  }

  @Test
  void should_failStartup_when_theBaseUriIsMissing() {
    context.run(started -> assertThat(started).hasFailed());
  }

  @Test
  void should_buildLinksFromTheBaseUri_withOrWithoutATrailingSlash() {
    context.withPropertyValues("app.public-base-uri=https://app.example/").run(started ->
        assertThat(started.getBean(PublicBaseUriProperties.class).resolve("/reset-password"))
            .isEqualTo("https://app.example/reset-password"));
    context.withPropertyValues("app.public-base-uri=https://app.example:8443").run(started ->
        assertThat(started.getBean(PublicBaseUriProperties.class).resolve("/reset-password"))
            .isEqualTo("https://app.example:8443/reset-password"));
  }

  @Configuration
  @EnableConfigurationProperties(PublicBaseUriProperties.class)
  static class Bind {}
}
