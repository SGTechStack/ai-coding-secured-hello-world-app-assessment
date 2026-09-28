package sg.securedhello.testsupport;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test that deliberately drives the audit emitter onto its degraded path. Without it, {@link LogOutputGuard}
 * fails any test during which a degraded audit row is written (ADR-055 constraint 4).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ExpectsDegradedAuditRow {
}
