package com.example.hello.common;

import org.springframework.http.HttpStatus;

/** 400: reset token unknown, expired or already used (one message for all three). */
public class InvalidResetTokenException extends ApiException {

  public InvalidResetTokenException() {
    super(HttpStatus.BAD_REQUEST, "Reset token is invalid, expired or has already been used");
  }
}
