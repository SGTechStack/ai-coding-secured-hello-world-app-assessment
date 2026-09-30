package com.example.securedhello.account;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.securedhello.config.LockoutProperties;
import com.example.securedhello.credential.CredentialPolicy;

/**
 * Password Change: a logged-in Account holder replaces their known password. Checks, in order, the
 * current password, the Credential policy and the Password History; then stores the new hash and
 * records it in the Password History (keeping only the latest entries), and cancels any pending
 * Reset Token. It publishes {@link AccountEvent.PasswordChanged}, so the Account's Sessions are
 * ended, the change audited and the holder notified once this transaction commits.
 * <p>
 * A wrong current password counts toward lockout exactly like a failed login (same counter,
 * threshold and duration, and the Account holder is notified when it locks), so a hijacked Session
 * cannot guess the password without limit. A Locked Account is refused even the correct current
 * password, with the same error.
 */
@Service
class PasswordChangeService {

	private final AccountRepository accounts;

	private final CredentialPolicy credentialPolicy;

	private final PasswordUpdater passwordUpdater;

	private final PasswordResetTokenCanceller resetTokenCanceller;

	private final LockoutProperties lockout;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	PasswordChangeService(AccountRepository accounts, CredentialPolicy credentialPolicy,
			PasswordUpdater passwordUpdater, PasswordResetTokenCanceller resetTokenCanceller, LockoutProperties lockout,
			ApplicationEventPublisher events, Clock clock) {
		this.accounts = accounts;
		this.credentialPolicy = credentialPolicy;
		this.passwordUpdater = passwordUpdater;
		this.resetTokenCanceller = resetTokenCanceller;
		this.lockout = lockout;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * Changes the Account's password. The Account row is locked for the change, so concurrent changes
	 * and logins run one after another and each sees the other's history and failure count. The
	 * failure count is committed even though a wrong current password is refused, and so is the
	 * Account-locked notification when it locks the Account.
	 * @throws CurrentPasswordInvalidException when the current password is wrong or the Account is
	 *         Locked
	 * @throws com.example.securedhello.credential.PasswordPolicyException when the new password
	 *         breaks the Credential policy
	 * @throws com.example.securedhello.credential.PasswordHistoryException when the new password is
	 *         the current one or another in the Password History
	 */
	@Transactional(noRollbackFor = CurrentPasswordInvalidException.class)
	void change(UUID accountId, String currentPassword, String newPassword) {
		// RequiredPasswordChangeFilter already refused a Session whose Account is gone. The row still has
		// to be read here, under its lock, and a delete may have committed while this request waited.
		Account account = accounts.findForUpdateById(accountId).orElseThrow(CurrentAccount::gone);
		Instant now = clock.instant();
		if (!credentialPolicy.matches(currentPassword, account.getPasswordHash())) {
			boolean newlyLocked = account.recordFailedLogin(now, lockout.threshold(), lockout.duration());
			if (newlyLocked) {
				events.publishEvent(new AccountEvent.Locked(accountId, account.getEmail()));
			}
			throw new CurrentPasswordInvalidException(newlyLocked);
		}
		if (account.isLocked(now)) {
			throw new CurrentPasswordInvalidException(false);
		}
		boolean requirementCleared = passwordUpdater.apply(account, newPassword, now);
		resetTokenCanceller.cancelPending(accountId, now);
		events.publishEvent(new AccountEvent.PasswordChanged(accountId, account.getEmail(), requirementCleared));
	}

}
