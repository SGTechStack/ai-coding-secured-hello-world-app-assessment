package com.assessment.auth.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;

/**
 * The <strong>only</strong> writer to the audit stream (spec.md S11).
 *
 * <p>The AUDIT appender is bound <em>by logger name, not by marker</em>, precisely so ArchUnit can
 * enforce that nothing outside {@code com.assessment.auth.audit} reaches it. A marker binding would
 * be unenforceable — any package can attach a marker.
 *
 * <p>No field written here may carry a username or an email address. Reading the audit trail
 * requires database access to resolve UUIDs, and that is deliberate (spec.md S11).
 */
@Component
public class AuditLogger {

  /**
   * The audit logger name. {@code logback-spring.xml} binds the AUDIT appender to exactly this
   * name with {@code additivity=false}, so audit lines do not also land in the application log.
   */
  public static final String AUDIT_LOGGER_NAME = "AUDIT";

  private static final Logger audit = LoggerFactory.getLogger(AUDIT_LOGGER_NAME);

  public void emit(AuditEvent event) {
    LoggingEventBuilder builder =
        audit
            .atLevel(event.level())
            .addKeyValue("event.action", event.action().name())
            .addKeyValue("event.reason", event.reason().name())
            .addKeyValue("event.outcome", event.outcome());

    if (event.actorUserId() != null) {
      builder = builder.addKeyValue("user.id", event.actorUserId().toString());
    }
    if (event.targetUserId() != null) {
      builder = builder.addKeyValue("target_user_id", event.targetUserId().toString());
    }
    if (event.sourceIp() != null) {
      builder = builder.addKeyValue("source.ip", event.sourceIp());
    }
    if (event.authenticationMethod() != null) {
      builder = builder.addKeyValue("authentication.method", event.authenticationMethod());
    }
    // Flat here, nested into an `error` object by MaskingStructuredLogEncoder. Creating the object
    // rather than stripping the keys is a deliberate deviation from Encoder:408 -- the silent drop
    // violates Std_Logging:164 and would lose nine fields across the three most important security
    // events with no failing build (spec.md S11).
    if (event.errorCode() != null) {
      builder = builder.addKeyValue("error.code", event.errorCode());
    }
    if (event.errorCategory() != null) {
      builder = builder.addKeyValue("error.category", event.errorCategory());
    }
    if (event.errorFollowUpAction() != null) {
      builder = builder.addKeyValue("error.follow_up_action", event.errorFollowUpAction());
    }

    builder.log(event.reason().name());
  }
}
