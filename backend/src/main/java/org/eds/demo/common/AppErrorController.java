package org.eds.demo.common;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AppErrorController implements ErrorController {

  public static final String ERROR_URL = WebPaths.ERROR;
  public static final String NOT_FOUND_PAGE = "/404.html";
  public static final String FORBIDDEN_PAGE = "/403.html";
  public static final String SERVER_ERROR_PAGE = "/500.html";

  @GetMapping(ERROR_URL)
  public Object handleError(HttpServletRequest request) {
    Object statusObj = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    Object pathObj = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

    int statusCode = statusObj != null ? Integer.parseInt(statusObj.toString()) : 500;

    String path = pathObj != null ? pathObj.toString() : request.getRequestURI();

    HttpStatus status = HttpStatus.resolve(statusCode);
    if (status == null) {
      status = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    if (path.startsWith("/api/")) {
      ProblemDetail problem = ProblemDetail.forStatus(status);
      problem.setTitle(getTitle(status));
      problem.setDetail("No API endpoint exists for this path.");
      problem.setInstance(URI.create(path));
      return problem;
    }

    if (status == HttpStatus.NOT_FOUND) {
      return "forward:" + NOT_FOUND_PAGE;
    }

    if (status == HttpStatus.FORBIDDEN) {
      return "forward:" + FORBIDDEN_PAGE;
    }

    return "forward:" + SERVER_ERROR_PAGE;
  }

  private String getTitle(HttpStatus status) {
    return switch (status) {
      case NOT_FOUND -> "API endpoint not found";
      case METHOD_NOT_ALLOWED -> "Method not allowed";
      case BAD_REQUEST -> "Bad request";
      case INTERNAL_SERVER_ERROR -> "Internal server error";
      default -> status.getReasonPhrase();
    };
  }
}
