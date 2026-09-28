package sg.securedhello.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static sg.securedhello.audit.AuditKey.ACTIVE_PROFILES;
import static sg.securedhello.audit.AuditKey.HOST_NAME;
import static sg.securedhello.audit.AuditRowDefinition.row;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import sg.securedhello.audit.AuditRowDefinition.Scope;
import sg.securedhello.audit.AuditRowDefinition.Severity;
import sg.securedhello.security.source.SourceKey;
import sg.securedhello.testsupport.EcsJson;
import sg.securedhello.testsupport.ExpectsDegradedAuditRow;
import sg.securedhello.testsupport.Proves;

/** The single emitter (ADR-055): validated rows, request fields (ADR-054; REJ-081) and the degraded path. */
class AuditEmitterTest {

    private static final byte[] LOG_KEY = HexFormat.of()
            .parseHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
    private static final String CLIENT_ADDRESS = "203.0.113.7";
    private static final SourceKey CLIENT_SOURCE_KEY = new SourceKey("4:cb007107");

    private static final AuditRowDefinition PROCESS_ROW = row("test-process-row", "Process row written.")
            .type("change").scope(Scope.PROCESS).required(HOST_NAME).build();
    private static final AuditRowDefinition REQUEST_ROW = row("test-request-row", "Request row written.")
            .type("access").level(org.slf4j.event.Level.WARN, Severity.MEDIUM).build();
    private static final AuditRowDefinition REASONED_ROW = row("test-reasoned-row", "Reasoned row written.")
            .scope(Scope.PROCESS).reasons(Degradation.class).build();

    private final Logger auditLogger = (Logger) LoggerFactory.getLogger(AuditEmitter.AUDIT_LOGGER);
    private final Logger alertLogger = (Logger) LoggerFactory.getLogger(AuditEmitter.class);
    private final ListAppender<ILoggingEvent> rows = new ListAppender<>();
    private final ListAppender<ILoggingEvent> alerts = new ListAppender<>();

    private final LogFieldHasher hasher = new LogFieldHasher(LOG_KEY);
    private final AuditEmitter emitter = new AuditEmitter(new AuditRequestFields(
            request -> CLIENT_ADDRESS.equals(request.getRemoteAddr()) ? CLIENT_SOURCE_KEY : SourceKey.UNPARSEABLE,
            hasher, 256));
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users/42");

    @BeforeEach
    void capture() {
        rows.start();
        alerts.start();
        auditLogger.addAppender(rows);
        alertLogger.addAppender(alerts);
        auditLogger.setLevel(Level.INFO);
        request.setRemoteAddr(CLIENT_ADDRESS);
    }

    @AfterEach
    void release() {
        auditLogger.detachAppender(rows);
        alertLogger.detachAppender(alerts);
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @Proves("T-AUD-043")
    @ExpectsDegradedAuditRow
    void anUnknownContextKeyProducesTheDegradedRowAndAnErrorInsteadOfTheRow() {
        emitter.write("TEST", PROCESS_ROW,
                fields -> fields.put(HOST_NAME, "host-1").put(ACTIVE_PROFILES, "unknown-value-9d2f"));

        Map<String, Object> row = onlyRow();
        assertThat(row).containsEntry("message", AuditEmitter.DEGRADED_MESSAGE)
                .containsEntry("event.action", "test-process-row")
                .containsEntry("event.outcome", "unknown")
                .containsEntry("event.reason", "UNKNOWN_KEY")
                .doesNotContainKey("host.name");
        assertThat(alerts.list).singleElement().satisfies(alert -> {
            assertThat(alert.getLevel()).isEqualTo(Level.ERROR);
            assertThat(alert.getFormattedMessage()).startsWith(AuditEmitter.DEGRADED_MESSAGE)
                    .contains("event=TEST", "degradation=UNKNOWN_KEY");
        });
        assertThat(everythingWritten()).doesNotContain("unknown-value-9d2f", "active_profiles", "ACTIVE_PROFILES",
                "host-1");
    }

    @Test
    @ExpectsDegradedAuditRow
    void aMissingRequiredKeyDegradesTheRow() {
        emitter.write("TEST", PROCESS_ROW, AuditContext.NONE);

        assertThat(onlyRow()).containsEntry("event.reason", "MISSING_KEY");
    }

    @Test
    @ExpectsDegradedAuditRow
    void aRequestScopedRowOutsideARequestDegrades() {
        emitter.write("TEST", REQUEST_ROW, AuditContext.NONE);

        assertThat(onlyRow()).containsEntry("event.reason", "MISSING_KEY").doesNotContainKey("url.path");
    }

    @Test
    @ExpectsDegradedAuditRow
    void aReasonOutsideTheFamilyDegradesTheRow() {
        emitter.write("TEST", REASONED_ROW, AuditContext.NONE);

        assertThat(onlyRow()).containsEntry("event.reason", "REASON_OUTSIDE_FAMILY");
    }

    @Test
    @ExpectsDegradedAuditRow
    void aFailingContextDegradesTheRowAndNoExceptionTextIsWritten() {
        emitter.write("TEST", PROCESS_ROW, fields -> {
            throw new IllegalStateException("secret-in-exception-5b1c");
        });
        emitter.emit(AuditEvent.APPLICATION_SHUTDOWN, null);

        assertThat(rows.list).hasSize(2).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
        assertThat(EcsJson.flatten(EcsJson.render(rows.list.get(0)))).containsEntry("event.reason", "EMIT_FAILED");
        assertThat(EcsJson.flatten(EcsJson.render(rows.list.get(1))))
                .containsEntry("event.action", "application-shutdown").containsEntry("event.reason", "EMIT_FAILED");
        assertThat(everythingWritten()).doesNotContain("secret-in-exception-5b1c");
    }

    @Test
    void aValidProcessRowCarriesTheConstantsAndTheContextKeys() {
        emitter.write("TEST", PROCESS_ROW, fields -> fields.put(HOST_NAME, "host|one\r\n"));

        assertThat(onlyRow()).containsEntry("message", "Process row written.")
                .containsEntry("log.logger", "audit")
                .containsEntry("log.level", "INFO")
                .containsEntry("event.kind", "event")
                .containsEntry("event.category", List.of("process"))
                .containsEntry("event.type", List.of("change"))
                .containsEntry("event.action", "test-process-row")
                .containsEntry("event.outcome", "success")
                .containsEntry("event.severity", "low")
                .containsEntry("host.name", "hostone")
                .doesNotContainKeys("event.reason", "url.path", "source.ip_hash");
        assertThat(alerts.list).isEmpty();
    }

    @Test
    @Proves("T-AUD-045")
    void aReasonIsWrittenByItsCode() {
        emitter.write("TEST", REASONED_ROW, fields -> fields.reason(Degradation.MISSING_KEY));

        assertThat(onlyRow()).containsEntry("event.reason", Degradation.MISSING_KEY.code())
                .containsEntry("event.outcome", "success");
    }

    @Test
    @Proves({"T-AUD-042", "T-AUD-041"})
    void aRequestRowCarriesTheRouteMethodAndKeyedHashesButNeverTheAddressOrSessionId() throws Exception {
        MockHttpSession session = new MockHttpSession(null, "session-id-4e7a1c9b");
        request.setSession(session);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/admin/users/{id}");
        bind(request);

        emitter.write("TEST", REQUEST_ROW, AuditContext.NONE);

        Map<String, Object> row = onlyRow();
        String unkeyed = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("session-id-4e7a1c9b".getBytes(StandardCharsets.UTF_8)));
        assertThat(row).containsEntry("url.path", "/api/admin/users/{id}")
                .containsEntry("http.request.method", "GET")
                .containsEntry("log.level", "WARN")
                .containsEntry("event.severity", "medium")
                .containsEntry("source.ip_hash", hasher.sourceIpHash(CLIENT_SOURCE_KEY))
                .containsEntry("session.hash", hasher.sessionHash("session-id-4e7a1c9b"))
                .doesNotContainKey("source.ip");
        assertThat((String) row.get("session.hash")).matches("[0-9a-f]{64}").isNotEqualTo(unkeyed);
        assertThat(everythingWritten()).doesNotContain(CLIENT_ADDRESS, "cb007107", "cb00:7107", "session-id-4e7a1c9b");
    }

