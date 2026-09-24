package com.example.helloauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.read.ListAppender;
import com.example.helloauth.audit.AuditLogger;
import com.example.helloauth.auth.IpThrottleService;
import com.example.helloauth.passwordreset.EmailService;
import com.example.helloauth.passwordreset.PasswordResetService;
import com.example.helloauth.user.Role;
import com.example.helloauth.user.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Ticket-14 coverage at the HTTP seam: every required audit event fires from
 * the real request path — login outcomes via the wired
 * {@code DefaultAuthenticationEventPublisher}, service-level rejections
 * (throttled/locked) emitted directly by {@code LoginService}, reset
 * request/complete, and the admin mutations with actor+target. Assertions
 * read the events back through a {@link ListAppender} on the dedicated
 * {@code audit} logger; the JSON encoding itself is pinned by running a
 * captured event through a real {@link LogstashEncoder} plus a wiring check
 * on the logger's configured appenders (logback-spring.xml is in effect
 * under {@code @SpringBootTest}). HTTP plumbing rides the shared
 * {@link ApiTestSupport} fixture.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditLoggingTests extends ApiTestSupport {

    @Autowired
    IpThrottleService ipThrottle;

    /** Captures the plaintext token at its only egress (ticket 12 seam). */
    @MockitoBean
    EmailService emailService;

    private final Logger auditLogger =
        (Logger) LoggerFactory.getLogger(AuditLogger.LOGGER_NAME);
    private ListAppender<ILoggingEvent> auditAppender;

    @BeforeEach
    void clean() {
        auditAppender = new ListAppender<>();
        auditAppender.start();
        auditLogger.addAppender(auditAppender);
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        ipThrottle.clear();
    }

    @AfterEach
    void detachAppender() {
        auditLogger.detachAppender(auditAppender);
        // The default-context suite shares one in-memory H2 — tokens must
        // not leak past this class (their user_id FK blocks later
        // deleteAll-on-users cleanups).
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ------------------------------------------------------------------
    // The wiring itself — the dedicated logger feeds JSON, nothing else
    // ------------------------------------------------------------------

    @Test
    void auditLoggerRoutesOnlyToTheLogstashJsonAppender() {
        // logback-spring.xml in effect: every appender on "audit" (excluding
        // this test's ListAppender) must be a ConsoleAppender encoding with
        // LogstashEncoder, and the logger must not leak into the root
        // pattern appender — one JSON line per event, nowhere else.
        List<Appender<ILoggingEvent>> appenders = new ArrayList<>();
        for (Iterator<Appender<ILoggingEvent>> it =
                auditLogger.iteratorForAppenders(); it.hasNext();) {
            Appender<ILoggingEvent> appender = it.next();
            if (!(appender instanceof ListAppender)) {
                appenders.add(appender);
            }
        }
        assertThat(appenders).singleElement().satisfies(appender -> {
            assertThat(appender).isInstanceOf(ConsoleAppender.class);
            assertThat(((ConsoleAppender<ILoggingEvent>) appender).getEncoder())
                .isInstanceOf(LogstashEncoder.class);
        });
        assertThat(auditLogger.isAdditive()).isFalse();
    }

    @Test
    void capturedEventEncodesAsJsonWithTopLevelFields() throws Exception {
        seedUser("alice", "alice@example.com", Role.USER);
        login("alice", "wrong password");
        ILoggingEvent event = lastAuditEvent();

        // The real encoder against the real emitted event — the marker's
        // entries must land as top-level JSON keys (machine-parseable),
        // not interpolated into the message string.
        LogstashEncoder encoder = new LogstashEncoder();
        encoder.start();
        String json = new String(encoder.encode(event));
        encoder.stop();

        assertThat(JsonPath.<String>read(json, "$.event"))
            .isEqualTo("login_failure");
        assertThat(JsonPath.<String>read(json, "$.actor")).isEqualTo("alice");
        assertThat(JsonPath.<String>read(json, "$.reason"))
            .isEqualTo("bad_credentials");
        assertThat(JsonPath.<String>read(json, "$.level")).isEqualTo("INFO");
        assertThat(JsonPath.<Object>read(json, "$.['@timestamp']")).isNotNull();
    }

    // ------------------------------------------------------------------
    // Login outcomes — via the wired AuthenticationEventPublisher
    // ------------------------------------------------------------------

    @Test
    void successfulLoginEmitsLoginSuccessWithActor() throws Exception {
        seedUser("alice", "alice@example.com", Role.USER);

        loginSession("alice", VALID_PASSWORD);

        assertThat(markersContaining("login_success")).singleElement()
            .satisfies(m -> assertThat(m).contains("actor=alice"));
    }

    @Test
    void failedLoginEmitsLoginFailureWithReason() throws Exception {
        seedUser("alice", "alice@example.com", Role.USER);

        login("alice", "wrong password");

        assertThat(markersContaining("login_failure")).singleElement()
            .satisfies(m -> assertThat(m)
                .contains("actor=alice")
                .contains("reason=bad_credentials"));
    }

    @Test
    void disabledAccountLoginEmitsDisabledReason() throws Exception {
        User alice = seedUser("alice", "alice@example.com", Role.USER);
        alice.setEnabled(false);
        userRepository.save(alice);

        // The provider's pre-auth check throws DisabledException inside
        // authenticate() — the publisher's event maps it to the stable
        // reason vocabulary, not the exception's free-form text.
        login("alice", VALID_PASSWORD, "10.3.0.7")
            .andExpect(status().isUnauthorized());

        assertThat(markersContaining("reason=disabled")).singleElement()
            .satisfies(m -> assertThat(m)
                .contains("event=login_failure")
                .contains("actor=alice"));
    }

    // ------------------------------------------------------------------
    // Service-level rejections — no auth event fires, audited directly
    // ------------------------------------------------------------------

    @Test
    void lockoutTriggerAndLockedAttemptEmitAuditEvents() throws Exception {
        seedUser("alice", "alice@example.com", Role.USER);

        // Five failures — each from a fresh IP so the anti-DoS invariant
        // (throttle 4 < lockout 5) can't intercept them — trigger the lock.
        for (int i = 1; i <= 5; i++) {
            login("alice", "bad", "10.1.0." + i);
        }

        assertThat(markersContaining("account_locked")).singleElement()
            .satisfies(m -> assertThat(m)
                .contains("target=alice")
                .contains("locked_until="));

        // A locked rejection happens before authenticate() runs — no
        // AuthenticationFailureEvent can fire; LoginService emits it.
        login("alice", VALID_PASSWORD, "10.1.0.99");
        assertThat(markersContaining("reason=locked")).singleElement();
    }

    @Test
    void throttledAttemptEmitsLoginFailureWithRemoteAddr() throws Exception {
        seedUser("alice", "alice@example.com", Role.USER);

        // Unknown usernames still burn the IP budget — four failures from
        // one address, then the fifth is throttled before any lookup.
        for (int i = 0; i < 4; i++) {
            login("ghost" + i, "bad", "10.2.0.5");
        }
        login("alice", VALID_PASSWORD, "10.2.0.5")
            .andExpect(status().isTooManyRequests());

        assertThat(markersContaining("reason=ip_throttled")).singleElement()
            .satisfies(m -> assertThat(m).contains("remote_addr=10.2.0.5"));
    }

    // ------------------------------------------------------------------
    // Password reset — requested and completed
    // ------------------------------------------------------------------

    @Test
    void resetRequestAndCompletionEmitAuditEvents() throws Exception {
        seedUser("alice", "alice@example.com", Role.USER);

        requestReset("alice@example.com").andExpect(status().isOk());
        requestReset("ghost@example.com").andExpect(status().isOk());

        // Both requests audit — the act is auditable whether or not the
        // email matched; the generic response stays enumeration-safe.
        assertThat(markersContaining("password_reset_requested"))
            .hasSize(2)
            .anySatisfy(m -> assertThat(m).contains("email=alice@example.com"))
            .anySatisfy(m -> assertThat(m).contains("email=ghost@example.com"));

        String token = emailedToken(emailService);
        confirmReset(token, NEW_PASSWORD).andExpect(status().isOk());

        assertThat(markersContaining("password_reset_completed"))
            .singleElement()
            .satisfies(m -> assertThat(m).contains("target=alice"));
    }

    // ------------------------------------------------------------------
    // Admin mutations — actor + target
    // ------------------------------------------------------------------

    @Test
    void adminMutationsEmitActorAndTarget() throws Exception {
        seedUser("root", "root@example.com", Role.ADMIN);
        User bob = seedUser("bob", "bob@example.com", Role.USER);
        Cookie session = loginSession("root", VALID_PASSWORD);

        patchWithCsrf("/api/admin/users/" + bob.getId() + "/status", session,
                "{\"enabled\":false}")
            .andExpect(status().isOk());
        patchWithCsrf("/api/admin/users/" + bob.getId() + "/role", session,
                "{\"role\":\"ADMIN\"}")
            .andExpect(status().isOk());
        deleteWithCsrf("/api/admin/users/" + bob.getId(), session)
            .andExpect(status().isNoContent());

        assertThat(markersContaining("admin_status_changed")).singleElement()
            .satisfies(m -> assertThat(m)
                .contains("actor=root").contains("target=bob")
                .contains("enabled=false"));
        assertThat(markersContaining("admin_role_changed")).singleElement()
            .satisfies(m -> assertThat(m)
                .contains("actor=root").contains("target=bob")
                .contains("role=ADMIN"));
        assertThat(markersContaining("admin_user_deleted")).singleElement()
            .satisfies(m -> assertThat(m)
                .contains("actor=root").contains("target=bob"));
    }

    // ------------------------------------------------------------------
    // Secrets never appear in audit output
    // ------------------------------------------------------------------

    @Test
    void noAuditLineContainsPasswordOrTokenMaterial() throws Exception {
        seedUser("alice", "alice@example.com", Role.USER);
        loginSession("alice", VALID_PASSWORD);
        login("alice", "a wrong guess");
        requestReset("alice@example.com").andExpect(status().isOk());
        String token = emailedToken(emailService);
        confirmReset(token, NEW_PASSWORD).andExpect(status().isOk());

        for (ILoggingEvent event : auditAppender.list) {
            String rendered = event.getFormattedMessage()
                + event.getMarkerList();
            assertThat(rendered)
                .doesNotContain(VALID_PASSWORD)
                .doesNotContain(NEW_PASSWORD)
                .doesNotContain("a wrong guess")
                .doesNotContain(token)
                .doesNotContain(PasswordResetService.hashToken(token));
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private ILoggingEvent lastAuditEvent() {
        assertThat(auditAppender.list).isNotEmpty();
        return auditAppender.list.get(auditAppender.list.size() - 1);
    }

    /** Marker renderings (the future JSON fields) containing a needle. */
    private List<String> markersContaining(String needle) {
        List<String> matches = new ArrayList<>();
        for (ILoggingEvent event : auditAppender.list) {
            String text = event.getFormattedMessage() + event.getMarkerList();
            if (text.contains(needle)) {
                matches.add(text);
            }
        }
        return matches;
    }
}
