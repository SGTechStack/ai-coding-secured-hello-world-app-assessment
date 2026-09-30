package com.assessment.auth.user;

import com.assessment.auth.audit.AuditAction;
import com.assessment.auth.audit.AuditEvent;
import com.assessment.auth.audit.AuditLogger;
import com.assessment.auth.audit.AuditReason;
import com.assessment.auth.common.ApiErrorCode;
import com.assessment.auth.common.ApiException;
import com.assessment.auth.password.PasswordResetService;
import com.assessment.auth.security.SelfActionGuard;
import com.assessment.auth.security.SessionRevocationService;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.event.Level;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrator account operations (stories 1.14–1.20).
 *
 * <p>Every mutating operation here shares three obligations: the self-action guard, an audit event
 * naming both the acting administrator and the target, and — where the account's authority or
 * credential changes — session revocation. They are applied per operation rather than by an
 * interceptor, so an operation that legitimately skips one is visibly skipping it.
 */
@Service
public class AdminUserService {

  private final UserRepository userRepository;
  private final DeletedUserRepository deletedUserRepository;
  private final RoleRepository roleRepository;
  private final SelfActionGuard selfActionGuard;
  private final SessionRevocationService sessionRevocationService;
  private final PasswordResetService passwordResetService;
  private final AuditLogger auditLogger;
  private final Clock clock;

  public AdminUserService(
      UserRepository userRepository,
      DeletedUserRepository deletedUserRepository,
      RoleRepository roleRepository,
      SelfActionGuard selfActionGuard,
      SessionRevocationService sessionRevocationService,
      PasswordResetService passwordResetService,
      AuditLogger auditLogger,
      Clock clock) {
    this.userRepository = userRepository;
    this.deletedUserRepository = deletedUserRepository;
    this.roleRepository = roleRepository;
    this.selfActionGuard = selfActionGuard;
    this.sessionRevocationService = sessionRevocationService;
    this.passwordResetService = passwordResetService;
    this.auditLogger = auditLogger;
    this.clock = clock;
  }

  private User require(UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "No such user."));
  }

  /** Story 1.16. Disabling kills sessions; re-enabling forces a password change (Std:130). */
  @Transactional
  public void setEnabled(UUID actorId, UUID targetId, boolean enabled) {
    selfActionGuard.reject(actorId, targetId);
    User target = require(targetId);

    if (!enabled) {
      // The last enabled administrator cannot be disabled either -- the same reasoning as a role
      // change, and the same 409.
      assertNotLastEnabledUserManager(target);
    }

    target.setEnabled(enabled);
    target.setDisabledAt(enabled ? null : clock.instant());
    if (enabled) {
      // Std:130: a re-enabled account must choose a new password before regaining access.
      target.setRequirePasswordChange(true);
    }
    userRepository.save(target);

    if (!enabled) {
      sessionRevocationService.revokeAllFor(target.getUsername());
    }

    auditLogger.emit(
        AuditEvent.of(
                AuditAction.ACCOUNT_MANAGEMENT,
                enabled ? AuditReason.ACCOUNT_ENABLED : AuditReason.ACCOUNT_DISABLED,
                Level.INFO)
            .actor(actorId)
            .target(targetId)
            .build());
  }

  /** Story 1.17. A demoted user must not keep elevated authority on an open session. */
  @Transactional
  public void changeRole(UUID actorId, UUID targetId, String role) {
    selfActionGuard.reject(actorId, targetId);
    if (!roleRepository.existsByName(role)) {
      throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "Unknown role.");
    }
    User target = require(targetId);

    if (Role.USER_MANAGER.equals(target.getRole()) && !Role.USER_MANAGER.equals(role)) {
      assertNotLastEnabledUserManager(target);
    }

    target.setRole(role);
    userRepository.save(target);
    sessionRevocationService.revokeAllFor(target.getUsername());

    auditLogger.emit(
        AuditEvent.of(AuditAction.ACCOUNT_MANAGEMENT, AuditReason.ACCOUNT_ROLE_CHANGED, Level.INFO)
            .actor(actorId)
            .target(targetId)
            .build());
  }

  /** Story 1.18. Required by Std:367, :451 and :282; no reason field (Qs:384 over :386). */
  @Transactional
  public void unlock(UUID actorId, UUID targetId) {
    selfActionGuard.reject(actorId, targetId);
    User target = require(targetId);
    target.setLockedUntil(null);
    target.setFailedLoginAttempts(0);
    userRepository.save(target);

    auditLogger.emit(
        AuditEvent.of(AuditAction.ACCOUNT_MANAGEMENT, AuditReason.ACCOUNT_UNLOCKED, Level.INFO)
            .actor(actorId)
            .target(targetId)
            .build());
  }

  /**
   * Story 1.19. Tombstone then hard-delete, <strong>in one transaction</strong>.
   *
   * <p>Std:31's glossary ("a read-only archive record") settles :408's "soft-delete" wording in
   * favour of this mechanism. Because the row is gone, story 1.14's list needs no filter.
   *
   * <p>{@code password_history} rows cascade away with the account. The tombstone never carries a
   * password hash.
   */
  @Transactional
  public void delete(UUID actorId, UUID targetId) {
    selfActionGuard.reject(actorId, targetId);
    User target = require(targetId);
    assertNotLastEnabledUserManager(target);

    String username = target.getUsername();
    deletedUserRepository.save(new DeletedUser(target, clock.instant(), actorId));
    userRepository.delete(target);
    userRepository.flush();
    sessionRevocationService.revokeAllFor(username);

    auditLogger.emit(
        AuditEvent.of(AuditAction.ACCOUNT_MANAGEMENT, AuditReason.ACCOUNT_DELETED, Level.INFO)
            .actor(actorId)
            .target(targetId)
            .build());
  }

  /**
   * Story 1.20. Issues a token; the system never picks a password on the user's behalf (Std:401).
   *
   * <p>The lock is deliberately not cleared (Std:131 over Priv:441-442) and {@code
   * requirePasswordChange} is deliberately not set — the user chooses their own password at
   * confirm, so there is nothing to force afterwards.
   *
   * <p>The self-action guard is applied here as well. spec.md S3 lists the guard against rows 11,
   * 12, 13 and 15, but story 1.20 states the rejection for this row explicitly; guarding it
   * satisfies both, and an administrator resetting their own password has the self-service change
   * endpoint for that.
   *
   * @return the plaintext token, returned exactly once and never logged
   */
  @Transactional
  public String issueResetToken(UUID actorId, UUID targetId) {
    selfActionGuard.reject(actorId, targetId);
    User target = require(targetId);
    String token = passwordResetService.issueTokenFor(target);

    auditLogger.emit(
        AuditEvent.of(
                AuditAction.CREDENTIAL_MANAGEMENT,
                AuditReason.PASSWORD_RESET_ISSUED_BY_ADMIN,
                Level.INFO)
            .actor(actorId)
            .target(targetId)
            .build());
    return token;
  }

  /**
   * Refuses a change that would leave the system with no enabled administrator.
   *
   * <p>409 rather than 403: the request is authorized, the system state is what forbids it.
   */
  private void assertNotLastEnabledUserManager(User target) {
    if (!Role.USER_MANAGER.equals(target.getRole()) || !target.isEnabled()) {
      return;
    }
    if (userRepository.countEnabledWithRoleExcluding(Role.USER_MANAGER, target.getId()) == 0) {
      throw new ApiException(
          ApiErrorCode.LAST_USER_MANAGER,
          "This would leave the system with no enabled user manager.");
    }
  }
}
