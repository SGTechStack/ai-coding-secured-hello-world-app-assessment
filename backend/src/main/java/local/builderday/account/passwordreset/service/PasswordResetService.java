package local.builderday.account.passwordreset.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import local.builderday.common.audit.SecurityAudit;
import local.builderday.common.audit.SecurityAudit.Outcome;
import local.builderday.common.audit.SecurityAudit.RequestContext;
import local.builderday.common.logging.LogFields;
import local.builderday.common.ratelimit.RateLimitBuckets;
import local.builderday.account.passwordreset.config.PasswordResetProperties;
import local.builderday.account.passwordreset.config.PublicBaseUriProperties;
import local.builderday.account.passwordreset.repository.PasswordResetTokenRepository;
import local.builderday.account.passwordreset.repository.entity.PasswordResetTokenEntity;
import local.builderday.notification.service.EmailService;
import local.builderday.account.core.model.ResetCandidate;
import local.builderday.account.core.service.AccountRules;
import local.builderday.account.core.service.AccountSessions;
import local.builderday.account.core.service.PasswordHistory;
import local.builderday.account.core.service.PasswordPolicy;
import local.builderday.account.core.service.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Password reset (ADR 0004). A request is validated and handed to a background executor on the request thread, so the
 * caller gets the same answer at the same speed whatever the account state; the lookup, eligibility check, token issue
 * and email all happen off that thread.
 */
@Service
public class PasswordResetService {
  static final String REQUEST_ACTION = "password-reset-request";
  static final String RESET_ACTION = "password-reset";
  static final String PASSWORD_REUSED = "PASSWORD_REUSED";
  static final String RESET_LINK_PATH = "/reset-password#token=";
  /** Boot's auto-configured executor, which carries the shared MDC task decorator (bounded in application.yml). */
  private static final String APPLICATION_TASK_EXECUTOR = "applicationTaskExecutor";
  private static final String ISSUE_KEY_PREFIX = "password-reset-issue:";
  private static final String REQUEST_LIMIT_PREFIX = "password-reset-request:";
  private static final String CONFIRM_LIMIT_PREFIX = "password-reset-confirm:";
  /** Keyed by account id, never by email, so the store holds no submitted identifier. */
  private static final String EMAIL_LIMIT_PREFIX = "password-reset-email:";
  private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

  private final UserProfileService userProfileService;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final EmailService emailService;
  private final RateLimitBuckets rateLimitBuckets;
  private final PasswordResetProperties properties;
  private final PublicBaseUriProperties publicBaseUri;
  private final Clock clock;
  private final TaskExecutor executor;
  private final PasswordPolicy passwordPolicy;
  private final PasswordEncoder passwordEncoder;
  private final AccountSessions accountSessions;
  private final TransactionTemplate transaction;
  private final PasswordHistory passwordHistory;

  PasswordResetService(UserProfileService userProfileService, PasswordResetTokenRepository passwordResetTokenRepository,
      EmailService emailService, RateLimitBuckets rateLimitBuckets, PasswordResetProperties properties,
      PublicBaseUriProperties publicBaseUri, Clock clock,
      @Qualifier(APPLICATION_TASK_EXECUTOR) TaskExecutor executor, PasswordPolicy passwordPolicy,
      PasswordEncoder passwordEncoder, AccountSessions accountSessions, PlatformTransactionManager transactions,
      PasswordHistory passwordHistory) {
    this.userProfileService = userProfileService;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.emailService = emailService;
    this.rateLimitBuckets = rateLimitBuckets;
    this.properties = properties;
    this.publicBaseUri = publicBaseUri;
    this.clock = clock;
    this.executor = executor;
    this.passwordPolicy = passwordPolicy;
    this.passwordEncoder = passwordEncoder;
    this.accountSessions = accountSessions;
    this.transaction = new TransactionTemplate(transactions);
    this.passwordHistory = passwordHistory;
  }

  /**
   * Applies the per-IP request rate limit (every request counts), validates the email and, when it is well-formed,
   * queues the reset. The result never depends on the account.
   *
   * @param email the email as submitted
   */
  public RequestResult requestReset(String email, RequestContext request) {
    if (!withinLimit(REQUEST_LIMIT_PREFIX + request.sourceIp(), properties.requestRateLimit())) {
      audit(request, Outcome.FAILURE, "rate_limited", null);
      return new RequestResult.RateLimited();
    }
    String normalized = AccountRules.normalize(email);
    var violations = AccountRules.emailViolations(normalized);
    if (!violations.isEmpty()) return new RequestResult.Invalid(violations);
    try {
      executor.execute(() -> issue(normalized, request));
    } catch (TaskRejectedException rejected) {
      failed(request, null, rejected);
    }
    return new RequestResult.Accepted();
  }

