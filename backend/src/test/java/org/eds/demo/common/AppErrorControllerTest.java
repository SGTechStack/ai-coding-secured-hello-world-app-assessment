package org.eds.demo.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

class AppErrorControllerTest {

  private final AppErrorController controller = new AppErrorController();

  private HttpServletRequest requestWith(Integer statusCode, String errorPath) {
    var request = mock(HttpServletRequest.class);
    when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(statusCode);
    when(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn(errorPath);
    return request;
  }

  @Test
  void apiPathReturns404ProblemDetail() {
    var request = requestWith(404, "/api/users/missing");

    var result = (ProblemDetail) controller.handleError(request);

    assertThat(result.getStatus()).isEqualTo(404);
    assertThat(result.getTitle()).isEqualTo("API endpoint not found");
    assertThat(result.getDetail()).isEqualTo("No API endpoint exists for this path.");
    assertThat(result.getInstance().toString()).isEqualTo("/api/users/missing");
  }

  @Test
  void apiPathReturns405ProblemDetail() {
    var request = requestWith(405, "/api/users");

    var result = (ProblemDetail) controller.handleError(request);

    assertThat(result.getStatus()).isEqualTo(405);
    assertThat(result.getTitle()).isEqualTo("Method not allowed");
  }

  @Test
  void apiPathReturns400ProblemDetail() {
    var request = requestWith(400, "/api/users");

    var result = (ProblemDetail) controller.handleError(request);

    assertThat(result.getStatus()).isEqualTo(400);
    assertThat(result.getTitle()).isEqualTo("Bad request");
  }

  @Test
  void apiPathReturns500ProblemDetail() {
    var request = requestWith(500, "/api/users");

    var result = (ProblemDetail) controller.handleError(request);

    assertThat(result.getStatus()).isEqualTo(500);
    assertThat(result.getTitle()).isEqualTo("Internal server error");
  }

  @Test
  void apiPathWithUnrecognisedStatusFallsBackToReasonPhrase() {
    var request = requestWith(418, "/api/tea");

    var result = (ProblemDetail) controller.handleError(request);

    assertThat(result.getStatus()).isEqualTo(418);
    assertThat(result.getTitle()).isEqualTo("I'm a teapot");
  }

  @Test
  void nonApiNotFoundForwardsTo404Html() {
    var request = requestWith(404, "/app/missing");

    var result = (String) controller.handleError(request);

    assertThat(result).isEqualTo("forward:/404.html");
  }

  @Test
  void nonApiForbiddenForwardsTo403Html() {
    var request = requestWith(403, "/app/dashboard");

    var result = (String) controller.handleError(request);

    assertThat(result).isEqualTo("forward:/403.html");
  }

  @Test
  void nonApi500ForwardsTo500Html() {
    var request = requestWith(500, "/app/dashboard");

    var result = (String) controller.handleError(request);

    assertThat(result).isEqualTo("forward:/500.html");
  }

  @Test
  void missingStatusCodeDefaultsTo500AndForwards() {
    var request = mock(HttpServletRequest.class);
    when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(null);
    when(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn("/app/page");

    var result = (String) controller.handleError(request);

    assertThat(result).isEqualTo("forward:/500.html");
  }

  @Test
  void missingPathAttributeFallsBackToRequestUri() {
    var request = mock(HttpServletRequest.class);
    when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(500);
    when(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn(null);
    when(request.getRequestURI()).thenReturn("/error");

    var result = (String) controller.handleError(request);

    assertThat(result).isEqualTo("forward:/500.html");
  }

  @Test
  void missingPathAttributeWithApiUriFallsBackToRequestUri() {
    var request = mock(HttpServletRequest.class);
    when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);
    when(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn(null);
    when(request.getRequestURI()).thenReturn("/api/users/missing");

    var result = (ProblemDetail) controller.handleError(request);

    assertThat(result.getInstance().toString()).isEqualTo("/api/users/missing");
  }
}
