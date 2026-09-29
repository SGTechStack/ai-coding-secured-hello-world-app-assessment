package sg.securedhello.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;

/**
 * The database-invariant half of the schema gate: constraints {@code ddl-auto: validate} cannot see (ADR-051). Every
 * write and read is its own auto-committed JDBC statement, outside any persistence context.
 */
class SchemaInvariantsTest extends CtxDefaultTest {

    /** Every NOT NULL column of V1-V6, by table. Adding or dropping one must be a deliberate edit here. */
    private static final Map<String, Set<String>> NOT_NULL = Map.of(
            "ROLES", Set.of("NAME"),
            "USERS", Set.of("ID", "USERNAME", "EMAIL", "ROLE", "ENABLED", "FAILED_LOGIN_ATTEMPTS",
                    "CONSECUTIVE_FAILURES_SINCE_SUCCESS", "FORCE_PASSWORD_CHANGE", "CREATED_AT"),
            "CREDENTIAL_TOKENS", Set.of("ID", "USER_ID", "TYPE", "TOKEN_HASH", "EXPIRES_AT", "CREATED_AT"),
            "PASSWORD_HISTORY", Set.of("ID", "USER_ID", "PASSWORD_HASH", "CREATED_AT"),
            "TOTP_USER_DETAILS", Set.of("USER_ID", "TOTP_KEY", "KEY_VERSION", "FAILED_ATTEMPTS",
                    "CUMULATIVE_FAILURES", "CREATED_AT"),
            "PENDING_TOTP", Set.of("USER_ID", "TOTP_KEY", "KEY_VERSION", "CREATED_AT"),
            "DELETED_USERS", Set.of("USER_ID", "USERNAME", "EMAIL_HMAC", "DELETED_AT", "DELETED_BY_ID"),
            "USERNAME_HOLDS", Set.of("ID", "USERNAME", "EMAIL", "EXPIRES_AT"));

    /** The primary-key column each table's fixture row is addressed by. */
    private static final Map<String, String> KEY = Map.of(
            "ROLES", "NAME", "USERS", "ID", "CREDENTIAL_TOKENS", "ID", "PASSWORD_HISTORY", "ID",
            "TOTP_USER_DETAILS", "USER_ID", "PENDING_TOTP", "USER_ID", "DELETED_USERS", "USER_ID",
            "USERNAME_HOLDS", "ID");

    /** The foreign keys of V1-V6 as "child.column -> parent.column delete-rule". */
    private static final Set<String> FOREIGN_KEYS = Set.of(
            "USERS.ROLE -> ROLES.NAME NO ACTION",
            "CREDENTIAL_TOKENS.USER_ID -> USERS.ID CASCADE",
            "PASSWORD_HISTORY.USER_ID -> USERS.ID CASCADE",
            "TOTP_USER_DETAILS.USER_ID -> USERS.ID CASCADE",
            "PENDING_TOTP.USER_ID -> USERS.ID CASCADE");

    @Autowired
    JdbcTemplate jdbc;

    SchemaFixture fixture() {
        return new SchemaFixture(jdbc);
    }

    @Test
    @Proves("T-CFG-007")
    void everyNotNullColumnRejectsNull() {
        Map<String, Set<String>> declared = jdbc.query(
                "SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS"
                        + " WHERE TABLE_SCHEMA = 'PUBLIC' AND IS_NULLABLE = 'NO' AND TABLE_NAME IN (?, ?, ?, ?, ?, ?, ?, ?)",
                (row, i) -> List.of(row.getString(1), row.getString(2)), NOT_NULL.keySet().toArray())
                .stream().collect(Collectors.groupingBy(List::getFirst,
                        Collectors.mapping(List::getLast, Collectors.toSet())));
        assertThat(declared).isEqualTo(NOT_NULL);

        SchemaFixture fixture = fixture();
        UUID userId = fixture.user();
        fixture.totp("totp_user_details", userId, SchemaFixture.bytes(SchemaFixture.TOTP_KEY_BYTES), 1);
        fixture.totp("pending_totp", userId, SchemaFixture.bytes(SchemaFixture.TOTP_KEY_BYTES), 1);
        Map<String, Object> rows = Map.of(
                "ROLES", "USER", "USERS", userId, "CREDENTIAL_TOKENS", fixture.token(userId),
                "PASSWORD_HISTORY", fixture.history(userId), "TOTP_USER_DETAILS", userId, "PENDING_TOTP", userId,
                "DELETED_USERS", fixture.tombstone(), "USERNAME_HOLDS", fixture.usernameHold());

        NOT_NULL.forEach((table, columns) -> columns.forEach(column -> assertThatThrownBy(() -> jdbc.update(
                "UPDATE " + table + " SET " + column + " = NULL WHERE " + KEY.get(table) + " = ?", rows.get(table)))
                .as("%s.%s = NULL", table, column)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("NULL not allowed for column \"" + column + "\"")));
    }

