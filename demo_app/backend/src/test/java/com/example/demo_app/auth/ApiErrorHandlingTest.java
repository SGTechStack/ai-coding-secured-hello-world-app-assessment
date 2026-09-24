package com.example.demo_app.auth;

import static com.example.demo_app.auth.SpaAuthFlow.logIn;
import static com.example.demo_app.auth.SpaAuthFlow.loginRequest;
import static com.example.demo_app.auth.SpaAuthFlow.withCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * Failures Spring MVC raises before or instead of a controller still answer with the standard
 * JSON error body. Unknown paths and wrong methods are probed with a session, since anonymous
 * requests to anything but the public endpoints are {@code 401} first.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiErrorHandlingTest {

  @Autowired private MockMvc mvc;

  @Test
  void malformedJsonIs400MalformedRequest() throws Exception {
    mvc.perform(loginRequest(mvc, "{\"username\": "))
        .andExpect(status().isBadRequest())
        .andExpectAll(apiError(400, "MALFORMED_REQUEST", "/api/v1/auth/login"));
  }

  @Test
  void unsupportedMediaTypeIs415() throws Exception {
    mvc.perform(loginRequest(mvc, "username=johndoe").contentType(MediaType.TEXT_PLAIN))
        .andExpect(status().isUnsupportedMediaType())
        .andExpectAll(apiError(415, "UNSUPPORTED_MEDIA_TYPE", "/api/v1/auth/login"));
  }

  @Test
  void unknownPathIs404NotFound() throws Exception {
    MockHttpSession session = logIn(mvc);

    mvc.perform(get("/api/v1/does-not-exist").session(session))
        .andExpect(status().isNotFound())
        .andExpectAll(apiError(404, "NOT_FOUND", "/api/v1/does-not-exist"));
  }

  @Test
  void wrongMethodIs405MethodNotAllowed() throws Exception {
    MockHttpSession session = logIn(mvc);

    mvc.perform(withCsrf(mvc, post("/api/v1/auth/me")).session(session))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string("Allow", "GET"))
        .andExpectAll(apiError(405, "METHOD_NOT_ALLOWED", "/api/v1/auth/me"));
  }

  @Test
  void jsonIsReturnedEvenWhenTheClientAsksForHtml() throws Exception {
    MockHttpSession session = logIn(mvc);

    mvc.perform(get("/api/v1/does-not-exist").session(session).accept(MediaType.TEXT_HTML))
        .andExpect(status().isNotFound())
        .andExpectAll(apiError(404, "NOT_FOUND", "/api/v1/does-not-exist"));
  }

  @Test
  void unacceptableResponseTypeIs406Json() throws Exception {
    mvc.perform(loginRequest(mvc, SpaAuthFlow.DEMO_LOGIN).accept(MediaType.TEXT_HTML))
        .andExpect(status().isNotAcceptable())
        .andExpectAll(apiError(406, "NOT_ACCEPTABLE", "/api/v1/auth/login"));
  }

  @Test
  void bodyOver16KbIsRejectedWith413Json() throws Exception {
    String oversized =
        "{\"username\": \"" + "a".repeat(16 * 1024) + "\", \"password\": \"Password123!\"}";

    mvc.perform(loginRequest(mvc, oversized))
        .andExpect(status().isPayloadTooLarge())
        .andExpectAll(apiError(413, "PAYLOAD_TOO_LARGE", "/api/v1/auth/login"))
        .andExpect(header().exists("Content-Security-Policy"));
  }

  @Test
  void bodyOf16KbIsAccepted() throws Exception {
    String prefix = "{\"username\": \"";
    String suffix = "\", \"password\": \"Password123!\"}";
    String exactly16Kb =
        prefix + "a".repeat(16 * 1024 - prefix.length() - suffix.length()) + suffix;

    // Read and authenticated (and rejected as a wrong login), not cut off by the size cap.
    mvc.perform(loginRequest(mvc, exactly16Kb))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  static ResultMatcher[] apiError(int status, String code, String path) {
    return new ResultMatcher[] {
      content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON),
      jsonPath("$.status").value(status),
      jsonPath("$.code").value(code),
      jsonPath("$.message").isString(),
      jsonPath("$.timestamp").isString(),
      jsonPath("$.path").value(path),
      jsonPath("$.fieldErrors").doesNotExist(),
      jsonPath("$.trace").doesNotExist(),
      jsonPath("$.exception").doesNotExist()
    };
  }
}
