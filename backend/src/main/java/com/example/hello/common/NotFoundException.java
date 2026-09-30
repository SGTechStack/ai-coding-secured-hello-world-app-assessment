package com.example.hello.common;

import org.springframework.http.HttpStatus;

/** 404: the addressed resource does not exist. */
public class NotFoundException extends ApiException {

  public NotFoundException(String detail) {
    super(HttpStatus.NOT_FOUND, detail);
  }
}
