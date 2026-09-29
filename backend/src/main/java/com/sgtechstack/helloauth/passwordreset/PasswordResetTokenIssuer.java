package com.sgtechstack.helloauth.passwordreset;

import java.net.URI;
import java.time.Clock;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.sgtechstack.helloauth.audit.AuditEvent;
import com.sgtechstack.helloauth.audit.AuditLog;
import com.sgtechstack.helloauth.user.User;
import com.sgtechstack.helloauth.user.UserRepository;

/**
 * Issues a reset token and sends the email, off the request thread.
 */
@Component
class PasswordResetTokenIssuer {

	private final UserRepository users;

	private final PasswordResetTokenRepository tokens;

	private final EmailService emailService;

	private final PasswordResetProperties properties;

	private final Clock clock;

	private final AuditLog audit;

	private final TransactionTemplate transaction;

	PasswordResetTokenIssuer(UserRepository users, PasswordResetTokenRepository tokens, EmailService emailService,
			PasswordResetProperties properties, Clock clock, AuditLog audit, TransactionTemplate transaction) {
		this.users = users;
		this.tokens = tokens;
		this.emailService = emailService;
		this.properties = properties;
		this.clock = clock;
		this.audit = audit;
		this.transaction = transaction;
	}

	@Async
	public void issueFor(String email, String clientIp) {
		Issued issued = this.transaction.execute(status -> createToken(email));
		if (issued == null) {
			// The email address is not logged: unregistered addresses are someone else's PII.
			this.audit.event(AuditEvent.PASSWORD_RESET_REQUESTED).ip(clientIp).reason("NO_ELIGIBLE_ACCOUNT").log();
			return;
		}
		// Email is sent after commit, so the link always refers to a stored token.
		URI link = UriComponentsBuilder.fromUri(this.properties.resetUrl())
			// A fragment is never sent to servers or leaked in Referer headers.
			.fragment("token=" + issued.token())
			.build()
			.toUri();
		this.emailService.sendPasswordResetEmail(issued.email(), issued.username(), link);
		this.audit.event(AuditEvent.PASSWORD_RESET_REQUESTED).target(issued.username()).ip(clientIp).log();
	}

	private Issued createToken(String email) {
		User user = this.users.findByEmail(email).filter(User::isEnabled).orElse(null);
		if (user == null) {
			return null;
		}
		// Only the newest link works.
		this.tokens.deleteUnusedByUser(user);
		String token = ResetTokens.generate();
		this.tokens.save(new PasswordResetToken(user, ResetTokens.hash(token),
				this.clock.instant().plus(this.properties.tokenTtl())));
		return new Issued(token, user.getUsername(), user.getEmail());
	}

	private record Issued(String token, String username, String email) {

		@Override
		public String toString() {
			return "Issued[username=" + this.username + ", token=<redacted>]";
		}

	}

}
