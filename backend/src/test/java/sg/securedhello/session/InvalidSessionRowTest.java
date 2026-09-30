package sg.securedhello.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;

/**
 * Row 11, {@code UNKNOWN_OR_EXPIRED}: a presented session id that resolves to nothing is observed once, on the lookup
 * the chain already made, whatever the route (R-AUD-003; ADR-017).
 */
class InvalidSessionRowTest extends CtxDefaultTest {

    private static final String INVALID = "Invalid session presented.";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuditEmitter emitter;

    /** A Base64-decodable {@code SESSION} cookie naming a session the store has never held. */
    private static Cookie fabricatedCookie() {
        return new Cookie("SESSION", Base64.getEncoder().encodeToString(UUID.randomUUID().toString()
                .getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @Proves("T-SES-029")
    void aFabricatedCookieOnTheHealthEndpointCostsOneSessionQueryAndWritesOneRow11() throws Exception {
        emitter.closeKeyingWindow();
        jdbc.execute("SET QUERY_STATISTICS FALSE");
        jdbc.execute("SET QUERY_STATISTICS TRUE");
        try (AuditCapture audit = AuditCapture.start()) {
            mockMvc.perform(get("/actuator/health").cookie(fabricatedCookie()).with(request -> {
                request.setRemoteAddr("203.0.113.61");
                return request;
            })).andExpect(status().isOk());
            Integer lookups = jdbc.queryForObject("SELECT COALESCE(SUM(EXECUTION_COUNT), 0)"
                    + " FROM INFORMATION_SCHEMA.QUERY_STATISTICS"
                    + " WHERE SQL_STATEMENT LIKE '%SPRING_SESSION%' AND SQL_STATEMENT NOT LIKE '%QUERY_STATISTICS%'",
                    Integer.class);
            emitter.closeKeyingWindow();

            assertThat(lookups).as("SPRING_SESSION statements").isEqualTo(1);
            assertThat(audit.rows()).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("message", INVALID)
                    .containsEntry("event.action", "session-end")
                    .containsEntry("event.reason", "UNKNOWN_OR_EXPIRED")
                    .containsEntry("event.count", 1)
                    .containsKey("source.ip_hash")
                    .doesNotContainKeys("user.id", "session.hash"));
        } finally {
            jdbc.execute("SET QUERY_STATISTICS FALSE");
        }
    }

    @Test
    void aLiveSessionWritesNoRow11() throws Exception {
        Cookie live = CsrfSession.bootstrap(mockMvc).cookie();
        emitter.closeKeyingWindow();
        try (AuditCapture audit = AuditCapture.start()) {
            mockMvc.perform(get("/actuator/health").cookie(live)).andExpect(status().isOk());
            emitter.closeKeyingWindow();

            assertThat(audit.withMessage(INVALID)).isEmpty();
        }
    }
}
