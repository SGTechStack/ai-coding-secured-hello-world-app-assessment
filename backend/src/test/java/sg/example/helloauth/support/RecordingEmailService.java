package sg.example.helloauth.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import sg.example.helloauth.email.EmailService;

/** Stands in for email delivery, so tests can see what was sent to whom, reset links included. */
public final class RecordingEmailService implements EmailService {

    public enum Kind { PASSWORD_RESET, LOCKOUT, PASSWORD_CHANGED }

    /** @param resetLink only for {@link Kind#PASSWORD_RESET} */
    public record SentEmail(Kind kind, Recipient to, String resetLink) {
    }

    private final List<SentEmail> sent = new CopyOnWriteArrayList<>();

    @Override
    public void sendPasswordResetEmail(Recipient to, String resetLink) {
        sent.add(new SentEmail(Kind.PASSWORD_RESET, to, resetLink));
    }

    @Override
    public void sendLockoutNotification(Recipient to) {
        sent.add(new SentEmail(Kind.LOCKOUT, to, null));
    }

    @Override
    public void sendPasswordChangedNotification(Recipient to) {
        sent.add(new SentEmail(Kind.PASSWORD_CHANGED, to, null));
    }

    public List<SentEmail> sent() {
        return List.copyOf(sent);
    }

    public List<SentEmail> sent(Kind kind) {
        return sent.stream().filter(email -> email.kind() == kind).toList();
    }

    void clear() {
        sent.clear();
    }
}
