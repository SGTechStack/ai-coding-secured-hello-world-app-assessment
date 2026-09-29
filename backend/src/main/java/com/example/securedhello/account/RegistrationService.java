package com.example.securedhello.account;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.securedhello.credential.CredentialPolicy;

/** Creates Accounts for Visitors. Every registered Account is enabled and has the User role. */
@Service
class RegistrationService {

	private final CredentialPolicy credentialPolicy;

	private final AccountRepository accounts;

	private final PasswordHistoryRepository passwordHistory;

	private final Clock clock;

	RegistrationService(CredentialPolicy credentialPolicy, AccountRepository accounts,
			PasswordHistoryRepository passwordHistory, Clock clock) {
		this.credentialPolicy = credentialPolicy;
		this.accounts = accounts;
		this.passwordHistory = passwordHistory;
		this.clock = clock;
	}

	/**
	 * Registers an Account from already format-checked input.
	 * @return the new Account's UUID
	 * @throws com.example.securedhello.credential.PasswordPolicyException when the password breaks
	 *         the Credential policy
	 * @throws UserExistException when the username or email is taken, compared case-insensitively
	 */
	@Transactional
	UUID register(String username, String email, String password) {
		credentialPolicy.check(password);
		String normalisedUsername = username.toLowerCase(Locale.ROOT);
		String normalisedEmail = email.toLowerCase(Locale.ROOT);
		if (accounts.existsByUsernameOrEmail(normalisedUsername, normalisedEmail)) {
			throw new UserExistException();
		}
		Instant now = clock.instant();
		Account account = Account.registered(normalisedUsername, normalisedEmail, credentialPolicy.hash(password),
				now);
		try {
			accounts.saveAndFlush(account);
		}
		catch (DataIntegrityViolationException ex) {
			// A concurrent registration took the username or email after the check above.
			throw new UserExistException();
		}
		passwordHistory.save(new PasswordHistoryEntry(account.getId(), account.getPasswordHash(), now));
		return account.getId();
	}

}
