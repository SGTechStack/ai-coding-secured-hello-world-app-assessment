package com.example.helloauth.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.helloauth.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * The stub IS the delivery mechanism (no SMTP exists), so its log line is a
 * behavioral AC: the link it logs is the URL a developer clicks. Pinned here
 * at the narrow seam — the HTTP tests replace the bean to capture the token.
 */
class EmailServiceTests {

    @Test
    void sendPasswordResetLinkLogsTheSpaResetUrl() {
        EmailService emailService = new EmailService(new AppProperties());

        Logger logger = (Logger) LoggerFactory.getLogger(EmailService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            emailService.sendPasswordResetLink("alice@example.com", "tok-123");
        } finally {
            logger.detachAppender(appender);
        }

        // The ratified stub shape (ticket 07): the SPA's reset-password page
        // plus the plaintext token as the query param — the only place the
        // token may ever be logged.
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).isEqualTo(
                "Password reset link for alice@example.com: "
                    + "http://localhost:3000/reset-password?token=tok-123");
        });
    }

    @Test
    void suppressedModeNamesTheRecipientButNeverTheToken() {
        // log-reset-link=false (the prod setting, security-review F-02): the
        // line must still record that a reset was requested — but neither the
        // link nor any token fragment may appear. The token is a live bearer
        // credential; prod logs typically ship to aggregation.
        AppProperties properties = new AppProperties();
        properties.getPasswordReset().setLogResetLink(false);
        EmailService emailService = new EmailService(properties);

        Logger logger = (Logger) LoggerFactory.getLogger(EmailService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            emailService.sendPasswordResetLink("alice@example.com", "tok-123");
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage())
                .contains("alice@example.com")
                .doesNotContain("tok-123")
                .doesNotContain("token=")
                .doesNotContain(properties.getPasswordReset().getLinkBaseUrl());
        });
    }
}
