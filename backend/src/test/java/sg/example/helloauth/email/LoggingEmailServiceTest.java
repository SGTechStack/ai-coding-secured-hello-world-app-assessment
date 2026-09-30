package sg.example.helloauth.email;

import static org.assertj.core.api.Assertions.assertThat;
import static sg.example.helloauth.support.LogCapture.everything;
import static sg.example.helloauth.support.LogCapture.fields;

import java.util.UUID;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mock.env.MockEnvironment;

import sg.example.helloauth.email.EmailService.Recipient;
import sg.example.helloauth.support.LogCapture;

/** The stub is replaced in the full-context tests, so it is checked on its own here. */
class LoggingEmailServiceTest {

    private static final Recipient ALICE = new Recipient(UUID.fromString("7d7e2f5e-9a4b-4c1e-8f3a-2b6c9d0e1f2a"),
            "testuser1@test.example.com");

    private static final String RESET_LINK = "https://app.test.example.com/reset-password?token=secret-token";

    @RegisterExtension
    final LogCapture logs = LogCapture.application();

    private static LoggingEmailService stub(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new LoggingEmailService(environment);
    }

    private ILoggingEvent onlyEvent() {
        assertThat(logs.events()).hasSize(1);
        return logs.events().getFirst();
    }

    @Test
    void logsThatEachKindOfMessageWasSentByAccountIdAlone() {
        LoggingEmailService emails = stub();

        emails.sendLockoutNotification(ALICE);
        emails.sendPasswordChangedNotification(ALICE);
        emails.sendPasswordResetEmail(ALICE, RESET_LINK);

        assertThat(logs.events()).hasSize(3).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).contains("sent");
            assertThat(fields(event)).containsEntry("user.target.id", ALICE.accountId().toString());
            assertThat(everything(event)).doesNotContain(ALICE.address(), "secret-token");
        });
    }

    @Test
    void logsTheResetLinkInTheDevProfileOnly() {
        stub("dev").sendPasswordResetEmail(ALICE, RESET_LINK);

        assertThat(everything(onlyEvent())).contains(RESET_LINK).doesNotContain(ALICE.address());
    }
}
