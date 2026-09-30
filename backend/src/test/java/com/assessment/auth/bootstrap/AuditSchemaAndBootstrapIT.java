package com.assessment.auth.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.assessment.auth.audit.AuditLogger;
import com.assessment.auth.audit.AuditReason;
import com.assessment.auth.support.AbstractIntegrationTest;
import com.assessment.auth.support.ApiClient;
import com.assessment.auth.user.Role;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import org.springframework.http.HttpStatus;

/**
 * Audit routing and content (story 1.22), the schema assertions Liquibase cannot make for itself
 * (story 1.2), and bootstrap idempotence (story 1.21).
 */
class AuditSchemaAndBootstrapIT extends AbstractIntegrationTest {

  private ListAppender<ILoggingEvent> auditAppender;
  private ListAppender<ILoggingEvent> rootAppender;
  private Logger auditLogger;
  private Logger rootLogger;

  @BeforeEach
  void attachAppenders() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    auditLogger = context.getLogger(AuditLogger.AUDIT_LOGGER_NAME);
    rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);

    auditAppender = new ListAppender<>();
    auditAppender.setContext(context);
    auditAppender.start();
    auditLogger.addAppender(auditAppender);

    // Attached to BOTH, so the test proves routing as well as emission: an audit event must reach
    // the audit stream and must NOT also land in the application stream (additivity=false).
    rootAppender = new ListAppender<>();
    rootAppender.setContext(context);
    rootAppender.start();
    rootLogger.addAppender(rootAppender);
  }

  @AfterEach
  void detachAppenders() {
    auditLogger.detachAppender(auditAppender);
    rootLogger.detachAppender(rootAppender);
  }

  private static Map<String, String> fieldsOf(ILoggingEvent event) {
    List<KeyValuePair> pairs = event.getKeyValuePairs();
    if (pairs == null) {
      return Map.of();
    }
    return pairs.stream()
        .collect(
            java.util.stream.Collectors.toMap(
                pair -> pair.key, pair -> String.valueOf(pair.value), (a, b) -> a));
  }

  private Optional<ILoggingEvent> auditEvent(String reason) {
    return auditAppender.list.stream()
        .filter(event -> reason.equals(fieldsOf(event).get("event.reason")))
        .findFirst();
  }

  // ------------------------------------------------------------------- routing

  @Test
  @DisplayName("audit events reach the audit logger and not the application logger")
  void auditEventsAreRoutedNotDuplicated() {
    ApiClient admin = adminSession();
    assertThat(admin.get("/currentUser").getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(auditAppender.list).as("the audit stream must receive events").isNotEmpty();
    assertThat(rootAppender.list)
        .as("additivity=false: audit lines must not also land on root")
        .noneMatch(event -> AuditLogger.AUDIT_LOGGER_NAME.equals(event.getLoggerName()));
  }

  // ------------------------------------------------------------------- content

  @Test
  @DisplayName("a login success records the authentication method as password")
  void loginSuccessRecordsPasswordMethod() {
    // spec.md S13, test 3. AuthN:112-118's resolver switches on the authentication CLASS NAME and
    // would yield "unknown" for PasswordAuthenticationToken, breaching Std:240. The audit site
    // sets it explicitly, and this is the test that keeps it honest.
    adminSession();
    Map<String, String> fields = fieldsOf(auditEvent("LOGIN_SUCCESS").orElseThrow());
    assertThat(fields.get("authentication.method")).isEqualTo("password");
    assertThat(fields.get("user.id")).isNotBlank();
    assertThat(fields.get("source.ip")).isNotBlank();
  }

  @Test
  @DisplayName("S13.5: an unknown username and a wrong password produce indistinguishable lines")
  void failedLoginsAreIndistinguishable() {
    new ApiClient(port).login("no-such-person", "wrong wrong wrong");
    Map<String, String> unknown = fieldsOf(auditEvent("LOGIN_FAILURE").orElseThrow());
    auditAppender.list.clear();

    new ApiClient(port).login(ADMIN_USERNAME, "wrong wrong wrong");
    Map<String, String> wrongPassword = fieldsOf(auditEvent("LOGIN_FAILURE").orElseThrow());

    // A correct HTTP response with a leaky log line passes every other test. Neither line may
    // carry a subject at all: only trace.id and source.ip.
    assertThat(unknown).doesNotContainKey("user.id").doesNotContainKey("target_user_id");
    assertThat(wrongPassword).doesNotContainKey("user.id").doesNotContainKey("target_user_id");
    assertThat(unknown.keySet()).isEqualTo(wrongPassword.keySet());
    assertThat(unknown.get("event.reason")).isEqualTo(wrongPassword.get("event.reason"));
    assertThat(unknown.get("event.outcome")).isEqualTo(wrongPassword.get("event.outcome"));
  }

  @Test
  @DisplayName("S13.2: lockout emits error.code 423 with the full triplet")
  void lockoutCarriesTheErrorTriplet() {
    // Guards the deliberate deviation from Encoder:408. Nothing throws on this path, so a recipe
    // -faithful encoder would strip these three fields and gut the audit trail with no failing
    // build.
    ApiClient client = new ApiClient(port);
    client.fetchCsrf();
    client.post(
        "/auth/register",
        "{\"username\":\"lockaudit\",\"email\":\"lockaudit@example.com\","
            + "\"password\":\"lantern quiet field\"}");
    for (int attempt = 1; attempt <= 5; attempt++) {
      new ApiClient(port).login("lockaudit", "wrong wrong wrong");
    }

    Map<String, String> fields = fieldsOf(auditEvent("ACCOUNT_LOCKED").orElseThrow());
    assertThat(fields.get("error.code")).isEqualTo("423");
    assertThat(fields.get("error.category")).isEqualTo("authentication");
    assertThat(fields.get("error.follow_up_action")).isNotBlank();
    assertThat(fields.get("target_user_id")).isNotBlank();
  }

  @Test
  @DisplayName("every account-lifecycle event names both the actor and the target, by id")
  void accountLifecycleEventsCarryActorAndTarget() {
    // prd:122 requires audit lines for "role change/enable/disable/delete (actor + target)" and this
    // was the gap: none of those events had an assertion of any kind. Emission is nearly free to
    // check, and an unasserted event is an event that can quietly stop firing -- the audit trail is
    // the only record that an administrator did any of this, so a silent regression here is
    // unrecoverable after the fact.
    //
    // Actor and target are checked SEPARATELY, because conflating them is the specific failure that
    // stops an audit trail answering "who did this": both fields are UUIDs and a mix-up type-checks.
    ApiClient admin = adminSession();
    UUID actorId = userRepository.findByUsername(ADMIN_USERNAME).orElseThrow().getId();

    admin.fetchCsrf();
    assertThat(
            admin
                .post(
                    "/users",
                    "{\"username\":\"lifecycle\",\"email\":\"lifecycle@example.com\","
                        + "\"password\":\"lantern quiet field\",\"role\":\"USER\"}")
                .getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    UUID targetId = userRepository.findByUsername("lifecycle").orElseThrow().getId();

    admin.fetchCsrf();
    admin.patch("/users/" + targetId + "/status", "{\"enabled\":false}");
    admin.fetchCsrf();
    admin.patch("/users/" + targetId + "/status", "{\"enabled\":true}");
    admin.fetchCsrf();
    admin.patch("/users/" + targetId + "/role", "{\"role\":\"USER_MANAGER\"}");
    admin.fetchCsrf();
    admin.patch("/users/" + targetId + "/unlock", null);
    admin.fetchCsrf();
    admin.patch("/users/" + targetId + "/resetPassword", null);
    admin.fetchCsrf();
    admin.delete("/users/" + targetId);

    for (String reason :
        List.of(
            "ACCOUNT_CREATED_BY_ADMIN",
            "ACCOUNT_DISABLED",
            "ACCOUNT_ENABLED",
            "ACCOUNT_ROLE_CHANGED",
            "ACCOUNT_UNLOCKED",
            "PASSWORD_RESET_ISSUED_BY_ADMIN",
            "ACCOUNT_DELETED")) {
      Map<String, String> fields =
          fieldsOf(
              auditEvent(reason)
                  .orElseThrow(() -> new AssertionError("no audit event emitted for " + reason)));
      assertThat(fields.get("user.id")).as("actor on %s", reason).isEqualTo(actorId.toString());
      assertThat(fields.get("target_user_id"))
          .as("target on %s", reason)
          .isEqualTo(targetId.toString());
      // The tombstone deliberately keeps the same id, so target_user_id stays resolvable after the
      // account row is gone -- which is the whole reason a delete event can name a target at all.
      assertThat(fields.get("event.reason")).isEqualTo(reason);
    }

    assertThat(deletedUserRepository.findById(targetId)).isPresent();
  }

  @Test
  @DisplayName("event.reason never takes a value outside the closed vocabulary")
  void everyReasonIsInTheEnum() {
    // The vocabulary is closed by contract (spec.md S11). A free-text reason would make the audit
    // trail unqueryable one careless string at a time, and nothing but this test closes it.
    ApiClient admin = adminSession();
    admin.get("/currentUser");
    new ApiClient(port).login("nobody-at-all", "wrong wrong wrong");

    Set<String> permitted =
        Arrays.stream(AuditReason.values()).map(Enum::name).collect(Collectors.toSet());

    assertThat(auditAppender.list).isNotEmpty();
    for (ILoggingEvent event : auditAppender.list) {
      assertThat(fieldsOf(event).get("event.reason"))
          .as("reason on %s", event.getMessage())
          .isIn(permitted);
    }
  }

  @Test
  @DisplayName("no audit line carries a username, an email, a password or a token")
  void noSecretsInTheAuditTrail() {
    ApiClient admin = adminSession();
    admin.fetchCsrf();
    admin.post(
        "/users",
        "{\"username\":\"secretive\",\"email\":\"secretive@example.com\","
            + "\"password\":\"lantern quiet field\",\"role\":\"USER\"}");
    String id = userRepository.findByUsername("secretive").orElseThrow().getId().toString();
    admin.fetchCsrf();
    String token =
        admin
            .patch("/users/" + id + "/resetPassword", null)
            .getBody()
            .replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");

    // Reading the audit trail requires database access to resolve UUIDs. That is deliberate.
    for (ILoggingEvent event : auditAppender.list) {
      String rendered = event.getFormattedMessage() + " " + fieldsOf(event);
      assertThat(rendered)
          .doesNotContain("secretive")
          .doesNotContain("secretive@example.com")
          .doesNotContain("lantern quiet field")
          .doesNotContain(token)
          .doesNotContain(com.assessment.auth.password.PasswordResetService.sha256(token));
    }
  }

  @Test
  @DisplayName("a reset request logs the same event whether or not the email is registered")
  void resetRequestAuditIsIdentical() {
    ApiClient known = new ApiClient(port);
    known.fetchCsrf();
    known.post(
        "/auth/password-reset/request",
        "{\"email\":\"" + ADMIN_USERNAME + "@example.com\"}");
    Map<String, String> registered = fieldsOf(auditEvent("PASSWORD_RESET_REQUESTED").orElseThrow());
    auditAppender.list.clear();

    ApiClient unknown = new ApiClient(port);
    unknown.fetchCsrf();
    unknown.post("/auth/password-reset/request", "{\"email\":\"nobody-at-all@example.com\"}");
    Map<String, String> unregistered =
        fieldsOf(auditEvent("PASSWORD_RESET_REQUESTED").orElseThrow());

    assertThat(registered).isEqualTo(unregistered);
  }

  // ------------------------------------------------------------------- MDC hygiene

  @Test
  @DisplayName("MDC is clear after a request, including on a failure path")
  void mdcIsClearedOnEveryExitPath() {
    adminSession();
    new ApiClient(port).login(ADMIN_USERNAME, "wrong wrong wrong");
    new ApiClient(port).get("/hello");
    // The filter removes only the key it owns, in a finally block. MDC.clear() is banned outright
    // because it would strip Micrometer's traceId/spanId.
    assertThat(org.slf4j.MDC.get("user.id")).isNull();
  }

  // ------------------------------------------------------------------- schema (story 1.2)

  @Test
  @DisplayName("the SPRING_SESSION PRINCIPAL_NAME index exists")
  void principalNameIndexExists() {
    // ddl-auto: validate CANNOT catch this -- the Spring Session tables have no JPA entity. The
    // index is load-bearing for "invalidate all sessions", which is a keyed lookup by principal.
    //
    // Read through JDBC metadata rather than information_schema: H2 exposes
    // INFORMATION_SCHEMA.INDEXES and PostgreSQL does not, and this assertion has to survive the
    // Testcontainers re-run unchanged.
    List<String> indexedColumns =
        jdbcTemplate.execute(
            (java.sql.Connection connection) -> {
              List<String> columns = new java.util.ArrayList<>();
              for (String table : List.of("SPRING_SESSION", "spring_session")) {
                try (java.sql.ResultSet rs =
                    connection.getMetaData().getIndexInfo(null, null, table, false, false)) {
                  while (rs.next()) {
                    String column = rs.getString("COLUMN_NAME");
                    if (column != null) {
                      columns.add(column.toUpperCase(java.util.Locale.ROOT));
                    }
                  }
                }
              }
              return columns;
            });

    assertThat(indexedColumns)
        .as("an index on SPRING_SESSION.PRINCIPAL_NAME must exist")
        .contains("PRINCIPAL_NAME");
  }

  @Test
  @DisplayName("every Liquibase-owned table exists and users has no account_non_locked column")
  void schemaShape() {
    for (String table :
        List.of(
            "users",
            "roles",
            "password_history",
            "deleted_users",
            "password_reset_tokens",
            "spring_session",
            "spring_session_attributes")) {
      // Scoped to the application schema: H2 ships its own INFORMATION_SCHEMA.USERS, so an
      // unscoped count on `users` finds two tables and the assertion means nothing.
      Integer found =
          jdbcTemplate.queryForObject(
              "select count(*) from information_schema.tables "
                  + "where upper(table_name) = upper(?) and upper(table_schema) = 'PUBLIC'",
              Integer.class,
              table);
      assertThat(found).as("table %s", table).isEqualTo(1);
    }

    Integer bogus =
        jdbcTemplate.queryForObject(
            "select count(*) from information_schema.columns "
                + "where upper(table_name) = 'USERS' and upper(table_schema) = 'PUBLIC' "
                + "and upper(column_name) = 'ACCOUNT_NON_LOCKED'",
            Integer.class);
    // Two sources of truth for "is this account locked" is a silent auth bypass (ticket 07).
    assertThat(bogus).as("there must be no account_non_locked column").isZero();
  }

  // ------------------------------------------------------------------- bootstrap (story 1.21)

  @Test
  @DisplayName("the bootstrap seeded exactly one administrator, flagged for a forced change")
  void bootstrapSeededOnce() {
    List<com.assessment.auth.user.User> managers =
        userRepository.findAll().stream()
            .filter(user -> Role.USER_MANAGER.equals(user.getRole()))
            .toList();
    assertThat(managers).hasSize(1);
    assertThat(managers.getFirst().getUsername()).isEqualTo(ADMIN_USERNAME);
    // resetAdmin() restores the flag, so this asserts the shape the seed produces.
    assertThat(managers.getFirst().isRequirePasswordChange()).isTrue();
    // Hashed like any other account, with its first history row written.
    // The COST FACTOR, not just the algorithm prefix. `startsWith("$2")` passes at BCrypt's default
    // strength of 10 as readily as at the configured 12, so it proves the algorithm and nothing about
    // the work factor -- and the work factor is the entire security property here. Every other
    // account's hash is produced by the same encoder bean, so pinning it once pins it everywhere.
    assertThat(managers.getFirst().getPasswordHash()).startsWith("$2a$12$");
    assertThat(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(managers.getFirst().getId()))
        .isNotEmpty();
  }
}
