package local.builderday.account.registration.service;

import java.util.List;
import java.util.UUID;
import local.builderday.account.registration.model.RegistrationViolation;

/** The outcome of a registration attempt. Unexpected failures are thrown instead. */
public sealed interface RegistrationResult {
  record Created(UUID userId) implements RegistrationResult {}

  /** Every rule the submission violated; the rejection has been counted and no account was created. */
  record Rejected(List<RegistrationViolation> violations) implements RegistrationResult {
    public Rejected {
      violations = List.copyOf(violations);
    }
  }

  /** The source is locked out. */
  record Locked() implements RegistrationResult {}
}
