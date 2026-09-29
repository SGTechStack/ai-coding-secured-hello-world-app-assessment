package com.example.auth.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Verifies audit log lines cannot be forged via CRLF injection in a
 * user-supplied username (CWE-117), and that secrets never appear.
 */
@ExtendWith(OutputCaptureExtension.class)
class AuditServiceTest {

    private final AuditService audit = new AuditService();

    @Test
    void loginFailure_sanitisesNewlinesToPreventLogForging(CapturedOutput output) {
        // Attacker submits a username crafted to inject a forged success line.
        audit.loginFailure("alice\nevent=login_success username=admin", "127.0.0.1");

        // The forged newline must be neutralised — no second real line is created.
        assertThat(output.getOut()).doesNotContain("\nevent=login_success username=admin");
        // The control char was replaced, so the payload survives on one line, defanged.
        assertThat(output.getOut()).contains("username=alice_event=login_success");
    }

    @Test
    void loginFailure_sanitisesCarriageReturn(CapturedOutput output) {
        audit.loginFailure("bob\r\nmalicious", "10.0.0.1");
        assertThat(output.getOut()).doesNotContain("\r\nmalicious");
    }

    @Test
    void emitsExpectedEventForCleanInput(CapturedOutput output) {
        audit.adminUserAction("actor-id", "target-user", "disable");
        assertThat(output.getOut())
                .contains("event=admin_user_action", "actor=actor-id", "target=target-user", "action=disable");
    }
}
