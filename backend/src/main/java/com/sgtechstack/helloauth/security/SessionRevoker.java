package com.sgtechstack.helloauth.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Deletes every server-side session belonging to a user, making their session cookies useless.
 */
@Component
public class SessionRevoker {

	private static final Logger log = LoggerFactory.getLogger(SessionRevoker.class);

	private final FindByIndexNameSessionRepository<? extends Session> sessions;

	public SessionRevoker(FindByIndexNameSessionRepository<? extends Session> sessions) {
		this.sessions = sessions;
	}

	/**
	 * Revokes the user's sessions. Inside a transaction this runs after commit, so a rollback
	 * does not log anyone out, and a login cannot slip in between revocation and the change
	 * (such as a new password) becoming visible.
	 */
	public void revokeAllSessionsOf(String username) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					revokeNow(username);
				}
			});
		}
		else {
			revokeNow(username);
		}
	}

	private void revokeNow(String username) {
		var sessionIds = this.sessions.findByPrincipalName(username).keySet();
		sessionIds.forEach(this.sessions::deleteById);
		log.debug("Revoked {} session(s)", sessionIds.size());
	}

}
