package com.example.hello.common;

import org.springframework.http.HttpStatus;

/**
 * 401 with a deliberately generic message: the same response is produced for an unknown
 * username, a wrong password, a locked account and a disabled account.
 */
public class InvalidCredentialsException extends ApiException {

  public static final String DETAIL = "Invalid username or password";

  public InvalidCredentialsException() {
    super(HttpStatus.UNAUTHORIZED, DETAIL);
  }
}
