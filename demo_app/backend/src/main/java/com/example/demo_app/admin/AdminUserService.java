package com.example.demo_app.admin;

import com.example.demo_app.audit.AuditEvent;
import com.example.demo_app.audit.AuditLog;
import com.example.demo_app.security.SessionExpiry;
import com.example.demo_app.user.Role;
import com.example.demo_app.user.UserAccount;
import com.example.demo_app.user.UserAccountRepository;
import com.example.demo_app.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

  /** Disables or re-enables account {@code id} on behalf of admin {@code actor}. */
  @Transactional
  AdminUserView setEnabled(long id, boolean enabled, String actor, HttpServletRequest request) {
    UserAccount target = otherAccount(id, actor, "status", request);
    if (target.isEnabled() != enabled) {
      target.setEnabled(enabled);
      changed(
          enabled ? AuditEvent.USER_ENABLED : AuditEvent.USER_DISABLED,
          target,
          actor,
          request,
          Map.of());
    }
    return AdminUserView.of(target);
  }

  /** Gives account {@code id} the {@code role} on behalf of admin {@code actor}. */
  @Transactional
  AdminUserView setRole(long id, Role role, String actor, HttpServletRequest request) {
    UserAccount target = otherAccount(id, actor, "role", request);
    Role oldRole = target.getRole();
    if (oldRole != role) {
      target.setRole(role);
      Map<String, Role> roles = new LinkedHashMap<>();
      roles.put("oldRole", oldRole);
      roles.put("newRole", role);
      changed(AuditEvent.USER_ROLE_CHANGED, target, actor, request, roles);
    }
    return AdminUserView.of(target);
  }

  /**
   * Deletes account {@code id} on behalf of admin {@code actor}. The database cascades the delete
   * to the account's password reset tokens.
   */
  @Transactional
  void deleteUser(long id, String actor, HttpServletRequest request) {
    UserAccount target = otherAccount(id, actor, "delete", request);
    accounts.delete(target);
    changed(AuditEvent.USER_DELETED, target, actor, request, Map.of());
  }

  /** Account {@code id}, provided it exists and isn't the {@code actor}'s own. */
  private UserAccount otherAccount(
      long id, String actor, String action, HttpServletRequest request) {
    UserAccount target =
        accounts
            .findById(id)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found", List.of()));
    if (target.getUsername().equals(actor)) {
      auditLog.record(
          AuditEvent.ADMIN_SELF_ACTION_REJECTED,
          actor,
          request,
          fields(target, Map.of("action", action)));
      throw new ApiException(
          HttpStatus.CONFLICT, "SELF_ACTION_NOT_ALLOWED", SELF_ACTION_MESSAGE, List.of());
    }
    return target;
  }

  /**
   * Once the change to {@code target} is committed, ends the target's sessions and audits it.
   * Waiting for the commit means a login racing the change can't keep a session with the old
   * state, and the audit log never records a change that was rolled back.
   */
  private void changed(
      AuditEvent event,
      UserAccount target,
      String actor,
      HttpServletRequest request,
      Map<String, ?> extra) {
    String username = target.getUsername();
    Map<String, Object> fields = fields(target, extra);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            sessionExpiry.expireAllSessionsOf(username);
            auditLog.record(event, actor, request, fields);
          }
        });
  }

  /** {@code target} first, then the event-specific fields in their given order. */
  private static Map<String, Object> fields(UserAccount target, Map<String, ?> extra) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("target", target.getUsername());
    fields.putAll(extra);
    return fields;
  }
}
