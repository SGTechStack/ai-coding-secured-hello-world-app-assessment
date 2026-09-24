package com.example.demo_app.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The SPA's origin ({@code http://localhost:3000} in the test profile) may call the API with
 * credentials; any other origin gets no {@code Access-Control-Allow-Origin}, and its preflight is
 * refused.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsApiTest {

  private static final String SPA_ORIGIN = "http://localhost:3000";
  private static final String HOSTILE_ORIGIN = "https://evil.example";

  @Autowired private MockMvc mvc;

  @Test
  void preflightFromTheAllowedOriginGetsTheAllowHeaders() throws Exception {
    mvc.perform(preflight(SPA_ORIGIN, "POST", "content-type,x-xsrf-token"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, SPA_ORIGIN))
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PATCH,DELETE"))
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "content-type, x-xsrf-token"))
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"));
  }

  @Test
  void preflightForPatchAndDeleteIsAllowed() throws Exception {
    for (String method : new String[] {"PATCH", "DELETE"}) {
      mvc.perform(preflight(SPA_ORIGIN, method, "x-xsrf-token"))
          .andExpect(status().isOk())
          .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, SPA_ORIGIN));
    }
  }

  @Test
  void preflightForAnUnlistedMethodIsRefused() throws Exception {
    mvc.perform(preflight(SPA_ORIGIN, "PUT", "content-type"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
  }

  @Test
  void preflightForAnUnlistedHeaderIsRefused() throws Exception {
    mvc.perform(preflight(SPA_ORIGIN, "POST", "authorization"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
  }

  @Test
  void preflightFromAnotherOriginIsRefusedWithoutAllowHeaders() throws Exception {
    mvc.perform(preflight(HOSTILE_ORIGIN, "POST", "content-type,x-xsrf-token"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
  }

  @Test
  void preflightFromTheNullOriginIsRefused() throws Exception {
    mvc.perform(preflight("null", "POST", "content-type"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
  }

  @Test
  void actualRequestFromTheAllowedOriginCarriesTheAllowHeaders() throws Exception {
    mvc.perform(get("/api/v1/auth/csrf").header(HttpHeaders.ORIGIN, SPA_ORIGIN))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, SPA_ORIGIN))
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
  }

  /** The body carries the CSRF token: only the allow-list stops another origin reading it. */
  @Test
  void actualRequestFromAnotherOriginGetsNoAllowOrigin() throws Exception {
    mvc.perform(get("/api/v1/auth/csrf").header(HttpHeaders.ORIGIN, HOSTILE_ORIGIN))
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
  }

  private static MockHttpServletRequestBuilder preflight(
      String origin, String method, String headers) {
    return options("/api/v1/auth/login")
        .header(HttpHeaders.ORIGIN, origin)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, headers);
  }
}
