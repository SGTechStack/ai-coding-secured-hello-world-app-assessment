package org.eds.demo.auth.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.eds.demo.support.MutableClock;
import org.eds.demo.support.MutableClockConfiguration;
import org.eds.demo.user.domain.AppUser;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Refusals that never reach the real credential check still hash a password, so response time does
 * not reveal whether an Account is disabled, delayed or holds an expired Temporary Password.
 */
@ActiveProfiles("local")
@AutoConfigureMockMvc
@Import(MutableClockConfiguration.class)
@SpringBootTest(
    properties = {
      "app.email.inbound.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:sign-in-timing-it;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
class SignInTimingEqualizationIT {

  private static final String PASSWORD = "test-only-timing-secret";
  private static final String WRONG_PASSWORD = "not-the-password";

  @Autowired private MockMvc mockMvc;
  @Autowired private MutableClock clock;
  @Autowired private AppUserRepository appUserRepository;
  @MockitoSpyBean private PasswordEncoder passwordEncoder;

  @Test
  void disabledAccountRefusalStillHashesAPassword() throws Exception {
    appUserRepository.save(
        AppUser.builder()
            .username("timing-disabled")
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .enabled(false)
            .build());
    clearInvocations(passwordEncoder);

    refuse("timing-disabled", "10.1.0.1");

    verify(passwordEncoder, atLeastOnce()).matches(anyString(), anyString());
  }

  @Test
  void activeBackoffRefusalStillHashesAPassword() throws Exception {
    appUserRepository.save(
        AppUser.builder()
            .username("timing-delayed")
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .lockedUntil(clock.instant().plus(Duration.ofMinutes(5)))
            .build());
    clearInvocations(passwordEncoder);

    refuse("timing-delayed", "10.1.0.2");

    verify(passwordEncoder, atLeastOnce()).matches(anyString(), anyString());
  }

  @Test
  void expiredTemporaryPasswordRefusalStillHashesAPassword() throws Exception {
    var account =
        AppUser.builder()
            .username("timing-expired")
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .build();
    account.issueTemporaryPassword(
        passwordEncoder.encode(PASSWORD), clock.instant().minus(Duration.ofMinutes(1)));
    appUserRepository.save(account);
    clearInvocations(passwordEncoder);

    refuse("timing-expired", "10.1.0.3");

    verify(passwordEncoder, atLeastOnce()).matches(anyString(), anyString());
  }

  private void refuse(String username, String ip) throws Exception {
    mockMvc
        .perform(
            post("/login")
                .with(
                    request -> {
                      request.setRemoteAddr(ip);
                      return request;
                    })
                .cookie(new Cookie("XSRF-TOKEN", "t"))
                .header("X-XSRF-TOKEN", "t")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"%s\",\"password\":\"%s\"}"
                        .formatted(username, WRONG_PASSWORD)))
        .andExpect(status().isUnauthorized());
  }
}
