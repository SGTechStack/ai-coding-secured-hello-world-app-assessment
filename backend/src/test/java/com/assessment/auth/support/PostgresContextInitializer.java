package com.assessment.auth.support;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Switches the whole suite onto Testcontainers PostgreSQL when the build passes {@code -Ddb=postgres}
 * (spec.md S12, ticket 02).
 *
 * <p>The {@code postgres-integration-test} failsafe execution sets that property, so <strong>the
 * entire security-critical suite runs twice</strong> — once on H2 and once on PostgreSQL — rather
 * than H2 plus a smaller portability subset. Subsetting means choosing which portability bugs to
 * not find.
 *
 * <p>The container is {@code static} and never stopped: one container is shared by every test class
 * in the JVM, and Testcontainers' Ryuk sidecar removes it when the JVM exits. Starting one per class
 * would multiply a ~2s startup across the suite for no isolation benefit, since each test already
 * uses unique identifiers.
 */
public class PostgresContextInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final String IMAGE = "postgres:17-alpine";

  private static PostgreSQLContainer<?> container;

  /** True when the build asked for PostgreSQL. Absent property means the default H2 run. */
  public static boolean postgresRequested() {
    return "postgres".equalsIgnoreCase(System.getProperty("db", ""));
  }

  @Override
  public void initialize(ConfigurableApplicationContext context) {
    if (!postgresRequested()) {
      return;
    }
    TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
        context,
        "spring.datasource.url=" + start().getJdbcUrl(),
        "spring.datasource.username=" + start().getUsername(),
        "spring.datasource.password=" + start().getPassword(),
        "spring.datasource.driver-class-name=org.postgresql.Driver");
  }

  private static synchronized PostgreSQLContainer<?> start() {
    if (container == null) {
      container =
          new PostgreSQLContainer<>(IMAGE)
              .withDatabaseName("securedlogin")
              .withUsername("securedlogin")
              .withPassword("securedlogin");
      container.start();
    }
    return container;
  }
}
