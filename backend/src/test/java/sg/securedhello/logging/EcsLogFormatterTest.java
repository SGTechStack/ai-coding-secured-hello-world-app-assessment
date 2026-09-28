package sg.securedhello.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.testsupport.EcsJson;
import sg.securedhello.testsupport.Proves;

/** The application's ECS format, and the redaction it applies at source (REJ-001; REJ-087; REJ-088; ADR-055). */
class EcsLogFormatterTest {

    private final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

    @Test
    @Proves("T-AUD-012")
    void onlyTheTraceIdsLeaveTheMdcAndTheyTakeTheirEcsNames() {
        LoggingEvent event = event("sg.securedhello.Example", null);
        event.setMDCPropertyMap(Map.of("traceId", "0af7651916cd43dd8448eb211c80319c", "spanId", "b7ad6b7169203331",
                "correlation.id", "caller-correlation-5e1a", "probe-key", "caller-baggage-5e1a", "user.email",
                "someone@example.test"));

        String line = EcsJson.render(event);

        assertThat(EcsJson.flatten(line)).containsEntry("trace.id", "0af7651916cd43dd8448eb211c80319c")
                .containsEntry("span.id", "b7ad6b7169203331")
                .doesNotContainKeys("traceId", "spanId", "correlation.id", "probe-key", "user.email");
        assertThat(line).doesNotContain("caller-correlation-5e1a", "caller-baggage-5e1a", "someone@example.test");
    }

    @Test
    void anEventIsOneEcsJsonLineWithItsKeyValuePairsNested() {
        LoggingEvent event = event("sg.securedhello.Example", null);
        event.addKeyValuePair(new KeyValuePair("event.action", "application-startup"));

        String line = EcsJson.render(event);

        assertThat(line.strip()).startsWith("{").endsWith("}").doesNotContain("\n", "\r")
                .contains("\"event\":{\"action\":\"application-startup\"}");
        assertThat(EcsJson.flatten(line)).containsEntry("message", "Something happened.")
                .containsEntry("log.level", "INFO")
                .containsEntry("log.logger", "sg.securedhello.Example")
                .containsEntry("ecs.version", "8.11")
                .containsEntry("service.name", "secured-hello-world")
                .containsKey("@timestamp");
    }

    @Test
    void anApplicationLineReportsItsThrowable() {
        String line = EcsJson.render(event("sg.securedhello.Example", new IllegalStateException("boom")));

        assertThat(EcsJson.flatten(line)).containsEntry("error.type", "java.lang.IllegalStateException")
                .containsEntry("error.message", "boom")
                .containsKey("error.stack_trace");
    }

    @Test
    void anAuditRowNeverReportsAThrowableEvenIfOneIsAttached() {
        String line = EcsJson.render(event(AuditEmitter.AUDIT_LOGGER,
                new IllegalStateException("request-content-in-message-44c1")));

        assertThat(EcsJson.flatten(line)).doesNotContainKeys("error.type", "error.message", "error.stack_trace");
        assertThat(line).doesNotContain("request-content-in-message-44c1", "IllegalStateException");
    }

    private LoggingEvent event(String loggerName, Throwable throwable) {
        Logger logger = context.getLogger(loggerName);
        return new LoggingEvent(Logger.class.getName(), logger, Level.INFO, "Something happened.", throwable, null);
    }
}
