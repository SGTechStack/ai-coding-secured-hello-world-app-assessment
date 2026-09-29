package com.example.securedhello.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The {@link EmailService} stub, standing in for a mailbox (ADR 0001). Each email is one line on the
 * dedicated {@code email} logger, which writes only to its own file (see {@code logback-spring.xml}),
 * never to the application or audit log. The subject is the message; the recipient is written under
 * {@code email.to}, which the log formatter masks (as it masks every key naming an email).
 */
@Component
class LoggingEmailService implements EmailService {

	static final String LOGGER_NAME = "email";

	private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

	@Override
	public void notifyAccountLocked(String to) {
		send(to, "Account Locked");
	}

	@Override
	public void notifyPasswordChanged(String to) {
		send(to, "Password changed");
	}

	/** One email: the static subject is the message, the recipient a masked key. */
	private static void send(String to, String subject) {
		log.atInfo().addKeyValue("email.to", to).log(subject);
	}

}
