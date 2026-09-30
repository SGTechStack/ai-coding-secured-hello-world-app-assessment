package org.eds.demo.common.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends BusinessException {

  public NotFoundException(String message) {
    super(HttpStatus.NOT_FOUND, message);
  }

  public NotFoundException(String resource, Object identifier) {
    super(HttpStatus.NOT_FOUND, resource + " not found with id '" + identifier + "'");
  }
}
