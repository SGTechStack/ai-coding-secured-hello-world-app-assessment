package com.example.hello.hello;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.hello.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Story 5 and the INFRA-BE-01 response conventions. */
class HelloIntegrationTest extends IntegrationTestSupport {

  @Test
  @DisplayName("authenticated user receives a personalised greeting")
  void greetsAuthenticatedUser() throws Exception {
    String username = registerUser("hi");
    ClientSession session = login(username, GOOD_PASSWORD);

    mockMvc
        .perform(get("/api/hello").cookie(session.cookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Hello, " + username))
        .andExpect(header().exists("X-Correlation-Id"))
        .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));
  }

  @Test
  @DisplayName("no session yields 401 as a problem document")
  void anonymousIsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/hello"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(401));
  }

  @Test
  @DisplayName("an invalid session cookie yields 401")
  void bogusCookieIsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/hello").cookie(new Cookie(SESSION_COOKIE, "bm90LWEtcmVhbC1zZXNzaW9u")))
        .andExpect(status().isUnauthorized());
  }
}
