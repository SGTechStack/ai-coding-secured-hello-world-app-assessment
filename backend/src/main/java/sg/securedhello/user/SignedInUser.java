package sg.securedhello.user;

import java.io.Serial;
import java.time.Instant;
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
    /** Read by the pre-authentication checks only; a signed-in principal was neither locked nor capped. */
    private final boolean accountNonLocked;
    private final boolean passwordDisabled;
    /** Read by the pre-authentication checks only; a signed-in principal's credential had not expired. */
    private final boolean forcedChangeExpired;
    /** Session state: the credential must be changed before anything outside the allowlist (ADR-046). */
    private final boolean passwordChangeRequired;

    private SignedInUser(UUID id, String username, String role, boolean enabled, @Nullable String passwordHash,
            boolean accountNonLocked, boolean passwordDisabled, boolean forcedChangeExpired,
            boolean passwordChangeRequired) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.enabled = enabled;
        this.passwordHash = passwordHash;
        this.accountNonLocked = accountNonLocked;
        this.passwordDisabled = passwordDisabled;
        this.forcedChangeExpired = forcedChangeExpired;
        this.passwordChangeRequired = passwordChangeRequired;
    }

    /**
     * The account as the provider checks it at {@code now}: also locked while {@code locked_until} is ahead, capped
     * once the NIST cap has disabled its password (ADR-011; ADR-013), and expired once its forced-change credential
     * is more than 30 days old (ADR-046). The pre-authentication checks refuse all three, before the password is
     * compared, and the provider still runs {@code matches()} for them. A disabled or never-activated account is not
     * enabled. The forced-change flag is read once, here, and travels with the session.
     */
    public static SignedInUser of(UserAccount account, Instant now) {
        PasswordLockoutState lockout = account.getLockoutState();
        return new SignedInUser(account.getId(), account.getUsername(), account.getRole(),
                account.isEnabled() && account.getActivatedAt() != null, account.getPasswordHash(),
                !lockout.lockedAt(now), lockout.passwordDisabled(), account.forcedChangeExpiredAt(now),
                account.isForcePasswordChange());
    }

    /** This principal once its forced change is complete: the session's next request is no longer confined. */
    public SignedInUser withPasswordChanged() {
        return new SignedInUser(id, username, role, enabled, passwordHash, accountNonLocked, passwordDisabled, false,
                false);
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

    /** Derived from {@code locked_until}, never stored (REJ-017). */
    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    /** Whether the NIST cap has disabled this account's password authenticator (ADR-013). */
    public boolean passwordDisabled() {
        return passwordDisabled;
    }

    /** Whether the forced-change credential is past its 30-day grace (ADR-046); read by the pre-authentication checks. */
    public boolean forcedChangeExpired() {
        return forcedChangeExpired;
    }

    /** Whether the session must change its password before anything outside the forced-change allowlist. */
    public boolean passwordChangeRequired() {
        return passwordChangeRequired;
    }

    /**
     * Hard-wired: a forced-change credential's expiry is refused by the pre-authentication checks, before the password
     * is compared, never by {@code DefaultPostAuthenticationChecks} after it (ADR-046; REJ-019; T-ADM-030).
     */
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
