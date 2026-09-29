package com.eitri.auth;

import com.eitri.audit.AuditAccount;
import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated account held in the security context and serialized into the session. The password
 * hash is needed only while the DAO provider checks a login: {@link #eraseCredentials()}, which the
 * {@code ProviderManager} calls after a successful authentication, drops it, so the hash never reaches
 * the session store. {@link #toString()} names the account by UUID only, so a stray log of the
 * principal says no more than the id; audit lines name accounts through {@link #auditAccount()}.
 */
final class AccountPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID accountId;
    private final String username;
    private final Role role;
    private String password;

    AccountPrincipal(UUID accountId, String username, String password, Role role) {
        this.accountId = accountId;
        this.username = username;
        this.password = password;
        this.role = role;
    }

    UUID accountId() {
        return accountId;
    }

    String username() {
        return username;
    }

    Role role() {
        return role;
    }

    /** The id and username that name this account in audit lines. */
    AuditAccount auditAccount() {
        return new AuditAccount(accountId, username);
    }

    /** The same account with another role and no credentials, for refreshing a live session. */
    AccountPrincipal withRole(Role newRole) {
        return new AccountPrincipal(accountId, username, null, newRole);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public void eraseCredentials() {
        password = null;
    }

    /** Identity is the account, its username and role; credentials never take part. */
    @Override
    public boolean equals(Object other) {
        return other instanceof AccountPrincipal that
                && accountId.equals(that.accountId)
                && username.equals(that.username)
                && role == that.role;
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, username, role);
    }

    @Override
    public String toString() {
        return "AccountPrincipal[accountId=" + accountId + ", role=" + role + "]";
    }
}
