package com.assessment.auth.bootstrap;

import com.assessment.auth.audit.AuditAction;
import com.assessment.auth.audit.AuditEvent;
import com.assessment.auth.audit.AuditLogger;
import com.assessment.auth.audit.AuditReason;
import jakarta.annotation.PreDestroy;
import org.slf4j.event.Level;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Application start and stop as audit events (spec.md S11, system events 25 and 26).
 *
 * <p>They belong in the audit stream rather than the application log because a gap in the audit
 * trail is only interpretable if you can tell "nothing happened" from "the application was not
 * running".
 */
@Component
public class LifecycleAuditListener {

  private final AuditLogger auditLogger;

  public LifecycleAuditListener(AuditLogger auditLogger) {
    this.auditLogger = auditLogger;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onStarted() {
    auditLogger.emit(
        AuditEvent.of(AuditAction.SYSTEM, AuditReason.APPLICATION_STARTED, Level.INFO).build());
  }

  @PreDestroy
  public void onStopping() {
    auditLogger.emit(
        AuditEvent.of(AuditAction.SYSTEM, AuditReason.APPLICATION_STOPPED, Level.INFO).build());
  }
}
