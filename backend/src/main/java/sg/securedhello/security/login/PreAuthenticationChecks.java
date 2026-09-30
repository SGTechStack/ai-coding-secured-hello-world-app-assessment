package sg.securedhello.security.login;

import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;

import sg.securedhello.user.SignedInUser;

/**
 * The provider's pre-authentication checks, in a fixed order (ADR-013; ADR-046):
 * <ol>
 *   <li>disabled: an account an administrator disabled, or one never activated. The lock is not checked here: which
 *       lock applies depends on the sign-in's lane, which only the token shows, so the provider checks it just before
 *       the password (ADR-075; {@link ClockedPasswordFactorProvider});</li>
 *   <li>the NIST cap: a disabled password authenticator throws {@link PasswordDisabledException};</li>
 *   <li>the forced-change expiry: a forced-change credential issued more than 30 days ago throws Spring's
 *       {@link CredentialsExpiredException}, audited as {@code CREDENTIAL_EXPIRED}. It is here, not in
 *       {@code isCredentialsNonExpired()}, which is hard-wired {@code true} (T-ADM-030).</li>
 * </ol>
 *
 * <p>They run before the password is compared, so a refusal never depends on whether the password was right and its
 * audit reason is never a password oracle. With {@code alwaysPerformAdditionalChecksOnUser} left {@code true}, the
 * provider still runs {@code matches()} once for a refused account and rethrows the check's exception (T-AUTH-003).
 */
final class PreAuthenticationChecks implements UserDetailsChecker {

    @Override
    public void check(UserDetails user) {
        if (!user.isEnabled()) {
            throw new DisabledException("The account is disabled");
        }
        if (user instanceof SignedInUser account) {
            if (account.passwordDisabled()) {
                throw new PasswordDisabledException(account.id());
            }
            if (account.forcedChangeExpired()) {
                throw new CredentialsExpiredException("The forced-change credential has expired");
            }
        }
    }
}
