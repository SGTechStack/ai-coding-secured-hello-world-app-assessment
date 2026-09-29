package com.eitri.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.testsupport.SessionClient;
import com.eitri.testsupport.SessionClient.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationIT {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void authenticatedAccountIsForbiddenFromRequestsOutsideTheMatrix() throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);
        assertThat(session.login(mvc, "{\"username\":\"johndoe\",\"password\":\"Password123!\"}"))
                .hasStatusOk();

        assertThat(mvc.get().uri("/api/v1/anything").cookie(session.cookie()))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("[assessment/story13-ac8] a USER's client-supplied role claims are ignored by authorization")
    void clientSuppliedRoleClaimsAreIgnored() throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);
        assertThat(session.login(mvc, "{\"username\":\"johndoe\",\"password\":\"Password123!\"}"))
                .hasStatusOk();

        assertThat(mvc.get()
                        .uri("/api/v1/admin/users?role=ADMIN")
                        .cookie(session.cookie())
                        .header("X-User-Role", "ADMIN"))
                .hasStatus(HttpStatus.FORBIDDEN);
    }
}
