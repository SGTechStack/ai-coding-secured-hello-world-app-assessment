package org.eds.demo.common.exception;

import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class RequestLimitExceededException extends BusinessException {

  private final long retryAfterSeconds;

  @Builder
  public RequestLimitExceededException(String message, long retryAfterSeconds) {
    super(HttpStatus.TOO_MANY_REQUESTS, message);
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
