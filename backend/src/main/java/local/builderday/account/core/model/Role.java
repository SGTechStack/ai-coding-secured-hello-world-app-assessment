package local.builderday.account.core.model;

/**
 * Every role an Account may hold: the one list of roles in code. The constant names are the stored role names and the
 * values the API carries. {@code app.security.roles} must list exactly these, or the application does not start.
 * Adding a role is a code change.
 */
public enum Role {
  USER,
  ADMIN
}
