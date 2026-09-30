package org.eds.demo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.eds.demo.support.MutableClock;
import org.eds.demo.support.MutableClockConfiguration;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Tests can control the time the application sees, for persisted audit timestamps. */
@ActiveProfiles("local")
@AutoConfigureMockMvc
@Import(MutableClockConfiguration.class)
@SpringBootTest(
    properties = {
      "app.email.inbound.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:controllable-clock-it;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
class ControllableClockIT {

  @Autowired private MockMvc mockMvc;
  @Autowired private MutableClock clock;
  @Autowired private AppUserRepository appUserRepository;

  @Test
  void signInFollowsTheControlledTime() throws Exception {
    var fixed = Instant.parse("2031-06-15T12:00:00Z");
    clock.set(fixed);

    mockMvc
        .perform(formLogin("/login").user("alice").password("password"))
        .andExpect(status().is3xxRedirection());

    var account = appUserRepository.findByUsername("alice").orElseThrow();

    assertThat(account.getCreatedAt()).isEqualTo(fixed);
  }
}
