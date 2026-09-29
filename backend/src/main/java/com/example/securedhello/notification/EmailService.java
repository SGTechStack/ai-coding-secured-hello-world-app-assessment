package com.example.securedhello.notification;

/**
 * Emails to Account holders. Delivery is fire-and-forget: there is no retry while email is a stub
 * ({@link LoggingEmailService}). Tests replace it with a recording implementation.
 */
public interface EmailService {

	/** Tells the Account holder their Account has become Locked after repeated wrong passwords. */
	void notifyAccountLocked(String to);

	/** Tells the Account holder their password has been changed, so they can react if they did not do it. */
	void notifyPasswordChanged(String to);

	/**
	 * Sends the password-reset link, with the Reset Token written in full (ADR 0001). The link is
	 * never written anywhere but the stub's own file.
	 */
	void sendPasswordResetLink(String to, String resetLink);

	/** Tells the Account holder their password reset has completed, so they can react if they did not request it. */
	void notifyPasswordResetCompleted(String to);

}
