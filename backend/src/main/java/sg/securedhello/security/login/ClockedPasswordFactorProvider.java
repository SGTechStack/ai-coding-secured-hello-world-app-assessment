package sg.securedhello.security.login;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * The password provider, with its {@code FACTOR_PASSWORD} authority issued at the shared {@link Clock}'s instant
 * (ADR-066). The framework issues it at the system clock's instant, but the admin factor rules check its age on the
 * shared clock (ADR-021; ADR-026), so both must read the same one. Nothing else differs from
 * {@link DaoAuthenticationProvider}.
 */
final class ClockedPasswordFactorProvider extends DaoAuthenticationProvider {

    private final Clock clock;

    ClockedPasswordFactorProvider(UserDetailsService userDetailsService, Clock clock) {
        super(userDetailsService);
        this.clock = clock;
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
