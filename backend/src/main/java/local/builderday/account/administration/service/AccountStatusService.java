package local.builderday.account.administration.service;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import local.builderday.account.administration.model.ListedAccount;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import local.builderday.account.core.service.AccountModel;
import local.builderday.account.core.service.AccountSessions;
import local.builderday.common.audit.SecurityAudit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The admin account status toggle (Story 9): an Admin sets one Account's {@code enabled} state by id. Runs the read,
 * the guards, the state change and the audit in one transaction; a real disable ends the target's Sessions after the
 * commit, exactly as account hygiene does. The {@code UserEntity} never leaves this boundary — the result is the
 * domain {@link ListedAccount} row.
 */
@Service
public class AccountStatusService {

  /** The outcome of a toggle: the updated row, or one of the two refusals / not-found. */
  public sealed interface Result {
    record Toggled(ListedAccount account) implements Result {}
    record NotFound() implements Result {}
    record SelfTarget() implements Result {}
    record Deleted() implements Result {}
  }

  private final UserRepository userRepository;
  private final AccountSessions accountSessions;
  private final Clock clock;
  private final TransactionTemplate transaction;

  AccountStatusService(UserRepository userRepository, AccountSessions accountSessions, Clock clock,
      PlatformTransactionManager transactions) {
    this.userRepository = userRepository;
    this.accountSessions = accountSessions;
    this.clock = clock;
    this.transaction = new TransactionTemplate(transactions);
  }

  /**
   * Sets the target Account's {@code enabled} state.
   *
   * @param targetId the Account to change (a path variable, already parsed as a UUID)
   * @param enabled the desired state
   * @param callerId the acting Admin's own Account id, resolved from the authenticated principal
   * @param request the HTTP request, for audit correlation
   */
  public Result setEnabled(UUID targetId, boolean enabled, UUID callerId, HttpServletRequest request) {
    var auditContext = SecurityAudit.RequestContext.capture(request);
    var outcome = transaction.execute(status -> apply(targetId, enabled, callerId, auditContext));
    // Only a committed change is audited, so a save that lost a race (ADR 0013: 409, no event) leaves no trail.
    if (outcome instanceof Changed changed) {
      audit(enabled, SecurityAudit.Outcome.SUCCESS, "admin_action", targetId, callerId, auditContext);
      // Session ending after commit (OWASP: terminate Sessions when account state changes), as account hygiene does.
      if (!enabled) accountSessions.endAll(changed.username());
      return new Result.Toggled(changed.account());
    }
    return (Result) outcome;
  }

  /** A real transition, audited (and for a disable, its Sessions ended) once the transaction has committed. */
  private record Changed(String username, ListedAccount account) {}

  private Object apply(UUID targetId, boolean enabled, UUID callerId, SecurityAudit.RequestContext request) {
    if (targetId.equals(callerId)) {
      audit(enabled, SecurityAudit.Outcome.FAILURE, "self_target", targetId, callerId, request);
      return new Result.SelfTarget();
    }
    UserEntity entity = userRepository.findById(targetId).orElse(null);
    if (entity == null) return new Result.NotFound();
    if (entity.getDeletedAt() != null) {
      audit(enabled, SecurityAudit.Outcome.FAILURE, "account_deleted", targetId, callerId, request);
      return new Result.Deleted();
    }
    Instant now = clock.instant();
    boolean realTransition = entity.isEnabled() != enabled;
    if (realTransition) {
      var user = AccountModel.of(entity);
      if (enabled) user.enable(now); else user.disable(now);
      AccountModel.applyTo(user, entity);
      userRepository.save(entity);
    }
    var row = ListedAccounts.of(entity, now);
    // A no-op answers the unchanged row, with no audit and no Session ending.
    return realTransition ? new Changed(entity.getUsername(), row) : new Result.Toggled(row);
  }

  /** Target id in the event's {@code user.id}; acting Admin id in extra under the ECS key {@code source.user.id}. */
  private void audit(boolean enabled, SecurityAudit.Outcome outcome, String reason, UUID targetId, UUID callerId,
      SecurityAudit.RequestContext request) {
    SecurityAudit.recordFrom(request, new SecurityAudit.Event(enabled ? "account-enabled" : "account-disabled", "iam",
        "change", outcome, reason, targetId, Map.of("source.user.id", callerId.toString())));
  }
}
