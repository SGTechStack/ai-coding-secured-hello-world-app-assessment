package sg.securedhello.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.config.ResetLinkLoggerGuard;
import sg.securedhello.credential.CredentialTokenHash;
import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.LogOutputGuard;
import sg.securedhello.testsupport.PasswordResets;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.RestartHarness;
import sg.securedhello.testsupport.RestartHarness.Boot;

import tools.jackson.databind.json.JsonMapper;

/**
 * The real {@code EmailService} stub, with no capture in its place (level restart, own database): in {@code dev} the
 * reset link reaches the dev-only link logger and nothing else; outside {@code dev} it reaches no log at all (ADR-057;
 * R-CRED-020; R-CRED-021). Every logging event of the boot is recorded from the root logger, which the audit logger
 * also reaches (it is additive, T-AUD-044).
 */
class ResetLinkConfinementTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String LINK_MARKER = "/reset#token=";

    /**
     * Boots with {@code customiser}, requests a reset for a fresh activated account, and returns every log event of the
     * request. The recorder is attached once the application is up: startup reinitialises Logback, which would drop it.
     */
    private static List<ILoggingEvent> eventsOfAReset(Consumer<SpringApplicationBuilder> customiser,
            List<String> issuedHashes) {
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        Boot boot = RestartHarness.run(customiser, context -> {
            Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            events.start();
            root.addAppender(events);
            try {
                issuedHashes.add(requestReset(context));
            } finally {
                root.detachAppender(events);
            }
        });
        assertThat(boot.failure()).as("the boot and the request").isNull();
        return List.copyOf(events.list);
    }

    /** Requests a reset over HTTP, as a browser would; returns the stored hash of the token it issued. */
    private static String requestReset(ConfigurableApplicationContext context) {
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        Accounts.Account account = new Accounts(jdbc, context.getBean(PasswordEncoder.class)).user();
        String base = "http://localhost:" + context.getEnvironment().getProperty("local.server.port");
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> bootstrap = client.send(HttpRequest.newBuilder(URI.create(base + "/api/csrf"))
                    .build(), BodyHandlers.ofString());
            String cookie = bootstrap.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
            HttpResponse<String> request = client.send(HttpRequest.newBuilder(URI.create(base
                            + "/api/password-reset/request"))
                    .header("Content-Type", "application/json").header("Cookie", cookie)
                    .header("X-CSRF-TOKEN", JSON.readTree(bootstrap.body()).get("token").asString())
                    .POST(BodyPublishers.ofString(JSON.writeValueAsString(Map.of("email",
                            PasswordResets.emailOf(account)))))
                    .build(), BodyHandlers.ofString());
            assertThat(request.statusCode()).isEqualTo(202);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
        return jdbc.queryForObject("SELECT token_hash FROM credential_tokens WHERE user_id = ?", String.class,
                account.id());
    }

    /** Everything an event could put in an appender's output: its logger, message, arguments and key-value pairs. */
    private static String text(ILoggingEvent event) {
        return event.getLoggerName() + " " + event.getFormattedMessage() + " " + event.getKeyValuePairs() + " "
                + event.getMDCPropertyMap();
    }

    @Test
    @Proves("T-AUD-030")
    void inDevTheRealStubWritesTheLinkToTheDevOnlyLoggerAndNowhereElse() {
        List<String> hashes = new java.util.ArrayList<>();
        List<ILoggingEvent> events = eventsOfAReset(builder -> builder.profiles("dev"), hashes);

        List<ILoggingEvent> links = events.stream().filter(event -> text(event).contains(LINK_MARKER)).toList();
        assertThat(links).singleElement().satisfies(event -> assertThat(event.getLoggerName())
                .isEqualTo(ResetLinkLoggerGuard.LOGGER_NAME));
        String message = links.getFirst().getFormattedMessage();
        String token = message.substring(message.indexOf(LINK_MARKER) + LINK_MARKER.length()).strip();
        assertThat(CredentialTokenHash.hash(CredentialTokenType.PASSWORD_RESET, token)).isEqualTo(hashes.getFirst());

        String elsewhere = String.join("\n", events.stream()
                .filter(event -> !event.getLoggerName().equals(ResetLinkLoggerGuard.LOGGER_NAME))
                .map(ResetLinkConfinementTest::text).toList());
        assertThat(LogOutputGuard.leaks(elsewhere, List.of(token, hashes.getFirst())))
                .as("the token and its hash on any other logger, the audit logger included").isEmpty();
    }

    @Test
    void outsideDevAResetRequestLogsNoLink() {
        List<String> hashes = new java.util.ArrayList<>();
        List<ILoggingEvent> events = eventsOfAReset(builder -> { }, hashes);

        assertThat(hashes).as("a token was issued, so a link was built and handed to the stub").hasSize(1);
        assertThat(events).as("no logger, the dev-only one included, wrote it")
                .noneMatch(event -> text(event).contains(LINK_MARKER) || text(event).contains(hashes.getFirst()));
    }
}