    @Test
    void withoutASessionTheRowHasNoSessionHashAndNoSessionIsCreated() {
        bind(request);

        emitter.write("TEST", REQUEST_ROW, AuditContext.NONE);

        assertThat(onlyRow()).doesNotContainKey("session.hash").containsKey("source.ip_hash");
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void beforeAHandlerMatchesTheRawUriIsNeutralisedAndCappedWithAMarker() {
        MockHttpServletRequest raw = new MockHttpServletRequest("GET", "/api/x\r\ninjected|" + "a".repeat(7000));
        raw.setQueryString("q=query-canary-31f0");
        raw.setRemoteAddr(CLIENT_ADDRESS);
        bind(raw);

        emitter.write("TEST", REQUEST_ROW, AuditContext.NONE);

        String urlPath = (String) onlyRow().get("url.path");
        assertThat(urlPath).hasSize(256).startsWith("/api/xinjected").endsWith(AuditText.TRUNCATION_MARKER)
                .doesNotContain("\r", "\n", "|");
        assertThat(everythingWritten()).doesNotContain("query-canary-31f0");
    }

    @Test
    void aShortRawUriIsKeptWhole() {
        bind(new MockHttpServletRequest("GET", "/api/unmatched"));

        emitter.write("TEST", REQUEST_ROW, AuditContext.NONE);

        assertThat(onlyRow()).containsEntry("url.path", "/api/unmatched");
    }

    @Test
    void anUnknownMethodTokenIsWrittenAsOther() {
        bind(new MockHttpServletRequest("PROPFIND\r\nX", "/api/x"));

        emitter.write("TEST", REQUEST_ROW, AuditContext.NONE);

        assertThat(onlyRow()).containsEntry("http.request.method", "OTHER");
    }

    @Test
    void theStartupRowCarriesItsContext() {
        emitter.emit(AuditEvent.APPLICATION_STARTUP, new StartupContext("host-1", List.of("10.0.0.1"),
                List.of("default"), 64, List.of("app.security.hmac.log.key=0011aabb"), List.of("audit=INFO")));

        assertThat(onlyRow()).containsEntry("event.action", "application-startup")
                .containsEntry("event.type", List.of("start"))
                .containsEntry("host.name", "host-1")
                .containsEntry("host.ip", List.of("10.0.0.1"))
                .containsEntry("labels.active_profiles", List.of("default"))
                .containsEntry("labels.ipv6_prefix_length", 64)
                .containsEntry("labels.key_fingerprints", List.of("app.security.hmac.log.key=0011aabb"))
                .containsEntry("labels.audit_loggers", List.of("audit=INFO"));
    }

    private static void bind(MockHttpServletRequest boundRequest) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(boundRequest));
    }

    private Map<String, Object> onlyRow() {
        assertThat(rows.list).hasSize(1);
        return EcsJson.flatten(EcsJson.render(rows.list.get(0)));
    }

    private String everythingWritten() {
        StringBuilder written = new StringBuilder();
        rows.list.forEach(event -> written.append(EcsJson.render(event)));
        alerts.list.forEach(event -> written.append(EcsJson.render(event)));
        return written.toString();
    }
}
