package com.example.securedhello.account;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.example.securedhello.logging.AccountIdentified;

/**
 * The authenticated Account held in a Session. Its name is the Account's UUID, so Spring Session
 * indexes Sessions by UUID and never stores the username as the principal name.
 *
 * @param accountId the Account's UUID
 * @param username the Account's (lowercase) username, for the greeting
 * @param role the Account's role when the Session started
 */
public record AccountPrincipal(UUID accountId, String username, Role role)
		implements AccountIdentified, AuthenticatedPrincipal, Serializable {

	static AccountPrincipal of(Account account) {
		return new AccountPrincipal(account.getId(), account.getUsername(), account.getRole());
	}

	@Override
	public String getName() {
		return accountId.toString();
	}

	/** An authenticated token for this Account, with {@code ROLE_USER} or {@code ROLE_ADMIN}. */
	public Authentication toAuthentication() {
		return UsernamePasswordAuthenticationToken.authenticated(this, null,
				List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
	}

	/** Never includes the username, so a principal written to a log cannot leak it. */
	@Override
	public String toString() {
		return "AccountPrincipal[" + accountId + "]";
	}

}
