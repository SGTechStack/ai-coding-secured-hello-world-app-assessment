package sg.securedhello.mfa;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.stereotype.Component;

import sg.securedhello.user.SignedInUser;

/**
 * The second-factor state of a signed-in session, as the self-read reports it (spec, API surface). The SPA routes on
 * it (belief); the envelope {@code code} of a later refusal overrides it (authority).
 */
@Component
public class TotpFactorStatus {

    /**
     * The four factor members. Only administrators use a second factor (ADR-023), so a user's are all {@code false}.
     *
     * @param held           the session holds {@code FACTOR_TOTP}, of any age
     * @param required       the account's role requires the factor on the admin surface
     * @param enrolled       a confirmed factor exists (ADR-053)
     * @param rebindRequired tier 2 has disabled the factor, so it must be rebound (ADR-027; REJ-049)
     */
    public record Factors(boolean held, boolean required, boolean enrolled, boolean rebindRequired) {

        /** A regular user's: no factor is offered or required. */
        public static final Factors NONE = new Factors(false, false, false, false);
    }

    private static final String ADMIN = "ADMIN";

    private final TotpUserDetailsRepository factors;

    TotpFactorStatus(TotpUserDetailsRepository factors) {
        this.factors = factors;
    }

    /**
     * Whether {@code user}'s sign-in needs the second factor: an administrator's does (ADR-023), so its sign-in is
     * complete only at a verified code, which is when its browser becomes a trusted device (ADR-075).
     */
    public static boolean requiredFor(SignedInUser user) {
        return ADMIN.equals(user.role());
    }

    /** The factor state of {@code authentication}, whose principal is a {@link SignedInUser}. */
    public Factors of(Authentication authentication) {
        SignedInUser user = (SignedInUser) authentication.getPrincipal();
        if (!requiredFor(user)) {
            return Factors.NONE;
        }
        boolean held = authentication.getAuthorities().stream().anyMatch(authority -> authority
                instanceof FactorGrantedAuthority && TotpFactorGrant.AUTHORITY.equals(authority.getAuthority()));
        Optional<TotpUserDetails> factor = factors.findById(user.id());
        return new Factors(held, true, factor.isPresent(), factor.map(TotpUserDetails::isDisabled).orElse(false));
    }
}
