package com.example.securedhello.account;

import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.securedhello.credential.CredentialPolicy;

/**
 * Owns the login decision: credential check, then the Disabled check. Every refusal is the same
 * {@link AuthenticationFailedException}. An unknown username still costs one BCrypt comparison
 * against a dummy hash, so timing does not reveal whether it exists.
 * <p>
 * Lockout, the per-username rate limit and the IP Throttle are added in front of and after the
 * credential check by later tickets.
 */
@Service
class AuthenticationGuard {

	private final AccountRepository accounts;

	private final CredentialPolicy credentialPolicy;

	AuthenticationGuard(AccountRepository accounts, CredentialPolicy credentialPolicy) {
		this.accounts = accounts;
		this.credentialPolicy = credentialPolicy;
	}

	/**
	 * @return the authenticated Account
	 * @throws AuthenticationFailedException for an unknown username, a wrong password or a
	 *         Disabled Account, without saying which
	 */
	@Transactional(readOnly = true)
	Account authenticate(String username, String password) {
		Optional<Account> found = accounts.findByUsername(username.toLowerCase(Locale.ROOT));
		boolean credentialsMatch = found.isPresent()
				? credentialPolicy.matches(password, found.get().getPasswordHash())
				: credentialPolicy.matchesNoAccount(password);
		if (!credentialsMatch || !found.get().isEnabled()) {
			throw new AuthenticationFailedException();
		}
		return found.get();
	}

}
