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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:concurrent-session-login;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ConcurrentSessionLoginIT {

    private static final String LOGIN_JSON =
            """
            {"username":"johndoe","password":"Password123!"}
            """;
    private static final String TEST_HASH_KEY = "test-only-session-hash-placeholder-not-for-production";

    @Autowired
    private MockMvcTester mvc;

    @Test
    void simultaneousLoginsLeaveExactlyOneActiveSessionAndAuditTheDisplacedSession() throws Exception {
        try (StructuredLogTestCapture audit = StructuredLogTestCapture.audit()) {
            Session first = SessionClient.fetchCsrf(mvc);
        Session second = SessionClient.fetchCsrf(mvc);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcTestResult> firstLogin = executor.submit(() -> loginTogether(first, ready, start));
            Future<MvcTestResult> secondLogin = executor.submit(() -> loginTogether(second, ready, start));
            ready.await();
            start.countDown();

            assertThat(firstLogin.get()).hasStatusOk();
            assertThat(secondLogin.get()).hasStatusOk();
        } finally {
            executor.shutdownNow();
        }

        int firstStatus = currentUserStatus(first);
        int secondStatus = currentUserStatus(second);
        assertThat(List.of(firstStatus, secondStatus)).containsExactlyInAnyOrder(200, 401);

        String displacedSessionId = firstStatus == 401 ? first.sessionId() : second.sessionId();
        String expectedHash = hmac(displacedSessionId);
            String event = audit.line("Session expired");
            assertThat(event)
                    .contains("\"action\":\"session-expired\"", "\"reason\":\"concurrent-login\"", expectedHash)
                    .doesNotContain(displacedSessionId);
            assertThat(JsonPath.<String>read(event, "$.user.name")).isEqualTo("johndoe");
        }
    }

    private MvcTestResult loginTogether(Session session, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        start.await();
        return session.login(mvc, LOGIN_JSON);
    }

    private int currentUserStatus(Session session) {
        return mvc.get()
                .uri("/api/v1/auth/me")
                .cookie(session.cookie())
                .exchange()
                .getResponse()
                .getStatus();
    }

    private static String hmac(String sessionId) throws GeneralSecurityException {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(TEST_HASH_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(hmac.doFinal(sessionId.getBytes(StandardCharsets.UTF_8)));
    }
}
