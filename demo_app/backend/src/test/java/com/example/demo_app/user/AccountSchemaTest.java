package com.example.demo_app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;

/**
 * The Flyway schema for accounts and reset tokens, checked with plain SQL against the migrated
 * test database. Rows created here use unique names, since the in-memory database is shared by
 * every test context in the JVM.
 */
@SpringBootTest
@ActiveProfiles("test")
class AccountSchemaTest {

  @Autowired private JdbcTemplate jdbc;

  @Test
  void demoSeedHasEmailRoleAndDefaultAccountState() {
    Map<String, Object> johndoe =
        jdbc.queryForMap("SELECT * FROM user_account WHERE username = 'johndoe'");

    assertThat(johndoe)
        .containsEntry("EMAIL", "johndoe@example.com")
        .containsEntry("ROLE", "USER")
        .containsEntry("ENABLED", true)
        .containsEntry("FAILED_LOGIN_ATTEMPTS", 0)
        .containsEntry("LOCKED_UNTIL", null);
    assertThat(johndoe.get("CREATED_AT")).isNotNull();
  }

  @Test
  void newRowsGetDefaultRoleEnabledCounterAndCreatedAt() {
    insertAccount("schema-defaults", "schema-defaults@example.com");

    Map<String, Object> row =
        jdbc.queryForMap("SELECT * FROM user_account WHERE username = 'schema-defaults'");

    assertThat(row)
        .containsEntry("ROLE", "USER")
        .containsEntry("ENABLED", true)
        .containsEntry("FAILED_LOGIN_ATTEMPTS", 0)
        .containsEntry("LOCKED_UNTIL", null);
    assertThat(row.get("CREATED_AT")).isNotNull();
  }

  @Test
  void emailIsUnique() {
    insertAccount("schema-email-a", "schema-dup@example.com");

    assertThatThrownBy(() -> insertAccount("schema-email-b", "schema-dup@example.com"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void roleIsRestrictedToUserAndAdmin() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO user_account (username, email, password_hash, first_name, role)"
                        + " VALUES ('schema-role', 'schema-role@example.com', 'x', 'R', 'ROOT')"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void deletingAnAccountCascadesToItsResetTokens() {
    long userId = insertAccount("schema-cascade", "schema-cascade@example.com");
    Timestamp expiry = Timestamp.from(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30).toInstant());
    jdbc.update(
        "INSERT INTO password_reset_token (user_id, token_hash, expires_at, created_at)"
            + " VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
        userId,
        "a".repeat(64),
        expiry);

    jdbc.update("DELETE FROM user_account WHERE id = ?", userId);

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM password_reset_token WHERE user_id = ?",
                Integer.class,
                userId))
        .isZero();
  }

  @Test
  void versionedMigrationsAvoidH2OnlySyntax() throws IOException {
    String v2 =
        new ClassPathResource("db/migration/V2__account_roles_lockout_and_reset_tokens.sql")
            .getContentAsString(StandardCharsets.UTF_8)
            .replaceAll("--[^\n]*", "")
            .toUpperCase(Locale.ROOT);

    assertThat(v2).doesNotContain("MERGE").doesNotContain("IF NOT EXISTS");
  }

  @Test
  void migrationLowercasesExistingUsernamesAndBackfillsTheDemoAccount() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource("jdbc:h2:mem:account-schema-upgrade;DB_CLOSE_DELAY=-1", "sa", "");
    JdbcTemplate upgrade = new JdbcTemplate(dataSource);
    Flyway.configure().dataSource(dataSource).target("1").load().migrate();
    upgrade.update(
        "INSERT INTO user_account (username, password_hash, first_name) VALUES ('JohnDoe', 'x', 'John')");

    Flyway.configure().dataSource(dataSource).load().migrate();

    Map<String, Object> row = upgrade.queryForMap("SELECT * FROM user_account");
    assertThat(row)
        .containsEntry("USERNAME", "johndoe")
        .containsEntry("EMAIL", "johndoe@example.com")
        .containsEntry("ROLE", "USER")
        .containsEntry("ENABLED", true)
        .containsEntry("FAILED_LOGIN_ATTEMPTS", 0);
    assertThat(row.get("CREATED_AT")).isNotNull();
  }

  private long insertAccount(String username, String email) {
    jdbc.update(
        "INSERT INTO user_account (username, email, password_hash, first_name) VALUES (?, ?, 'x', 'S')",
        username,
        email);
    return jdbc.queryForObject(
        "SELECT id FROM user_account WHERE username = ?", Long.class, username);
  }
}
