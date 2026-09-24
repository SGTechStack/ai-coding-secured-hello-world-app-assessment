package com.example.demo_app.auth;

import static com.example.demo_app.auth.ApiErrorHandlingTest.apiError;
import static com.example.demo_app.auth.SpaAuthFlow.DEMO_LOGIN;
import static com.example.demo_app.auth.SpaAuthFlow.loginRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** A controller failing unexpectedly: generic JSON {@code 500}, detail in the server log only. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class UnexpectedExceptionTest {

  private static final String SECRET_DETAIL = "db password is hunter2";

  @Autowired private MockMvc mvc;

  @MockitoBean private AuthenticationManager authenticationManager;

  @Test
  void unexpectedExceptionIsGeneric500WithDetailOnlyInTheLog(CapturedOutput output)
      throws Exception {
    given(authenticationManager.authenticate(any()))
        .willThrow(new IllegalStateException(SECRET_DETAIL));

    String body =
        mvc.perform(loginRequest(mvc, DEMO_LOGIN))
            .andExpect(status().isInternalServerError())
            .andExpectAll(apiError(500, "INTERNAL_ERROR", "/api/v1/auth/login"))
            .andExpect(jsonPath("$.message").value("Something went wrong"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body)
        .doesNotContain(SECRET_DETAIL)
        .doesNotContain("IllegalStateException")
        .doesNotContain("at com.example");
    assertThat(output).contains(SECRET_DETAIL).contains("IllegalStateException");
  }

  @Test
  void accessDeniedIsLeftToSpringSecurityRatherThanTurnedInto500() throws Exception {
    given(authenticationManager.authenticate(any()))
        .willThrow(new AccessDeniedException("denied"));

    // Anonymous caller, so Spring Security's entry point answers 401.
    mvc.perform(loginRequest(mvc, DEMO_LOGIN))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }
}
