package sg.securedhello.user;

import java.util.Locale;
import java.util.Set;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * The unique indexes an identifier is inserted into (V2; V8), and how to tell a lost insert race on one of them from any
 * other integrity failure. Two writers of the same username or email address pass their checks at once; the one that
 * commits second hits the index, and its caller answers as if it had found the identifier taken.
 */
public final class UniqueIdentifierIndexes {

    /** {@code users.username} (V2). */
    public static final String USERS_USERNAME = "UX_USERS_USERNAME";

    /** {@code users.email} (V2). */
    public static final String USERS_EMAIL = "UX_USERS_EMAIL";

    /** {@code username_holds.username} (V8). */
    public static final String USERNAME_HOLDS_USERNAME = "UX_USERNAME_HOLDS_USERNAME";

    private UniqueIdentifierIndexes() {
    }

    /** Whether {@code e} is a violation of one of {@code indexes}, named as above, and not some other failure. */
    public static boolean violated(DataIntegrityViolationException e, Set<String> indexes) {
        String cause = String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                .toUpperCase(Locale.ROOT);
        return indexes.stream().anyMatch(cause::contains);
    }
}
