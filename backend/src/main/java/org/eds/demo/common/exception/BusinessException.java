package org.eds.demo.common.exception;

import java.net.URI;
import org.springframework.http.HttpStatus;

public abstract class BusinessException extends RuntimeException {

  private static final URI DEFAULT_TYPE = URI.create("about:blank");

  private final HttpStatus status;

  protected BusinessException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  protected BusinessException(HttpStatus status, String message, Throwable cause) {
    super(message, cause);
    this.status = status;
  }

  public HttpStatus getStatus() {
    return status;
  }

  /**
   * A stable, machine-readable identifier for this error condition, used as the RFC 9457 {@code
   * type} URI on the error response. Override in subclasses that need to be distinguished from
   * other exceptions sharing the same HTTP status (e.g. by the frontend).
   */
  public URI getType() {
    return DEFAULT_TYPE;
  }
}
