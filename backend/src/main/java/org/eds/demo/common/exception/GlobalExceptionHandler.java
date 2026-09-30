package org.eds.demo.common.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem = ProblemDetail.forStatus(422);
    problem.setType(URI.create("/problems/validation-failed"));
    problem.setTitle("Validation Failed");

    Map<String, Map<String, String>> fieldErrors = new LinkedHashMap<>();
    for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
      Map<String, String> entry = new LinkedHashMap<>();
      entry.put("code", fe.getCode() != null ? fe.getCode() : "Invalid");
      entry.put("message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid");
      fieldErrors.put(fe.getField(), entry);
    }

    problem.setProperty("fieldErrors", fieldErrors);
    return ResponseEntity.status(422).body(problem);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem = ProblemDetail.forStatus(400);
    problem.setType(URI.create("/problems/malformed-request-body"));
    problem.setTitle("Malformed Request Body");

    Throwable cause = ex.getCause();
    if (cause instanceof InvalidFormatException ife) {
      problem.setDetail(
          "Invalid value for field '"
              + ife.getPath().stream()
                  .map(JsonMappingException.Reference::getFieldName)
                  .collect(Collectors.joining("."))
              + "'");
    } else if (cause instanceof MismatchedInputException mie) {
      problem.setDetail(
          "Type mismatch for field '"
              + mie.getPath().stream()
                  .map(JsonMappingException.Reference::getFieldName)
                  .collect(Collectors.joining("."))
              + "'");
    } else {
      problem.setDetail("Request body could not be parsed");
    }

    return ResponseEntity.status(400).body(problem);
  }

  @ExceptionHandler(BusinessException.class)
  public ProblemDetail handleBusinessException(BusinessException ex) {
    ProblemDetail problem = ProblemDetail.forStatus(ex.getStatus());
    problem.setType(ex.getType());
    problem.setTitle(ex.getStatus().getReasonPhrase());
    problem.setDetail(ex.getMessage());
    if (ex instanceof ConflictException ce && ce.getField() != null) {
      Map<String, Map<String, String>> fieldErrors = new LinkedHashMap<>();
      Map<String, String> entry = new LinkedHashMap<>();
      entry.put("code", "Conflict");
      entry.put("message", ex.getMessage());
      fieldErrors.put(ce.getField(), entry);
      problem.setProperty("fieldErrors", fieldErrors);
    }
    if (ex instanceof RequestLimitExceededException rlee) {
      problem.setProperty("retryAfterSeconds", rlee.getRetryAfterSeconds());
    }
    return problem;
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
    ProblemDetail problem = ProblemDetail.forStatus(403);
    problem.setType(URI.create("about:blank"));
    problem.setTitle("Access Denied");
    problem.setDetail("You do not have permission to perform this action");
    return problem;
  }

  @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
  public ProblemDetail handleAuthenticationCredentialsNotFound(
      AuthenticationCredentialsNotFoundException ex) {
    ProblemDetail problem = ProblemDetail.forStatus(401);
    problem.setType(URI.create("about:blank"));
    problem.setTitle("Unauthorized");
    problem.setDetail("Authentication is required");
    return problem;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleAll(Exception ex) {
    log.error("Unhandled exception", ex);
    ProblemDetail problem = ProblemDetail.forStatus(500);
    problem.setType(URI.create("about:blank"));
    problem.setTitle("Internal Server Error");
    problem.setDetail("An unexpected error occurred");
    return problem;
  }
}
