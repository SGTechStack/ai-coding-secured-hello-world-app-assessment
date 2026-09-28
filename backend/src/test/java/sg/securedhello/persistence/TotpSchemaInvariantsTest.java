package sg.securedhello.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;

/**
 * The TOTP tables' named checks, which {@code ddl-auto: validate} cannot see (ADR-028; ADR-051; REJ-033). Each insert
 * is its own auto-committed statement, so each assertion runs in a fresh transaction.
 */
class TotpSchemaInvariantsTest extends CtxDefaultTest {

    @Autowired
    JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(strings = {"totp_user_details", "pending_totp"})
    @Proves("T-MFA-003")
    void totpKeyMustBeExactly69Bytes(String table) {
        String check = ("ck_" + table + "_totp_key_len").toUpperCase();
        // The check, not the column width, is what holds on PostgreSQL's bytea (R-DATA-006), so pin its clause.
        assertThat(jdbc.queryForObject("SELECT CHECK_CLAUSE FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS"
                + " WHERE CONSTRAINT_NAME = ?", String.class, check)).isEqualTo("OCTET_LENGTH(\"TOTP_KEY\") = 69");

        SchemaFixture fixture = new SchemaFixture(jdbc);
        UUID shortKeyUser = fixture.user();
        assertThatThrownBy(() -> fixture.totp(table, shortKeyUser, SchemaFixture.bytes(48), 1))
                .as("48-byte totp_key")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(check);
        // On H2 the VARBINARY(69) width rejects an 81-byte value before the check runs.
        UUID longKeyUser = fixture.user();
        assertThatThrownBy(() -> fixture.totp(table, longKeyUser, SchemaFixture.bytes(81), 1))
                .as("81-byte totp_key")
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID userId = fixture.user();
        byte[] envelope = SchemaFixture.bytes(SchemaFixture.TOTP_KEY_BYTES);
        fixture.totp(table, userId, envelope, 1);
        assertThat(jdbc.queryForObject("SELECT totp_key FROM " + table + " WHERE user_id = ?", byte[].class, userId))
                .isEqualTo(envelope);
    }

    @ParameterizedTest
    @ValueSource(strings = {"totp_user_details", "pending_totp"})
    @Proves("T-MFA-004")
    void keyVersionMustFitInOneByte(String table) {
        SchemaFixture fixture = new SchemaFixture(jdbc);
        for (int version : new int[] {-1, 256}) {
            UUID userId = fixture.user();
            assertThatThrownBy(() -> fixture.totp(table, userId, SchemaFixture.bytes(SchemaFixture.TOTP_KEY_BYTES),
                    version))
                    .as("key_version %d", version)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining(("ck_" + table + "_key_version_range").toUpperCase());
        }
        for (int version : new int[] {0, 255}) {
            UUID userId = fixture.user();
            fixture.totp(table, userId, SchemaFixture.bytes(SchemaFixture.TOTP_KEY_BYTES), version);
            assertThat(fixture.count(table, "user_id", userId)).as("key_version %d", version).isOne();
        }
    }
}
