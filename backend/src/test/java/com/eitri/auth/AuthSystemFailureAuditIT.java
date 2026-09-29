package com.eitri.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.eitri.logging.SanitizedLogException;
import com.eitri.testsupport.SessionClient;
import com.eitri.testsupport.SessionClient.Session;
import com.eitri.testsupport.StructuredLogTestCapture;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AuthSystemFailureAuditIT {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AuthenticationManager authenticationManager;

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
    void authenticationSystemFailureWritesSafeErrorAuditAndReturnsServerError(CapturedOutput output)
            throws Exception {
        Session csrf = SessionClient.fetchCsrf(mvc);
        String sessionId = csrf.sessionId();
        when(authenticationManager.authenticate(any())).thenThrow(new InternalAuthenticationServiceException(
                "database canary: system-audit-user Password123! " + sessionId));

        assertThat(mvc.post()
                        .uri("/api/v1/auth/login")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"system-audit-user","password":"Password123!"}
                                """))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson()
                .isStrictlyEqualTo("""
                        {"message":"Internal Server Error"}
                        """);

        String event = auditCapture.line("Authentication system failure");
        assertThat(JsonPath.<String>read(event, "$.log.level")).isEqualTo("ERROR");
        assertThat(JsonPath.<String>read(event, "$.log.logger")).isEqualTo("AUDIT");
        assertThat(JsonPath.<String>read(event, "$.event.action")).isEqualTo("user-authentication");
        assertThat(JsonPath.<String>read(event, "$.event.outcome")).isEqualTo("failure");
        assertThat(JsonPath.<Integer>read(event, "$.error.code")).isEqualTo(500);
        assertThat(JsonPath.<String>read(event, "$.error.category")).isEqualTo("database");
        assertThat(JsonPath.<Boolean>read(event, "$.error.follow_up_action")).isTrue();
        assertThat(JsonPath.<String>read(event, "$.error.type")).isEqualTo(SanitizedLogException.class.getName());
        assertThat(JsonPath.<String>read(event, "$.error.message")).isEqualTo("Authentication system failure");
        assertThat(JsonPath.<String>read(event, "$.error.stack_trace")).isNotBlank();
        assertThat(event)
                .doesNotContain("system-audit-user", "Password123!", csrf.token(), "database canary")
                .doesNotContain("\"" + sessionId + "\"")
                .doesNotContain("\"user\":", "\"username\"", "\"password\"")
                .doesNotContain("error_code", "error_category", "error_follow_up_action");
        assertThat(output.getAll()).doesNotContain("\"message\":\"Authentication system failure\"");
    }
}
