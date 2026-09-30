package org.eds.demo.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class AppUserTest {

  @Test
  void constructorSetsUsernameAndRoles() {
    var user = AppUser.create("alice", EnumSet.of(Role.USER));

    assertThat(user.getUsername()).isEqualTo("alice");
    assertThat(user.getRoles()).containsExactly(Role.USER);
    assertThat(user.getEmail()).isNull();
  }

  @Test
  void addRoleExpandsRoleSet() {
    var user = AppUser.create("bob", EnumSet.of(Role.USER));

    user.addRole(Role.USER_MANAGER);

    assertThat(user.getRoles()).containsExactlyInAnyOrder(Role.USER, Role.USER_MANAGER);
  }

  @Test
  void updateEmailSetsEmail() {
    var user = AppUser.create("dave", EnumSet.of(Role.USER));

    user.updateEmail("dave@example.com");

    assertThat(user.getEmail()).isEqualTo("dave@example.com");
  }

  @Test
  void updateEmailNormalizesEmptyStringToNull() {
    var user = AppUser.create("dave", EnumSet.of(Role.USER));

    user.updateEmail("   ");
    assertThat(user.getEmail()).isNull();

    user.updateEmail("");
    assertThat(user.getEmail()).isNull();
  }

  @Test
  void getRolesReturnsUnmodifiableCopy() {
    var user = AppUser.create("eve", EnumSet.of(Role.USER));

    var roles = user.getRoles();

    assertThat(roles).isUnmodifiable();
  }

  @Test
  void addRoleDoesNotDuplicateExistingRole() {
    var user = AppUser.create("frank", EnumSet.of(Role.USER));

    user.addRole(Role.USER);

    assertThat(user.getRoles()).containsExactly(Role.USER);
  }
}
