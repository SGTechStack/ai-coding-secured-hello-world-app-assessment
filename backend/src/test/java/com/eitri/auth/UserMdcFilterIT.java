package com.eitri.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.testsupport.SessionClient;
import com.eitri.testsupport.SessionClient.Session;
import com.eitri.testsupport.StructuredLogTestCapture;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(properties = {
    "app.security.authorization.matrix[0].role=USER",
    "app.security.authorization.matrix[0].permissions[0].method=GET",
    "app.security.authorization.matrix[0].permissions[0].path=/api/v1/auth/me",
    "app.security.authorization.matrix[1].role=ADMIN",
    "app.security.authorization.matrix[1].permissions[0].method=GET",
    "app.security.authorization.matrix[1].permissions[0].path=/api/v1/auth/me",
    "app.security.authorization.matrix[2].role=USER",
    "app.security.authorization.matrix[2].permissions[0].method=GET",
    "app.security.authorization.matrix[2].permissions[0].path=/test/mdc-probe"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import(UserMdcFilterIT.MdcProbeController.class)
class UserMdcFilterIT {

    private static final String ACCOUNT_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvcTester mvc;

    @Test
    void configuredFilterAddsUserIdForAuthenticatedHttpRequestAndCleansItBeforeAnonymousRequest(
            CapturedOutput output) throws Exception {
        Session authenticated = fetchSession();
        assertThat(login(authenticated, "johndoe", "Password123!")).hasStatusOk();

        assertThat(mvc.get().uri("/test/mdc-probe").cookie(authenticated.cookie())).hasStatusOk();
        String probe = logLine(output, "Authenticated MDC probe");
        assertThat(JsonPath.<String>read(probe, "$.user.id")).isEqualTo(ACCOUNT_ID);

        Session anonymous = fetchSession();
        try (StructuredLogTestCapture audit = StructuredLogTestCapture.audit()) {
            assertThat(login(anonymous, "unknown-mdc-user", "wrong-mdc-password")).hasStatus(401);
            String failure = audit.line("User authentication failed");
            assertThat(failure).doesNotContain("\"user\":", "unknown-mdc-user", "wrong-mdc-password");
        }
    }

    private Session fetchSession() throws Exception {
        return SessionClient.fetchCsrf(mvc);
    }

    private MvcTestResult login(Session session, String username, String password) {
        return session.login(
                mvc, "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }

    private static String logLine(CapturedOutput output, String message) {
        return output.getAll().lines()
                .filter(line -> line.contains("\"message\":\"" + message + "\""))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No structured log line found for: " + message));
    }

    @RestController
    static class MdcProbeController {

        private static final Logger LOG = LoggerFactory.getLogger(MdcProbeController.class);

        @GetMapping("/test/mdc-probe")
        void probe() {
            LOG.info("Authenticated MDC probe");
        }
    }
}
