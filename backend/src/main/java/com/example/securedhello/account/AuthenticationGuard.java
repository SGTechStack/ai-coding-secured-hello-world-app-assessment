package com.example.securedhello.account;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.securedhello.config.LockoutProperties;
import com.example.securedhello.credential.CredentialPolicy;
import com.example.securedhello.notification.EmailService;
import com.example.securedhello.ratelimit.RateLimitExceededException;
import com.example.securedhello.ratelimit.RateLimiters;

/**
 * Owns the login decision, in order: per-username rate limit, credential check, Locked and Disabled
 * checks, failure counter update. Every refusal after the rate limit is the same
 * {@link AuthenticationFailedException}. An unknown username still costs one BCrypt comparison
 * against a dummy hash, so timing does not reveal whether it exists.
 * <p>
 * Each wrong password is counted on the Account; at the lockout threshold the Account becomes Locked
 * for the lockout duration, and the Account holder is notified. Both are stored on the Account, so
 * they survive a restart. A successful login resets the count. The IP Throttle goes in front of the
 * rate limit in a later ticket.
 */
@Service
class AuthenticationGuard {

	private final AccountRepository accounts;

	private final CredentialPolicy credentialPolicy;

	private final LockoutProperties lockout;

	private final Clock clock;

	private final EmailService emailService;

	private final RateLimiters rateLimiters;

	AuthenticationGuard(AccountRepository accounts, CredentialPolicy credentialPolicy, LockoutProperties lockout,
			Clock clock, EmailService emailService, RateLimiters rateLimiters) {
		this.accounts = accounts;
		this.credentialPolicy = credentialPolicy;
		this.lockout = lockout;
		this.clock = clock;
		this.emailService = emailService;
		this.rateLimiters = rateLimiters;
	}

	/**
	 * Commits the failure counter even though the login is refused. The Account row is locked for the
	 * decision, so concurrent attempts on one Account are counted one after another.
	 * @return the authenticated Account
	 * @throws RateLimitExceededException when the username has had too many attempts; nothing else is
	 *         checked or counted
	 * @throws AuthenticationFailedException for an unknown username, a wrong password, or a Locked or
	 *         Disabled Account, without saying which
	 */
	@Transactional(noRollbackFor = AuthenticationFailedException.class)
	Account authenticate(String username, String password) {
		String key = username.toLowerCase(Locale.ROOT);
		rateLimiters.login().acquire(key);
		Optional<Account> found = accounts.findForUpdateByUsername(key);
		if (found.isEmpty()) {
			credentialPolicy.matchesNoAccount(password);
			throw new AuthenticationFailedException();
		}
		Account account = found.get();
		Instant now = clock.instant();
		if (!credentialPolicy.matches(password, account.getPasswordHash())) {
			if (account.recordFailedLogin(now, lockout.threshold(), lockout.duration())) {
				emailService.notifyAccountLocked(account.getEmail());
				throw AuthenticationFailedException.lockedAccount(account.getId());
			}
			throw new AuthenticationFailedException();
		}
		if (account.isLocked(now) || !account.isEnabled()) {
			throw new AuthenticationFailedException();
		}
		account.recordSuccessfulLogin();
		return account;
	}

}
