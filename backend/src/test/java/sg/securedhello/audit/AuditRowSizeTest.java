package sg.securedhello.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import org.springframework.boot.logging.structured.StructuredLogFormatterFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import sg.securedhello.audit.AuditRowDefinition.Scope;
import sg.securedhello.logging.EcsLogFormatter;
import sg.securedhello.security.source.SourceKey;
import sg.securedhello.testsupport.MutableClock;
import sg.securedhello.testsupport.Proves;

/**
 * {@code bytes_per_row}, measured rather than argued (R-AUD-030; REJ-084): every request-scoped row in the catalogue,
 * each key at its widest, rendered through the production formatter with production's service fields, on a raw URI
 * past the cap, stays under {@link #BYTES_PER_ROW}. The daily audit volume in R-AUD-030 and R-OBS-012 is computed
 * from that bound; exceeding it is R-AUD-030's trigger to recompute the volume and the disk-space threshold.
 */
class AuditRowSizeTest {

    /**
     * The pinned upper bound, in bytes, of one audit line and its newline. Measured at 1,177 bytes (the widest row,
     * {@code ADMIN_ACTION_REFUSED}, with every row taken at the capped raw URI although only pre-handler rows carry
     * one), and rounded up for a keyed row's count and start. R-AUD-030 records it.
     */
    static final int BYTES_PER_ROW = 1_280;

    private static final byte[] LOG_KEY = HexFormat.of()
            .parseHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");

    /** The widest source key: an IPv6 /64 prefix. */
    private static final SourceKey WIDEST_SOURCE = new SourceKey("6:20010db885a3000000000000000000000000");

    private final Logger auditLogger = (Logger) LoggerFactory.getLogger(AuditEmitter.AUDIT_LOGGER);
    private final ListAppender<ILoggingEvent> rows = new ListAppender<>();
    private final AuditEmitter emitter = new AuditEmitter(new AuditRequestFields(request -> WIDEST_SOURCE,
            new LogFieldHasher(LOG_KEY), 256), MutableClock.startingNow(), Duration.ofMinutes(15), 20, 500);
    private String threadName;

    @BeforeEach
    void capture() {
        rows.start();
        auditLogger.addAppender(rows);
        auditLogger.setLevel(Level.INFO);
        MDC.put("traceId", "0af7651916cd43dd8448eb211c80319c");
        MDC.put("spanId", "b7ad6b7169203331");
        threadName = Thread.currentThread().getName();
        // Tomcat's request threads, at the pinned pool size (server.tomcat.threads.max: 200).
        Thread.currentThread().setName("http-nio-65535-exec-200");
    }

    @AfterEach
    void release() {
        Thread.currentThread().setName(threadName);
        auditLogger.detachAppender(rows);
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    /** Every allowed key at its widest value, and the family's longest reason code. */
    private static AuditContext widest(AuditRowDefinition definition) {
        return fields -> {
            for (AuditKey key : definition.allowed()) {
                switch (key) {
                    case USER_TARGET_COUNT -> fields.put(key, Long.MAX_VALUE);
                    case USER_TARGET_UNLOCK_REASON -> fields.put(key, Arrays.stream(UnlockReason.values())
                            .max(Comparator.comparingInt(reason -> reason.name().length())).orElseThrow());
                    default -> fields.put(key, UUID.randomUUID());
                }
            }
            AuditReason[] reasons = definition.reasonFamily().getEnumConstants();
            if (reasons.length > 0) {
                fields.reason(Arrays.stream(reasons).max(Comparator.comparingInt(reason -> reason.code().length()))
                        .orElseThrow());
            }
        };
    }

    private static void bindWidestRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/" + "x".repeat(400));
        request.setSession(new MockHttpSession());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    /** The production formatter with production's service fields and a process id at its widest. */
    private static StructuredLogFormatter<ILoggingEvent> productionFormatter() {
        ThrowableProxyConverter converter = new ThrowableProxyConverter();
        converter.setContext(new LoggerContext());
        converter.start();
        return new StructuredLogFormatterFactory<>(ILoggingEvent.class,
                new MockEnvironment().withProperty("spring.application.name", "secured-hello-world")
                        .withProperty("spring.application.pid", "4194304")
                        .withProperty("logging.structured.ecs.service.version", "0.0.1-SNAPSHOT")
                        .withProperty("logging.structured.ecs.service.environment", "production"),
                parameters -> parameters.add(ThrowableProxyConverter.class, converter), formatters -> {
                })
                .get(EcsLogFormatter.class.getName());
    }

    @Test
    @Proves("T-AUD-032")
    void theWidestRequestScopedRowAtTheCappedUriIsUnderThePinnedBytesPerRow() {
        List<AuditEvent> requestScoped = Arrays.stream(AuditEvent.values())
                .filter(event -> event.definition().scope() == Scope.REQUEST).toList();
        bindWidestRequest();
        requestScoped.forEach(event -> emitter.emit(event, widest(event.definition())));
        emitter.closeKeyingWindow();

        StructuredLogFormatter<ILoggingEvent> formatter = productionFormatter();
        List<Integer> sizes = rows.list.stream()
                .map(event -> formatter.format(event).strip().getBytes(StandardCharsets.UTF_8).length + 1)
                .toList();
        assertThat(rows.list).as("every request-scoped row, none degraded").hasSize(requestScoped.size())
                .noneMatch(event -> AuditEmitter.DEGRADED_MESSAGE.equals(event.getFormattedMessage()));
        assertThat(rows.list).as("the raw URI is capped").allSatisfy(event -> assertThat(formatter.format(event))
                .doesNotContain("x".repeat(300)));
        int widest = sizes.stream().max(Integer::compare).orElseThrow();
        String widestRow = rows.list.get(sizes.indexOf(widest)).getFormattedMessage();
        assertThat(widest).as("the widest row, '%s', in bytes", widestRow).isLessThanOrEqualTo(BYTES_PER_ROW);
    }
}
