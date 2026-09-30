package org.eds.demo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.eds.demo.user.domain.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalMockLoginControllerTest {

  @Mock private LocalUsersProperties localUsersProperties;

  @InjectMocks private LocalMockLoginController controller;

  @Test
  void loginPage_forwardsToLocalLoginHtml() {
    assertThat(controller.loginPage()).isEqualTo("forward:/local-mock-login.html");
  }

  @Test
  void loginOptions_returnsUsernamesAndRoles() {
    when(localUsersProperties.users())
        .thenReturn(
            List.of(
                LocalUsersProperties.UserEntry.of("alice", Set.of(Role.USER)),
                LocalUsersProperties.UserEntry.of("bob", Set.of(Role.USER_MANAGER))));

    var response = controller.loginOptions();

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody())
        .extracting(LocalMockLoginController.LoginOption::username)
        .containsExactly("alice", "bob");
    assertThat(response.getBody())
        .extracting(LocalMockLoginController.LoginOption::roles)
        .containsExactly(Set.of(Role.USER), Set.of(Role.USER_MANAGER));
  }

  @Test
  void loginOptions_emptyUsers_returnsEmptyList() {
    when(localUsersProperties.users()).thenReturn(List.of());

    var response = controller.loginOptions();

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isEmpty();
  }
}
