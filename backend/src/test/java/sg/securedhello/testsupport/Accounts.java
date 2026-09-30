package sg.securedhello.testsupport;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.user.PasswordLockoutState;

/**
 * Creates accounts directly in the {@code users} table, for tests that need someone to sign in before registration
 * exists. Each call makes a fresh username (keyed isolation), so tests in a shared context never collide.
 */
public final class Accounts {

    /** The password every fixture account gets unless a test says otherwise; 15+ characters, scanned for in output. */
    public static final String PASSWORD = "fixture-password-correct-horse";

    /** The wrong password the sign-in tests submit; scanned for in output like {@link #PASSWORD}. */
    public static final String WRONG_PASSWORD = "not-the-password-at-all";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public Accounts(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    /** A signed-up, activated and enabled account. */
    public record Account(UUID id, String username, String password) {
    }

    /** An active {@code USER} account with {@link #PASSWORD}. */
    public Account user() {
        return create("USER", true, true, PASSWORD);
    }

    /** An active account with {@code role}. */
    public Account withRole(String role) {
        return create(role, true, true, PASSWORD);
    }

    /** An activated account an administrator disabled. */
    public Account disabled() {
        return create("USER", false, true, PASSWORD);
    }

    /** An account that has never been activated, so it has no password yet. */
    public Account notActivated() {
        return create("USER", true, false, null);
    }

    /** An active {@code USER} account with a chosen username and password, for fixtures outside the test suite. */
    public Account named(String username, String password) {
        return create(UUID.randomUUID(), username, "USER", true, true, password);
    }

    /** The account's password-lockout columns, read straight from {@code users}. */
    public PasswordLockoutState lockoutState(Account account) {
        return jdbc.queryForObject("SELECT failed_login_attempts, last_failed_at, locked_until,"
                + " consecutive_failures_since_success, password_disabled_at FROM users WHERE id = ?",
                (rs, row) -> new PasswordLockoutState(rs.getInt(1), instant(rs.getTimestamp(2)),
                        instant(rs.getTimestamp(3)), rs.getInt(4), instant(rs.getTimestamp(5))), account.id());
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    /** A username no account has. */
    public static String unknownUsername() {
        return FixtureIdentities.username("nobody-", 15);
    }

    private Account create(String role, boolean enabled, boolean activated, String password) {
        UUID id = UUID.randomUUID();
        return create(id, FixtureIdentities.username("u", 17), role, enabled, activated, password);
    }

    private Account create(UUID id, String username, String role, boolean enabled, boolean activated,
            String password) {
        Timestamp now = Timestamp.from(Instant.now(TestClock.shared()));
        jdbc.update("INSERT INTO users (id, username, email, password_hash, role, enabled, activated_at, created_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, username, FixtureIdentities.email(username),
                password == null ? null : encoder.encode(password), role, enabled, activated ? now : null, now);
        return new Account(id, username, password);
    }
}