    @Test
    @Proves("T-CFG-008")
    void everyForeignKeyRejectsAMissingParent() {
        assertThat(foreignKeys()).isEqualTo(FOREIGN_KEYS);

        SchemaFixture fixture = fixture();
        UUID ghost = UUID.randomUUID();
        UUID userId = fixture.user();

        assertThatThrownBy(() -> jdbc.update("UPDATE users SET role = 'SUPERUSER' WHERE id = ?", userId))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("FK_USERS_ROLE");
        assertThatThrownBy(() -> fixture.token(ghost))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("FK_CREDENTIAL_TOKENS_USER_ID");
        assertThatThrownBy(() -> fixture.history(ghost))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("FK_PASSWORD_HISTORY_USER_ID");
        for (String table : List.of("totp_user_details", "pending_totp")) {
            assertThatThrownBy(() -> fixture.totp(table, ghost, SchemaFixture.bytes(SchemaFixture.TOTP_KEY_BYTES), 1))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("FK_" + table.toUpperCase() + "_USER_ID");
        }
    }

    @Test
    @Proves("T-ADM-021")
    void deletingAUserCascadesToItsRowsButNotToSessionsOrTombstones() {
        SchemaFixture fixture = fixture();
        UUID userId = fixture.user();
        UUID tokenId = fixture.token(userId);
        UUID historyId = fixture.history(userId);
        fixture.totp("totp_user_details", userId, SchemaFixture.bytes(SchemaFixture.TOTP_KEY_BYTES), 1);
        fixture.totp("pending_totp", userId, SchemaFixture.bytes(SchemaFixture.TOTP_KEY_BYTES), 1);
        String sessionId = fixture.session(fixture.username(userId));

        assertThat(jdbc.update("DELETE FROM users WHERE id = ?", userId)).isEqualTo(1);

        assertThat(fixture.count("credential_tokens", "id", tokenId)).isZero();
        assertThat(fixture.count("password_history", "id", historyId)).isZero();
        assertThat(fixture.count("totp_user_details", "user_id", userId)).isZero();
        assertThat(fixture.count("pending_totp", "user_id", userId)).isZero();
        // Sessions end through SessionTerminationService after commit (ADR-039), never by cascade.
        assertThat(fixture.count("SPRING_SESSION", "PRIMARY_ID", sessionId)).isOne();
        // The tombstone must outlive both the deleted row and the deleting admin (ADR-044; REJ-034).
        assertThat(foreignKeys()).noneMatch(fk -> fk.startsWith("DELETED_USERS."));
    }

    @Test
    @Proves("T-CRED-020")
    void tokenTypeIsLimitedToActivationAndPasswordReset() {
        SchemaFixture fixture = fixture();
        UUID tokenId = fixture.token(fixture.user());

        jdbc.update("UPDATE credential_tokens SET type = 'ACTIVATION' WHERE id = ?", tokenId);
        assertThatThrownBy(() -> jdbc.update("UPDATE credential_tokens SET type = 'INVITE' WHERE id = ?", tokenId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CK_CREDENTIAL_TOKENS_TYPE");
    }

    @Test
    @Proves("T-LCK-018")
    void capColumnsDefaultAndRoundTripAtMicrosecondPrecision() {
        UUID userId = fixture().user();
        assertThat(jdbc.queryForObject("SELECT consecutive_failures_since_success FROM users WHERE id = ?",
                Integer.class, userId)).isZero();
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE users SET consecutive_failures_since_success = NULL WHERE id = ?", userId))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("SELECT password_disabled_at FROM users WHERE id = ?",
                OffsetDateTime.class, userId)).isNull();
        OffsetDateTime disabledAt = OffsetDateTime.of(2026, 3, 1, 12, 0, 0, 123_456_000, ZoneOffset.UTC);
        jdbc.update("UPDATE users SET password_disabled_at = ? WHERE id = ?", disabledAt, userId);
        assertThat(jdbc.queryForObject("SELECT password_disabled_at FROM users WHERE id = ?",
                OffsetDateTime.class, userId).toInstant()).isEqualTo(disabledAt.toInstant());
        assertThat(jdbc.queryForObject("SELECT DATA_TYPE || '(' || DATETIME_PRECISION || ')' FROM"
                + " INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'USERS' AND COLUMN_NAME = 'PASSWORD_DISABLED_AT'",
                String.class)).isEqualTo("TIMESTAMP WITH TIME ZONE(6)");
    }

    private Set<String> foreignKeys() {
        return new TreeSet<>(jdbc.query("""
                SELECT fk.TABLE_NAME, fk.COLUMN_NAME, pk.TABLE_NAME, pk.COLUMN_NAME, rc.DELETE_RULE
                FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS rc
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE fk
                  ON fk.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA AND fk.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE pk
                  ON pk.CONSTRAINT_SCHEMA = rc.UNIQUE_CONSTRAINT_SCHEMA
                 AND pk.CONSTRAINT_NAME = rc.UNIQUE_CONSTRAINT_NAME
                 AND pk.ORDINAL_POSITION = fk.POSITION_IN_UNIQUE_CONSTRAINT
                WHERE fk.TABLE_SCHEMA = 'PUBLIC' AND fk.TABLE_NAME NOT LIKE 'SPRING_SESSION%'
                """, (row, i) -> "%s.%s -> %s.%s %s".formatted(row.getString(1), row.getString(2), row.getString(3),
                row.getString(4), row.getString(5))));
    }
}
