package sg.securedhello.testsupport;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import sg.securedhello.credential.CredentialTokenHash;
import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.email.EmailService;
import sg.securedhello.email.LinkEmail;

/**
 * The capturing {@link EmailService} every shared context runs in place of the dev link logger: the one fixed override
 * of ADR-067. Tests read activation and reset tokens from here, never from a log. Every captured token, and its stored
 * hash, is registered with {@link LogOutputGuard}, so a test fails if either reaches any log output.
 *
 * <p>One instance serves the whole suite, like {@link TestClock#shared()}; tests use their own recipients, so they
 * never read each other's mail.
 */
public final class CapturedEmails implements EmailService {

    private static final CapturedEmails SHARED = new CapturedEmails();

    private final List<LinkEmail> sent = new CopyOnWriteArrayList<>();

    /** The shared capture. Also the {@code @TestBean} factory for the {@code EmailService} bean. */
    public static CapturedEmails shared() {
        return SHARED;
    }

    @Override
    public void send(LinkEmail email) {
        String token = token(email);
        LogOutputGuard.register(token);
        LogOutputGuard.register(CredentialTokenHash.hash(email.type(), token));
        sent.add(email);
    }

    /** Every email sent to {@code recipient}, oldest first. */
    public List<LinkEmail> to(String recipient) {
        return sent.stream().filter(email -> email.recipient().equals(recipient)).toList();
    }

    /** The token in the latest {@code type} email to {@code recipient}, if one was sent. */
    public Optional<String> latestToken(String recipient, CredentialTokenType type) {
        return to(recipient).stream().filter(email -> email.type() == type).reduce((first, second) -> second)
                .map(CapturedEmails::token);
    }

    /** The token a link carries in its fragment ({@code #token=...}). */
    public static String token(LinkEmail email) {
        String fragment = email.link().getRawFragment();
        return fragment.substring(fragment.indexOf('=') + 1);
    }
}
