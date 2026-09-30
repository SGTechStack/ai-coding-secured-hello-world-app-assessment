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
            "No ADMIN Account exists and app.admin.password is not configured. Set"
                + " app.admin.username/app.admin.password in AWS Secrets Manager (there is no"
                + " default password).");
      }
      log.warn("No ADMIN Account exists and app.admin.password is not set; skipping admin seed");
      return;
    }
    if (appUserRepository.existsByUsername(properties.username())) {
      // Promoting silently would hand ADMIN to an Account nobody chose to elevate; seeding over it
      // would violate the unique username. The operator must resolve it explicitly.
      throw new IllegalStateException(
          "No ADMIN Account exists, but app.admin.username '"
              + properties.username()
              + "' already belongs to an Account that is not an ADMIN. Configure a different"
              + " app.admin.username, or change that Account's Role to ADMIN.");
    }
    var admin = AppUser.create(properties.username(), EnumSet.of(Role.ADMIN));
    admin.updatePasswordHash(passwordEncoder.encode(properties.password()));
    appUserRepository.save(admin);
    log.info("Seeded initial admin Account: username={}", properties.username());
  }
}
