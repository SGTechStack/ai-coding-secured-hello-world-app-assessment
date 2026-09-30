package org.eds.demo.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void handleValidationReturns422WithFieldErrors() {
    var bindingResult = new BeanPropertyBindingResult(new Object(), "request");
    bindingResult.addError(
        new FieldError(
            "request", "email", null, false, new String[] {"NotBlank"}, null, "must not be blank"));
    bindingResult.addError(
        new FieldError(
            "request",
            "name",
            null,
            false,
            new String[] {"Size"},
            null,
            "size must be between 1 and 50"));

    var ex = new MethodArgumentNotValidException(null, bindingResult);

    var response =
        handler.handleMethodArgumentNotValid(ex, HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST, null);
    var problem = (ProblemDetail) response.getBody();

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(problem.getTitle()).isEqualTo("Validation Failed");
    assertThat(problem.getType().toString()).isEqualTo("/problems/validation-failed");

    @SuppressWarnings("unchecked")
    var fieldErrors = (Map<String, Map<String, String>>) problem.getProperties().get("fieldErrors");
    assertThat(fieldErrors.get("email")).containsEntry("code", "NotBlank");
    assertThat(fieldErrors.get("email")).containsEntry("message", "must not be blank");
  }

  @Test
  void handleUnreadableMessageWithInvalidFormat() {
    var ife = mock(InvalidFormatException.class);
    var ref = new JsonMappingException.Reference(null, "age");
    when(ife.getPath()).thenReturn(List.of(ref));
    when(ife.getValue()).thenReturn("abc");

    var ex = new HttpMessageNotReadableException("parse error", ife, null);

    var response =
        handler.handleHttpMessageNotReadable(ex, HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST, null);
    var problem = (ProblemDetail) response.getBody();

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(problem.getType().toString()).isEqualTo("/problems/malformed-request-body");
    assertThat(problem.getTitle()).isEqualTo("Malformed Request Body");
    assertThat(problem.getDetail()).contains("age");
    assertThat(problem.getDetail()).doesNotContain("abc");
  }

  @Test
  void handleUnreadableMessageWithMismatchedInput() {
    var mie = mock(MismatchedInputException.class);
    var ref = new JsonMappingException.Reference(null, "count");
    when(mie.getPath()).thenReturn(List.of(ref));

    var ex = new HttpMessageNotReadableException("parse error", mie, null);

    var response =
        handler.handleHttpMessageNotReadable(ex, HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST, null);
    var problem = (ProblemDetail) response.getBody();

    assertThat(problem.getDetail()).contains("count");
  }

  @Test
  void handleUnreadableMessageWithGenericCause() {
    var ex = new HttpMessageNotReadableException("bad json", (Throwable) null, null);

    var response =
        handler.handleHttpMessageNotReadable(ex, HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST, null);
    var problem = (ProblemDetail) response.getBody();

    assertThat(problem.getDetail()).isEqualTo("Request body could not be parsed");
  }

  @Test
  void handleBusinessExceptionUsesMessageAndStatus() {
    var ex = new NotFoundException("User", "abc-123");

    var problem = handler.handleBusinessException(ex);

    assertThat(problem.getStatus()).isEqualTo(404);
    assertThat(problem.getDetail()).isEqualTo("User not found with id 'abc-123'");
  }

  @Test
  void handleConflictException() {
    var ex = new ConflictException("Username already taken");

    var problem = handler.handleBusinessException(ex);

    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getDetail()).isEqualTo("Username already taken");
  }

  @Test
  void handleAccessDeniedReturns403() {
    var ex = new AccessDeniedException("denied");

    var problem = handler.handleAccessDenied(ex);

    assertThat(problem.getStatus()).isEqualTo(403);
    assertThat(problem.getTitle()).isEqualTo("Access Denied");
  }

  @Test
  void handleAllReturns500WithSafeMessage() {
    var ex = new RuntimeException("sensitive internal details");

    var problem = handler.handleAll(ex);

    assertThat(problem.getStatus()).isEqualTo(500);
    assertThat(problem.getTitle()).isEqualTo("Internal Server Error");
    assertThat(problem.getDetail()).doesNotContain("sensitive");
  }
}
