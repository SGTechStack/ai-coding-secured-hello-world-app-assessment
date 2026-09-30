package org.eds.demo.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.eds.demo.user.domain.AppUser;
import org.eds.demo.user.domain.Role;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** A newly created Account starts enabled, with no failed sign-ins and no forced change. */
@ActiveProfiles("local")
@SpringBootTest(
    properties = {
      "app.email.inbound.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:account-credential-state-it;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
class AccountCredentialStateIT {

  @Autowired private AppUserRepository appUserRepository;

  @Test
  void newAccountStartsEnabledWithCleanCredentialState() {
    var account = appUserRepository.save(AppUser.create("fresh-holder", Set.of(Role.USER)));

    assertThat(account.isEnabled()).isTrue();
    assertThat(account.getFailedLoginAttempts()).isZero();
    assertThat(account.isMustChangePassword()).isFalse();
    assertThat(account.getPasswordHash()).isNull();
    assertThat(account.getLockedUntil()).isNull();
    assertThat(account.getTempPasswordExpiresAt()).isNull();
  }
}
