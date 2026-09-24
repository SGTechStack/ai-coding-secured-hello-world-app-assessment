package com.example.demo_app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The {@code prod} profile: plain HTTP is redirected to HTTPS, the demo user is not seeded, and the
 * console logs ECS JSON.
 * Uses its own in-memory database so the seed applied by other test contexts cannot leak in.
 */
@SpringBootTest(
    properties = "spring.datasource.url=jdbc:h2:mem:prod-profile-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ProdProfileTest {

  @Autowired private MockMvc mvc;

  @Autowired private Environment environment;

  @Test
  void consoleLoggingIsEcsJson() {
    assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
  }

  @Test
  void plainHttpIsRedirectedToHttps() throws Exception {
    mvc.perform(get("http://localhost/api/v1/auth/csrf"))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location", "https://localhost/api/v1/auth/csrf"));
  }

  @Test
  void httpsIsServed() throws Exception {
    mvc.perform(get("https://localhost/api/v1/auth/csrf")).andExpect(status().isNoContent());
  }

  @Test
  void demoUserIsNotSeeded() throws Exception {
    Cookie xsrf =
        mvc.perform(get("https://localhost/api/v1/auth/csrf"))
            .andReturn()
            .getResponse()
            .getCookie("XSRF-TOKEN");

    mvc.perform(
            post("https://localhost/api/v1/auth/login")
                .cookie(xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"johndoe\", \"password\": \"Password123!\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }
}
