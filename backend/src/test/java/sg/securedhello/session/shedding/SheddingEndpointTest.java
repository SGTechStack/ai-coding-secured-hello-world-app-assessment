package sg.securedhello.session.shedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.json.JsonCompareMode;

import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SessionRows;
import sg.securedhello.testsupport.SignedIn;

/**
 * ADR-041 end to end: a session-less {@code GET /api/csrf} is shed past either line, signed-in traffic continues,
 * {@code /actuator/health/storage} reports the episode without details, and {@code /actuator/health} stays up.
 *
 * <p>Its own context, because the volume is replaced by a settable one ({@link #FREE}) so a test can make the disk
 * "low" without filling it, and because episodes and the rows it inserts must not reach the shared context. Probes are
 * on here, as a deployer with an orchestrator would set them, to show readiness stays up during an episode.
 */
@TestPropertySource(properties = "management.endpoint.health.probes.enabled=true")
class SheddingEndpointTest extends CtxDefaultTest {

    private static final long PLENTY = Long.MAX_VALUE / 2;

    /** The usable bytes the replaced volume reports. */
    static final AtomicLong FREE = new AtomicLong(PLENTY);

    @TestBean(methodName = "sg.securedhello.session.shedding.SheddingEndpointTest#settableVolume")
    private SessionStoreVolume volume;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AnonymousSessionShedding shedding;

    @Autowired
    private AuditEmitter emitter;

    private SessionRows sessions;
    private Accounts accounts;

    static SessionStoreVolume settableVolume() {
        return FREE::get;
    }

    @BeforeEach
    void setUp() {
        sessions = new SessionRows(jdbc);
        accounts = new Accounts(jdbc, passwordEncoder);
    }

    @AfterEach
    void endAnyEpisode() {
        FREE.set(PLENTY);
        jdbc.update("DELETE FROM SPRING_SESSION WHERE SESSION_ID LIKE 'flood-%'");
        clock.advance(ShedEpisode.DWELL.plus(AnonymousSessionShedding.COUNT_TTL));
        assertThat(shedding.shedsNewSession()).as("the episode clears").isFalse();
    }

    @Test
    @Proves("T-RL-024")
    void pastTheRowLimitATokenFetchWithNoSessionIsShedWhileSignedInTrafficContinues() throws Exception {
        CsrfSession signedIn = SignedIn.as(mockMvc, accounts.user());
        CsrfSession anonymous = CsrfSession.bootstrap(mockMvc);
        long live = jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Long.class);
        flood(ShedEpisode.N_MAX - live);
        clock.advance(AnonymousSessionShedding.COUNT_TTL);

        assertShedWithoutARow();
        mockMvc.perform(get("/api/hello").cookie(signedIn.cookie())).andExpect(status().isOk());
        SignedIn.refreshed(mockMvc, signedIn.cookie());
        SignedIn.refreshed(mockMvc, anonymous.cookie());
    }

    @Test
    @Proves("T-RL-024")
    void aLowDiskShedsANewAnonymousSession() throws Exception {
        CsrfSession anonymous = CsrfSession.bootstrap(mockMvc);
        lowDisk();

        assertShedWithoutARow();
        SignedIn.refreshed(mockMvc, anonymous.cookie());
    }

    @Test
    @Proves("T-OBS-009")
    void theStorageGroupReportsTheEpisodeWhileTheRoutingHealthStaysUp() throws Exception {
        assertHealth("/actuator/health/storage", 200, "UP");
        lowDisk();
        mockMvc.perform(get("/api/csrf")).andExpect(status().isTooManyRequests());

        // The routing endpoints: the root and, where a deployer enables probes, readiness (REJ-063; R-OBS-006).
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        assertHealth("/actuator/health/readiness", 200, "UP");
        assertHealth("/actuator/health/storage", 503, "DOWN");
        mockMvc.perform(get("/actuator/health/h2Data")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/health/storage/h2Data")).andExpect(status().isNotFound());

        FREE.set(PLENTY);
        clock.advance(ShedEpisode.DWELL);
        mockMvc.perform(get("/api/csrf")).andExpect(status().isOk());
        assertHealth("/actuator/health/storage", 200, "UP");
    }

    /** A detail-free health body: the status and nothing else. */
    private void assertHealth(String path, int httpStatus, String healthStatus) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().is(httpStatus))
                .andExpect(content().json("{\"status\":\"" + healthStatus + "\"}", JsonCompareMode.STRICT));
    }

    @Test
    @Proves("T-AUD-039")
    void anEpisodeYieldsOneStartRowAndOneClearRowHoweverManyRequestsAreRefused() throws Exception {
        emitter.closeKeyingWindow();
        try (AuditCapture audit = AuditCapture.start()) {
            lowDisk();
            for (int i = 0; i < 5; i++) {
                mockMvc.perform(get("/api/csrf")).andExpect(status().isTooManyRequests());
            }
            FREE.set(PLENTY);
            clock.advance(ShedEpisode.DWELL);
            for (int i = 0; i < 3; i++) {
                mockMvc.perform(get("/api/csrf")).andExpect(status().isOk());
            }
            emitter.closeKeyingWindow();

            List<Map<String, Object>> started = audit.withMessage(
                    AuditEvent.SOURCE_THROTTLED.definition().message()).stream()
                    .filter(row -> "DISK_RESERVE_SHED".equals(row.get("event.reason"))).toList();
            assertThat(started).hasSize(1);
            assertThat(String.valueOf(started.getFirst().get("event.count"))).isEqualTo("1");
            assertThat(audit.withMessage(AuditEvent.SHED_EPISODE_CLEARED.definition().message())).hasSize(1);
        }
    }

    /** The volume reports no free space from the next sample on. */
    private void lowDisk() {
        FREE.set(0);
        clock.advance(AnonymousSessionShedding.COUNT_TTL);
    }

    private void assertShedWithoutARow() throws Exception {
        var before = sessions.ids();
        mockMvc.perform(get("/api/csrf"))
                .andExpect(problem(ErrorCode.TOO_MANY_REQUESTS))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        assertThat(sessions.ids()).as("a refused request creates no session row").isEqualTo(before);
    }

    /** Inserts {@code count} live session rows in one statement. */
    private void flood(long count) {
        // Far in the future: Spring Session judges expiry on its own clock, not the shared one (ADR-066).
        long expiry = Long.MAX_VALUE / 2;
        jdbc.update("INSERT INTO SPRING_SESSION (PRIMARY_ID, SESSION_ID, CREATION_TIME, LAST_ACCESS_TIME,"
                + " MAX_INACTIVE_INTERVAL, EXPIRY_TIME) SELECT RANDOM_UUID(), 'flood-' || X, 0, 0, 900, ?"
                + " FROM SYSTEM_RANGE(1, ?)", expiry, count);
    }
}
