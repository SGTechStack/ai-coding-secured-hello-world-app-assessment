package org.eds.demo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/** Shared security exception handlers that write RFC 9457 Problem Detail JSON responses. */
final class SecurityProblemDetailHandlers {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private SecurityProblemDetailHandlers() {}

  static AuthenticationEntryPoint problemDetailEntryPoint() {
    return (request, response, authException) ->
        writeProblemDetail(
            response, HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication is required");
  }

  static AccessDeniedHandler problemDetailAccessDeniedHandler() {
    return (request, response, accessDeniedException) ->
        writeProblemDetail(
            response,
            HttpStatus.FORBIDDEN,
            "Access Denied",
            "You do not have permission to perform this action");
  }

  static void writeProblemDetail(
      HttpServletResponse response, HttpStatus status, String title, String detail)
      throws IOException {
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setTitle(title);
    problem.setDetail(detail);
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    MAPPER.writeValue(response.getWriter(), problem);
  }
}
