package local.builderday.account.administration.service;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import local.builderday.account.administration.model.ListedAccount;
import local.builderday.account.core.model.Role;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import local.builderday.account.core.service.AccountModel;
import local.builderday.account.core.service.AccountSessions;
import local.builderday.common.audit.SecurityAudit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The admin Role change (Story 10): an Admin gives another Account a different role through the domain
 * {@code User.changeRole} transition. The read, the guards and the transition run in one transaction; the success
 * audit and the ending of every Session of the Account follow the commit (ADR 0015), so a save that lost a race (ADR
 * 0013, answered 409 by the global handler) changes nothing, ends nothing and writes no event.
 */
@Service
public class AccountRoleService {

  /** The outcome of a Role change: the (possibly unchanged) row, not found, or one of the two refusals. */
  public sealed interface Result {
    record Updated(ListedAccount account) implements Result {}
    record NotFound() implements Result {}
    record SelfTarget() implements Result {}
    record Deleted() implements Result {}
  }

  private final UserRepository userRepository;
  private final AccountSessions accountSessions;
  private final Clock clock;
  private final TransactionTemplate transaction;

  AccountRoleService(UserRepository userRepository, AccountSessions accountSessions, Clock clock,
      PlatformTransactionManager transactions) {
    this.userRepository = userRepository;
    this.accountSessions = accountSessions;
    this.clock = clock;
    this.transaction = new TransactionTemplate(transactions);
  }

  /**
   * Gives the target Account {@code role}.
   *
   * @param targetId the Account to change (a path variable, already parsed as a UUID)
   * @param role the requested role
   * @param callerId the acting Admin's own Account id, resolved from the authenticated principal
   * @param request the HTTP request, for audit correlation
   */
  public Result changeRole(UUID targetId, Role role, UUID callerId, HttpServletRequest request) {
    var auditContext = SecurityAudit.RequestContext.capture(request);
    var outcome = transaction.execute(status -> apply(targetId, role, callerId, auditContext));
    if (outcome instanceof Changed change) {
      audit(role, SecurityAudit.Outcome.SUCCESS, "admin_action", targetId, callerId, auditContext);
      // ponytail: a login that read the old role just before the commit and saves its Session after endAll keeps the
      // old role until that Session expires (ADR 0015), as with the admin disable. Closing it needs a per-request role
      // read or a Session version check.
      accountSessions.endAll(change.username());
      return new Result.Updated(change.account());
    }
    return (Result) outcome;
  }

  /** A committed Role change, audited and followed by the ending of the Account's Sessions. */
  private record Changed(String username, ListedAccount account) {}

  private Object apply(UUID targetId, Role role, UUID callerId, SecurityAudit.RequestContext request) {
    if (targetId.equals(callerId)) {
      audit(role, SecurityAudit.Outcome.FAILURE, "self_target", targetId, callerId, request);
      return new Result.SelfTarget();
    }
    UserEntity entity = userRepository.findById(targetId).orElse(null);
    if (entity == null) return new Result.NotFound();
    if (entity.getDeletedAt() != null) {
      audit(role, SecurityAudit.Outcome.FAILURE, "account_deleted", targetId, callerId, request);
      return new Result.Deleted();
    }
    var user = AccountModel.of(entity);
    // ponytail: two Admins demoting each other at the same instant write different rows, so optimistic locking does
    // not stop it and no enabled Admin may remain. Recovery belongs to the Admin recovery story.
    boolean changed = user.changeRole(role);
    if (changed) {
      AccountModel.applyTo(user, entity);
      userRepository.save(entity);
    }
    var row = ListedAccounts.of(entity, clock.instant());
    // Asking for the held role answers the unchanged row, with no audit and no Session ending.
    return changed ? new Changed(entity.getUsername(), row) : new Result.Updated(row);
  }

  /**
   * Target id in {@code user.id}, acting Admin in extra {@code source.user.id}, and the requested role in
   * {@code user.changes.roles} (ECS). Never the previous role, username or email.
   */
  private void audit(Role role, SecurityAudit.Outcome outcome, String reason, UUID targetId, UUID callerId,
      SecurityAudit.RequestContext request) {
    SecurityAudit.recordFrom(request, new SecurityAudit.Event("role-changed", "iam", "change", outcome, reason,
        targetId, Map.of("source.user.id", callerId.toString(), "user.changes.roles", List.of(role.name()))));
  }
}
