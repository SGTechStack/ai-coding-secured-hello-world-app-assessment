package sg.securedhello.profile;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import sg.securedhello.mfa.TotpFactorStatus;
import sg.securedhello.user.SignedInUser;

/** Builds the self-read of a signed-in session, for {@code GET /api/profile} and the sign-in response. */
@Component
public class ProfileReader {

    private final TotpFactorStatus factorStatus;

    ProfileReader(TotpFactorStatus factorStatus) {
        this.factorStatus = factorStatus;
    }

    /** The self-read of {@code authentication}, whose principal is a {@link SignedInUser}. */
    public Profile read(Authentication authentication) {
        return Profile.of((SignedInUser) authentication.getPrincipal(), factorStatus.of(authentication));
    }
}
