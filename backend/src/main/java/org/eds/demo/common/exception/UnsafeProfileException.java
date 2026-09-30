package org.eds.demo.common.exception;

import org.eds.demo.config.SecurityProfileValidator;

/**
 * Thrown when an {@code unsafe-} profile is detected alongside a production-like profile.
 *
 * <p>This exception is raised at startup by {@link SecurityProfileValidator} to prevent unsafe
 * feature flags from reaching qa, pre-prod, or prod environments.
 */
public class UnsafeProfileException extends RuntimeException {

  public UnsafeProfileException(String message) {
    super(message);
  }
}
