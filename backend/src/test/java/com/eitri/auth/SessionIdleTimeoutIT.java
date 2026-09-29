package com.eitri.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.testsupport.SessionClient;
import com.eitri.testsupport.SessionClient.Session;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:session-idle-timeout;DB_CLOSE_DELAY=-1",
    "app.security.session.idle-timeout=1s"
})
@AutoConfigureMockMvc
class SessionIdleTimeoutIT {

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("[assessment/story5-ac2] a session past the idle timeout is unauthorized")
    void inactiveSessionIsRejectedAfterTheConfiguredIdleTimeout() throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);
        assertThat(session.login(
                        mvc,
                        """
                        {"username":"johndoe","password":"Password123!"}
                        """))
                .hasStatusOk();

        Thread.sleep(Duration.ofMillis(1_200));

        assertThat(mvc.get().uri("/api/v1/auth/me").cookie(session.cookie()))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        MvcTestResult hello = mvc.get().uri("/api/v1/hello").cookie(session.cookie()).exchange();
        assertThat(hello).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(hello.getResponse().getContentAsString()).doesNotContain("Hello");
    }
}
