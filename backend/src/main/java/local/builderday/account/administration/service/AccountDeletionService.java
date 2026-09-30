package local.builderday.account.administration.service;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import local.builderday.account.core.service.AccountModel;
import local.builderday.account.core.service.AccountSessions;
import local.builderday.common.audit.SecurityAudit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Admin Account deletion (Story 11): an Admin turns another Account into a permanent tombstone (ADR 0012) through the
 * same {@code User.markDeleted} transition account hygiene uses. The read, the guards and the transition run in one
 * transaction; the success audit and the ending of the target's Sessions follow the commit, so a save that lost a race
 * (ADR 0013, answered 409 by the global handler) changes nothing and writes no event.
 */
@Service
public class AccountDeletionService {

  /** The outcome of a deletion: done, or not found, or one of the two refusals. */
  public sealed interface Result {
    record Deleted() implements Result {}
    record NotFound() implements Result {}
    record SelfTarget() implements Result {}
    record AlreadyDeleted() implements Result {}
  }

  private final UserRepository userRepository;
  private final AccountSessions accountSessions;
  private final Clock clock;
  private final TransactionTemplate transaction;

  AccountDeletionService(UserRepository userRepository, AccountSessions accountSessions, Clock clock,
      PlatformTransactionManager transactions) {
    this.userRepository = userRepository;
    this.accountSessions = accountSessions;
    this.clock = clock;
    this.transaction = new TransactionTemplate(transactions);
  }

  /**
   * Deletes the target Account.
   *
   * @param targetId the Account to delete (a path variable, already parsed as a UUID)
   * @param callerId the acting Admin's own Account id, resolved from the authenticated principal
   * @param request the HTTP request, for audit correlation
   */
  public Result delete(UUID targetId, UUID callerId, HttpServletRequest request) {
    var auditContext = SecurityAudit.RequestContext.capture(request);
    var outcome = transaction.execute(status -> apply(targetId, callerId, auditContext));
    if (outcome instanceof Changed changed) {
      audit(SecurityAudit.Outcome.SUCCESS, "admin_action", targetId, callerId, auditContext);
      accountSessions.endAll(changed.username());
      return new Result.Deleted();
    }
    return (Result) outcome;
  }

  /** A committed deletion, whose target's Sessions end after the commit. */
  private record Changed(String username) {}

  private Object apply(UUID targetId, UUID callerId, SecurityAudit.RequestContext request) {
    if (targetId.equals(callerId)) {
      audit(SecurityAudit.Outcome.FAILURE, "self_target", targetId, callerId, request);
      return new Result.SelfTarget();
    }
    UserEntity entity = userRepository.findById(targetId).orElse(null);
    if (entity == null) return new Result.NotFound();
    if (entity.getDeletedAt() != null) {
      audit(SecurityAudit.Outcome.FAILURE, "account_deleted", targetId, callerId, request);
      return new Result.AlreadyDeleted();
    }
    var user = AccountModel.of(entity);
    user.markDeleted(clock.instant());
    AccountModel.applyTo(user, entity);
    userRepository.save(entity);
    return new Changed(entity.getUsername());
  }

  /** Target id in the event's {@code user.id}; acting Admin id in extra under the ECS key {@code source.user.id}. */
  private void audit(SecurityAudit.Outcome outcome, String reason, UUID targetId, UUID callerId,
      SecurityAudit.RequestContext request) {
    SecurityAudit.recordFrom(request, new SecurityAudit.Event("account-deleted", "iam", "deletion", outcome, reason,
        targetId, Map.of("source.user.id", callerId.toString())));
  }
}
