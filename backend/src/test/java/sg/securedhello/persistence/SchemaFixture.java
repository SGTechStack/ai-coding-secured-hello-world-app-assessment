package sg.securedhello.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.jdbc.core.JdbcTemplate;

import sg.securedhello.testsupport.TestClock;

/**
 * Writes schema fixture rows with plain JDBC, one auto-committed statement each, so every assertion runs outside any
 * persistence context (ADR-051). Identifiers are fresh per call ({@code keyed} isolation).
 */
final class SchemaFixture {

    /** The width of the stored TOTP envelope (ADR-028). */
    static final int TOTP_KEY_BYTES = 69;

    private final JdbcTemplate jdbc;

    SchemaFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static OffsetDateTime now() {
        return TestClock.shared().instant().atOffset(ZoneOffset.UTC);
    }

    static byte[] bytes(int length) {
        byte[] bytes = new byte[length];
        ThreadLocalRandom.current().nextBytes(bytes);
        return bytes;
    }

    static String hex64() {
        return HexFormat.of().formatHex(bytes(32));
    }

    UUID user() {
        UUID id = UUID.randomUUID();
        String handle = "u" + id.toString().substring(0, 8);
        jdbc.update("INSERT INTO users (id, username, email, role, enabled, created_at) VALUES (?, ?, ?, 'USER', TRUE, ?)",
                id, handle, handle + "@example.test", now());
        return id;
    }

    UUID token(UUID userId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO credential_tokens (id, user_id, type, token_hash, expires_at, created_at)"
                + " VALUES (?, ?, 'PASSWORD_RESET', ?, ?, ?)", id, userId, hex64(), now().plusMinutes(30), now());
        return id;
    }

    UUID history(UUID userId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO password_history (id, user_id, password_hash, created_at) VALUES (?, ?, ?, ?)",
                id, userId, "{bcrypt}$2a$04$fixture", now());
        return id;
    }

    /** Inserts into {@code totp_user_details} or {@code pending_totp}, which share these columns. */
    void totp(String table, UUID userId, byte[] totpKey, int keyVersion) {
        jdbc.update("INSERT INTO " + table + " (user_id, totp_key, key_version, created_at) VALUES (?, ?, ?, ?)",
                userId, totpKey, keyVersion, now());
    }

    UUID tombstone() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO deleted_users (user_id, username, email_hmac, deleted_at, deleted_by_id)"
                + " VALUES (?, ?, ?, ?, ?)", id, "d" + id.toString().substring(0, 8), hex64(), now(), UUID.randomUUID());
        return id;
    }

    UUID usernameHold() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO username_holds (id, username, email, expires_at) VALUES (?, ?, ?, ?)",
                id, "h" + id.toString().substring(0, 8), "h" + id.toString().substring(0, 8) + "@example.test", now());
        return id;
    }

    /** A Spring Session row owned by {@code principal}; returns its primary id. */
    String session(String principal) {
        String primaryId = UUID.randomUUID().toString();
        long millis = TestClock.shared().millis();
        jdbc.update("INSERT INTO SPRING_SESSION (PRIMARY_ID, SESSION_ID, CREATION_TIME, LAST_ACCESS_TIME,"
                + " MAX_INACTIVE_INTERVAL, EXPIRY_TIME, PRINCIPAL_NAME) VALUES (?, ?, ?, ?, 1800, ?, ?)",
                primaryId, UUID.randomUUID().toString(), millis, millis, millis + 1_800_000, principal);
        return primaryId;
    }

    String username(UUID userId) {
        return jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, userId);
    }

    int count(String table, String column, Object value) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class, value);
        return count == null ? 0 : count;
    }
}
