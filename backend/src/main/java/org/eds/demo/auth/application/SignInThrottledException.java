package org.eds.demo.auth.application;

import java.time.Duration;
import lombok.Getter;

/**
 * Sign-in refused because the caller's address has failed too often. Says nothing about any
 * Account, so it is safe to reveal to the client together with when to retry.
 */
@Getter
public class SignInThrottledException extends RuntimeException {

  private final Duration retryAfter;

  public SignInThrottledException(Duration retryAfter) {
    super("Too many failed sign-in attempts");
    this.retryAfter = retryAfter;
  }
}
