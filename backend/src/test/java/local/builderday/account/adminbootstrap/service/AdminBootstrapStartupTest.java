package local.builderday.account.adminbootstrap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import local.builderday.BuilderdayApplication;
import local.builderday.account.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The startup wiring (Story 12, ADR 0009 §4). Prior art: {@code SessionLifecycleTest}, which starts a real context
 * with {@code SpringApplicationBuilder}. Command-line arguments outrank {@code application.yml}, so each start gets a
 * private in-memory database and its own {@code app.admin.*}. A valid pair seeds an Admin through the runner; an
 * invalid password makes the runner throw, which stops startup.
 */
class AdminBootstrapStartupTest {
  private static final String PASSWORD = "Str0ng!Passw0rd";

  private ConfigurableApplicationContext start(String database, String username, String password) {
    return new SpringApplicationBuilder(BuilderdayApplication.class).run(
        "--server.port=0", "--server.ssl.enabled=false",
        // A private in-memory H2 (PostgreSQL mode) per start, so one test's Admin never satisfies another's check.
        "--spring.datasource.url=jdbc:h2:mem:" + database + ";MODE=PostgreSQL;LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1",
        "--spring.datasource.username=sa", "--spring.datasource.password=test-only-h2-password",
        "--app.admin.username=" + username, "--app.admin.password=" + password);
  }

  @Test
  void should_haveAnAdmin_whenStartedWithValidCredentials() {
    try (var context = start("bootstrap-valid", "admin.one", PASSWORD)) {
      assertThat(context.getBean(UserRepository.class).existsByRole("ADMIN")).isTrue();
    }
  }

  @Test
  void should_failStartup_whenStartedWithAnInvalidPassword() {
    assertThatThrownBy(() -> start("bootstrap-invalid", "admin.one", "short").close());
  }
}
