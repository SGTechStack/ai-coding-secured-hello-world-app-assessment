package com.eitri.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * The H2 console as the dev profile enables it. MockMvc doesn't mount the console servlet, so a
 * request that gets past security ends at the dispatcher with a 404 rather than the 401/403 it
 * would get from the API chain.
 */
@SpringBootTest(properties = "spring.h2.console.enabled=true")
@AutoConfigureMockMvc
class H2ConsoleSecurityIT {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void consoleIsReachableWithoutLoginAndMayRenderInSameOriginFrames() {
        assertThat(mvc.get().uri("/h2-console/login.jsp"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .headers().hasValue("X-Frame-Options", "SAMEORIGIN");
    }

    @Test
    void consoleFormsPostWithoutACsrfToken() {
        assertThat(mvc.post().uri("/h2-console/login.do")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void apiStaysLockedDown() {
        assertThat(mvc.get().uri("/api/v1/anything")).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
