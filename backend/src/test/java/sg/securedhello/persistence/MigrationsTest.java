package sg.securedhello.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Map;
import java.util.stream.Collectors;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import sg.securedhello.testsupport.CtxDefaultTest;

/** The seven migrations on a fresh H2 file, their naming, and the guard against wiping them (REJ-041). */
class MigrationsTest extends CtxDefaultTest {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aFreshFileMigratesV1ToV9InSpecOrder() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).extracting(MigrationInfo::getState).containsOnly(MigrationState.SUCCESS);
        assertThat(applied).extracting(MigrationInfo::getScript).containsExactly(
                "V1__roles.sql",
                "V2__users.sql",
                "V3__credential_tokens.sql",
                "V4__password_history.sql",
                "V5__totp.sql",
                "V6__deleted_users.sql",
                "V7__spring_session.sql",
                "V8__username_holds.sql",
                "V9__admin_issued_tokens.sql");
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void rolesAreSeededWithExactlyUserAndAdmin() {
        assertThat(jdbc.queryForList("SELECT name FROM roles", String.class)).containsExactlyInAnyOrder("USER", "ADMIN");
    }

    @Test
    void everyApplicationIndexIsNamedUxOrIx() {
        // Declared indexes only: H2 generates its own for primary and foreign keys, and Spring Session's are upstream's.
        Map<String, String> typeByIndex = jdbc.queryForList("""
                SELECT INDEX_NAME, INDEX_TYPE_NAME FROM INFORMATION_SCHEMA.INDEXES
                WHERE TABLE_SCHEMA = 'PUBLIC' AND IS_GENERATED = FALSE
                  AND TABLE_NAME NOT LIKE 'SPRING_SESSION%' AND TABLE_NAME <> 'flyway_schema_history'
                """).stream().collect(Collectors.toMap(
                        row -> (String) row.get("INDEX_NAME"), row -> (String) row.get("INDEX_TYPE_NAME")));

        assertThat(typeByIndex).containsOnlyKeys("UX_USERS_USERNAME", "UX_USERS_EMAIL",
                "UX_CREDENTIAL_TOKENS_TOKEN_HASH", "IX_CREDENTIAL_TOKENS_USER_ID_TYPE",
                "IX_PASSWORD_HISTORY_USER_ID_CREATED_AT", "UX_DELETED_USERS_USERNAME", "UX_DELETED_USERS_EMAIL_HMAC",
                "UX_USERNAME_HOLDS_USERNAME", "IX_USERNAME_HOLDS_EXPIRES_AT");
        typeByIndex.forEach((name, type) -> assertThat(name).as(type)
                .startsWith(type.startsWith("UNIQUE") ? "UX_" : "IX_"));
    }

    @Test
    void flywayRefusesToClean() {
        assertThat(flyway.getConfiguration().isCleanDisabled()).isTrue();
        assertThatExceptionOfType(FlywayException.class).isThrownBy(flyway::clean);
        assertThat(flyway.info().applied()).hasSize(9);
    }
}
