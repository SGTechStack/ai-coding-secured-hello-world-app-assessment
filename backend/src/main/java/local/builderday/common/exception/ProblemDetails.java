package local.builderday.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;

/**
 * The single RFC 7807 construction and writing mechanism of ADR 0001: standard fields, default {@code about:blank}
 * type and the stable {@code code} extension. MVC advice returns {@link #of}; Spring Security handlers, which run
 * before any controller, call {@link #write}.
 */
public final class ProblemDetails {
  /** Registers the mixin that flattens {@code code} and other properties to top-level members, as MVC does. */
  private static final JacksonJsonHttpMessageConverter JSON = new JacksonJsonHttpMessageConverter();

  private ProblemDetails() {}

  public static ProblemDetail of(ApiError error) {
    var problem = ProblemDetail.forStatusAndDetail(error.status(), error.detail());
    problem.setProperty("code", error.name());
    return problem;
  }

  public static ResponseEntity<ProblemDetail> response(ApiError error) {
    return entity(of(error));
  }

  /** {@code error} with an {@code errors} extension listing each distinct entry once, in order. */
  public static ResponseEntity<ProblemDetail> rejected(ApiError error, List<FieldErrorResponse> errors) {
    var problem = of(error);
    problem.setProperty("errors", errors.stream().distinct().toList());
    return entity(problem);
  }

  /** An explicit content type, so a controller's {@code produces = application/json} never downgrades it. */
  private static ResponseEntity<ProblemDetail> entity(ProblemDetail problem) {
    return ResponseEntity.status(problem.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
  }

  /**
   * Writes {@code error} as {@code application/problem+json}. Used where no controller has been reached: Spring
   * Security handlers and the frontend-path answers of {@code SpaDocumentRequestFilter} (ADR 0007).
   */
  public static void write(HttpServletRequest request, HttpServletResponse response, ApiError error)
      throws IOException {
    response.setStatus(error.status().value());
    var problem = of(error);
    problem.setInstance(URI.create(request.getRequestURI()));
    JSON.write(problem, MediaType.APPLICATION_PROBLEM_JSON, new ServletServerHttpResponse(response));
  }

  /** The JSON/REST surface: {@code /api/**} and {@code GET /csrf}. */
  public static boolean isApiRequest(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return path.equals("/csrf") || path.equals("/api") || path.startsWith("/api/");
  }
}
