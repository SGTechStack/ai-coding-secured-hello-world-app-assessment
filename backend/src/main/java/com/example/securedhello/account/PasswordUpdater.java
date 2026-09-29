package com.example.securedhello.account;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.example.securedhello.credential.CredentialPolicy;

/**
 * Applies the Credential policy and Password History to a candidate new password, then stores it:
 * shared by Password Change and reset confirmation, which differ only in how they establish the
 * caller is allowed to set it (a known current password vs. a redeemed Reset Token).
 */
@Component
class PasswordUpdater {

	private final PasswordHistoryRepository passwordHistory;

	private final CredentialPolicy credentialPolicy;

	PasswordUpdater(PasswordHistoryRepository passwordHistory, CredentialPolicy credentialPolicy) {
		this.passwordHistory = passwordHistory;
		this.credentialPolicy = credentialPolicy;
	}

	/**
	 * Checks {@code newPassword} against the Credential policy and the Account's Password History
	 * (the latest {@link CredentialPolicy#historyLength()} passwords, the current one included), then
	 * replaces the Account's password and records it in the Password History, keeping only the latest
	 * entries.
	 * @throws com.example.securedhello.credential.PasswordPolicyException when the new password
	 *         breaks the Credential policy
	 * @throws com.example.securedhello.credential.PasswordHistoryException when the new password is
	 *         the current one or another in the Password History
	 */
	void apply(Account account, String newPassword, Instant now) {
		credentialPolicy.check(newPassword);
		List<PasswordHistoryEntry> history = passwordHistory.findByUserIdOrderByCreatedAtDesc(account.getId());
		int keep = credentialPolicy.historyLength();
		// The current hash is always checked, even if its history entry is missing.
		credentialPolicy.checkHistory(newPassword,
				Stream.concat(Stream.of(account.getPasswordHash()), history.stream().map(PasswordHistoryEntry::getPasswordHash))
					.distinct()
					.limit(keep)
					.toList());

		account.changePassword(credentialPolicy.hash(newPassword));
		passwordHistory.save(new PasswordHistoryEntry(account.getId(), account.getPasswordHash(), now));
		if (history.size() >= keep) {
			// The new entry is one of the kept ones, so only keep - 1 older ones remain.
			passwordHistory.deleteAll(history.subList(keep - 1, history.size()));
		}
	}

}
