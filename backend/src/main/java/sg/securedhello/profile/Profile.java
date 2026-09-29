package sg.securedhello.profile;

import java.util.UUID;

import sg.securedhello.mfa.TotpFactorStatus.Factors;
import sg.securedhello.user.SignedInUser;

/**
 * The self-read (ADR-043): who the session belongs to. The SPA routes on it (belief), and the envelope {@code code}
 * of any later refusal overrides it (authority).
 *
 * @param id                     the account's UUID (ADR-050)
 * @param username               the username
 * @param role                   {@code USER} or {@code ADMIN}
 * @param passwordChangeRequired the session holds a forced-change credential: the SPA's first gate (ADR-046)
 * @param factors                the second-factor state: {@code held}, {@code required}, {@code enrolled} and
 *                               {@code rebindRequired}
 */
public record Profile(UUID id, String username, String role, boolean passwordChangeRequired, Factors factors) {

    public static Profile of(SignedInUser user, Factors factors) {
        return new Profile(user.id(), user.getUsername(), user.role(), user.passwordChangeRequired(), factors);
    }
}
