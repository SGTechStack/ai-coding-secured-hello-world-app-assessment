package org.eds.demo.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends BusinessException {

  private final String field;

  public ConflictException(String message) {
    super(HttpStatus.CONFLICT, message);
    this.field = null;
  }

  public ConflictException(String field, String message) {
    super(HttpStatus.CONFLICT, message);
    this.field = field;
  }

  public String getField() {
    return field;
  }
}
