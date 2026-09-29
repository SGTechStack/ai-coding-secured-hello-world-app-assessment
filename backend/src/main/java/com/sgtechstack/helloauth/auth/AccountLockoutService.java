package com.sgtechstack.helloauth.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sgtechstack.helloauth.user.User;
import com.sgtechstack.helloauth.user.UserRepository;

/**
 * Per-account failed-login tracking and lockout.
 * <p>
 * Each method is a short transaction holding a row lock on the user, so concurrent attempts
 * against one account are counted exactly. The slow BCrypt check runs between
 * {@link #begin(String)} and {@link #recordFailure(UUID)} / {@link #recordSuccess(UUID)},
 * outside any transaction.
 */
@Service
class AccountLockoutService {

	private final UserRepository users;

	private final LoginProperties.Lockout policy;

	private final Clock clock;

	AccountLockoutService(UserRepository users, LoginProperties properties, Clock clock) {
		this.users = users;
		this.policy = properties.lockout();
		this.clock = clock;
	}

	@Transactional
	public LoginAttempt begin(String username) {
		User user = this.users.findByUsernameForUpdate(username).orElse(null);
		if (user == null) {
			return new LoginAttempt.UnknownAccount();
		}
		Instant now = this.clock.instant();
		user.expireStaleLoginFailures(now, this.policy.window());
		// Reaching the threshold without a lock means the last permitted attempt is still being
		// checked by a concurrent request; treat the account as locked until that resolves.
		if (user.isLockedAt(now) || user.getFailedLoginAttempts() >= this.policy.maxAttempts()) {
			return new LoginAttempt.Locked(user.getUsername());
		}
		user.registerLoginAttempt(now);
		return new LoginAttempt.Permitted(user.getId(), user.getUsername(), user.getPasswordHash(), user.getRole(),
				user.isEnabled());
	}

	/**
	 * @return true if this failure locked the account
	 */
	@Transactional
	public boolean recordFailure(UUID userId) {
		User user = this.users.findByIdForUpdate(userId).orElse(null);
		if (user == null) {
			return false;
		}
		Instant now = this.clock.instant();
		if (user.getFailedLoginAttempts() >= this.policy.maxAttempts() && !user.isLockedAt(now)) {
			user.lockUntil(now.plus(this.policy.duration()));
			return true;
		}
		return false;
	}

	@Transactional
	public void recordSuccess(UUID userId) {
		this.users.findByIdForUpdate(userId).ifPresent(User::clearLoginFailures);
	}

}
