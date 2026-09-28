package sg.securedhello.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static sg.securedhello.audit.AuditRowDefinition.row;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;

import sg.securedhello.audit.AuditRowDefinition.Scope;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.EcsJson;
import sg.securedhello.testsupport.Proves;

/**
 * Where audit rows go (ADR-056): the dedicated daily rolling file, and a copy on stdout that one
 * {@code additivity="false"} would silently remove.
 */
@ExtendWith(OutputCaptureExtension.class)
class AuditStreamDestinationsTest extends CtxDefaultTest {

    private static final String AUDIT_FILE_APPENDER = "AUDIT_FILE";

    @Autowired
    private AuditEmitter emitter;

    @Test
    @Proves("T-AUD-044")
    void anAuditRowReachesBothTheDedicatedFileAndStdoutAsEcsNdjson(CapturedOutput output) throws IOException {
        String marker = "host-" + UUID.randomUUID();
        RequestContextHolder.resetRequestAttributes();

        emitter.write("TEST", row("test-destinations", "Destination probe.").scope(Scope.PROCESS)
                .required(AuditKey.HOST_NAME).build(), fields -> fields.put(AuditKey.HOST_NAME, marker));

        assertThat(EcsJson.rows(output.getOut())).filteredOn(row -> marker.equals(row.get("host.name")))
                .singleElement().satisfies(AuditStreamDestinationsTest::isAnEcsAuditRow);
        assertThat(EcsJson.rows(Files.readString(auditFile(), StandardCharsets.UTF_8)))
                .filteredOn(row -> marker.equals(row.get("host.name")))
                .singleElement().satisfies(AuditStreamDestinationsTest::isAnEcsAuditRow);
    }

    @Test
    @Proves("T-AUD-044")
    void theAuditLoggerKeepsItsAdditivitySoTheStdoutCopyCannotBeDroppedSilently() {
        Logger audit = (Logger) LoggerFactory.getLogger(AuditEmitter.AUDIT_LOGGER);

        assertThat(audit.isAdditive()).isTrue();
        assertThat(audit.getAppender(AUDIT_FILE_APPENDER)).isNotNull();
    }

    @Test
    @Proves("T-AUD-011")
    void theAuditFileRollsDailyKeeps90ArchivesAndHasNoTotalSizeCap() throws IOException {
        TimeBasedRollingPolicy<?> policy = (TimeBasedRollingPolicy<?>) auditFileAppender().getRollingPolicy();

        assertThat(policy.getFileNamePattern()).contains("%d{yyyy-MM-dd}");
        assertThat(policy.getMaxHistory()).isEqualTo(90);
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(policy, "isUnboundedTotalSizeCap")).isTrue();
        String configuration = new ClassPathResource("logback-spring.xml").getContentAsString(StandardCharsets.UTF_8);
        assertThat(configuration).as("one configuration for every profile, with no size cap")
                .doesNotContain("<springProfile", "<totalSizeCap", "additivity=\"false\"");
    }

    private static void isAnEcsAuditRow(Map<String, Object> row) {
        assertThat(row).containsEntry("log.logger", AuditEmitter.AUDIT_LOGGER)
                .containsEntry("event.action", "test-destinations")
                .containsEntry("ecs.version", "8.11")
                .containsEntry("service.name", "secured-hello-world")
                .containsKeys("@timestamp", "log.level", "message", "process.thread.name", "service.version");
    }

    private static Path auditFile() {
        return Path.of(auditFileAppender().getFile());
    }

    private static RollingFileAppender<ILoggingEvent> auditFileAppender() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Appender<ILoggingEvent> appender = context.getLogger(AuditEmitter.AUDIT_LOGGER).getAppender(AUDIT_FILE_APPENDER);
        assertThat(appender).isInstanceOf(RollingFileAppender.class);
        return (RollingFileAppender<ILoggingEvent>) appender;
    }
}
