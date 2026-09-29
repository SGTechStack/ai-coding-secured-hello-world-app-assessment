package sg.securedhello.security.login;

import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;

import sg.securedhello.user.SignedInUser;

/**
 * The provider's pre-authentication checks, in a fixed order (ADR-013; ADR-046):
 * <ol>
 *   <li>Spring's account-status checks: locked, then disabled (then expired, which never applies);</li>
 *   <li>the NIST cap: a disabled password authenticator throws {@link PasswordDisabledException};</li>
 *   <li>the forced-change expiry (ADR-046). A documented no-op until the forced-change credential exists: nothing
 *       stamps {@code credential_issued_at} yet, so no credential can have expired. Its checker, throwing
 *       {@code CredentialsExpiredException}, is composed here, last.</li>
 * </ol>
 *
 * <p>They run before the password is compared, so a refusal never depends on whether the password was right and its
 * audit reason is never a password oracle. With {@code alwaysPerformAdditionalChecksOnUser} left {@code true}, the
 * provider still runs {@code matches()} once for a refused account and rethrows the check's exception (T-AUTH-003).
 */
final class PreAuthenticationChecks implements UserDetailsChecker {

    private final UserDetailsChecker accountStatus = new AccountStatusUserDetailsChecker();

    @Override
    public void check(UserDetails user) {
        accountStatus.check(user);
        if (user instanceof SignedInUser account && account.passwordDisabled()) {
            throw new PasswordDisabledException(account.id());
        }
    }
}
