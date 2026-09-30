package local.builderday.account.adminbootstrap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Admin bootstrap credentials, bound from {@code ADMIN_USERNAME} / {@code ADMIN_PASSWORD} via
 * {@code app.admin.username} / {@code app.admin.password} (see {@code application.yml}). Read only when no Admin has
 * ever existed (ADR 0009). A blank value counts as unset, so the raw values are trimmed to {@code null} here.
 *
 * <p>Only the pair is meaningful. Whether exactly one being set is a misconfiguration depends on whether an Admin
 * already exists, so that decision lives in {@code AdminBootstrap}, not in this binding.
 *
 * @param username the configured Admin username, or {@code null} when unset or blank
 * @param password the configured Admin password, or {@code null} when unset or blank
 */
@ConfigurationProperties("app.admin")
public record AdminBootstrapProperties(String username, String password) {
  public AdminBootstrapProperties {
    username = blankToNull(username);
    password = blankToNull(password);
  }

  /** Whether at least one credential is present. */
  public boolean anySet() {
    return username != null || password != null;
  }

  /** Whether exactly one of the two is present, which is always a misconfiguration. */
  public boolean exactlyOneSet() {
    return (username != null) != (password != null);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
