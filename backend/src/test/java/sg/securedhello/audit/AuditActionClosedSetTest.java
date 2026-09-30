package sg.securedhello.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;

import org.junit.jupiter.api.Test;

import sg.securedhello.testsupport.Proves;

/**
 * Every audit row's {@code event.action} is a value of {@code Log_Schema.md}'s closed {@code event.action} enum, which
 * the log platform validates at ingest (R-STD-004). The standard is not in this repository, so its enum is copied here
 * verbatim; a row that needs a value outside it picks the nearest one and tells itself apart by message and
 * {@code event.type} instead.
 */
class AuditActionClosedSetTest {

    /** {@code Log_Schema.md}, Event fields, {@code event.action}'s allowed values, in the standard's order. */
    private static final Set<String> LOG_SCHEMA_ACTIONS = Set.of(
            "api-push", "api-pull", "file-generation", "file-generation-retry", "file-ack-process", "file-retrieval",
            "file-read", "file-process", "file-cleanup", "application-startup", "application-shutdown",
            "user-authentication", "user-logout", "user-provisioning", "user-administration", "profile-read",
            "password-reset", "password-change-enforcement", "session-start", "session-end", "totp-enrol",
            "totp-remove", "sms-otp-enrol", "sms-otp-remove", "hardware-token-enrol", "hardware-token-remove",
            "backup-code-enrol", "backup-code-remove", "data-export", "access-control", "REPORT_PRE_FILL",
            "REPORT_FILL", "REPORT_EXPORT", "REPORT_BACKUP", "ATTEMPTS_EXCEEDED", "PIN_CREATED", "TOTP_SETUP",
            "ACCESS_DENIED", "CRITICAL_TRANSACTION", "OTP_DELIVERY", "OTP_VERIFICATION", "KMS_ENCRYPT",
            "KMS_DECRYPT", "NOTIFICATION_SEND", "NOTIFICATION_SEND_RETRY", "NOTIFICATION_QUARANTINE");

    @Test
    @Proves("T-AUD-046")
    void everyRowsActionIsInTheClosedEnum() {
        assertThat(Arrays.stream(AuditEvent.values()))
                .allSatisfy(event -> assertThat(event.definition().action())
                        .as("%s's event.action", event)
                        .isIn(LOG_SCHEMA_ACTIONS));
    }
}
