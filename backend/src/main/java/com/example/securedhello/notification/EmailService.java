package com.example.securedhello.notification;

/**
 * Emails to Account holders. Delivery is fire-and-forget: there is no retry while email is a stub
 * ({@link LoggingEmailService}). Tests replace it with a recording implementation.
 */
public interface EmailService {

	/** Tells the Account holder their Account has become Locked after repeated wrong passwords. */
	void notifyAccountLocked(String to);

}
