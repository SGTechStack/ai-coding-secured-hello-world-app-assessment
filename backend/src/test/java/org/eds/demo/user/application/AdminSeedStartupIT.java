package org.eds.demo.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.eds.demo.Application;
import org.eds.demo.user.domain.AppUser;
import org.eds.demo.user.domain.Role;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** Full application restarts against one database: seeding is safe to repeat. */
class AdminSeedStartupIT {

  private static final String[] SHARED_DATABASE_ARGS = {
    "--spring.profiles.active=local",
    "--app.email.inbound.enabled=false",
    "--server.port=0",
    "--management.server.port=0",
    "--spring.datasource.url=jdbc:h2:mem:admin-seed-startup-it;DB_CLOSE_DELAY=-1",
    "--spring.jpa.hibernate.ddl-auto=update",
  };

  private static ConfigurableApplicationContext start(String... extraArgs) {
    var args = new java.util.ArrayList<>(java.util.List.of(SHARED_DATABASE_ARGS));
    args.addAll(java.util.List.of(extraArgs));
    return new SpringApplicationBuilder(Application.class)
        .web(WebApplicationType.SERVLET)
        .run(args.toArray(String[]::new));
  }

  @Test
  void cloudStartupWithNoAdminAndNoPasswordFailsWithClearMessage() {
    assertThatThrownBy(
            () ->
                new SpringApplicationBuilder(Application.class)
                    .web(WebApplicationType.SERVLET)
                    .run(
                        "--spring.profiles.active=test",
                        "--server.port=0",
                        "--management.server.port=0",
                        "--spring.datasource.url=jdbc:h2:mem:admin-seed-cloud-it",
                        "--app.admin.password-required=true"))
        .hasStackTraceContaining("app.admin.password");
  }

  @Test
  void restartWithAnAdminPresentCreatesNoDuplicate() {
    try (var first = start("--app.admin.password=test-only-seed-secret")) {
      assertThat(first.getBean(AppUserRepository.class).existsByUserRolesRole(Role.ADMIN)).isTrue();
    }
    try (var second =
        start("--app.admin.username=another-admin", "--app.admin.password=other-secret")) {
      var users = second.getBean(AppUserRepository.class);
      assertThat(users.findByUsername("another-admin")).isEmpty();
      assertThat(users.findByUsername("admin")).isPresent();
    }
  }

  @Test
  void configuredAdminUsernameHeldByANonAdminFailsStartupWithClearMessage() {
    var database = "--spring.datasource.url=jdbc:h2:mem:admin-seed-clash-it;DB_CLOSE_DELAY=-1";
    var common =
        new String[] {
          "--spring.profiles.active=local",
          "--app.email.inbound.enabled=false",
          "--server.port=0",
          "--management.server.port=0",
          database,
          "--spring.jpa.hibernate.ddl-auto=update",
        };
    try (var first =
        new SpringApplicationBuilder(Application.class)
            .web(WebApplicationType.SERVLET)
            .run(common)) {
      first
          .getBean(AppUserRepository.class)
          .save(AppUser.create("clash", java.util.EnumSet.of(Role.USER)));
    }

    var withSeedConfig = new java.util.ArrayList<>(java.util.List.of(common));
    withSeedConfig.addAll(
        java.util.List.of("--app.admin.username=clash", "--app.admin.password=x"));

    assertThatThrownBy(
            () ->
                new SpringApplicationBuilder(Application.class)
                    .web(WebApplicationType.SERVLET)
                    .run(withSeedConfig.toArray(String[]::new)))
        .hasStackTraceContaining("app.admin.username")
        .hasStackTraceContaining("clash")
        .hasStackTraceContaining("is not an ADMIN");
  }
}
