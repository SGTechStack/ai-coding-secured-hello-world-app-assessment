package org.eds.demo.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import org.eds.demo.support.MutableClock;
import org.eds.demo.support.MutableClockConfiguration;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The Temporary Password lifetime is tunable through {@code app.temporary-password.ttl}. */
@ActiveProfiles("local")
@AutoConfigureMockMvc
@Import(MutableClockConfiguration.class)
@SpringBootTest(
    properties = {
      "app.email.inbound.enabled=false",
      "app.temporary-password.ttl=2h",
      "spring.datasource.url=jdbc:h2:mem:admin-accounts-ttl-it;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
class AdminAccountsTtlIT {

  private static final String CSRF_TOKEN = "test-csrf-token";

  @Autowired private MockMvc mockMvc;
  @Autowired private AppUserRepository appUserRepository;
  @Autowired private MutableClock clock;

  @Test
  void temporaryPasswordExpiryFollowsTheConfiguredTtl() throws Exception {
    clock.set(Instant.parse("2030-01-01T00:00:00Z"));

    mockMvc
        .perform(
            post("/admin/api/users")
                .with(user("root").roles("ADMIN"))
                .cookie(new Cookie("XSRF-TOKEN", CSRF_TOKEN))
                .header("X-XSRF-TOKEN", CSRF_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"short-lived\",\"role\":\"USER\"}"))
        .andExpect(status().isCreated());

    assertThat(
            appUserRepository
                .findByUsername("short-lived")
                .orElseThrow()
                .getTempPasswordExpiresAt())
        .isEqualTo(clock.instant().plus(Duration.ofHours(2)));
  }
}
