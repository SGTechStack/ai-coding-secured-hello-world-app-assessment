package org.eds.demo.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import org.eds.demo.support.MutableClock;
import org.eds.demo.support.MutableClockConfiguration;
import org.eds.demo.user.domain.AppUser;
import org.eds.demo.user.domain.Role;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Tests can control the time the application sees, for persisted audit timestamps. */
@ActiveProfiles("local")
@Import(MutableClockConfiguration.class)
@SpringBootTest(
    properties = {
      "app.email.inbound.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:controllable-clock-it;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
class ControllableClockIT {

  @Autowired private MutableClock clock;
  @Autowired private AppUserRepository appUserRepository;

  @Test
  void persistedAuditTimestampsFollowTheControlledTime() {
    var fixed = Instant.parse("2031-06-15T12:00:00Z");
    clock.set(fixed);

    var account = appUserRepository.save(AppUser.create("clock-holder", Set.of(Role.USER)));

    assertThat(account.getCreatedAt()).isEqualTo(fixed);
  }
}
