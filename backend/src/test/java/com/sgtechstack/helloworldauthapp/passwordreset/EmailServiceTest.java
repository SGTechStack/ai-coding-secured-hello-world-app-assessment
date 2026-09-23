package com.sgtechstack.helloworldauthapp.passwordreset;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the reset token against ending up in the logs.
 *
 * <p>A reset token is equivalent to the account's password until it expires,
 * so if it reaches the log then anyone who can read logs can take over any
 * account that has requested a reset. This was a real defect: the stub used
 * to log the assembled link at INFO. These tests exist so it cannot come
 * back, including when a real {@code EmailService} eventually replaces the
 * stub.
 *
 * <p>Deliberately a plain unit test rather than a {@code @SpringBootTest}:
 * the behaviour under test is a single constructor flag, and keeping it out
 * of a Spring context means it cannot be accidentally satisfied by whatever
 * the active profile happens to set.
 */
class EmailServiceTest {

    private static final String TOKEN = "Gk4tNotARealTokenJustForAssertions_0123456789abcd";
    private static final String RESET_LINK = "http://localhost:3000/?token=" + TOKEN;
    private static final String EMAIL = "someone@example.com";

    private ch.qos.logback.classic.Logger emailServiceLogger;
    private ListAppender<ILoggingEvent> captured;

    @BeforeEach
    void startCapturingLogs() {
        emailServiceLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(EmailService.class);
        captured = new ListAppender<>();
        captured.start();
        emailServiceLogger.addAppender(captured);
        // Capture everything, so the assertions can't pass merely because a
        // level threshold filtered the leak out of this particular run.
        emailServiceLogger.setLevel(Level.TRACE);
    }

    @AfterEach
    void stopCapturingLogs() {
        emailServiceLogger.detachAppender(captured);
        captured.stop();
    }

    private String capturedOutput() {
        return captured.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }

    @Test
    void doesNotLogTheResetTokenLinkOrRecipientByDefault() {
        new EmailService(false).sendPasswordResetEmail(EMAIL, RESET_LINK);

        assertThat(capturedOutput())
                .doesNotContain(TOKEN)
                .doesNotContain(RESET_LINK)
                .doesNotContain(EMAIL);
    }

    @Test
    void stillRecordsThatADispatchHappened() {
        new EmailService(false).sendPasswordResetEmail(EMAIL, RESET_LINK);

        // Suppressing the secret must not suppress the audit trail: the reset
        // path running at all is still a security-relevant event.
        assertThat(captured.list).isNotEmpty();
    }

    @Test
    void surfacesTheLinkOnlyWhenTheDevFlagIsExplicitlyEnabled() {
        new EmailService(true).sendPasswordResetEmail(EMAIL, RESET_LINK);

        assertThat(capturedOutput()).contains(RESET_LINK);
    }
}
