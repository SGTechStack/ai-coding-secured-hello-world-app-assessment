package sg.example.helloauth.account;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated Account as held in the session. It carries the Account id, so later lookups
 * and logs can use the UUID rather than the username. The password hash is erased after login.
 */
public final class AccountPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String username;
    private final Role role;
    private final boolean enabled;
    private final boolean locked;
    private String passwordHash;

    AccountPrincipal(Account account, boolean locked) {
        this.id = account.getId();
        this.username = account.getUsername();
        this.role = account.getRole();
        this.enabled = account.isEnabled();
        this.locked = locked;
        this.passwordHash = account.getPasswordHash();
    }

    public UUID id() {
        return id;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
