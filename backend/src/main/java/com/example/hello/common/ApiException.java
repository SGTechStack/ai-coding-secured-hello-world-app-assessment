package com.example.hello.common;

import java.util.Collections;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Base class for errors that map 1:1 to an HTTP problem response. */
public abstract class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final Map<String, Object> properties;

  protected ApiException(HttpStatus status, String detail) {
    this(status, detail, Map.of());
  }

  protected ApiException(HttpStatus status, String detail, Map<String, Object> properties) {
    super(detail);
    this.status = status;
    this.properties = Collections.unmodifiableMap(properties);
  }

  public HttpStatus getStatus() {
    return status;
  }

  /** Extra members to add to the problem document (e.g. field errors). */
  public Map<String, Object> getProperties() {
    return properties;
  }
}
