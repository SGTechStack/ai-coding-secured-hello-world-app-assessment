package com.eitri.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.testsupport.SessionClient;
import com.eitri.testsupport.SessionClient.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:session-cookie-dev;DB_CLOSE_DELAY=-1")
@ActiveProfiles({"test", "dev"})
@AutoConfigureMockMvc
class SessionCookieDevIT {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void devSessionCookieRemainsHttpOnlyAndSameSiteLaxButIsNotSecure() {
        assertThat(mvc.get().uri("/api/v1/csrf").exchange().getResponse().getHeader("Set-Cookie"))
                .contains("SESSION=", "Path=/", "HttpOnly", "SameSite=Lax")
                .doesNotContain("; Secure");
    }

    @Test
    @DisplayName("[assessment/story13-ac1] the dev profile's SESSION cookie is HttpOnly; SameSite=Lax without Secure")
    void devLoginSetsAHttpOnlySameSiteLaxCookieWithoutSecure() throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);

        MvcTestResult login =
                session.login(mvc, "{\"username\":\"johndoe\",\"password\":\"Password123!\"}");

        assertThat(login).hasStatusOk();
        assertThat(login.getResponse().getHeader("Set-Cookie"))
                .contains("SESSION=", "HttpOnly", "SameSite=Lax")
                .doesNotContain("Secure");
    }
}
