package sg.example.helloauth.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static sg.example.helloauth.support.ProblemAssertions.assertProblem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.status.Status;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import sg.example.helloauth.support.Browser;
import sg.example.helloauth.support.IntegrationTest;
import sg.example.helloauth.support.TestAccount;

/** What the configured appenders actually write: the console, and the audit log file. */
@ExtendWith(OutputCaptureExtension.class)
@Import(StructuredLoggingTest.FailingEndpoint.class)
class StructuredLoggingTest extends IntegrationTest {

    private static final TestAccount ALICE = TestAccount.testUser(1);

    @Value("${app.audit.log-file}")
    private Path auditLogFile;

    /** Stands in for a system failure, such as the database becoming unavailable. */
    @RestController
    static class FailingEndpoint {

        @GetMapping("${app.api.base-path}/test/failure")
        String fail() {
            throw new IllegalStateException("simulated system failure");
        }
    }

    private static List<DocumentContext> jsonLines(String text) {
        return text.lines().filter(line -> line.startsWith("{")).map(JsonPath::parse).toList();
    }

    private static DocumentContext lineContaining(List<DocumentContext> lines, String text) {
        return lines.stream().filter(line -> line.jsonString().contains(text)).findFirst()
                .orElseThrow(() -> new AssertionError("No log line contains " + text));
    }

    /**
     * Also pins the output shape: ECS nests dotted MDC keys ({@code user.id}, {@code correlation.id}),
     * but the renamed trace keys come out flat, as {@code "trace.id"} and {@code "span.id"}.
     */
    @Test
    void systemFailureIsLoggedAtErrorAsEcsJsonOnTheConsole(CapturedOutput output) {
        Browser browser = newBrowser().registerAndLogin(ALICE);
        String id = jdbc.sql("SELECT id FROM users").query(String.class).single();

        assertProblem(browser.get("/test/failure"), 500, "internal server error");

        DocumentContext line = lineContaining(jsonLines(output.getOut()), "simulated system failure");
        assertThat(line.read("$.log.level", String.class)).isEqualTo("ERROR");
        assertThat(line.read("$.service.name", String.class)).isEqualTo("hello-auth");
        assertThat(line.read("$.service.version", String.class)).isNotBlank().doesNotContain("@");
        assertThat(line.read("$.service.environment", String.class)).isEqualTo("unspecified");
        assertThat(line.read("$['trace.id']", String.class)).matches("[0-9a-f]{32}");
        assertThat(line.read("$.correlation.id", String.class)).isNotBlank();
        assertThat(line.read("$.user.id", String.class)).isEqualTo(id);
    }

    @Test
    void auditEventsGoToTheirOwnFileAndNotToTheConsole(CapturedOutput output) throws IOException {
        long startedAt = System.currentTimeMillis();
        String loginId = UUID.randomUUID().toString();
        String logoutId = UUID.randomUUID().toString();
        Browser browser = newBrowser().registered(ALICE);

        browser.send(browser.withCsrf(mvc.post().uri(basePath + "/login")
                .header("X-Correlation-ID", loginId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .formField("username", ALICE.username())
                .formField("password", ALICE.password()))).assertThat().hasStatusOk();
        browser.fetchCsrf();
        // Logged in, so the request also has a user.id of its own.
        browser.send(browser.withCsrf(mvc.post().uri(basePath + "/logout").header("X-Correlation-ID", logoutId)))
                .assertThat().hasStatusOk();

        List<DocumentContext> lines = jsonLines(Files.readString(auditLogFile, StandardCharsets.UTF_8));
        DocumentContext login = lineContaining(lines, loginId);
        assertThat(login.read("$.log.logger", String.class)).isEqualTo("audit");
        assertThat(login.read("$.event.action", String.class)).isEqualTo("user-authentication");
        assertThat(login.read("$.service.name", String.class)).isEqualTo("hello-auth");
        assertThat(login.read("$['trace.id']", String.class)).matches("[0-9a-f]{32}");
        DocumentContext logout = lineContaining(lines, logoutId);
        assertThat(logout.read("$.event.action", String.class)).isEqualTo("user-logout");
        assertThat(logout.read("$.user.id", String.class)).isEqualTo(login.read("$.user.id", String.class));
        assertThat(output.getOut()).doesNotContain(loginId, logoutId);
        // An event the encoder can't write is dropped, and reported only here.
        LoggerContext logback = (LoggerContext) LoggerFactory.getILoggerFactory();
        assertThat(logback.getStatusManager().getCopyOfStatusList())
                .filteredOn(status -> status.getLevel() == Status.ERROR && status.getTimestamp() >= startedAt)
                .isEmpty();
    }
}
