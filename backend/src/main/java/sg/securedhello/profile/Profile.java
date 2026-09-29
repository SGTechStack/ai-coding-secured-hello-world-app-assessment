package sg.securedhello.profile;

import java.util.UUID;

import sg.securedhello.user.SignedInUser;

/**
 * The self-read (ADR-043): who the session belongs to. The SPA routes on it (belief), and the envelope {@code code}
 * of any later refusal overrides it (authority).
 *
 * @param id                     the account's UUID (ADR-050)
 * @param username               the username
 * @param role                   {@code USER} or {@code ADMIN}
 * @param passwordChangeRequired the session holds a forced-change credential: the SPA's first gate (ADR-046)
 * @param factors                the second-factor state
 */
public record Profile(UUID id, String username, String role, boolean passwordChangeRequired, Factors factors) {

    /**
     * The four factor members. Only administrators use a second factor (ADR-023), so a user's are all {@code false};
     * an administrator's are stubbed here too until TOTP enrolment and verification land.
     */
    public record Factors(boolean held, boolean required, boolean enrolled, boolean rebindRequired) {
    }

    public static Profile of(SignedInUser user) {
        return new Profile(user.id(), user.getUsername(), user.role(), user.passwordChangeRequired(),
                new Factors(false, "ADMIN".equals(user.role()), false, false));
    }
}
