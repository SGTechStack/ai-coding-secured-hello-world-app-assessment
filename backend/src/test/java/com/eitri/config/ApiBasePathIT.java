package com.eitri.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The API base path is the {@code app.api.base-path} property, for both the controllers and the security rules. */
@SpringBootTest(properties = "app.api.base-path=/api/v2")
@AutoConfigureMockMvc
class ApiBasePathIT {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void endpointsAndTheirSecurityRulesFollowTheConfiguredBasePath() {
        assertThat(mvc.get().uri("/api/v2/csrf")).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/csrf")).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
