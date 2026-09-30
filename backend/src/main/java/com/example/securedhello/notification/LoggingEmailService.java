package com.example.securedhello.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * The {@link EmailService} stub, standing in for a mailbox (ADR 0001). Each email is one line on the
 * dedicated {@code email} logger, which writes only to its own file (see {@code logback-spring.xml}),
 * never to the application or audit log. The subject is the message; the recipient is written under
 * {@code email.to}, which the log formatter masks (as it masks every key naming an email).
 * <p>
 * Only in the {@code dev} profile does the file stand in for a mailbox a developer reads, so only
 * there is the reset link, with its plaintext Reset Token, written out (ADR 0001, IM8 lm-19). In
 * every other profile the reset email is still recorded, but the link is withheld: a Reset Token is a
 * credential, and a log file on a deployed host is not a mailbox. A deployment that needs working
 * password resets must replace this stub with real email delivery.
 */
@Component
class LoggingEmailService implements EmailService {

	static final String LOGGER_NAME = "email";

	/** The only profile in which the reset link, Reset Token included, is written out. */
	static final String LINK_WRITING_PROFILE = "dev";

	private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

	private final boolean writesResetLinks;

	LoggingEmailService(Environment environment) {
		this.writesResetLinks = environment.matchesProfiles(LINK_WRITING_PROFILE);
	}

	@Override
	public void notifyAccountLocked(String to) {
		send(to, "Account Locked");
	}

	@Override
	public void notifyPasswordChanged(String to) {
		send(to, "Password changed");
	}

	/**
	 * In {@code dev}, the reset link, with its Reset Token, is written in full under
	 * {@code reset.link}: that key name deliberately avoids every masked-key substring (password,
	 * token, secret, csrf, session id, email, username), so the encoder never masks it (ADR 0001).
	 * Elsewhere the link is never written, only {@code reset.link_withheld}. The recipient is always
	 * masked.
	 */
	@Override
	public void sendPasswordResetLink(String to, String resetLink) {
		if (writesResetLinks) {
			log.atInfo().addKeyValue("email.to", to).addKeyValue("reset.link", resetLink).log("Password reset requested");
		}
		else {
			log.atInfo().addKeyValue("email.to", to).addKeyValue("reset.link_withheld", true).log("Password reset requested");
		}
	}

	@Override
	public void notifyPasswordResetCompleted(String to) {
		send(to, "Password reset completed");
	}

	/** One email: the static subject is the message, the recipient a masked key. */
	private static void send(String to, String subject) {
		log.atInfo().addKeyValue("email.to", to).log(subject);
	}

}
