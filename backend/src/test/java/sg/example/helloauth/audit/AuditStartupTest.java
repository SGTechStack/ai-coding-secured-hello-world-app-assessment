package sg.example.helloauth.audit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;

import sg.example.helloauth.HelloAuthApplication;

/** Starts the real application, because the rule is about whether it may start at all. */
class AuditStartupTest {

    @Test
    void startupFailsWithoutAnIpHashSecretOfAtLeast32Characters() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(HelloAuthApplication.class).run(
                "--server.port=0",
                "--spring.datasource.url=jdbc:h2:mem:audit-startup",
                "--app.audit.ip-hash-secret=too-short").close())
                .rootCause()
                .isInstanceOf(BindValidationException.class)
                .hasMessageContaining("app.audit.ipHashSecret");
    }
}
