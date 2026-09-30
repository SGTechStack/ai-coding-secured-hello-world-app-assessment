package com.example.hello.common;

import org.springframework.http.HttpStatus;

/** 429: the calling IP address exceeded the failed-login budget. */
public class TooManyLoginAttemptsException extends ApiException {

  public TooManyLoginAttemptsException() {
    super(HttpStatus.TOO_MANY_REQUESTS, "Too many failed login attempts. Please try again later.");
  }
}
