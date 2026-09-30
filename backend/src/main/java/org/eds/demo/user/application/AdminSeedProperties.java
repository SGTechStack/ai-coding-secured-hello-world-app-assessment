package org.eds.demo.user.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Initial admin credentials, bound under {@code app.admin}.
 *
 * <p>Cloud profiles read these from AWS Secrets Manager; {@code local} from a git-ignored local
 * property or the {@code APP_ADMIN_PASSWORD} environment variable. There is deliberately no default
 * password.
 *
 * @param username login name of the seeded admin
 * @param password plaintext initial password; null when unconfigured. Never log it.
 * @param passwordRequired whether startup fails when no ADMIN exists and no password is configured.
 *     True by default so cloud profiles are safe; {@code local} and {@code test} relax it.
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminSeedProperties(
    @DefaultValue("admin") String username,
    String password,
    @DefaultValue("true") boolean passwordRequired) {

  @Override
  public String toString() {
    return "AdminSeedProperties[username=" + username + "]";
  }
}
