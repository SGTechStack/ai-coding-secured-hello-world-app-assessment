package com.eitri.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.testsupport.SessionClient;
import com.eitri.testsupport.SessionClient.Session;
import com.eitri.testsupport.StructuredLogTestCapture;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AuthAuditIT {

    private static final Logger MASKING_TEST_LOGGER = LoggerFactory.getLogger("MASKING_TEST");
    private static final String TEST_HASH_KEY = "test-only-session-hash-placeholder-not-for-production";
    private static final String ACCOUNT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String TRACE_ID = "7bf92f3577b34da6a3ce929d0e0e4736";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";

    @Autowired
    private MockMvcTester mvc;

    private StructuredLogTestCapture auditCapture;

    @BeforeEach
    void captureDedicatedAuditEvents() {
        auditCapture = StructuredLogTestCapture.audit();
    }

    @AfterEach
    void stopCapturingDedicatedAuditEvents() {
        auditCapture.close();
    }

    @Test
    void successfulLoginWritesSearchableInfoAuditForTheRotatedSessionWithoutSensitiveValues(CapturedOutput output)
            throws Exception {
        Session csrf = fetchSession();
        String originalSessionId = csrf.sessionId();

        MvcTestResult result = login(csrf, "JohnDoe", "Password123!");

        assertThat(result).hasStatusOk();
        String rotatedSessionId = csrf.sessionId();
        String event = auditLine("User authentication succeeded");
        assertThat(JsonPath.<String>read(event, "$.log.level")).isEqualTo("INFO");
        assertThat(JsonPath.<String>read(event, "$.log.logger")).isEqualTo("AUDIT");
        assertThat(JsonPath.<String>read(event, "$.event.kind")).isEqualTo("event");
        assertThat(JsonPath.<String>read(event, "$.event.category")).isEqualTo("authentication");
        assertThat(JsonPath.<String>read(event, "$.event.action")).isEqualTo("user-authentication");
        assertThat(JsonPath.<String>read(event, "$.event.outcome")).isEqualTo("success");
        assertThat(JsonPath.<String>read(event, "$.user.id")).isEqualTo(ACCOUNT_ID);
        // The stored lower-case username, never the text as typed.
        assertThat(JsonPath.<String>read(event, "$.user.name")).isEqualTo("johndoe");
        assertThat(JsonPath.<String>read(event, "$.session.hash"))
                .isEqualTo(hmac(rotatedSessionId))
                .isNotEqualTo(hmac(originalSessionId));
        assertThat(JsonPath.<String>read(event, "$.trace.id")).isEqualTo(TRACE_ID);
        assertThat(JsonPath.<String>read(event, "$.span.id")).hasSize(16);
        assertThat(event)
                .doesNotContain("JohnDoe", "Password123!", csrf.token())
                .doesNotContain("\"" + originalSessionId + "\"", "\"" + rotatedSessionId + "\"")
                .doesNotContain("\"username\"", "\"password\"");
        assertThat(output.getAll()).doesNotContain("\"message\":\"User authentication succeeded\"");
    }

    @Test
    void failedLoginForAKnownAccountIncludesItsIdWithoutLeakingCredentials() throws Exception {
        Session csrf = fetchSession();
        String sessionId = csrf.sessionId();

        assertThat(login(csrf, "JohnDoe", "wrong-audit-password"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .isStrictlyEqualTo("""
                        {"message":"Invalid username or password"}
                        """);

        String event = auditLine("User authentication failed");
        assertFailureEvent(event, sessionId);
        assertThat(JsonPath.<String>read(event, "$.user.id")).isEqualTo(ACCOUNT_ID);
        assertThat(JsonPath.<String>read(event, "$.user.name")).isEqualTo("johndoe");
        assertThat(event)
                .doesNotContain("JohnDoe", "wrong-audit-password", csrf.token())
                .doesNotContain("\"" + sessionId + "\"", "\"username\"", "\"password\"");
    }

    @Test
    void failedLoginForAnUnknownAccountOmitsUserId() throws Exception {
        Session csrf = fetchSession();

        assertThat(login(csrf, "unknown-audit-user", "wrong-audit-password"))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        String event = auditLine("User authentication failed");
        assertFailureEvent(event, csrf.sessionId());
        assertThat(event)
                .doesNotContain("unknown-audit-user", "wrong-audit-password", csrf.token())
                .doesNotContain("\"user\":", "\"username\"", "\"password\"");
    }

    @Test
    void structuredLoggingMasksSensitiveKeysIncludingContainersButLeavesSessionHashAvailable(
            CapturedOutput output) {
        MASKING_TEST_LOGGER
                .atInfo()
                .addKeyValue("password", "password-canary")
                .addKeyValue("auth.token", List.of("token-canary"))
                .addKeyValue("clientSecret", new String[] {"secret-canary"})
                .addKeyValue("session.id", "session-canary")
                .addKeyValue("session.hash", "approved-session-hash")
                .setMessage("Structured masking test")
                .log();

        String event = output.getAll().lines()
                .filter(line -> line.contains("\"message\":\"Structured masking test\""))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No structured masking log line found"));
        assertThat(event)
                .doesNotContain("password-canary", "token-canary", "secret-canary", "session-canary")
                .contains("***MASKED***", "approved-session-hash");
    }

    private static void assertFailureEvent(String event, String sessionId) throws GeneralSecurityException {
        assertThat(JsonPath.<String>read(event, "$.log.level")).isEqualTo("WARN");
        assertThat(JsonPath.<String>read(event, "$.log.logger")).isEqualTo("AUDIT");
        assertThat(JsonPath.<String>read(event, "$.event.kind")).isEqualTo("event");
        assertThat(JsonPath.<String>read(event, "$.event.category")).isEqualTo("authentication");
        assertThat(JsonPath.<String>read(event, "$.event.action")).isEqualTo("user-authentication");
        assertThat(JsonPath.<String>read(event, "$.event.outcome")).isEqualTo("failure");
        assertThat(JsonPath.<String>read(event, "$.session.hash")).isEqualTo(hmac(sessionId));
    }

    private Session fetchSession() throws Exception {
        return SessionClient.fetchCsrf(mvc);
    }

    private MvcTestResult login(Session session, String username, String password) {
        return session.login(
                mvc, "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}", TRACEPARENT);
    }

    private String auditLine(String message) {
        String line = auditCapture.line(message);
        assertThat(JsonPath.<String>read(line, "$.log.logger")).isEqualTo("AUDIT");
        return line;
    }

    private static String hmac(String sessionId) throws GeneralSecurityException {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(TEST_HASH_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(hmac.doFinal(sessionId.getBytes(StandardCharsets.UTF_8)));
    }
}
