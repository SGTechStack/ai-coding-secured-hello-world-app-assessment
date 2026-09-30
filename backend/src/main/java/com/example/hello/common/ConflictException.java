package com.example.hello.common;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 409: a uniqueness rule was violated on the named field. */
public class ConflictException extends ApiException {

  public ConflictException(String field, String message) {
    super(
        HttpStatus.CONFLICT,
        message,
        Map.of("errors", List.of(Map.of("field", field, "message", message))));
  }
}
