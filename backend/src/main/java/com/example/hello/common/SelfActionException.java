package com.example.hello.common;

import org.springframework.http.HttpStatus;

/** 400: an admin attempted a destructive action on their own account. */
public class SelfActionException extends ApiException {

  public SelfActionException(String detail) {
    super(HttpStatus.BAD_REQUEST, detail);
  }
}
