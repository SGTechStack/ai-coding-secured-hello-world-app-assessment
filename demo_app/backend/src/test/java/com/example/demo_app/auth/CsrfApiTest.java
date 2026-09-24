package com.example.demo_app.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/v1/auth/csrf} hands out the token in the body, for an SPA that cannot read the
 * API's cookies, and still sets the {@code XSRF-TOKEN} cookie the double-submit check needs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CsrfApiTest {

  @Autowired private MockMvc mvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void returnsTheTokenInTheBodyAndTheCookie() throws Exception {
    MockHttpServletResponse response =
        mvc.perform(get("/api/v1/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
            .andReturn()
            .getResponse();

    Cookie cookie = response.getCookie("XSRF-TOKEN");
    assertThat(cookie).isNotNull();
    assertThat(bodyToken(response)).isNotBlank().isEqualTo(cookie.getValue());
  }

  @Test
  void theBodyTokenWorksAsTheHeaderOnAStateChangingRequest() throws Exception {
    MockHttpServletResponse csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse();

    mvc.perform(
            post("/api/v1/auth/login")
                .cookie(csrf.getCookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN", bodyToken(csrf))
                .contentType(MediaType.APPLICATION_JSON)
                .content(SpaAuthFlow.DEMO_LOGIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("johndoe"));
  }

  private String bodyToken(MockHttpServletResponse response) throws Exception {
    JsonNode body = objectMapper.readTree(response.getContentAsString());
    return body.get("token").asText();
  }
}
