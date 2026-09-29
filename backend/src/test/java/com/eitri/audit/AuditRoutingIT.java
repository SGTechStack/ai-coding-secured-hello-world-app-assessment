package com.eitri.audit;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.Appender;
import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class AuditRoutingIT {

    @Value("${app.observability.audit.path}")
    private Path auditPath;

    @Test
    void auditAppenderIsDedicatedNonAdditiveMaskedNdjson(CapturedOutput output) throws Exception {
        Logger audit = (Logger) LoggerFactory.getLogger("AUDIT");
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Appender<?> destination = audit.getAppender("AUDIT_FILE");
        assertThat(destination).isNotNull();
        assertThat(audit.isAdditive()).isFalse();
        assertThat(root.getAppender("AUDIT_FILE")).isNull();

        String marker = "Audit routing verification " + UUID.randomUUID();
        audit.atInfo()
                .addKeyValue("event.kind", "event")
                .addKeyValue("password", "audit-password-canary")
                .setMessage(marker)
                .log();

        String line = Files.readAllLines(auditPath).stream()
                .filter(candidate -> candidate.contains(marker))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Audit line was not written to " + auditPath));
        assertThat(JsonPath.<String>read(line, "$.log.logger")).isEqualTo("AUDIT");
        assertThat(JsonPath.<String>read(line, "$.password")).isEqualTo("***MASKED***");
        assertThat(line).doesNotContain("audit-password-canary");
        assertThat(output.getAll()).doesNotContain(marker);
    }
}
