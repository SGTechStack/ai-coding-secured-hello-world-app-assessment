package sg.securedhello.security.login;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import sg.securedhello.security.lockout.LockoutLane;
import sg.securedhello.user.SignedInUser;

/**
 * The password provider, with two changes from {@link DaoAuthenticationProvider}:
 * <ul>
 *   <li><b>The lockout lane's lock check</b> (ADR-075). It needs the token, whose details carry the device claim, so it
 *       runs here, in {@link #additionalAuthenticationChecks}, not among the pre-authentication checks, which see only
 *       the account. It still runs before the password is compared, and a locked lane still costs exactly one
 *       {@code matches()}, whose result is ignored, before the uniform {@link LockedException} (T-AUTH-003): a trusted
 *       device's own lock, or else the account's untrusted lock, which never refuses a trusted device.</li>
 *   <li><b>The {@code FACTOR_PASSWORD} authority</b> is issued at the shared {@link Clock}'s instant (ADR-066). The
 *       framework issues it at the system clock's instant, but the admin factor rules check its age on the shared
 *       clock (ADR-021; ADR-026), so both must read the same one.</li>
 * </ul>
 */
final class ClockedPasswordFactorProvider extends DaoAuthenticationProvider {

    private final Clock clock;

    ClockedPasswordFactorProvider(UserDetailsService userDetailsService, Clock clock) {
        super(userDetailsService);
        this.clock = clock;
    }

    /**
     * Refuses a sign-in whose lane is locked, after the one {@code matches()} every request that reaches the provider
     * spends. The framework calls this once per authentication, also after a failed pre-authentication check, whose
     * exception then wins over this one.
     */
    @Override
    protected void additionalAuthenticationChecks(UserDetails user, UsernamePasswordAuthenticationToken token) {
        if (!(user instanceof SignedInUser account) || !LockoutLane.locked(token.getDetails(), account.id(),
                !account.isAccountNonLocked(), clock.instant())) {
            super.additionalAuthenticationChecks(user, token);
            return;
        }
        try {
            super.additionalAuthenticationChecks(user, token);
        } catch (AuthenticationException ignored) {
            // The lock refuses whether or not the password was right, so the refusal is never a password oracle.
        }
        throw new LockedException("The sign-in's lockout lane is locked");
    }

    @Override
    protected Authentication createSuccessAuthentication(Object principal, Authentication authentication,
            UserDetails user) {
        Authentication success = super.createSuccessAuthentication(principal, authentication, user);
        List<GrantedAuthority> authorities = new ArrayList<>(success.getAuthorities().stream()
                .filter(authority -> !FactorGrantedAuthority.PASSWORD_AUTHORITY.equals(authority.getAuthority()))
                .toList());
        authorities.add(FactorGrantedAuthority.withAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY)
                .issuedAt(clock.instant()).build());
        UsernamePasswordAuthenticationToken result = UsernamePasswordAuthenticationToken.authenticated(
                success.getPrincipal(), success.getCredentials(), authorities);
        result.setDetails(success.getDetails());
        return result;
    }
}
