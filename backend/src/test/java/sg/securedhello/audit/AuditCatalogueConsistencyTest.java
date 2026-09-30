package sg.securedhello.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import sg.securedhello.audit.AuditRowDefinition.Scope;
import sg.securedhello.security.source.SourceKey;
import sg.securedhello.testsupport.EcsJson;
import sg.securedhello.testsupport.MutableClock;
import sg.securedhello.testsupport.Proves;

/**
 * Every member of the audit catalogue, emitted with a context built from its own definition, is a full row: the Tier A
 * and Tier B fields, the request fields on a request-scoped row, and a reason from its own family. The degraded path
 * is never reached (Std §3.3; Std §3.4; ASVS 16.2.1 (L2)).
 */
class AuditCatalogueConsistencyTest {

    private static final List<String> TIER_A = List.of("@timestamp", "message", "log.level", "log.logger",
            "ecs.version", "process.thread.name", "service.name", "service.version", "service.environment", "trace.id",
            "span.id");

    private static final List<String> TIER_B = List.of("event.kind", "event.category", "event.type", "event.action",
            "event.outcome", "event.severity");

    private final Logger auditLogger = (Logger) LoggerFactory.getLogger(AuditEmitter.AUDIT_LOGGER);
    private final Logger alertLogger = (Logger) LoggerFactory.getLogger(AuditEmitter.class);
    private final ListAppender<ILoggingEvent> rows = new ListAppender<>();
    private final ListAppender<ILoggingEvent> alerts = new ListAppender<>();

    private static final byte[] LOG_KEY = HexFormat.of()
            .parseHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");

    private final AuditEmitter emitter = new AuditEmitter(new AuditRequestFields(request -> new SourceKey("4:cb007107"),
            new LogFieldHasher(LOG_KEY), 256), MutableClock.startingNow(), Duration.ofMinutes(15), 20, 500);

    @BeforeEach
    void capture() {
        rows.start();
        alerts.start();
        auditLogger.addAppender(rows);
        alertLogger.addAppender(alerts);
        auditLogger.setLevel(Level.INFO);
        MDC.put("traceId", "0af7651916cd43dd8448eb211c80319c");
        MDC.put("spanId", "b7ad6b7169203331");
    }

    @AfterEach
    void release() {
        auditLogger.detachAppender(rows);
        alertLogger.detachAppender(alerts);
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    /** A context writing every key {@code definition} requires, and the first reason of its family if it has one. */
    private static AuditContext contextFor(AuditRowDefinition definition) {
        return fields -> {
            definition.required().forEach(key -> fields.put(key, "sample-" + key.name().toLowerCase()));
            if (definition.reasonFamily() != AuditReason.None.class) {
                fields.reason(definition.reasonFamily().getEnumConstants()[0]);
            }
        };
    }

    @ParameterizedTest
    @EnumSource(AuditEvent.class)
    @Proves("T-AUD-007")
    void everyEventIsAFullRowWithAReasonFromItsFamily(AuditEvent event) {
        AuditRowDefinition definition = event.definition();
        if (definition.scope() == Scope.REQUEST) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/users/42");
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/admin/users/{id}");
            request.setSession(new MockHttpSession(null, "catalogue-session"));
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        }

        emitter.emit(event, contextFor(definition));
        emitter.closeKeyingWindow();

        assertThat(alerts.list).as("degradation alerts").isEmpty();
        List<Map<String, Object>> written = rows.list.stream()
                .map(row -> EcsJson.flatten(EcsJson.render(row))).toList();
        assertThat(written).as("rows other than the event's").allSatisfy(row ->
                assertThat(row.get("message")).isNotEqualTo(AuditEmitter.DEGRADED_MESSAGE));
        Map<String, Object> row = written.stream().filter(r -> definition.message().equals(r.get("message")))
                .findFirst().orElseThrow(() -> new AssertionError("no row for " + event + " in " + written));

        assertThat(row).containsKeys(TIER_A.toArray(String[]::new)).containsKeys(TIER_B.toArray(String[]::new))
                .containsEntry("log.logger", AuditEmitter.AUDIT_LOGGER)
                .containsEntry("event.action", definition.action())
                .containsEntry("event.outcome", definition.outcome().code())
                .containsEntry("event.severity", definition.severity().code())
                .containsEntry("log.level", definition.level().name());
        definition.required().forEach(key -> assertThat(row).containsKey(key.field()));
        if (definition.scope() == Scope.REQUEST) {
            assertThat(row).containsEntry("url.path", "/api/admin/users/{id}")
                    .containsEntry("http.request.method", "POST")
                    .containsKeys("source.ip_hash", "session.hash");
        } else {
            assertThat(row).doesNotContainKeys("url.path", "http.request.method");
        }
        if (definition.reasonFamily() == AuditReason.None.class) {
            assertThat(row).doesNotContainKey("event.reason");
        } else {
            assertThat(Arrays.stream(definition.reasonFamily().getEnumConstants()).map(AuditReason::code))
                    .contains((String) row.get("event.reason"));
        }
    }
}
