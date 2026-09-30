package com.assessment.auth.audit;

import java.util.UUID;
import org.slf4j.event.Level;

/**
 * One audit record (spec.md S11).
 *
 * <p><strong>{@code actorUserId} is always the actor</strong> and {@code targetUserId} is always the
 * account acted upon — an underscore key following the standard's own {@code error_*} precedent.
 * Conflating them is how an audit trail stops answering "who did this".
 *
 * <p>{@code sourceIp} is set on <strong>exactly seven security event classes</strong> and nowhere
 * else (ticket 18, amended by ticket 12 to add forced-change denial as the seventh). It comes from
 * {@code getRemoteAddr()} verbatim — never {@code X-Forwarded-For} — and never enters MDC.
 *
 * <p>The three {@code error*} fields exist because three security events carry an error semantic
 * with no throwable behind them; the custom encoder nests them into an {@code error} object rather
 * than dropping them (spec.md S11).
 *
 * @param authenticationMethod set to {@code password} on login success — the recipe's resolver
 *     switches on the authentication class name and would yield {@code "unknown"} for our custom
 *     filter's subclass, breaching Std:240 (spec.md S13, test 3).
 */
public record AuditEvent(
    AuditAction action,
    AuditReason reason,
    Level level,
    String outcome,
    UUID actorUserId,
    UUID targetUserId,
    String sourceIp,
    String errorCode,
    String errorCategory,
    String errorFollowUpAction,
    String authenticationMethod) {

  public static Builder of(AuditAction action, AuditReason reason, Level level) {
    return new Builder(action, reason, level);
  }

  /** Mutable builder; {@link AuditEvent} itself stays immutable. */
  public static final class Builder {
    private final AuditAction action;
    private final AuditReason reason;
    private final Level level;
    private String outcome = "success";
    private UUID actorUserId;
    private UUID targetUserId;
    private String sourceIp;
    private String errorCode;
    private String errorCategory;
    private String errorFollowUpAction;
    private String authenticationMethod;

    private Builder(AuditAction action, AuditReason reason, Level level) {
      this.action = action;
      this.reason = reason;
      this.level = level;
    }

    public Builder outcome(String outcome) {
      this.outcome = outcome;
      return this;
    }

    /** The acting principal. Left null for anonymous and unknown-subject events. */
    public Builder actor(UUID actorUserId) {
      this.actorUserId = actorUserId;
      return this;
    }

    public Builder target(UUID targetUserId) {
      this.targetUserId = targetUserId;
      return this;
    }

    /** Only on the seven classes listed in the logging contract. */
    public Builder sourceIp(String sourceIp) {
      this.sourceIp = sourceIp;
      return this;
    }

    public Builder error(String code, String category, String followUpAction) {
      this.errorCode = code;
      this.errorCategory = category;
      this.errorFollowUpAction = followUpAction;
      return this;
    }

    public Builder authenticationMethod(String method) {
      this.authenticationMethod = method;
      return this;
    }

    public AuditEvent build() {
      return new AuditEvent(
          action,
          reason,
          level,
          outcome,
          actorUserId,
          targetUserId,
          sourceIp,
          errorCode,
          errorCategory,
          errorFollowUpAction,
          authenticationMethod);
    }
  }
}
