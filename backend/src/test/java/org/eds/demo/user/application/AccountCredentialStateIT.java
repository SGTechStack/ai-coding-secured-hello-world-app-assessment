package org.eds.demo.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.eds.demo.user.infrastructure.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** A newly provisioned Account starts enabled, with no failed sign-ins and no forced change. */
@ActiveProfiles("local")
@AutoConfigureMockMvc
@SpringBootTest(
    properties = {
      "app.email.inbound.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:account-credential-state-it;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
class AccountCredentialStateIT {

  @Autowired private MockMvc mockMvc;
  @Autowired private AppUserRepository appUserRepository;

  @Test
  void provisionedAccountStartsEnabledWithCleanCredentialState() throws Exception {
    mockMvc
        .perform(formLogin("/login").user("alice").password("password"))
        .andExpect(status().is3xxRedirection());

    var account = appUserRepository.findByUsername("alice").orElseThrow();

    assertThat(account.isEnabled()).isTrue();
    assertThat(account.getFailedLoginAttempts()).isZero();
    assertThat(account.isMustChangePassword()).isFalse();
    assertThat(account.getPasswordHash()).isNull();
    assertThat(account.getLockedUntil()).isNull();
    assertThat(account.getTempPasswordExpiresAt()).isNull();
  }
}
