package sg.example.helloauth.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import sg.example.helloauth.DevProfile;

/**
 * Stands in for real email delivery, which is out of scope: it logs that a message was sent,
 * identifying the Account by UUID, never by address. In the dev profile only, it also logs the
 * reset link, so a developer can follow it. Logging returns at once, so no caller waits.
 */
@Component
class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    private final boolean logResetLinks;

    LoggingEmailService(Environment environment) {
        this.logResetLinks = DevProfile.isActive(environment);
    }

    @Override
    public void sendPasswordResetEmail(Recipient to, String resetLink) {
        LoggingEventBuilder event = sent("Password reset email sent.", to);
        if (logResetLinks) {
            event = event.addKeyValue("email.reset_link", resetLink);
        }
        event.log();
    }

    @Override
    public void sendLockoutNotification(Recipient to) {
        sent("Lockout notification sent.", to).log();
    }

    @Override
    public void sendPasswordChangedNotification(Recipient to) {
        sent("Password changed notification sent.", to).log();
    }

    /** The recipient is the event's target: {@code user.id} may already name whoever made the request. */
    private static LoggingEventBuilder sent(String message, Recipient to) {
        return log.atInfo().setMessage(message).addKeyValue("user.target.id", to.accountId().toString());
    }
}
