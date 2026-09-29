package com.sgtechstack.helloauth.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.sgtechstack.helloauth.audit.AuditEvent;
import com.sgtechstack.helloauth.audit.AuditLog;
import com.sgtechstack.helloauth.security.AuthenticatedUser;
import com.sgtechstack.helloauth.security.PasswordPolicy;
import com.sgtechstack.helloauth.throttle.AttemptThrottle;
import com.sgtechstack.helloauth.user.Identifiers;
import com.sgtechstack.helloauth.web.TooManyRequestsException;

/**
 * Verifies credentials with IP throttling and per-account lockout.
 */
@Service
public class LoginService {

	private final AccountLockoutService lockout;

	private final PasswordEncoder passwordEncoder;

	private final AttemptThrottle ipThrottle;

	private final AuditLog audit;

	/** Compared against when there is no real hash to check, so every path costs one BCrypt. */
	private final String timingDummyHash;

	LoginService(AccountLockoutService lockout, PasswordEncoder passwordEncoder, LoginProperties properties,
			Clock clock, AuditLog audit) {
		this.lockout = lockout;
		this.passwordEncoder = passwordEncoder;
		this.ipThrottle = new AttemptThrottle(properties.ipThrottle().maxFailures(), properties.ipThrottle().window(),
				clock);
		this.audit = audit;
		this.timingDummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	/**
	 * @throws InvalidCredentialsException for any failure, without saying which
	 * @throws TooManyRequestsException if the client IP is throttled
	 */
	public AuthenticatedUser authenticate(String username, String password, String clientIp) {
		// The IP throttle is checked first and independently of any account, so a throttled
		// source can neither keep guessing nor keep adding failures to a victim's account.
		Optional<Duration> throttled = this.ipThrottle.tryAcquire(clientIp);
		if (throttled.isPresent()) {
			this.audit.event(AuditEvent.LOGIN_THROTTLED).ip(clientIp).log();
			throw new TooManyRequestsException(throttled.get());
		}

		LoginAttempt attempt = this.lockout.begin(Identifiers.normalizeUsername(username));
		return switch (attempt) {
			case LoginAttempt.UnknownAccount unknown -> {
				this.passwordEncoder.matches(password, this.timingDummyHash);
				// The attempted username is not logged: users sometimes type a password into it.
				throw failed(clientIp, null, "UNKNOWN_USERNAME");
			}
			case LoginAttempt.Locked locked -> {
				this.passwordEncoder.matches(password, this.timingDummyHash);
				throw failed(clientIp, locked.username(), "ACCOUNT_LOCKED");
			}
			case LoginAttempt.Permitted account -> verify(account, password, clientIp);
		};
	}

	private AuthenticatedUser verify(LoginAttempt.Permitted account, String password, String clientIp) {
		if (!passwordMatches(password, account.passwordHash())) {
			if (this.lockout.recordFailure(account.id())) {
				this.audit.event(AuditEvent.ACCOUNT_LOCKED).target(account.username()).ip(clientIp).log();
			}
			throw failed(clientIp, account.username(), "BAD_PASSWORD");
		}
		this.lockout.recordSuccess(account.id());
		if (!account.enabled()) {
			throw failed(clientIp, account.username(), "ACCOUNT_DISABLED");
		}
		this.ipThrottle.release(clientIp);
		this.audit.event(AuditEvent.LOGIN_SUCCEEDED).actor(account.username()).ip(clientIp).log();
		return new AuthenticatedUser(account.id(), account.username(), account.role());
	}

	private boolean passwordMatches(String password, String passwordHash) {
		if (PasswordPolicy.exceedsMaxBytes(password)) {
			// BCrypt would truncate to 72 bytes and could accept it; no stored password is this long.
			this.passwordEncoder.matches(password, this.timingDummyHash);
			return false;
		}
		return this.passwordEncoder.matches(password, passwordHash);
	}

	private InvalidCredentialsException failed(String clientIp, String target, String reason) {
		this.audit.event(AuditEvent.LOGIN_FAILED).target(target).ip(clientIp).reason(reason).log();
		return new InvalidCredentialsException();
	}

}