  /** Background: issues a token and emails the link, but only for an eligible account. */
  private void issue(String email, RequestContext request) {
    UUID userId = null;
    try {
      ResetCandidate candidate = userProfileService.findResetCandidate(email).orElse(null);
      if (candidate == null || !candidate.eligible()) {
        audit(request, Outcome.FAILURE, "not_eligible", candidate == null ? null : candidate.id());
        return;
      }
      userId = candidate.id();
      // Silent: the requester already has the generic answer, so an inbox cannot be flooded from many sources.
      if (!withinLimit(EMAIL_LIMIT_PREFIX + userId, properties.emailCap())) {
        audit(request, Outcome.FAILURE, "capped", userId);
        return;
      }
      String token = ResetTokens.generate();
      var accountId = userId;
      // Serialised per account, so concurrent requests can never leave two live tokens.
      rateLimitBuckets.serialised(ISSUE_KEY_PREFIX + accountId, properties.tokenLifetime(), () -> {
        passwordResetTokenRepository.deleteByUserId(accountId);
        passwordResetTokenRepository.save(new PasswordResetTokenEntity(UUID.randomUUID(), accountId,
            ResetTokens.hash(token), clock.instant().plus(properties.tokenLifetime())));
        return null;
      });
      emailService.sendPasswordResetEmail(candidate.email(), userId, publicBaseUri.resolve(RESET_LINK_PATH + token));
      audit(request, Outcome.SUCCESS, "issued", userId);
    } catch (RuntimeException failure) {
      failed(request, userId, failure);
    }
  }

  /**
   * Completes a Password reset. Checks run in a fixed order: the per-IP confirm rate limit, token shape, token and
   * account, password policy, Password history, then the change. A policy rejection leaves the token usable. Never
   * creates a Session.
   *
   * @param token the token as submitted, from the link's fragment
   * @param newPassword the new password exactly as submitted
   */
  public ConfirmResult confirm(String token, String newPassword, RequestContext request) {
    try {
      return check(token, newPassword, request);
    } catch (RuntimeException failure) {
      auditReset(request, Outcome.ERROR, "system_error", null);
      throw failure;
    }
  }

  /** The fixed order: rate limit, then the token, then the password rules, then the change. */
  private ConfirmResult check(String token, String newPassword, RequestContext request) {
    if (!withinConfirmLimit(request)) return new ConfirmResult.RateLimited();
    LiveToken live = liveToken(token, request).orElse(null);
    if (live == null) return new ConfirmResult.InvalidToken();
    return passwordRejection(newPassword, live.account(), request).orElseGet(() -> reset(live, newPassword, request));
  }

  /** Every confirm counts against the per-IP limit, whatever its outcome. */
  private boolean withinConfirmLimit(RequestContext request) {
    if (withinLimit(CONFIRM_LIMIT_PREFIX + request.sourceIp(), properties.confirmRateLimit())) return true;
    auditReset(request, Outcome.FAILURE, "rate_limited", null);
    return false;
  }

  /** A token row and its account, both usable for a reset. */
  private record LiveToken(PasswordResetTokenEntity row, ResetCandidate account) {}

  /**
   * The token's row and account if it is well-formed, known, unused and unexpired for an eligible account; audits
   * why not.
   */
  private Optional<LiveToken> liveToken(String token, RequestContext request) {
    if (!ResetTokens.wellFormed(token)) return rejectToken(request, "invalid_token", null);
    Instant now = clock.instant();
    PasswordResetTokenEntity row = passwordResetTokenRepository.findByTokenHash(ResetTokens.hash(token)).orElse(null);
    if (row == null) return rejectToken(request, "invalid_token", null);
    if (row.getUsedAt() != null) return rejectToken(request, "used_token", row.getUserId());
    if (!row.getExpiresAt().isAfter(now)) return rejectToken(request, "expired_token", row.getUserId());
    ResetCandidate account = userProfileService.findResetCandidate(row.getUserId()).orElse(null);
    if (account == null || !account.eligible()) return rejectToken(request, "not_eligible", row.getUserId());
    return Optional.of(new LiveToken(row, account));
  }

