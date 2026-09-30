package com.example.hello.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.hello.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Story 4. */
class LogoutIntegrationTest extends IntegrationTestSupport {

  @Test
  @DisplayName("logout invalidates the server-side session, clears the cookie and rejects replay")
  void logoutEndsSession() throws Exception {
    String username = registerUser("bye");
    ClientSession session = login(username, GOOD_PASSWORD);

    mockMvc.perform(get("/api/hello").cookie(session.cookie())).andExpect(status().isOk());

    MvcResult logout =
        mockMvc
            .perform(authenticated(post("/api/auth/logout"), session))
            .andExpect(status().isNoContent())
            .andReturn();
    Cookie cleared = logout.getResponse().getCookie(SESSION_COOKIE);
    assertThat(cleared).isNotNull();
    assertThat(cleared.getMaxAge()).isZero();

    // Replay of the captured pre-logout cookie.
    mockMvc.perform(get("/api/hello").cookie(session.cookie())).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("logout requires the CSRF token")
  void logoutRequiresCsrf() throws Exception {
    String username = registerUser("byecsrf");
    ClientSession session = login(username, GOOD_PASSWORD);

    mockMvc.perform(post("/api/auth/logout").cookie(session.cookie())).andExpect(status().isForbidden());
    mockMvc.perform(get("/api/hello").cookie(session.cookie())).andExpect(status().isOk());
  }
}
