package sg.securedhello.user;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;


/**
 * The principal of a signed-in session: the account's UUID, username and role. It is what the session stores, so it
 * is on the session attribute allowlist (R-SES-003). The password hash is held only while the provider checks it,
 * then erased before the security context is saved.
 */
public final class SignedInUser implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String username;
    private final String role;
    private final boolean enabled;
    private @Nullable String passwordHash;

    SignedInUser(UUID id, String username, String role, boolean enabled, @Nullable String passwordHash) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.enabled = enabled;
        this.passwordHash = passwordHash;
    }

    /**
     * The account as the provider checks it. A disabled or never-activated account is not enabled; the provider still
     * runs {@code matches()} for it, because {@code alwaysPerformAdditionalChecksOnUser} stays {@code true}.
     */
    public static SignedInUser of(UserAccount account) {
        return new SignedInUser(account.getId(), account.getUsername(), account.getRole(),
                account.isEnabled() && account.getActivatedAt() != null, account.getPasswordHash());
    }

    /** The account's UUID, the only identity audit rows carry (ADR-054). */
    public UUID id() {
        return id;
    }

    /** The role name, {@code USER} or {@code ADMIN} (ADR-042). */
    public String role() {
        return role;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public @Nullable String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /** Hard-wired: a forced-change credential's expiry is checked elsewhere, before authentication (REJ-019). */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SignedInUser user && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "SignedInUser[" + id + "]";
    }
}
