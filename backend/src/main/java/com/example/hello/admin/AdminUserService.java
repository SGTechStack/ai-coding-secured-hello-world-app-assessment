package com.example.hello.admin;

import com.example.hello.auth.SessionInvalidationService;
import com.example.hello.common.AuditLogger;
import com.example.hello.common.NotFoundException;
import com.example.hello.common.SelfActionException;
import com.example.hello.passwordreset.PasswordResetTokenRepository;
import com.example.hello.user.Role;
import com.example.hello.user.User;
import com.example.hello.user.UserRepository;
import com.example.hello.user.UserSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stories 8-11. Every mutation refuses to target the acting admin's own account, invalidates
 * the target's live sessions so the change takes effect immediately, and writes an audit
 * line with actor and target.
 */
@Service
public class AdminUserService {

  private final UserRepository userRepository;
  private final PasswordResetTokenRepository tokenRepository;
  private final SessionInvalidationService sessionInvalidation;
  private final AuditLogger audit;

  public AdminUserService(
      UserRepository userRepository,
      PasswordResetTokenRepository tokenRepository,
      SessionInvalidationService sessionInvalidation,
      AuditLogger audit) {
    this.userRepository = userRepository;
    this.tokenRepository = tokenRepository;
    this.sessionInvalidation = sessionInvalidation;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<UserSummary> listUsers() {
    return userRepository.findAllByOrderByCreatedAtAsc().stream().map(UserSummary::from).toList();
  }

  @Transactional
  public UserSummary setEnabled(String actor, UUID targetId, boolean enabled) {
    User target = loadTarget(actor, targetId, "enable or disable");
    target.setEnabled(enabled);
    if (!enabled) {
      sessionInvalidation.invalidateAllForUser(target.getUsername());
    }
    audit.event(enabled ? "USER_ENABLED" : "USER_DISABLED", "actor", actor, "target", target.getUsername());
    return UserSummary.from(target);
  }

  @Transactional
  public UserSummary changeRole(String actor, UUID targetId, Role role) {
    User target = loadTarget(actor, targetId, "change the role of");
    target.setRole(role);
    // Authorities live in the session; force a fresh login so the new role applies.
    sessionInvalidation.invalidateAllForUser(target.getUsername());
    audit.event(
        "USER_ROLE_CHANGED", "actor", actor, "target", target.getUsername(), "role", role.name());
    return UserSummary.from(target);
  }

  @Transactional
  public void deleteUser(String actor, UUID targetId) {
    User target = loadTarget(actor, targetId, "delete");
    tokenRepository.deleteByUser(target);
    sessionInvalidation.invalidateAllForUser(target.getUsername());
    userRepository.delete(target);
    audit.event("USER_DELETED", "actor", actor, "target", target.getUsername());
  }

  private User loadTarget(String actor, UUID targetId, String action) {
    User target =
        userRepository.findById(targetId).orElseThrow(() -> new NotFoundException("User not found"));
    if (target.getUsername().equals(actor)) {
      throw new SelfActionException("You cannot " + action + " your own account");
    }
    return target;
  }
}
