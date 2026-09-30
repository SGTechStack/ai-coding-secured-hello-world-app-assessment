package com.example.hello.admin;

import com.example.hello.common.AuditLogger;
import com.example.hello.user.PasswordPolicy;
import com.example.hello.user.Role;
import com.example.hello.user.User;
import com.example.hello.user.UserRepository;
import java.time.Clock;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Story 12: seeds the first ADMIN on startup when none exists. Idempotent. Not profile-gated on
 * purpose (a fresh production database needs it too), but it refuses to start without explicit
 * credentials and the password must pass the same policy as everyone else's.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(AdminBootstrap.class);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicy passwordPolicy;
  private final AdminBootstrapProperties props;
  private final AuditLogger audit;
  private final Clock clock;

  public AdminBootstrap(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      PasswordPolicy passwordPolicy,
      AdminBootstrapProperties props,
      AuditLogger audit,
      Clock clock) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.passwordPolicy = passwordPolicy;
    this.props = props;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (userRepository.existsByRole(Role.ADMIN)) {
      LOG.info("Admin bootstrap skipped: an ADMIN account already exists");
      return;
    }
    if (!props.isComplete()) {
      throw new IllegalStateException(
          "No ADMIN account exists and app.admin.username / app.admin.email / app.admin.password "
              + "are not all set. Provide APP_ADMIN_USERNAME, APP_ADMIN_EMAIL and APP_ADMIN_PASSWORD.");
    }
    passwordPolicy.validate(props.password(), props.username());

    User admin =
        new User(
            props.username().trim(),
            props.email().trim().toLowerCase(Locale.ROOT),
            passwordEncoder.encode(props.password()),
            Role.ADMIN,
            clock.instant());
    userRepository.save(admin);
    audit.event("ADMIN_BOOTSTRAPPED", "username", admin.getUsername());
    LOG.info("Seeded initial ADMIN account '{}'", admin.getUsername());
  }
}
