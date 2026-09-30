package org.eds.demo.user.application;

import java.util.EnumSet;
import lombok.extern.slf4j.Slf4j;
import org.eds.demo.user.domain.AppUser;
import org.eds.demo.user.domain.Role;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seeds the initial ADMIN Account on startup when none exists. */
@Slf4j
@Component
class AdminSeeder implements ApplicationRunner {

  /** Property names quoted in startup failures and logs so operators know what to set. */
  private static final String USERNAME_PROPERTY = "app.admin.username";

  private static final String PASSWORD_PROPERTY = "app.admin.password";

  private static final String EVENT_KEY = "event";
  private static final String USERNAME_KEY = "username";

  /** Audit event names, stable so log queries can filter on them. */
  private static final String EVENT_ADMIN_SEED_SKIPPED = "admin_seed_skipped";

  private static final String EVENT_ADMIN_SEEDED = "admin_seeded";

  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final AdminSeedProperties properties;

  AdminSeeder(
      AppUserRepository appUserRepository,
      PasswordEncoder passwordEncoder,
      AdminSeedProperties properties) {
    this.appUserRepository = appUserRepository;
    this.passwordEncoder = passwordEncoder;
    this.properties = properties;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (appUserRepository.existsByUserRolesRole(Role.ADMIN)) {
      return;
    }
    if (properties.password() == null || properties.password().isBlank()) {
      if (properties.passwordRequired()) {
        throw new IllegalStateException(
            "No ADMIN Account exists and "
                + PASSWORD_PROPERTY
                + " is not configured. Set "
                + USERNAME_PROPERTY
                + "/"
                + PASSWORD_PROPERTY
                + " in AWS Secrets Manager (there is no default password).");
      }
      log.atWarn()
          .addKeyValue(EVENT_KEY, EVENT_ADMIN_SEED_SKIPPED)
          .log("No ADMIN Account exists and {} is not set; skipping admin seed", PASSWORD_PROPERTY);
      return;
    }
    if (appUserRepository.existsByUsername(properties.username())) {
      // Promoting silently would hand ADMIN to an Account nobody chose to elevate; seeding over it
      // would violate the unique username. The operator must resolve it explicitly.
      throw new IllegalStateException(
          "No ADMIN Account exists, but "
              + USERNAME_PROPERTY
              + " '"
              + properties.username()
              + "' already belongs to an Account that is not an ADMIN. Configure a different "
              + USERNAME_PROPERTY
              + ", or change that Account's Role to ADMIN.");
    }
    var admin = AppUser.create(properties.username(), EnumSet.of(Role.ADMIN));
    admin.updatePasswordHash(passwordEncoder.encode(properties.password()));
    appUserRepository.save(admin);
    log.atInfo()
        .addKeyValue(EVENT_KEY, EVENT_ADMIN_SEEDED)
        .addKeyValue(USERNAME_KEY, properties.username())
        .log("Seeded initial admin Account: username={}", properties.username());
  }
}
