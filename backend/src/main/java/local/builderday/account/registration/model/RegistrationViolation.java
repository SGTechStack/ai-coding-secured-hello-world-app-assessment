package local.builderday.account.registration.model;

/**
 * One violated registration rule. {@code field} is null when the rule is not tied to a single field. {@code code} is a
 * stable, client-mapped wire code: one of the constants below, or an account rule's name. The frontend owns the text.
 */
public record RegistrationViolation(String field, String code) {
  public static final String REQUEST_TOO_LARGE = "REQUEST_TOO_LARGE";
  public static final String FIELD_NOT_ALLOWED = "FIELD_NOT_ALLOWED";
  public static final String USER_EXISTS = "USER_EXISTS";

  public static RegistrationViolation of(String code) { return new RegistrationViolation(null, code); }
}
