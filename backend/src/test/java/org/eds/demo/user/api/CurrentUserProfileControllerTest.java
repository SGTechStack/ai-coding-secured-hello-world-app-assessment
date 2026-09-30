package org.eds.demo.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.eds.demo.user.application.CurrentUserProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

@ExtendWith(MockitoExtension.class)
class CurrentUserProfileControllerTest {

  @Mock private CurrentUserProfileService currentUserProfileService;

  @InjectMocks private CurrentUserProfileController controller;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void throwsWhenNoAuthentication() {
    assertThatThrownBy(() -> controller.getCurrentUserProfile())
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }

  @Test
  void returnsRolesFromDatabase() {
    var userDetails = User.withUsername("alice").password("").authorities("ROLE_USER").build();
    var auth =
        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(auth);

    when(currentUserProfileService.getProfile("alice"))
        .thenReturn(
            new CurrentUserProfileService.UserProfile("alice", "alice", Set.of("USER", "ADMIN")));

    var response = controller.getCurrentUserProfile();

    assertThat(response.username()).isEqualTo("alice");
    assertThat(response.roles()).containsExactlyInAnyOrder("USER", "ADMIN");
  }
}
