package local.builderday.account.core.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Environment-configurable account hygiene policy (standalone standard defaults in {@code application.yml}).
 * Inactivity counts from the last successful login, or from account creation for an account that has never logged
 * in. The job schedule is {@code app.security.account-hygiene.cron} in {@code app.security.account-hygiene.zone},
 * read directly by the scheduler.
 *
 * @param disableAfter inactivity after which an account is disabled and its sessions ended
 * @param deleteAfter inactivity after which an account is soft-deleted (tombstoned); must exceed {@code disableAfter}
 */
@Validated
@ConfigurationProperties("app.security.account-hygiene")
public record AccountHygieneProperties(
    @NotNull @DurationMin(nanos = 1) Duration disableAfter, @NotNull Duration deleteAfter) {

  /** A cross-field rule, so it stays in code. Binding runs this before validation, hence the null checks. */
  public AccountHygieneProperties {
    if (disableAfter != null && deleteAfter != null && deleteAfter.compareTo(disableAfter) <= 0) {
      throw new IllegalArgumentException("delete-after must be longer than disable-after.");
    }
  }
}
