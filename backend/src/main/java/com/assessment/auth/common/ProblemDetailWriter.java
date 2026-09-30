package com.assessment.auth.common;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * The single writer for every error body (spec.md S10).
 *
 * <p>The {@code @RestControllerAdvice} returns {@link ProblemDetail} directly; the four off-MVC
 * sites — {@code accessDeniedHandler}, {@code PasswordChangeFilter}, {@code RateLimitFilter} and
 * {@code AbsoluteSessionTimeoutFilter} — call {@link #write} because they run outside MVC's message
 * converters. Both paths must produce the same bytes, which is why there is one writer and not two.
 *
 * <p>{@code response.sendError} is banned chain-wide and enforced by ArchUnit rule #5 (spec.md S4):
 * an ERROR dispatch is itself authorized under {@code filterErrorDispatch=true}, and
 * {@code sendError} cannot carry a {@code code}.
 */
@Component
public class ProblemDetailWriter {

  private final ObjectMapper objectMapper;

  public ProblemDetailWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Builds the body. {@code type} stays {@code about:blank} (spec.md S10). */
  public ProblemDetail problem(ApiErrorCode code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatus(code.status());
    problem.setType(URI.create("about:blank"));
    problem.setTitle(code.status().getReasonPhrase());
    problem.setDetail(detail);
    problem.setProperty("code", code.name());
    return problem;
  }

  /** Writes the body from an off-MVC site. Never calls {@code response.sendError}. */
  public void write(HttpServletResponse response, ApiErrorCode code, String detail)
      throws IOException {
    if (response.isCommitted()) {
      return;
    }
    response.setStatus(code.status().value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getOutputStream(), problem(code, detail));
    response.flushBuffer();
  }
}
