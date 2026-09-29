package sg.securedhello.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import sg.securedhello.config.ResetLinkLoggerGuard;

/**
 * The stubbed {@link EmailService} (PRD Story 6; ADR-057). It sends nothing: it writes the link, never the recipient,
 * at {@code DEBUG} to the dedicated non-audit logger {@value ResetLinkLoggerGuard#LOGGER_NAME}, which only the
 * {@code dev} profile enables. Three controls refuse that logger everywhere else, so outside {@code dev} a link is
 * delivered nowhere (R-CRED-021).
 */
@Service
public class DevLinkLogger implements EmailService {

    private static final Logger links = LoggerFactory.getLogger(ResetLinkLoggerGuard.LOGGER_NAME);

    @Override
    public void send(LinkEmail email) {
        if (links.isDebugEnabled()) {
            links.debug("Dev-only {} link (no mail is sent): {}", email.type(), email.link());
        }
    }
}
