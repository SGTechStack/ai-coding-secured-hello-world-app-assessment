package com.example.securedhello.account;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.securedhello.config.LockoutProperties;
import com.example.securedhello.credential.CredentialPolicy;
import com.example.securedhello.ratelimit.IpThrottle;
import com.example.securedhello.ratelimit.RateLimitExceededException;
import com.example.securedhello.ratelimit.RateLimiters;

/**
 * Owns the login decision, in order: IP Throttle, per-username rate limit, credential check, Locked
 * and Disabled checks, failure counter update. Every refusal after the rate limit is the same
 * {@link AuthenticationFailedException}. An unknown username still costs one BCrypt comparison
 * against a dummy hash, so timing does not reveal whether it exists.
 * <p>
 * Each wrong password is counted on the Account; at the lockout threshold the Account becomes Locked
 * for the lockout duration, and the Account holder is notified. Both are stored on the Account, so
 * they survive a restart. A successful login resets the count. Every refused credential attempt also
 * counts against the client address in the IP Throttle, which never touches the Account.
 */
@Service
class AuthenticationGuard {

	private final AccountRepository accounts;

	private final CredentialPolicy credentialPolicy;

	private final LockoutProperties lockout;

	private final Clock clock;

	private final ApplicationEventPublisher events;

	private final RateLimiters rateLimiters;

	private final IpThrottle ipThrottle;

	AuthenticationGuard(AccountRepository accounts, CredentialPolicy credentialPolicy, LockoutProperties lockout,
			Clock clock, ApplicationEventPublisher events, RateLimiters rateLimiters, IpThrottle ipThrottle) {
		this.accounts = accounts;
		this.credentialPolicy = credentialPolicy;
		this.lockout = lockout;
		this.clock = clock;
		this.events = events;
		this.rateLimiters = rateLimiters;
		this.ipThrottle = ipThrottle;
	}

	/**
	 * Commits the failure counter even though the login is refused. The Account row is locked for the
	 * decision, so concurrent attempts on one Account are counted one after another.
	 * @param clientAddress the direct connection address, never a forwarded one
	 * @return the authenticated Account
	 * @throws RateLimitExceededException when the address is blocked by the IP Throttle or the
	 *         username has had too many attempts; nothing else is checked or counted
	 * @throws AuthenticationFailedException for an unknown username, a wrong password, or a Locked or
	 *         Disabled Account, without saying which
	 */
	@Transactional(noRollbackFor = AuthenticationFailedException.class)
	Account authenticate(String username, String password, String clientAddress) {
		ipThrottle.check(clientAddress);
		String key = username.toLowerCase(Locale.ROOT);
		rateLimiters.login().acquire(key);
		try {
			return checkCredentials(key, password);
		}
		catch (AuthenticationFailedException ex) {
			ipThrottle.recordFailure(clientAddress);
			throw ex;
		}
	}

	private Account checkCredentials(String key, String password) {
		Optional<Account> found = accounts.findForUpdateByUsername(key);
		if (found.isEmpty()) {
			credentialPolicy.matchesNoAccount(password);
			throw new AuthenticationFailedException();
		}
		Account account = found.get();
		Instant now = clock.instant();
		if (!credentialPolicy.matches(password, account.getPasswordHash())) {
			if (account.recordFailedLogin(now, lockout.threshold(), lockout.duration())) {
				// Sent once the counter commits, which it does although the login is refused.
				events.publishEvent(new AccountEvent.Locked(account.getId(), account.getEmail()));
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
