package com.assessment.auth.bootstrap;

import com.assessment.auth.user.Role;
import com.assessment.auth.user.RoleRepository;
import com.assessment.auth.user.RoleSeedProperties;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the read-only {@code roles} table from {@code app.roles} (spec.md S2).
 *
 * <p>Runs <strong>after Liquibase and before the administrator bootstrap</strong>, which is why the
 * order is explicit: {@code AdminBootstrapRunner} assigns {@code USER_MANAGER} and would otherwise
 * race a table that has no rows yet.
 *
 * <p>Idempotent by name. Roles are seeded on <em>every</em> boot, which is exactly why the
 * bootstrap runner's existence check cannot be the recipe's {@code count == 0}.
 */
@Component
@Order(RoleSeedRunner.ORDER)
public class RoleSeedRunner implements ApplicationRunner {

  static final int ORDER = 100;

  private final RoleRepository roleRepository;
  private final RoleSeedProperties properties;

  public RoleSeedRunner(RoleRepository roleRepository, RoleSeedProperties properties) {
    this.roleRepository = roleRepository;
    this.properties = properties;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    for (String name : properties.roles()) {
      if (!roleRepository.existsByName(name)) {
        roleRepository.save(new Role(UUID.randomUUID(), name));
      }
    }
  }
}
