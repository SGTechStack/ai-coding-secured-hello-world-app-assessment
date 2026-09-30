package local.builderday.account.core.model;

/**
 * A violated account rule (identifier format or password policy). The names are the stable wire codes that
 * registration and Password reset report, so they must not be renamed.
 */
public enum AccountRuleViolation {
  FIELD_REQUIRED,
  USERNAME_TOO_SHORT,
  USERNAME_TOO_LONG,
  USERNAME_INVALID_CHARACTER,
  EMAIL_INVALID,
  EMAIL_TOO_LONG,
  PASSWORD_TOO_SHORT,
  PASSWORD_TOO_LONG,
  PASSWORD_INVALID_CHARACTER,
  PASSWORD_MISSING_UPPERCASE,
  PASSWORD_MISSING_LOWERCASE,
  PASSWORD_MISSING_DIGIT,
  PASSWORD_MISSING_SPECIAL,
  PASSWORD_CONTAINS_IDENTITY,
  PASSWORD_TOO_COMMON
}
