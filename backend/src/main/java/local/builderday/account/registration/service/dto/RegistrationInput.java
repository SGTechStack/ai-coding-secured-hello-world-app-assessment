package local.builderday.account.registration.service.dto;

import java.util.List;
import local.builderday.account.registration.model.RegistrationViolation;

/**
 * Raw registration submission as received. Values are unnormalized and may be null; the password is never altered.
 *
 * @param structuralViolations body-level problems found while reading the request (e.g. unknown fields)
 * @param bodyReadable false when the body could not be read at all (too large or malformed); field rules are then
 *     not evaluated and only the structural violations are reported
 */
public record RegistrationInput(String username, String email, String password,
    List<RegistrationViolation> structuralViolations, boolean bodyReadable) {

  public RegistrationInput {
    structuralViolations = List.copyOf(structuralViolations);
  }

  public static RegistrationInput unreadable(List<RegistrationViolation> violations) {
    return new RegistrationInput(null, null, null, violations, false);
  }
}
