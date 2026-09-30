package org.eds.demo.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.eds.demo.user.domain.Role;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/** With no ADMIN Account, startup seeds one from configuration. */
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("local")
@SpringBootTest(
    properties = {
      "app.email.inbound.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:admin-seed-it;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "app.admin.username=seeded-admin",
      "app.admin.password=" + AdminSeedIT.CONFIGURED_PASSWORD
    })
class AdminSeedIT {

  static final String CONFIGURED_PASSWORD = "test-only-seed-secret";

  @Autowired private AppUserRepository appUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  void startupSeedsEnabledAdminWithHashedPasswordAndNoForcedChange(CapturedOutput output) {
    var admin = appUserRepository.findByUsername("seeded-admin").orElseThrow();

    assertThat(admin.getRoles()).containsExactly(Role.ADMIN);
    assertThat(admin.isEnabled()).isTrue();
    assertThat(admin.isMustChangePassword()).isFalse();
    assertThat(admin.getPasswordHash()).isNotEqualTo(CONFIGURED_PASSWORD);
    assertThat(passwordEncoder.matches(CONFIGURED_PASSWORD, admin.getPasswordHash())).isTrue();
    assertThat(output.getAll()).doesNotContain(CONFIGURED_PASSWORD);
  }
}
