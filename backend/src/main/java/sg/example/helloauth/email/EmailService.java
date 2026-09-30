package sg.example.helloauth.email;

import java.util.UUID;

/**
 * The port through which the app emails an Account's owner. A real implementation must deliver
 * in the background, so that sending an email never makes a response slower; the logging stub
 * returns at once anyway.
 */
public interface EmailService {

    /** The link carries a Password reset token: it must never be logged outside development. */
    void sendPasswordResetEmail(Recipient to, String resetLink);

    /** Tells the owner their Account became Locked, so they learn someone may be guessing their password. */
    void sendLockoutNotification(Recipient to);

    void sendPasswordChangedNotification(Recipient to);

    /** @param accountId identifies the Account in logs; the address itself is never logged */
    record Recipient(UUID accountId, String address) {
    }
}
