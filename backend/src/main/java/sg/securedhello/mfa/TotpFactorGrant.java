package sg.securedhello.mfa;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import sg.securedhello.session.MinimalSessionStrategy;

/**
 * Grants {@code FACTOR_TOTP} to the current session (ADR-021): a {@link FactorGrantedAuthority} issued at the shared
 * {@link Clock}'s instant (T-MFA-008), in a new {@code Authentication} that replaces the old one, since the factor
 * rules read the first matching authority (ADR-038). Any earlier {@code FACTOR_TOTP} is dropped. The session id and
 * CSRF token then rotate, without re-stamping {@code AUTH_INSTANT}, and the context is saved.
 */
@Component
public class TotpFactorGrant {

    /** The authority the admin factor rules require. */
    public static final String AUTHORITY = "FACTOR_TOTP";

    private final Clock clock;
    private final MinimalSessionStrategy rotation;

    TotpFactorGrant(Clock clock, MinimalSessionStrategy rotation) {
        this.clock = clock;
        this.rotation = rotation;
    }

    public void grant(Authentication current, HttpServletRequest request, HttpServletResponse response) {
        List<GrantedAuthority> authorities = new ArrayList<>(current.getAuthorities().stream()
                .filter(authority -> !AUTHORITY.equals(authority.getAuthority()))
                .toList());
        authorities.add(FactorGrantedAuthority.withAuthority(AUTHORITY).issuedAt(clock.instant()).build());
        UsernamePasswordAuthenticationToken granted =
                UsernamePasswordAuthenticationToken.authenticated(current.getPrincipal(), null, authorities);
        granted.setDetails(current.getDetails());
        SecurityContextHolder.getContext().setAuthentication(granted);
        rotation.onAuthentication(granted, request, response);
    }
}
