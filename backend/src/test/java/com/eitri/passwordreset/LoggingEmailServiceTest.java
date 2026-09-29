package com.eitri.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LoggingEmailServiceTest {

    @Test
    void logsTheResetLinkOnItsOwnLoggerInsteadOfSendingMail() {
        Logger logger = (Logger) LoggerFactory.getLogger(LoggingEmailService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new LoggingEmailService()
                    .sendPasswordResetEmail("john@example.com", "http://localhost:5173/reset-password?token=abc");

            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLoggerName()).isNotEqualTo("AUDIT");
                assertThat(event.getFormattedMessage()).isEqualTo("Password reset email (stub, not sent)");
                assertThat(event.getKeyValuePairs())
                        .extracting(pair -> pair.key + "=" + pair.value)
                        .containsExactly("email.reset_link=http://localhost:5173/reset-password?token=abc");
                assertThat(event.getFormattedMessage()).doesNotContain("john@example.com");
            });
        } finally {
            logger.detachAppender(appender);
        }
    }
}
