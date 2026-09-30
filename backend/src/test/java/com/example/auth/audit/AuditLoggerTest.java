package com.example.auth.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.support.LogCapture;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/** Unit tests for {@link AuditLogger}'s sanitising and its per-event MDC actor handling. */
class AuditLoggerTest {

    private final AuditLogger auditLogger = new AuditLogger();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void sanitizeReplacesLineBreaksAndOtherControlCharacters() {
        assertThat(AuditLogger.sanitize("a\rb\nc")).isEqualTo("a_b_c");
        assertThat(AuditLogger.sanitize("tab\there")).isEqualTo("tab_here");
        assertThat(AuditLogger.sanitize("x\u0085y\u2028z\u2029w")).isEqualTo("x_y_z_w");
        assertThat(AuditLogger.sanitize("nul\u0000del\u007f")).isEqualTo("nul_del_");
        assertThat(AuditLogger.sanitize("forged\n{\"event\":\"fake\"}")).doesNotContain("\n");
    }

    @Test
    void sanitizeLeavesSafeValuesAlone() {
        assertThat(AuditLogger.sanitize("bad_credentials")).isEqualTo("bad_credentials");
        assertThat(AuditLogger.sanitize("")).isEmpty();
        assertThat(AuditLogger.sanitize(null)).isNull();
    }

    @Test
    void sanitizeCapsLengthAtSixtyFour() {
        assertThat(AuditLogger.sanitize("a".repeat(64))).hasSize(64);
        assertThat(AuditLogger.sanitize("a".repeat(65))).isEqualTo("a".repeat(64));
        assertThat(AuditLogger.sanitize("\n".repeat(100))).isEqualTo("_".repeat(64));
    }

    @Test
    void emitSetsTheActorForOneEventAndRestoresThePreviousMdcUserId() {
        UUID requestUser = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        MDC.put(AuditLogger.USER_ID, requestUser.toString());

        try (LogCapture logs = LogCapture.start()) {
            auditLogger.loginSucceeded(actor);

            LogCapture.Event event = logs.audit("User authenticated").getFirst();
            assertThat(event.mdc()).containsEntry(AuditLogger.USER_ID, actor.toString());
            assertThat(event.kv("event.action")).isEqualTo("user-authentication");
            assertThat(event.kv("event.category")).isEqualTo("authentication");
            assertThat(event.kv("event.outcome")).isEqualTo("success");
        }
        assertThat(MDC.get(AuditLogger.USER_ID)).isEqualTo(requestUser.toString());
    }

    @Test
    void preAuthEventCarriesNoUserIdButTheRequestUserIsRestoredAfterwards() {
        UUID requestUser = UUID.randomUUID();
        MDC.put(AuditLogger.USER_ID, requestUser.toString());

        try (LogCapture logs = LogCapture.start()) {
            auditLogger.loginFailed(null, "bad\ncredentials");

            LogCapture.Event event = logs.audit("User authentication failed").getFirst();
            assertThat(event.mdc()).doesNotContainKey(AuditLogger.USER_ID);
            assertThat(event.kv("event.reason")).isEqualTo("bad_credentials");
            assertThat(event.kv("event.outcome")).isEqualTo("failure");
        }
        assertThat(MDC.get(AuditLogger.USER_ID)).isEqualTo(requestUser.toString());
    }

    @Test
    void emitWithNoPriorUserIdLeavesMdcClean() {
        auditLogger.registered(UUID.randomUUID());
        assertThat(MDC.get(AuditLogger.USER_ID)).isNull();
    }

    @Test
    void sessionsTerminatedNamesTheCurrentMdcUserAsActorAndTheTargetAsAField() {
        UUID admin = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        MDC.put(AuditLogger.USER_ID, admin.toString());

        try (LogCapture logs = LogCapture.start()) {
            auditLogger.sessionsTerminated(target, "account_disabled", 2);

            LogCapture.Event event = logs.audit("User sessions terminated").getFirst();
            assertThat(event.mdc()).containsEntry(AuditLogger.USER_ID, admin.toString());
            assertThat(event.kv("user.target.id")).isEqualTo(target.toString());
            assertThat(event.kv("event.reason")).isEqualTo("account_disabled");
            assertThat(event.kv("labels.session_count")).isEqualTo("2");
        }
    }

    @Test
    void everyEventTypeIsEmittedOnTheAuditLogger() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        try (LogCapture logs = LogCapture.start()) {
            auditLogger.accountLocked(a);
            auditLogger.rateLimited("login_ip");
            auditLogger.loggedOut(a);
            auditLogger.sessionsTerminated(a, "password_reset", 1);
            auditLogger.accessDenied(null, "csrf");
            auditLogger.registered(a);
            auditLogger.passwordResetRequested(a);
            auditLogger.passwordResetCompleted(a);
            auditLogger.passwordResetRejected("invalid_token");
            auditLogger.adminChange(a, b, "role_changed", "ADMIN");
            auditLogger.adminChange(a, b, "account_deleted", null);
            auditLogger.adminChangeRejected(a, b, "account_disabled", "last_admin");

            assertThat(logs.audit()).hasSize(12);
            assertThat(logs.audit("Request rate limited").getFirst().kv("labels.limiter")).isEqualTo("login_ip");
            assertThat(logs.audit("User administration change").getFirst().kv("labels.new_value")).isEqualTo("ADMIN");
            assertThat(logs.audit("User administration change").get(1).keyValues()).doesNotContainKey("labels.new_value");
            assertThat(logs.audit("User administration change rejected").getFirst().kv("event.reason"))
                    .isEqualTo("last_admin");
        }
    }
}
