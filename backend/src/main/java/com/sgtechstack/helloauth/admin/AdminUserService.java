package com.sgtechstack.helloauth.admin;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sgtechstack.helloauth.admin.AdminDtos.AdminUserResponse;
import com.sgtechstack.helloauth.admin.AdminDtos.UserPage;
import com.sgtechstack.helloauth.admin.AdminExceptions.SelfActionNotAllowedException;
import com.sgtechstack.helloauth.admin.AdminExceptions.UserNotFoundException;
import com.sgtechstack.helloauth.audit.AuditEvent;
import com.sgtechstack.helloauth.audit.AuditLog;
import com.sgtechstack.helloauth.passwordreset.PasswordResetTokenRepository;
import com.sgtechstack.helloauth.security.AuthenticatedUser;
import com.sgtechstack.helloauth.security.SessionRevoker;
import com.sgtechstack.helloauth.user.Role;
import com.sgtechstack.helloauth.user.User;
import com.sgtechstack.helloauth.user.UserRepository;

/**
 * Account management for admins. Every change that reduces what a user may do also revokes
 * their sessions, so it takes effect immediately rather than when the session expires.
 */
@Service
class AdminUserService {

	private static final Sort LIST_ORDER = Sort.by("createdAt", "username");

	private final UserRepository users;

	private final PasswordResetTokenRepository resetTokens;

	private final SessionRevoker sessionRevoker;

	private final AuditLog audit;

	AdminUserService(UserRepository users, PasswordResetTokenRepository resetTokens, SessionRevoker sessionRevoker,
			AuditLog audit) {
		this.users = users;
		this.resetTokens = resetTokens;
		this.sessionRevoker = sessionRevoker;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public UserPage list(int page, int size) {
		return UserPage.from(this.users.findAll(PageRequest.of(page, size, LIST_ORDER)));
	}

	@Transactional
	public AdminUserResponse setEnabled(AuthenticatedUser actor, UUID targetId, boolean enabled, String clientIp) {
		User target = loadOtherUser(actor, targetId, enabled ? AuditEvent.USER_ENABLED : AuditEvent.USER_DISABLED,
				clientIp);
		target.setEnabled(enabled);
		if (!enabled) {
			this.sessionRevoker.revokeAllSessionsOf(target.getUsername());
		}
		this.audit.event(enabled ? AuditEvent.USER_ENABLED : AuditEvent.USER_DISABLED)
			.actor(actor.username())
			.target(target.getUsername())
			.ip(clientIp)
			.log();
		return AdminUserResponse.from(target);
	}

	@Transactional
	public AdminUserResponse changeRole(AuthenticatedUser actor, UUID targetId, Role role, String clientIp) {
		User target = loadOtherUser(actor, targetId, AuditEvent.USER_ROLE_CHANGED, clientIp);
		Role previous = target.getRole();
		if (previous != role) {
			target.changeRole(role);
			// Sessions hold the old authorities; a demoted admin must not keep admin access.
			this.sessionRevoker.revokeAllSessionsOf(target.getUsername());
		}
		this.audit.event(AuditEvent.USER_ROLE_CHANGED)
			.actor(actor.username())
			.target(target.getUsername())
			.ip(clientIp)
			.with("from", previous)
			.with("to", role)
			.log();
		return AdminUserResponse.from(target);
	}

	@Transactional
	public void delete(AuthenticatedUser actor, UUID targetId, String clientIp) {
		User target = loadOtherUser(actor, targetId, AuditEvent.USER_DELETED, clientIp);
		this.resetTokens.deleteAllByUser(target);
		this.users.delete(target);
		this.sessionRevoker.revokeAllSessionsOf(target.getUsername());
		this.audit.event(AuditEvent.USER_DELETED)
			.actor(actor.username())
			.target(target.getUsername())
			.ip(clientIp)
			.log();
	}

	private User loadOtherUser(AuthenticatedUser actor, UUID targetId, AuditEvent attempted, String clientIp) {
		if (actor.id().equals(targetId)) {
			this.audit.event(AuditEvent.ADMIN_ACTION_REJECTED)
				.actor(actor.username())
				.target(actor.username())
				.ip(clientIp)
				.reason("SELF_ACTION")
				.with("action", attempted)
				.log();
			throw new SelfActionNotAllowedException();
		}
		return this.users.findByIdForUpdate(targetId).orElseThrow(UserNotFoundException::new);
	}

}
