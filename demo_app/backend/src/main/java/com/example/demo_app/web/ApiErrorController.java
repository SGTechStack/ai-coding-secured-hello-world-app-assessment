package com.example.demo_app.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's {@code BasicErrorController} (and its whitelabel page) for errors raised
 * outside Spring MVC, which the servlet container forwards to {@code /error}: a request rejected
 * by the security firewall, an exception thrown by a filter, a {@code sendError}. The body is the
 * generic {@link ApiError} for the status and never includes exception detail.
 */
@RestController
class ApiErrorController implements ErrorController {

  private static final Logger log = LoggerFactory.getLogger(ApiErrorController.class);

  @RequestMapping("${server.error.path:/error}")
  ResponseEntity<ApiError> error(HttpServletRequest request) {
    HttpStatusCode status =
        request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code
            ? HttpStatusCode.valueOf(code)
            // Requested directly rather than forwarded: there is nothing here.
            : HttpStatus.NOT_FOUND;
    String path =
        request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String uri
            ? uri
            : request.getRequestURI();
    if (request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable ex) {
      log.error("Request to {} failed with {}", path, status.value(), ex);
    }
    ApiError body = ApiError.forStatus(status, path);
    return ResponseEntity.status(body.status()).contentType(MediaType.APPLICATION_JSON).body(body);
  }
}
