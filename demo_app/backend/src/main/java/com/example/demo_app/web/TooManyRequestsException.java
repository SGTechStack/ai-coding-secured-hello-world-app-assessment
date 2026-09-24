package com.example.demo_app.web;

import java.time.Duration;

/**
 * The client has used up its attempts for now. {@code ApiExceptionHandler} answers {@code 429
 * TOO_MANY_REQUESTS} with a {@code Retry-After} header of {@link #retryAfter()} in whole seconds.
 * No stack trace is captured: a flood of these is expected and must stay cheap.
 */
public class TooManyRequestsException extends RuntimeException {

  private final Duration retryAfter;

  public TooManyRequestsException(Duration retryAfter) {
    super("Too many requests; retry after " + retryAfter, null, false, false);
    this.retryAfter = retryAfter;
  }

  /** How long until the client may try again; at least one second. */
  public Duration retryAfter() {
    return retryAfter;
  }
}
