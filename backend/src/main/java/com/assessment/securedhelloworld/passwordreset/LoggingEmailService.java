package com.assessment.securedhelloworld.passwordreset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Stub {@link EmailService}: real SMTP delivery is out of scope for this
 * build (see PRODUCT.md), so this implementation only logs that a reset
 * email was sent and retains the most recent link per recipient in memory
 * so tests can drive the reset-confirm flow without a real inbox.
 *
 * <p>The reset link (and the plaintext token it carries) is a bearer
 * credential capable of resetting the account's password and MUST NEVER
 * be written to the application log — see the org logging standard
 * ({@code App-Standards/Appfw-Logging-Standards}), which explicitly lists
 * "reset links" as a category that must never be logged. Only
 * {@link #lastLinkFor(String)} exposes it, for test use.
 */
@Service
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    private final ConcurrentMap<String, String> lastLinkByRecipient = new ConcurrentHashMap<>();

    @Override
    public void sendPasswordResetEmail(String toAddress, String resetLink) {
        lastLinkByRecipient.put(toAddress, resetLink);
        log.info("Password reset email sent");
    }

    /**
     * Test-only accessor for the most recent reset link sent to a given
     * address. Never logged; kept in memory only for the lifetime of this
     * bean.
     */
    public String lastLinkFor(String toAddress) {
        String link = lastLinkByRecipient.get(toAddress);
        if (link == null) {
            throw new IllegalStateException("No reset link recorded for " + toAddress);
        }
        return link;
    }
}
