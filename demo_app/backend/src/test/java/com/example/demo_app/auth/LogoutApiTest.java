package com.example.demo_app.auth;

import static com.example.demo_app.auth.SpaAuthFlow.logIn;
import static com.example.demo_app.auth.SpaAuthFlow.withCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * HTTP seam for {@code POST /api/v1/auth/logout}: real security filter chain and H2, real SPA CSRF
 * flow ({@link SpaAuthFlow}; see there for why spring-security-test's {@code csrf()}
 * post-processor is not used).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LogoutApiTest {

  private static final String LOGOUT = "/api/v1/auth/logout";

  @Autowired private MockMvc mvc;

  @Test
  void logoutReturnsEmpty204AndExpiresSessionAndCsrfCookies() throws Exception {
    MockHttpSession session = logIn(mvc);

    mvc.perform(logoutRequest().session(session))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""))
        .andExpect(header().doesNotExist("Location"))
        .andExpect(cookie().maxAge("JSESSIONID", 0))
        .andExpect(cookie().path("JSESSIONID", "/"))
        .andExpect(cookie().httpOnly("JSESSIONID", true))
        .andExpect(cookie().secure("JSESSIONID", true))
        .andExpect(cookie().maxAge("XSRF-TOKEN", 0));
  }

  @Test
  void oldSessionIsRejectedByMeAfterLogout() throws Exception {
    MockHttpSession session = logIn(mvc);

    mvc.perform(logoutRequest().session(session)).andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());
  }

  @Test
  void logoutWithoutASessionReturns204() throws Exception {
    mvc.perform(logoutRequest()).andExpect(status().isNoContent());
  }

  @Test
  void loggingOutTwiceReturns204BothTimes() throws Exception {
    MockHttpSession session = logIn(mvc);

    mvc.perform(logoutRequest().session(session)).andExpect(status().isNoContent());
    mvc.perform(logoutRequest().session(session)).andExpect(status().isNoContent());
  }

  @Test
  void logoutWithoutCsrfTokenIs403AndKeepsTheSession() throws Exception {
    MockHttpSession session = logIn(mvc);

    mvc.perform(post(LOGOUT).session(session))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.message").value("Forbidden"))
        .andExpect(jsonPath("$.timestamp").isString())
        .andExpect(jsonPath("$.path").value(LOGOUT));

    mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk());
  }

  @Test
  void logoutWithWrongCsrfTokenIs403AndKeepsTheSession() throws Exception {
    MockHttpSession session = logIn(mvc);

    mvc.perform(
            post(LOGOUT)
                .session(session)
                .cookie(new Cookie("XSRF-TOKEN", "cookie-token"))
                .header("X-XSRF-TOKEN", "different-token"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk());
  }

  @Test
  void getOnTheLogoutPathDoesNotEndTheSession() throws Exception {
    MockHttpSession session = logIn(mvc);

    mvc.perform(get(LOGOUT).session(session));

    mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk());
  }

  /** Exact header values are pinned once, in {@code SecurityHeadersTest}. */
  @Test
  void logoutResponseCarriesSecurityHeaders() throws Exception {
    mvc.perform(logoutRequest())
        .andExpect(status().isNoContent())
        .andExpect(header().exists("Content-Security-Policy"))
        .andExpect(header().exists("Referrer-Policy"))
        .andExpect(header().exists("Permissions-Policy"))
        .andExpect(header().exists("X-Content-Type-Options"))
        .andExpect(header().exists("X-Frame-Options"));
  }

  /** A logout POST carrying a freshly issued CSRF cookie and matching header, as the SPA sends. */
  private MockHttpServletRequestBuilder logoutRequest() throws Exception {
    return withCsrf(mvc, post(LOGOUT));
  }
}
