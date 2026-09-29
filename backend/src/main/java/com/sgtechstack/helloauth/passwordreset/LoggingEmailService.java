package com.sgtechstack.helloauth.passwordreset;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Development stub from the PRD: logs the reset link instead of sending mail.
 * <p>
 * The link is a live credential, so this bean is excluded from the prod profile. Production
 * must provide a real {@link EmailService}, and the application will not start without one.
 */
@Service
@Profile("!prod")
class LoggingEmailService implements EmailService {

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

	@Override
	public void sendPasswordResetEmail(String toEmail, String username, URI resetLink) {
		log.info("[EMAIL STUB - not sent] Password reset link for user '{}': {}", username, resetLink);
	}

}
