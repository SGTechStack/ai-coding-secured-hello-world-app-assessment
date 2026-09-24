package com.example.demo_app.admin;

import com.example.demo_app.audit.Actor;
import com.example.demo_app.audit.AuditEvent;
import com.example.demo_app.audit.AuditLog;
import com.example.demo_app.security.SessionExpiry;
import com.example.demo_app.user.Role;
import com.example.demo_app.user.UserAccount;
import com.example.demo_app.user.UserAccountRepository;
import com.example.demo_app.web.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User management for admins. Every method requires {@code ROLE_ADMIN} itself, on top of the
 * filter chain's rule for {@code /api/v1/admin/**}, so a future caller outside that path (or a
 * mistake in the path rule) still can't reach it without the role.
 *
 * <p>The account actions follow the same rules:
 *
 * <ul>
 *   <li>An unknown id is {@code 404 USER_NOT_FOUND}.
 *   <li>The caller's own account is {@code 409 SELF_ACTION_NOT_ALLOWED}, audited as {@code
 *       ADMIN_SELF_ACTION_REJECTED}. Since no admin can disable, demote or delete themselves, at
 *       least one admin always remains.
 *   <li>A real change is audited and ends every session of the target once it is committed, so it
 *       takes effect at once: a session keeps the authorities and state it logged in with.
 *       Setting the value the account already has changes nothing and is not audited.
 * </ul>
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
class AdminUserService {

  /** The error code for an account id that doesn't exist (any more). */
  static final String USER_NOT_FOUND = "USER_NOT_FOUND";

  /** The error code for an action on the caller's own account. */
  static final String SELF_ACTION_NOT_ALLOWED = "SELF_ACTION_NOT_ALLOWED";

  /** The refusal message for an action on the caller's own account; the UI shows the same. */
  static final String SELF_ACTION_MESSAGE = "You can't change your own account.";

  private final UserAccountRepository accounts;
  private final SessionExpiry sessionExpiry;
  private final AuditLog auditLog;

  AdminUserService(
      UserAccountRepository accounts, SessionExpiry sessionExpiry, AuditLog auditLog) {
    this.accounts = accounts;
    this.sessionExpiry = sessionExpiry;
    this.auditLog = auditLog;
  }

  /** Every account, oldest first (ties by id, so the order is stable). */
  @Transactional(readOnly = true)
  List<AdminUserView> listUsers() {
    return accounts.findAllByOrderByCreatedAtAscIdAsc().stream().map(AdminUserView::of).toList();
  }

  /** Disables or re-enables account {@code id} on behalf of the admin {@code actor}. */
  @Transactional
  AdminUserView setEnabled(long id, boolean enabled, Actor actor) {
    UserAccount target = otherAccount(id, actor, AdminAction.STATUS);
    if (target.isEnabled() != enabled) {
      target.setEnabled(enabled);
      signOutAndAuditOnCommit(
          enabled ? AuditEvent.USER_ENABLED : AuditEvent.USER_DISABLED, target, actor, Map.of());
    }
    return AdminUserView.of(target);
  }

  /** Gives account {@code id} the {@code role} on behalf of the admin {@code actor}. */
  @Transactional
  AdminUserView setRole(long id, Role role, Actor actor) {
    UserAccount target = otherAccount(id, actor, AdminAction.ROLE);
    Role oldRole = target.getRole();
    if (oldRole != role) {
      target.setRole(role);
      Map<String, Role> roles = new LinkedHashMap<>();
      roles.put("oldRole", oldRole);
      roles.put("newRole", role);
      signOutAndAuditOnCommit(AuditEvent.USER_ROLE_CHANGED, target, actor, roles);
    }
    return AdminUserView.of(target);
  }

  /**
   * Deletes account {@code id} on behalf of the admin {@code actor}. The database cascades the
   * delete to the account's password reset tokens.
   */
  @Transactional
  void deleteUser(long id, Actor actor) {
    UserAccount target = otherAccount(id, actor, AdminAction.DELETE);
    accounts.delete(target);
    signOutAndAuditOnCommit(AuditEvent.USER_DELETED, target, actor, Map.of());
  }

  /** Account {@code id}, provided it exists and isn't the {@code actor}'s own. */
  private UserAccount otherAccount(long id, Actor actor, AdminAction action) {
    UserAccount target =
        accounts
            .findById(id)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, USER_NOT_FOUND, "User not found", List.of()));
    if (target.getUsername().equals(actor.username())) {
      auditLog.record(
          AuditEvent.ADMIN_SELF_ACTION_REJECTED,
          actor,
          AuditLog.withTarget(target.getUsername(), Map.of("action", action.auditName())));
      throw new ApiException(
          HttpStatus.CONFLICT, SELF_ACTION_NOT_ALLOWED, SELF_ACTION_MESSAGE, List.of());
    }
    return target;
  }

  /**
   * Once the change to {@code target} is committed, ends the target's sessions and audits the
   * change as {@code event} with {@code extra} after its {@code target} field (see {@link
   * SessionExpiry#expireAllSessionsOnCommit}).
   */
  private void signOutAndAuditOnCommit(
      AuditEvent event, UserAccount target, Actor actor, Map<String, ?> extra) {
    Map<String, Object> fields = AuditLog.withTarget(target.getUsername(), extra);
    sessionExpiry.expireAllSessionsOnCommit(
        target.getUsername(), () -> auditLog.record(event, actor, fields));
  }
}
