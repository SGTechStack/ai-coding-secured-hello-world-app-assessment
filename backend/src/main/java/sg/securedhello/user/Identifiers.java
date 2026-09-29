package sg.securedhello.user;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The one identifier canonicaliser (ADR-045) and the rules on each identifier. Canonical means Unicode NFC, then
 * trimmed, then lowercased, and nothing else: no dot or {@code +tag} folding. The canonical value is the stored value,
 * in live accounts and tombstones alike. Never applied to a password.
 * <ul>
 *   <li><b>A username is rejected</b> unless it is already canonical, matches {@code [a-z0-9._-]{3,32}} (so it has no
 *       {@code @}, REJ-027) and is not a reserved name (REJ-021).</li>
 *   <li><b>An email address is converted</b>: the whole address is lowercased (R-CRED-014), then checked for shape.</li>
 * </ul>
 */
public final class Identifiers {

    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3,32}");

    /** One {@code @}, a local part of at most 64 characters, and a dotted domain, with no whitespace anywhere. */
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]{1,64}@[^@\\s.]+(\\.[^@\\s.]+)+");

    /** The longest address the {@code email} column holds. */
    static final int EMAIL_MAX_LENGTH = 254;

    /**
     * Names no account may take, read by self-registration and by the bootstrap administrator's validator: one set,
     * two readers (ADR-047; REJ-021). It keeps predictable administrative names out of reach, an availability control
     * rather than an anti-enumeration one.
     */
    public static final Set<String> RESERVED_USERNAMES = Set.of("admin", "administrator", "root", "system", "sysadmin",
            "superuser", "support", "security", "webmaster", "postmaster", "hostmaster", "abuse", "noreply",
            "no-reply", "anonymous", "null", "undefined", "api", "www", "help", "info");

    private Identifiers() {
    }

    /** The canonical form of an identifier: NFC, then trim, then lowercase (ADR-045). */
    public static String canonical(String identifier) {
        return Normalizer.normalize(identifier, Normalizer.Form.NFC).strip().toLowerCase(Locale.ROOT);
    }

    /** Whether {@code submitted} may be a username as it stands: canonical, well-formed and not reserved. */
    public static boolean validUsername(String submitted) {
        return canonical(submitted).equals(submitted) && USERNAME.matcher(submitted).matches()
                && !RESERVED_USERNAMES.contains(submitted);
    }

    /** The canonical form of a submitted email address, or empty if it is not a well-formed address. */
    public static Optional<String> canonicalEmail(String submitted) {
        String canonical = canonical(submitted);
        return canonical.length() <= EMAIL_MAX_LENGTH && EMAIL.matcher(canonical).matches()
                ? Optional.of(canonical)
                : Optional.empty();
    }
}
