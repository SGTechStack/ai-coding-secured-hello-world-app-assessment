package com.example.securedhello.account;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.securedhello.config.LockoutProperties;
import com.example.securedhello.credential.CredentialPolicy;
import com.example.securedhello.notification.EmailService;

/**
 * Password Change: a logged-in Account holder replaces their known password. Checks, in order, the
 * current password, the Credential policy and the Password History; then stores the new hash and
 * records it in the Password History (keeping only the latest entries), and cancels any pending
 * Reset Token. Notifying the Account holder and ending the Account's Sessions are the caller's job,
 * after this transaction commits.
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

	private final EmailService emailService;

	private final Clock clock;

	PasswordChangeService(AccountRepository accounts, CredentialPolicy credentialPolicy,
			PasswordUpdater passwordUpdater, PasswordResetTokenCanceller resetTokenCanceller, LockoutProperties lockout,
			EmailService emailService, Clock clock) {
		this.accounts = accounts;
		this.credentialPolicy = credentialPolicy;
		this.passwordUpdater = passwordUpdater;
		this.resetTokenCanceller = resetTokenCanceller;
		this.lockout = lockout;
		this.emailService = emailService;
		this.clock = clock;
	}

	/**
	 * Changes the Account's password. The Account row is locked for the change, so concurrent changes
	 * and logins run one after another and each sees the other's history and failure count. The
	 * failure count is committed even though a wrong current password is refused.
	 * @return the Account's email, for the "password changed" notification once this has committed
	 * @throws CurrentPasswordInvalidException when the current password is wrong or the Account is
	 *         Locked
	 * @throws com.example.securedhello.credential.PasswordPolicyException when the new password
	 *         breaks the Credential policy
	 * @throws com.example.securedhello.credential.PasswordHistoryException when the new password is
	 *         the current one or another in the Password History
	 */
	@Transactional(noRollbackFor = CurrentPasswordInvalidException.class)
	String change(UUID accountId, String currentPassword, String newPassword) {
		Account account = accounts.findForUpdateById(accountId)
			.orElseThrow(() -> new InsufficientAuthenticationException("Account no longer exists"));
		Instant now = clock.instant();
		if (!credentialPolicy.matches(currentPassword, account.getPasswordHash())) {
			boolean newlyLocked = account.recordFailedLogin(now, lockout.threshold(), lockout.duration());
			if (newlyLocked) {
				emailService.notifyAccountLocked(account.getEmail());
			}
			throw new CurrentPasswordInvalidException(newlyLocked);
		}
		if (account.isLocked(now)) {
			throw new CurrentPasswordInvalidException(false);
		}
		passwordUpdater.apply(account, newPassword, now);
		resetTokenCanceller.cancelPending(accountId, now);
		return account.getEmail();
	}

}
