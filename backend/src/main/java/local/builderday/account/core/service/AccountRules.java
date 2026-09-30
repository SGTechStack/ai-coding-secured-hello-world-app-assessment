package local.builderday.account.core.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import local.builderday.account.core.model.AccountRuleViolation;

/**
 * Username and email normalization and validation, and the password composition rules, shared by registration and
 * Password reset. The password rules are shared with the frontend (see
 * {@code test-fixtures/password-policy-cases.json}). Callers wanting the full password policy, including the
 * common-password list, use {@link PasswordPolicy}.
 */
public final class AccountRules {
  static final int USERNAME_MIN_LENGTH = 5;
  static final int USERNAME_MAX_LENGTH = 100;
  static final int EMAIL_MAX_LENGTH = 254;
  static final int PASSWORD_MIN_LENGTH = 12;
  /** BCrypt's 72-byte input limit; passwords are printable ASCII, so characters equal bytes. */
  static final int PASSWORD_MAX_LENGTH = 72;
  private static final int EMAIL_LOCAL_PART_MAX_LENGTH = 64;
  private static final int MIN_IDENTITY_LENGTH_FOR_EMAIL = 4;
  private static final Pattern USERNAME = Pattern.compile("^[a-z0-9][a-z0-9._-]*$");
  private static final Pattern EMAIL_LOCAL_PART = Pattern.compile(
      "^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*$");
  private static final Pattern EMAIL_DOMAIN = Pattern.compile(
      "^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$");

  private AccountRules() {}

  /** Trims and lowercases an identifier; null stays null. */
  public static String normalize(String value) {
    return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
  }

  public static List<AccountRuleViolation> usernameViolations(String username) {
    if (username == null || username.isEmpty()) return List.of(AccountRuleViolation.FIELD_REQUIRED);
    var codes = new ArrayList<AccountRuleViolation>();
    if (username.length() < USERNAME_MIN_LENGTH) codes.add(AccountRuleViolation.USERNAME_TOO_SHORT);
    if (username.length() > USERNAME_MAX_LENGTH) codes.add(AccountRuleViolation.USERNAME_TOO_LONG);
    if (!USERNAME.matcher(username).matches()) codes.add(AccountRuleViolation.USERNAME_INVALID_CHARACTER);
    return codes;
  }

  public static List<AccountRuleViolation> emailViolations(String email) {
    if (email == null || email.isEmpty()) return List.of(AccountRuleViolation.FIELD_REQUIRED);
    var codes = new ArrayList<AccountRuleViolation>();
    if (email.length() > EMAIL_MAX_LENGTH) codes.add(AccountRuleViolation.EMAIL_TOO_LONG);
    if (!isValidEmail(email)) codes.add(AccountRuleViolation.EMAIL_INVALID);
    return codes;
  }

  /**
   * The password is inspected exactly as submitted: it is never trimmed or normalized.
   *
   * @param username normalized (trimmed, lowercased) username, possibly null
   * @param email normalized email, possibly null
   */
  public static List<AccountRuleViolation> passwordViolations(String password, String username, String email) {
    var codes = new ArrayList<AccountRuleViolation>();
    if (password == null || password.isEmpty()) {
      codes.add(AccountRuleViolation.FIELD_REQUIRED);
      return codes;
    }
    int length = password.codePointCount(0, password.length());
    if (length < PASSWORD_MIN_LENGTH) codes.add(AccountRuleViolation.PASSWORD_TOO_SHORT);
    if (length > PASSWORD_MAX_LENGTH) codes.add(AccountRuleViolation.PASSWORD_TOO_LONG);
    if (password.chars().anyMatch(c -> c < 0x20 || c > 0x7E)) {
      codes.add(AccountRuleViolation.PASSWORD_INVALID_CHARACTER);
    }
    if (password.chars().noneMatch(c -> c >= 'A' && c <= 'Z')) {
      codes.add(AccountRuleViolation.PASSWORD_MISSING_UPPERCASE);
    }
    if (password.chars().noneMatch(c -> c >= 'a' && c <= 'z')) {
      codes.add(AccountRuleViolation.PASSWORD_MISSING_LOWERCASE);
    }
    if (password.chars().noneMatch(c -> c >= '0' && c <= '9')) codes.add(AccountRuleViolation.PASSWORD_MISSING_DIGIT);
    if (password.chars().noneMatch(AccountRules::isSpecial)) codes.add(AccountRuleViolation.PASSWORD_MISSING_SPECIAL);
    if (containsIdentity(password.toLowerCase(Locale.ROOT), username, email)) {
      codes.add(AccountRuleViolation.PASSWORD_CONTAINS_IDENTITY);
    }
    return codes;
  }

  private static boolean isValidEmail(String email) {
    int at = email.lastIndexOf('@');
    if (at <= 0 || at == email.length() - 1 || email.indexOf('@') != at) return false;
    String local = email.substring(0, at);
    String domain = email.substring(at + 1);
    return local.length() <= EMAIL_LOCAL_PART_MAX_LENGTH && EMAIL_LOCAL_PART.matcher(local).matches()
        && EMAIL_DOMAIN.matcher(domain).matches();
  }

  private static boolean isSpecial(int c) {
    return c >= 0x20 && c <= 0x7E && !Character.isLetterOrDigit(c);
  }

  private static boolean containsIdentity(String lowercasePassword, String username, String email) {
    if (username != null && username.length() >= USERNAME_MIN_LENGTH
        && lowercasePassword.contains(username.toLowerCase(Locale.ROOT))) {
      return true;
    }
    if (email == null) return false;
    int at = email.lastIndexOf('@');
    if (at < 0) return false;
    String localPart = email.substring(0, at).toLowerCase(Locale.ROOT);
    return localPart.length() >= MIN_IDENTITY_LENGTH_FOR_EMAIL && lowercasePassword.contains(localPart);
  }
}
