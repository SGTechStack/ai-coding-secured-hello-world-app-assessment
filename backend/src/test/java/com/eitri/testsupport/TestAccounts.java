package com.eitri.testsupport;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Test fixtures for the {@code users} table, written and read with plain JDBC. */
public final class TestAccounts {

    public static final UUID JOHNDOE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final String JOHNDOE_PASSWORD = "Password123!";

    // Low cost keeps fixtures fast; the application still verifies them like any other BCrypt hash.
    private static final PasswordEncoder FIXTURE_ENCODER = new BCryptPasswordEncoder(4);

    private final JdbcTemplate jdbc;

    public TestAccounts(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static String bcrypt(String password) {
        return "{bcrypt}" + FIXTURE_ENCODER.encode(password);
    }

    public static boolean bcryptMatches(String password, String storedHash) {
        return storedHash.startsWith("{bcrypt}")
                && new BCryptPasswordEncoder().matches(password, storedHash.substring("{bcrypt}".length()));
    }

    /** Inserts an enabled account, or replaces one with the same username, and returns its id. */
    public UUID create(String username, String email, String password, String role) {
        delete(username);
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO users (id, username, email, password_hash, role, enabled, failed_login_attempts, "
                        + "locked_until, created_at) VALUES (?, ?, ?, ?, ?, TRUE, 0, NULL, ?)",
                id,
                username,
                email,
                bcrypt(password),
                role,
                Timestamp.from(Instant.now()));
        return id;
    }

    public void delete(String username) {
        jdbc.update("DELETE FROM users WHERE username = ?", username);
    }

    public static final String ADMIN_PASSWORD = "test-only-admin-password";

    /**
     * Back to the seeded state: only the bootstrapped {@code admin} and {@code johndoe} (re-created with
     * its fixed id if a test deleted it), both enabled, unlocked and with their original roles and
     * passwords. Also clears sessions and reset tokens.
     */
    public void restoreSeed() {
        jdbc.update("DELETE FROM SPRING_SESSION");
        jdbc.update("DELETE FROM password_reset_tokens");
        jdbc.update("DELETE FROM users WHERE username NOT IN ('admin', 'johndoe')");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, JOHNDOE_ID) == 0) {
            jdbc.update(
                    "INSERT INTO users (id, username, email, password_hash, role, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    JOHNDOE_ID,
                    "johndoe",
                    "john@example.com",
                    bcrypt(JOHNDOE_PASSWORD),
                    "USER",
                    Timestamp.from(Instant.now()));
        }
        resetJohndoe();
        jdbc.update(
                "UPDATE users SET enabled = TRUE, failed_login_attempts = 0, locked_until = NULL, "
                        + "failed_login_window_started_at = NULL, role = 'ADMIN', password_hash = ? "
                        + "WHERE username = 'admin'",
                bcrypt(ADMIN_PASSWORD));
    }

    /** Restores the seeded johndoe account to its initial enabled, unlocked USER state and password. */
    public void resetJohndoe() {
        jdbc.update(
                "UPDATE users SET enabled = TRUE, failed_login_attempts = 0, locked_until = NULL, "
                        + "failed_login_window_started_at = NULL, role = 'USER', password_hash = ? WHERE id = ?",
                bcrypt(JOHNDOE_PASSWORD),
                JOHNDOE_ID);
    }

    public UUID idOf(String username) {
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", UUID.class, username);
    }

    public Map<String, Object> row(String username) {
        return jdbc.queryForMap("SELECT * FROM users WHERE username = ?", username);
    }

    public int count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
    }

    public int failedLoginAttempts(String username) {
        return jdbc.queryForObject(
                "SELECT failed_login_attempts FROM users WHERE username = ?", Integer.class, username);
    }

    public Instant lockedUntil(String username) {
        Timestamp value = jdbc.queryForObject(
                "SELECT locked_until FROM users WHERE username = ?", Timestamp.class, username);
        return value == null ? null : value.toInstant();
    }

    public void setState(String username, boolean enabled, int failedLoginAttempts, Instant lockedUntil) {
        jdbc.update(
                "UPDATE users SET enabled = ?, failed_login_attempts = ?, locked_until = ? WHERE username = ?",
                enabled,
                failedLoginAttempts,
                lockedUntil == null ? null : Timestamp.from(lockedUntil),
                username);
    }
}
