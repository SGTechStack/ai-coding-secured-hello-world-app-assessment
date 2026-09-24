package com.example.demo_app.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.demo_app.DemoAppApplication;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * When the bootstrap admin is created, and when startup refuses to go on. Each test starts the
 * whole application in {@code prod} (which has no {@code app.admin.*} defaults) on its own
 * in-memory database, so it starts with no admin; a "restart" is a second context on the same
 * database.
 *
 * <p>Spring Boot's logging system is switched off for these contexts: re-initialising Logback
 * would drop the audit appender and change the console format for the rest of the JVM.
 */
class AdminBootstrapStartupTest {

  private static final String STRONG_PASSWORD = "Bootstrap-Startup-Test-7q";

  private final Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
  private final ListAppender<ILoggingEvent> audit = new ListAppender<>();
  private final String database = "jdbc:h2:mem:admin-bootstrap-" + UUID.randomUUID();

  @BeforeEach
  void captureAuditAndKeepLogging() {
    System.setProperty(LoggingSystem.SYSTEM_PROPERTY, LoggingSystem.NONE);
    audit.start();
    auditLogger.addAppender(audit);
  }

  @AfterEach
  void restore() {
    auditLogger.detachAppender(audit);
    System.clearProperty(LoggingSystem.SYSTEM_PROPERTY);
  }

  @Test
  void createsTheAdminOnceWithTheSharedNormalisationAndEncoder() {
    try (ConfigurableApplicationContext first =
        start("prod", adminArgs("Ops-Admin", " Ops-Admin@Example.COM ", STRONG_PASSWORD))) {
      Map<String, Object> admin = admins(first).getFirst();
      assertThat(admin)
          .containsEntry("USERNAME", "ops-admin")
          .containsEntry("EMAIL", "ops-admin@example.com")
          .containsEntry("FIRST_NAME", "Admin")
          .containsEntry("ENABLED", true);
      String hash = (String) admin.get("PASSWORD_HASH");
      assertThat(hash).startsWith("$2a$12$");
      assertThat(first.getBean(PasswordEncoder.class).matches(STRONG_PASSWORD, hash)).isTrue();
    }
    assertThat(auditLines())
        .containsExactly(
            "event=ADMIN_BOOTSTRAPPED actor=system ip=system outcome=success target=ops-admin");

    // A restart, even with other admin properties, finds the admin and does nothing.
    try (ConfigurableApplicationContext restarted =
        start("prod", adminArgs("second-admin", "second@example.com", STRONG_PASSWORD))) {
      assertThat(admins(restarted))
          .singleElement()
          .satisfies(admin -> assertThat(admin).containsEntry("USERNAME", "ops-admin"));
    }
    assertThat(auditLines()).hasSize(1);
  }

  @Test
  void anExistingAdminNeedsNoProperties() {
    start("prod", adminArgs("ops-admin", "ops-admin@example.com", STRONG_PASSWORD)).close();

    try (ConfigurableApplicationContext restarted = start("prod")) {
      assertThat(admins(restarted)).hasSize(1);
    }
  }

  @Test
  void missingPropertiesStopStartup() {
    assertThatThrownBy(() -> start("prod", "--app.admin.username=ops-admin"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "No ADMIN account exists and the bootstrap admin cannot be created: set"
                + " app.admin.email (APP_ADMIN_EMAIL), app.admin.password (APP_ADMIN_PASSWORD)");
  }

  @Test
  void aWeakPasswordStopsStartupWithoutRevealingIt() {
    String weak = "Short-pw-9";

    assertThatThrownBy(() -> start("prod", adminArgs("ops-admin", "ops@example.com", weak)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "No ADMIN account exists and the bootstrap admin cannot be created:"
                + " app.admin.password does not meet the password policy:"
                + " Password must be 12 to 64 characters.")
        .message()
        .doesNotContain(weak);
  }

  @Test
  void aCommonPasswordStopsStartup() {
    assertThatThrownBy(
            () -> start("prod", adminArgs("ops-admin", "ops@example.com", "Unbelievable")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageEndingWith("This password is too common. Choose a less guessable one.");
  }

  @Test
  void aUsernameHeldByAnotherAccountStopsStartup() {
    // The test profile seeds johndoe as a USER.
    assertThatThrownBy(
            () -> start("test", adminArgs("JohnDoe", "ops@example.com", STRONG_PASSWORD)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "No ADMIN account exists and the bootstrap admin cannot be created: An account with"
                + " these details already exists (app.admin.username or app.admin.email is"
                + " already used by another account)");
  }

  private ConfigurableApplicationContext start(String profile, String... args) {
    String[] common = {
      "--server.port=0",
      // Command-line args, not builder properties, so they win over application.yml.
      "--spring.datasource.url=" + database + ";DB_CLOSE_DELAY=-1",
      "--app.cors.allowed-origins=https://app.example.com"
    };
    return new SpringApplicationBuilder(DemoAppApplication.class)
        .profiles(profile)
        .run(Stream.concat(Stream.of(common), Stream.of(args)).toArray(String[]::new));
  }

  private static String[] adminArgs(String username, String email, String password) {
    return new String[] {
      "--app.admin.username=" + username,
      "--app.admin.email=" + email,
      "--app.admin.password=" + password
    };
  }

  private static List<Map<String, Object>> admins(ConfigurableApplicationContext context) {
    return context
        .getBean(JdbcTemplate.class)
        .queryForList("SELECT * FROM user_account WHERE role = 'ADMIN'");
  }

  private List<String> auditLines() {
    return audit.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .filter(line -> line.startsWith("event=ADMIN_BOOTSTRAPPED"))
        .toList();
  }
}