  /** The password policy, then Password history. A rejection leaves the token usable. */
  private Optional<ConfirmResult> passwordRejection(String newPassword, ResetCandidate account,
      RequestContext request) {
    var violations = passwordPolicy.violations(newPassword, account.username(), account.email());
    if (!violations.isEmpty()) {
      auditReset(request, Outcome.FAILURE, "weak_password", account.id());
      return Optional.of(new ConfirmResult.Rejected(violations.stream().map(Enum::name).distinct().toList()));
    }
    // Reachable only with a live token, so only the holder of the owner's link can learn a password was recent.
    if (passwordHistory.contains(account.id(), newPassword)) {
      auditReset(request, Outcome.FAILURE, "password_reused", account.id());
      return Optional.of(new ConfirmResult.Rejected(List.of(PASSWORD_REUSED)));
    }
    return Optional.empty();
  }

  /** Spends the token and changes the password in one transaction, ending every Session of the account. */
  private ConfirmResult reset(LiveToken live, String newPassword, RequestContext request) {
    PasswordResetTokenEntity row = live.row();
    ResetCandidate account = live.account();
    String passwordHash = passwordEncoder.encode(newPassword); // Slow by design, so outside the transaction.
    Instant spentAt = clock.instant();
    boolean spent = Boolean.TRUE.equals(transaction.execute(status -> {
      if (passwordResetTokenRepository.spend(row.getId(), spentAt) != 1) return false;
      userProfileService.resetPassword(account.id(), passwordHash, spentAt);
      // Inside the transaction and before commit: if ending a Session fails, the whole reset rolls back and can be
      // retried, rather than committing a new password while another holder's Session lives on.
      accountSessions.endAll(account.username());
      return true;
    }));
    // Lost a race with a concurrent confirm, or the token expired while the password was being hashed.
    if (!spent) {
      return invalid(request, row.getExpiresAt().isAfter(spentAt) ? "used_token" : "expired_token", account.id());
    }

    notifyPasswordChanged(account);
    auditReset(request, Outcome.SUCCESS, "success", account.id());
    return new ConfirmResult.Reset();
  }

  /** Best effort and off the request thread: a notification failure never changes a completed reset. */
  private void notifyPasswordChanged(ResetCandidate account) {
    try {
      executor.execute(() -> {
        try {
          emailService.send(account.email(), EmailService.NotificationType.PASSWORD_CHANGED, account.id());
        } catch (RuntimeException failure) {
          logError(RESET_ACTION, account.id(), failure, "Password changed notification failed");
        }
      });
    } catch (TaskRejectedException rejected) {
      logError(RESET_ACTION, account.id(), null, "Password changed notification could not be queued");
    }
  }

  /** A fixed-window limit in the shared durable store, so it holds across instances (ADR 0003, 0004). */
  private boolean withinLimit(String key, PasswordResetProperties.Limit limit) {
    return rateLimitBuckets.tryFixedWindow(key, limit.attempts(), limit.window());
  }

  private static Optional<LiveToken> rejectToken(RequestContext request, String reason, UUID userId) {
    auditReset(request, Outcome.FAILURE, reason, userId);
    return Optional.empty();
  }

  private static ConfirmResult invalid(RequestContext request, String reason, UUID userId) {
    auditReset(request, Outcome.FAILURE, reason, userId);
    return new ConfirmResult.InvalidToken();
  }

  private static void auditReset(RequestContext request, Outcome outcome, String reason, UUID userId) {
    SecurityAudit.recordFrom(request, new SecurityAudit.Event(RESET_ACTION, "iam", "change", outcome, reason, userId));
  }

  private void failed(RequestContext request, UUID userId, RuntimeException failure) {
    logError(REQUEST_ACTION, userId, failure, "Password reset request could not be completed");
    audit(request, Outcome.ERROR, "failure", userId);
  }

  /**
   * An operational failure line naming the account concerned. Through {@link LogFields}, so it stays one valid line
   * when a signed-in caller's MDC {@code user.id} differs: the account's id wins. Only the exception type is logged: a
   * message could carry submitted or account data.
   */
  private static void logError(String action, UUID userId, RuntimeException failure, String message) {
    var fields = new LinkedHashMap<String, Object>();
    fields.put("event.action", action);
    fields.put("event.outcome", "failure");
    fields.put("user.id", userId == null ? null : userId.toString());
    fields.put("error.type", failure == null ? null : failure.getClass().getName());
    LogFields.log(log.atError(), fields, message);
  }

  private static void audit(RequestContext request, Outcome outcome, String reason, UUID userId) {
    SecurityAudit.recordFrom(request, new SecurityAudit.Event(REQUEST_ACTION, "iam", "info", outcome, reason, userId));
  }
}
