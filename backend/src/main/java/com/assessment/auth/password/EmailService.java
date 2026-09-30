package com.assessment.auth.password;

import com.assessment.auth.audit.AuditAction;
import com.assessment.auth.audit.AuditEvent;
import com.assessment.auth.audit.AuditLogger;
import com.assessment.auth.audit.AuditReason;
import java.util.UUID;
import org.slf4j.event.Level;
import org.springframework.stereotype.Service;

/**
 * The notification stub (spec.md S7, Q30 answered "Email, synchronous").
 *
 * <p>Synchronous by necessity: {@code @Async} is banned application-wide, so there is no background
 * channel to hand a message to. The notifications Std:65, :71 and :517 require are emitted as audit
 * events by this stub — "log only" is the honest description, but it is Q30's email option with a
 * stub behind it, not a refusal of the question.
 *
 * <p>⚠️ <strong>The structured event must never carry the reset link or the token</strong>, in
 * plaintext or hashed form. The link travels through {@link ResetLinkChannel}, which does not touch
 * the logging framework at all.
 */
@Service
public class EmailService {

  private final AuditLogger auditLogger;
  private final ResetLinkChannel resetLinkChannel;

  public EmailService(AuditLogger auditLogger, ResetLinkChannel resetLinkChannel) {
    this.auditLogger = auditLogger;
    this.resetLinkChannel = resetLinkChannel;
  }

  /**
   * @param userId may be null — a reset requested for an unregistered email still emits an event,
   *     and it must be byte-identical to the registered case (spec.md S7)
   */
  public void sendPasswordResetLink(String email, String token, UUID userId) {
    resetLinkChannel.deliver(email, token);
    auditLogger.emit(
        AuditEvent.of(AuditAction.SYSTEM, AuditReason.NOTIFICATION_SENT, Level.INFO).build());
  }

  public void sendPasswordChangedEmail(UUID userId) {
    auditLogger.emit(
        AuditEvent.of(AuditAction.SYSTEM, AuditReason.NOTIFICATION_SENT, Level.INFO)
            .target(userId)
            .build());
  }
}
