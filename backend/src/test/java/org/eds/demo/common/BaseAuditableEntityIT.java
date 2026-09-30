package org.eds.demo.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.eds.demo.user.domain.AppUser;
import org.eds.demo.user.domain.AppUserDetails;
import org.eds.demo.user.domain.Role;
import org.eds.demo.user.domain.UserId;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class BaseAuditableEntityIT {

  @Autowired private AppUserRepository appUserRepository;

  @Test
  void createdAtIsSetOnPersist() {
    var user = AppUser.create("audit-user", EnumSet.of(Role.USER));

    var saved = appUserRepository.saveAndFlush(user);

    assertThat(saved.getCreatedAt()).isNotNull();
  }

  @Test
  void updatedAtIsSetOnPersist() {
    var user = AppUser.create("audit-user-2", EnumSet.of(Role.USER));

    var saved = appUserRepository.saveAndFlush(user);

    assertThat(saved.getUpdatedAt()).isNotNull();
  }

  @Test
  void updatedAtChangesOnModification() throws InterruptedException {
    var user = AppUser.create("audit-user-3", EnumSet.of(Role.USER));
    var saved = appUserRepository.saveAndFlush(user);
    var originalUpdatedAt = saved.getUpdatedAt();

    Thread.sleep(10);
    saved.updateEmail("new@example.com");
    var updated = appUserRepository.saveAndFlush(saved);

    assertThat(updated.getUpdatedAt()).isAfter(originalUpdatedAt);
  }

  @Test
  void createdAtDoesNotChangeOnModification() throws InterruptedException {
    var user = AppUser.create("audit-user-4", EnumSet.of(Role.USER));
    var saved = appUserRepository.saveAndFlush(user);
    var originalCreatedAt = saved.getCreatedAt();

    Thread.sleep(10);
    saved.updateEmail("another@example.com");
    var updated = appUserRepository.saveAndFlush(saved);

    assertThat(updated.getCreatedAt()).isEqualTo(originalCreatedAt);
  }

  @Test
  void displayNameIsSystemWhenNoAuthentication() {
    var user = AppUser.create("audit-user-5", EnumSet.of(Role.USER));

    var saved = appUserRepository.saveAndFlush(user);

    assertThat(saved.getCreatedByUserDisplayName()).isEqualTo("SYSTEM");
    assertThat(saved.getUpdatedByUserDisplayName()).isEqualTo("SYSTEM");
  }

  @Test
  @WithMockUser(username = "alice")
  void displayNameIsUsernameWhenAuthenticated() {
    var user = AppUser.create("alice", EnumSet.of(Role.USER));

    var saved = appUserRepository.saveAndFlush(user);

    assertThat(saved.getCreatedByUserDisplayName()).isEqualTo("alice");
    assertThat(saved.getUpdatedByUserDisplayName()).isEqualTo("alice");
  }

  @Test
  @WithMockUser(username = "bob")
  void modifiedByDisplayNameUpdatesOnChange() {
    var user = AppUser.create("bob", EnumSet.of(Role.USER));
    var saved = appUserRepository.saveAndFlush(user);

    assertThat(saved.getUpdatedByUserDisplayName()).isEqualTo("bob");
  }

  @Test
  void appUserDetailsSetsUserIdAndDisplayName() {
    UserId userId = new UserId(UUID.randomUUID());
    var details =
        new AppUserDetails(
            userId, "charlie", "Charlie D", "", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    var auth = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(auth);

    try {
      var user = AppUser.create("charlie", EnumSet.of(Role.USER));
      var saved = appUserRepository.saveAndFlush(user);

      assertThat(saved.getCreatedByUserId()).isEqualTo(userId);
      assertThat(saved.getUpdatedByUserId()).isEqualTo(userId);
      assertThat(saved.getCreatedByUserDisplayName()).isEqualTo("Charlie D");
      assertThat(saved.getUpdatedByUserDisplayName()).isEqualTo("Charlie D");
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void appUserDetailsUpdatesModifiedFieldsOnChange() {
    var user = AppUser.create("diana", EnumSet.of(Role.USER));
    var saved = appUserRepository.saveAndFlush(user);

    UserId userId = new UserId(UUID.randomUUID());
    var details =
        new AppUserDetails(
            userId, "manager", "Manager M", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    var auth = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(auth);

    try {
      saved.updateEmail("diana@example.com");
      var updated = appUserRepository.saveAndFlush(saved);

      assertThat(updated.getCreatedByUserDisplayName()).isEqualTo("SYSTEM");
      assertThat(updated.getUpdatedByUserDisplayName()).isEqualTo("Manager M");
      assertThat(updated.getCreatedByUserId()).isNull();
      assertThat(updated.getUpdatedByUserId()).isEqualTo(userId);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
