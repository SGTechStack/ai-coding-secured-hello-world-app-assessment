package com.eitri.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.testsupport.SessionClient;
import com.eitri.testsupport.SessionClient.Session;
import com.eitri.testsupport.StructuredLogTestCapture;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:session-hardening;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import(SessionHardeningIT.ClockConfiguration.class)
class SessionHardeningIT {

    private static final String LOGIN_JSON =
            """
            {"username":"johndoe","password":"Password123!"}
            """;
    private static final String ACCOUNT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String TEST_HASH_KEY = "test-only-session-hash-placeholder-not-for-production";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void resetClock() {
        clock.set(Instant.now());
    }

    @Test
    void csrfAndAuthenticatedSessionsAreStoredInFlywayOwnedJdbcTables() throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);

        assertThat(sessionRowCount(session.sessionId())).isOne();
        assertThat(session.login(mvc, LOGIN_JSON)).hasStatusOk();
        assertThat(sessionRowCount(session.sessionId())).isOne();
        assertThat(jdbc.queryForObject(
                        "SELECT PRINCIPAL_NAME FROM SPRING_SESSION WHERE SESSION_ID = ?",
                        String.class,
                        session.sessionId()))
                .isEqualTo("johndoe");
    }

    @Test
    void theAuthenticatedSessionStoresNoPasswordHash() throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);
        assertThat(session.login(mvc, LOGIN_JSON)).hasStatusOk();
        String storedHash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE username = 'johndoe'", String.class);

        byte[] securityContext = jdbc.queryForObject(
                """
                SELECT a.ATTRIBUTE_BYTES FROM SPRING_SESSION_ATTRIBUTES a
                JOIN SPRING_SESSION s ON a.SESSION_PRIMARY_ID = s.PRIMARY_ID
                WHERE s.SESSION_ID = ? AND a.ATTRIBUTE_NAME = 'SPRING_SECURITY_CONTEXT'
                """,
                byte[].class,
                session.sessionId());

        // The serialized context does hold the principal, just not its credentials.
        String serialized = new String(securityContext, StandardCharsets.ISO_8859_1);
        assertThat(serialized).contains("johndoe");
        assertThat(serialized).doesNotContain(storedHash.substring("{bcrypt}".length()));
    }

    @Test
    void newestLoginExpiresTheOlderSessionAndWritesSafeAudit() throws Exception {
        try (StructuredLogTestCapture audit = StructuredLogTestCapture.audit()) {
            Session older = SessionClient.fetchCsrf(mvc);
        assertThat(older.login(mvc, LOGIN_JSON)).hasStatusOk();
        String oldSessionId = older.sessionId();

        Session newer = SessionClient.fetchCsrf(mvc);
        assertThat(newer.login(mvc, LOGIN_JSON)).hasStatusOk();

        assertThat(mvc.get().uri("/api/v1/auth/me").cookie(older.cookie()))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/v1/auth/me").cookie(newer.cookie()))
                .hasStatusOk();

            String event = audit.line("Session expired");
            assertSessionExpiryEvent(event, "concurrent-login", hmac(oldSessionId));
            assertThat(event).doesNotContain(oldSessionId, newer.sessionId());
        }
    }

    @Test
    void absoluteLifetimeInvalidatesTheSessionAndWritesSafeAudit() throws Exception {
        try (StructuredLogTestCapture audit = StructuredLogTestCapture.audit()) {
            Session session = SessionClient.fetchCsrf(mvc);
        assertThat(session.login(mvc, LOGIN_JSON)).hasStatusOk();
        String sessionId = session.sessionId();

        clock.advance(Duration.ofHours(8).plusSeconds(1));

        assertThat(mvc.get().uri("/api/v1/auth/me").cookie(session.cookie()))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(sessionRowCount(sessionId)).isZero();

            String event = audit.line("Session expired");
            assertSessionExpiryEvent(event, "absolute-timeout", hmac(sessionId));
            assertThat(event).doesNotContain(sessionId);
        }
    }

    @Test
    void failedLoginInvalidatesTheIncomingSession() throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);
        String sessionId = session.sessionId();

        assertThat(session.login(
                        mvc,
                        """
                        {"username":"johndoe","password":"wrong-password"}
                        """))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        assertThat(sessionRowCount(sessionId)).isZero();
        assertThat(mvc.get().uri("/api/v1/auth/me").cookie(session.cookie()))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private int sessionRowCount(String sessionId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE SESSION_ID = ?", Integer.class, sessionId);
    }

    private static void assertSessionExpiryEvent(String event, String reason, String expectedHash) {
        assertThat(JsonPath.<String>read(event, "$.log.level")).isEqualTo("WARN");
        assertThat(JsonPath.<String>read(event, "$.log.logger")).isEqualTo("AUDIT");
        assertThat(JsonPath.<String>read(event, "$.event.kind")).isEqualTo("event");
        assertThat(JsonPath.<String>read(event, "$.event.category")).isEqualTo("authentication");
        assertThat(JsonPath.<String>read(event, "$.event.action")).isEqualTo("session-expired");
        assertThat(JsonPath.<String>read(event, "$.event.outcome")).isEqualTo("failure");
        assertThat(JsonPath.<String>read(event, "$.reason")).isEqualTo(reason);
        assertThat(JsonPath.<String>read(event, "$.user.id")).isEqualTo(ACCOUNT_ID);
        assertThat(JsonPath.<String>read(event, "$.user.name")).isEqualTo("johndoe");
        assertThat(JsonPath.<String>read(event, "$.session.hash")).isEqualTo(expectedHash);
    }

    private static String hmac(String sessionId) throws GeneralSecurityException {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(TEST_HASH_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(hmac.doFinal(sessionId.getBytes(StandardCharsets.UTF_8)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {
        @Bean
        @org.springframework.context.annotation.Primary
        MutableClock sessionTestClock() {
            return new MutableClock(Instant.now());
        }
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        MutableClock(Instant initial) {
            instant = new AtomicReference<>(initial);
        }

        void set(Instant value) {
            instant.set(value);
        }

        void advance(Duration duration) {
            instant.updateAndGet(value -> value.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return zone.equals(ZoneOffset.UTC) ? this : Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
