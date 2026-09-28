package sg.securedhello.testsupport;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;

/** Reads the Spring Session tables directly, for tests that count or age session rows. */
public final class SessionRows {

    private final JdbcTemplate jdbc;

    public SessionRows(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Every {@code SESSION_ID} currently stored. */
    public Set<String> ids() {
        return new HashSet<>(jdbc.queryForList("SELECT SESSION_ID FROM SPRING_SESSION", String.class));
    }

    /** The session row for {@code sessionId}: {@code CREATION_TIME}, {@code EXPIRY_TIME} and the rest. */
    public Map<String, Object> row(String sessionId) {
        return jdbc.queryForMap("SELECT * FROM SPRING_SESSION WHERE SESSION_ID = ?", sessionId);
    }

    /** Whether a row for {@code sessionId} exists. */
    public boolean exists(String sessionId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION WHERE SESSION_ID = ?",
                Integer.class, sessionId);
        return count != null && count > 0;
    }

    /**
     * Moves the session's creation time back by {@code millis}, leaving its last access and expiry alone, so the
     * repository still finds it live. Spring Session stamps its times from its own clock, not the shared one, so a
     * test ages the stored data instead of advancing the clock (ADR-066).
     */
    public void ageCreation(String sessionId, long millis) {
        jdbc.update("UPDATE SPRING_SESSION SET CREATION_TIME = CREATION_TIME - ? WHERE SESSION_ID = ?", millis,
                sessionId);
    }

    /** Moves the session's last access and expiry into the past, so the repository treats it as expired. */
    public void expire(String sessionId) {
        jdbc.update("UPDATE SPRING_SESSION SET LAST_ACCESS_TIME = 0, EXPIRY_TIME = 0 WHERE SESSION_ID = ?", sessionId);
    }

    /** The stored session id a {@code SESSION} cookie value names: Spring Session Base64-encodes it. */
    public static String idOf(String cookieValue) {
        return new String(Base64.getDecoder().decode(cookieValue), StandardCharsets.UTF_8);
    }
}
