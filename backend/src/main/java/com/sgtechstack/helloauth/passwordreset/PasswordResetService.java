package com.sgtechstack.helloauth.passwordreset;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sgtechstack.helloauth.audit.AuditEvent;
import com.sgtechstack.helloauth.audit.AuditLog;
import com.sgtechstack.helloauth.security.SessionRevoker;
import com.sgtechstack.helloauth.throttle.AttemptThrottle;
import com.sgtechstack.helloauth.user.Identifiers;
import com.sgtechstack.helloauth.user.User;
import com.sgtechstack.helloauth.web.TooManyRequestsException;

@Service
public class PasswordResetService {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

	private final PasswordResetTokenIssuer issuer;

	private final PasswordResetTokenRepository tokens;

	private final PasswordEncoder passwordEncoder;

	private final SessionRevoker sessionRevoker;

	private final AttemptThrottle ipThrottle;

	private final Clock clock;

	private final AuditLog audit;

	PasswordResetService(PasswordResetTokenIssuer issuer, PasswordResetTokenRepository tokens,
			PasswordEncoder passwordEncoder, SessionRevoker sessionRevoker, PasswordResetProperties properties,
			Clock clock, AuditLog audit) {
		this.issuer = issuer;
		this.tokens = tokens;
		this.passwordEncoder = passwordEncoder;
		this.sessionRevoker = sessionRevoker;
		this.ipThrottle = new AttemptThrottle(properties.ipThrottle().maxRequests(),
				properties.ipThrottle().window(), clock);
		this.clock = clock;
		this.audit = audit;
	}

	/**
	 * Starts a reset. Returns the same way whether or not the email is registered: the lookup,
	 * token and email all happen asynchronously, so response time cannot reveal the answer either.
	 */
	public void requestReset(String email, String clientIp) {
		Optional<Duration> throttled = this.ipThrottle.tryAcquire(clientIp);
		if (throttled.isPresent()) {
			this.audit.event(AuditEvent.PASSWORD_RESET_THROTTLED).ip(clientIp).log();
			throw new TooManyRequestsException(throttled.get());
		}
		this.issuer.issueFor(Identifiers.normalizeEmail(email), clientIp);
	}

	/**
	 * Redeems a token: sets the new password (already policy-checked), burns the token, clears
	 * any lockout, and revokes every existing session of the user.
	 */
	@Transactional
	public void confirm(String token, String newPassword, String clientIp) {
		Instant now = this.clock.instant();
		PasswordResetToken resetToken = this.tokens.findByTokenHashForUpdate(ResetTokens.hash(token)).orElse(null);
		String rejection = rejectionReason(resetToken, now);
		if (rejection != null) {
			this.audit.event(AuditEvent.PASSWORD_RESET_REJECTED)
				.target((resetToken != null) ? resetToken.getUser().getUsername() : null)
				.ip(clientIp)
				.reason(rejection)
				.log();
			throw new InvalidResetTokenException();
		}

		User user = resetToken.getUser();
		resetToken.markUsed(now);
		user.changePassword(this.passwordEncoder.encode(newPassword));
		user.clearLoginFailures();
		this.sessionRevoker.revokeAllSessionsOf(user.getUsername());
		this.audit.event(AuditEvent.PASSWORD_RESET_COMPLETED).target(user.getUsername()).ip(clientIp).log();
	}

	@Scheduled(cron = "${app.password-reset.cleanup-cron:0 17 * * * *}")
	@Transactional
	public void deleteExpiredTokens() {
		int deleted = this.tokens.deleteExpiredBefore(this.clock.instant());
		if (deleted > 0) {
			log.info("Deleted {} expired password reset token(s)", deleted);
		}
	}

	private static String rejectionReason(PasswordResetToken token, Instant now) {
		if (token == null) {
			return "UNKNOWN_TOKEN";
		}
		if (token.getUsedAt() != null) {
			return "TOKEN_ALREADY_USED";
		}
		if (!token.isUsable(now)) {
			return "TOKEN_EXPIRED";
		}
		if (!token.getUser().isEnabled()) {
			return "ACCOUNT_DISABLED";
		}
		return null;
	}

}
