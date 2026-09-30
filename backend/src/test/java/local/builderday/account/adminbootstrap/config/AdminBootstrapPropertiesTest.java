package local.builderday.account.adminbootstrap.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AdminBootstrapPropertiesTest {
  private final ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(Bind.class);

  @Test
  void should_bindBothCredentials() {
    context.withPropertyValues("app.admin.username=admin.one", "app.admin.password=Str0ng!Passw0rd")
        .run(started -> {
          var properties = started.getBean(AdminBootstrapProperties.class);
          assertThat(properties.username()).isEqualTo("admin.one");
          assertThat(properties.password()).isEqualTo("Str0ng!Passw0rd");
          assertThat(properties.anySet()).isTrue();
          assertThat(properties.exactlyOneSet()).isFalse();
        });
  }

  @Test
  void should_treatBlankOrAbsentValuesAsUnset() {
    context.withPropertyValues("app.admin.username=", "app.admin.password=   ")
        .run(started -> {
          var properties = started.getBean(AdminBootstrapProperties.class);
          assertThat(properties.username()).isNull();
          assertThat(properties.password()).isNull();
          assertThat(properties.anySet()).isFalse();
        });
  }

  @Test
  void should_reportExactlyOneSet_whenOnlyTheUsernameIsPresent() {
    context.withPropertyValues("app.admin.username=admin.one", "app.admin.password=")
        .run(started -> {
          var properties = started.getBean(AdminBootstrapProperties.class);
          assertThat(properties.exactlyOneSet()).isTrue();
          assertThat(properties.anySet()).isTrue();
        });
  }

  @Configuration
  @EnableConfigurationProperties(AdminBootstrapProperties.class)
  static class Bind {}
}
