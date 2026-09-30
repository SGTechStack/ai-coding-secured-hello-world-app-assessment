package local.builderday.auth.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Role definitions are configuration-owned: a bad definition must stop startup rather than weaken authorization. */
class AuthorizationPropertiesTest {
  private final ApplicationContextRunner context = new ApplicationContextRunner()
      .withUserConfiguration(BindAuthorization.class)
      .withPropertyValues("app.security.registration-role=USER");

  @Test
  void should_start_whenRolesAndGrantsAreWellFormed() {
    context.withPropertyValues("app.security.roles[0]=USER", "app.security.roles[1]=ADMIN",
            "app.security.grants[0].pattern=/api/admin/**", "app.security.grants[0].roles[0]=ADMIN")
        .run(started -> {
          assertThat(started).hasNotFailed();
          assertThat(started.getBean(AuthorizationProperties.class).roles()).containsExactly("USER", "ADMIN");
        });
  }

  @Test
  void should_failStartup_whenARoleIsDefinedTwice() {
    context.withPropertyValues("app.security.roles[0]=USER", "app.security.roles[1]=USER")
        .run(started -> assertThat(started).hasFailed().getFailure()
            .hasRootCauseMessage("Duplicate role definition: USER"));
  }

  @Test
  void should_failStartup_whenARoleIsMalformed() {
    context.withPropertyValues("app.security.roles[0]=USER", "app.security.roles[1]=admin role")
        .run(started -> assertThat(started).hasFailed());
  }

  @Test
  void should_failStartup_whenAGrantOrTheRegistrationRoleNamesAnUndefinedRole() {
    context.withPropertyValues("app.security.roles[0]=USER",
            "app.security.grants[0].pattern=/api/admin/**", "app.security.grants[0].roles[0]=ADMIN")
        .run(started -> assertThat(started).hasFailed());
    context.withPropertyValues("app.security.roles[0]=ADMIN")
        .run(started -> assertThat(started).hasFailed());
  }

  @Test
  void should_failStartup_naming_aConfiguredRoleTheRoleEnumDoesNotDeclare() {
    context.withPropertyValues("app.security.roles[0]=USER", "app.security.roles[1]=ADMIN",
            "app.security.roles[2]=AUDITOR")
        .run(started -> assertThat(started).hasFailed().getFailure()
            .hasRootCauseMessage("app.security.roles names a role the Role enum does not declare: AUDITOR"));
  }

  @Test
  void should_failStartup_naming_aRoleEnumConstantMissingFromConfiguration() {
    context.withPropertyValues("app.security.roles[0]=USER")
        .run(started -> assertThat(started).hasFailed().getFailure()
            .hasRootCauseMessage("app.security.roles is missing the Role enum constant: ADMIN"));
  }

  @Configuration
  @EnableConfigurationProperties(AuthorizationProperties.class)
  static class BindAuthorization {}
}
