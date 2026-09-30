package local.builderday.account.core.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import local.builderday.common.audit.SecurityAudit;
import local.builderday.account.core.config.AccountHygieneProperties;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.PredicateSpecification;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Daily account hygiene (standalone standard): soft-deletes accounts inactive longer than {@code delete-after} and
 * disables accounts inactive longer than {@code disable-after}, ending their active sessions either way.
 *
 * <p>Each account is changed in its own transaction after re-checking eligibility, so one failure never rolls back the
 * batch and a user who logged in since the scan is left alone. ShedLock serialises runs across instances.
 */
@Service
public class AccountHygieneJob {
  static final String JOB_NAME = "account-hygiene";
  private static final int PAGE_SIZE = 500;
  /** Attempts per Account when its change meets a concurrent write (ADR 0013). */
  private static final int MAX_ATTEMPTS = 3;
  private static final Logger log = LoggerFactory.getLogger(AccountHygieneJob.class);

  /** What a run changed. */
  public record Result(int deleted, int disabled, int failed) {}

  private enum Action { DELETE, DISABLE }

  private final UserRepository userRepository;
  private final AccountSessions accountSessions;
  private final AccountHygieneProperties properties;
  private final Clock clock;
  private final TransactionTemplate perAccount;

  AccountHygieneJob(UserRepository userRepository, AccountSessions accountSessions,
      AccountHygieneProperties properties, Clock clock, PlatformTransactionManager transactions) {
    this.userRepository = userRepository;
    this.accountSessions = accountSessions;
    this.properties = properties;
    this.clock = clock;
    this.perAccount = new TransactionTemplate(transactions);
    this.perAccount.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Scheduled(cron = "${app.security.account-hygiene.cron}", zone = "${app.security.account-hygiene.zone}")
  @SchedulerLock(name = JOB_NAME, lockAtMostFor = "PT30M",
      lockAtLeastFor = "${app.security.account-hygiene.lock-at-least-for:PT1M}")
  public Result run() {
    Instant now = clock.instant();
    jobEvent("job-start", null);
    // Deletion first, so an account past both thresholds is tombstoned in one step rather than disabled then deleted.
    int[] deleted = process(Action.DELETE, now.minus(properties.deleteAfter()), now);
    int[] disabled = process(Action.DISABLE, now.minus(properties.disableAfter()), now);
    var result = new Result(deleted[0], disabled[0], deleted[1] + disabled[1]);
    jobEvent("job-end", result);
    return result;
  }

  /** @return {changed, failed} */
  private int[] process(Action action, Instant cutoff, Instant now) {
    PredicateSpecification<UserEntity> eligible = action == Action.DISABLE
        ? UserRepository.inactiveSince(cutoff).and(UserRepository.enabled())
        : UserRepository.inactiveSince(cutoff);
    int changed = 0;
    int failed = 0;
    UUID after = null;
    // ponytail: keyset pages of 500 with one transaction per account; move to Spring Batch chunking if volumes grow.
    while (true) {
      var page = userRepository.findAll(Specification.where(eligible.and(UserRepository.idAfter(after))),
          PageRequest.of(0, PAGE_SIZE, Sort.by(UserEntity::getId))).getContent();
      if (page.isEmpty()) break;
      for (UserEntity candidate : page) {
        after = candidate.getId();
        try {
          String username = changeWithRetry(action, candidate.getId(), cutoff, now);
          if (username == null) continue;
          // Audited only once committed, so a change that lost a race (ADR 0013) leaves no event.
          audit(action, candidate.getId());
          accountSessions.endAll(username);
          changed++;
        } catch (RuntimeException failure) {
          failed++;
          log.atError().addKeyValue("event.action", "account-" + action.name().toLowerCase(Locale.ROOT))
              .addKeyValue("event.outcome", "failure").addKeyValue("user.id", candidate.getId().toString())
              .addKeyValue("batch.job.name", JOB_NAME).setCause(failure).log("Account hygiene change failed");
        }
      }
      if (page.size() < PAGE_SIZE) break;
    }
    return new int[] {changed, failed};
  }

  /**
   * Runs one Account's change, retrying a version conflict (ADR 0013) with a fresh read and re-check each time. After
   * the last attempt it logs one WARN and leaves the Account for the next run. @return the changed username, or null
   */
  private String changeWithRetry(Action action, UUID id, Instant cutoff, Instant now) {
    for (int attempt = 1; ; attempt++) {
      try {
        return perAccount.execute(status -> apply(action, id, cutoff, now));
      } catch (ObjectOptimisticLockingFailureException conflict) {
        if (attempt < MAX_ATTEMPTS) continue;
        log.atWarn().addKeyValue("event.action", "account-" + action.name().toLowerCase(Locale.ROOT))
            .addKeyValue("user.id", id.toString()).addKeyValue("batch.job.name", JOB_NAME)
            .log("Account hygiene change skipped after concurrent modifications; retried next run");
        return null;
      }
    }
  }

  /** Re-reads and re-checks the account inside its own transaction. @return the username if it was changed */
  private String apply(Action action, UUID id, Instant cutoff, Instant now) {
    var entity = userRepository.findById(id).orElse(null);
    if (entity == null || entity.getDeletedAt() != null) return null;
    Instant lastUse = entity.getLastLoginAt() != null ? entity.getLastLoginAt() : entity.getCreatedAt();
    if (lastUse == null || !lastUse.isBefore(cutoff)) return null;
    if (action == Action.DISABLE && !entity.isEnabled()) return null;
    var user = AccountModel.of(entity);
    if (action == Action.DELETE) user.markDeleted(now);
    else user.disable(now);
    AccountModel.applyTo(user, entity);
    return entity.getUsername();
  }

  private void audit(Action action, UUID id) {
    SecurityAudit.record(null, new SecurityAudit.Event(
        action == Action.DELETE ? "account-deleted" : "account-disabled", "iam",
        action == Action.DELETE ? "deletion" : "change", SecurityAudit.Outcome.SUCCESS, "inactivity", id,
        Map.of("batch.job.name", JOB_NAME)));
  }

  private void jobEvent(String type, Result result) {
    var entry = log.atInfo()
        .addKeyValue("event.kind", "event")
        .addKeyValue("event.category", List.of("batch"))
        .addKeyValue("event.type", List.of(type))
        .addKeyValue("batch.job.name", JOB_NAME);
    if (result != null) {
      entry = entry.addKeyValue("event.outcome", result.failed() == 0 ? "success" : "failure")
          .addKeyValue("record.deleted", result.deleted())
          .addKeyValue("record.disabled", result.disabled())
          .addKeyValue("record.failed", result.failed());
    }
    entry.log(result == null ? "Scheduled job started" : "Scheduled job completed");
  }
}
