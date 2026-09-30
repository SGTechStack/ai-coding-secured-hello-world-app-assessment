package sg.securedhello.testsupport;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The fixture allocator: the one source of the synthetic usernames and email addresses the test harness gives its
 * accounts ({@link Accounts}, {@link Registrations}, {@link PasswordResets}). Every username is fresh for the run and
 * every address sits under the reserved {@value #DOMAIN} domain, so no fixture ever names a real person or mailbox
 * (Std §5:525; MFA §5:408; LOG §5:379) and keyed isolation holds across the shared contexts.
 */
public final class FixtureIdentities {

    /** RFC 2606's reserved testing domain. */
    public static final String DOMAIN = "example.test";

    /** Every username handed out in this run; one is never handed out twice. */
    private static final Set<String> ISSUED = new HashSet<>();

    private FixtureIdentities() {
    }

    /**
     * A username never handed out before in this run: {@code prefix} and random hex, {@code length} characters in all,
     * canonical and free of {@code @}.
     */
    public static synchronized String username(String prefix, int length) {
        if (prefix.length() >= length || length - prefix.length() > 32 || !prefix.matches("[a-z0-9-]*")) {
            throw new IllegalArgumentException("prefix must be shorter than the username, lowercase and plain");
        }
        String username;
        do {
            username = prefix + randomHex(length - prefix.length());
        } while (!ISSUED.add(username));
        return username;
    }

    /** The address a fixture account with {@code username} has: always under {@value #DOMAIN}. */
    public static String email(String username) {
        return username + "@" + DOMAIN;
    }

    /** Up to 32 random hex characters, from a random UUID (never a hand-drawn random source; T-ARCH-004). */
    private static String randomHex(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length);
    }
}
