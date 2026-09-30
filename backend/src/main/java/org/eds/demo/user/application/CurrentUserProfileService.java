package org.eds.demo.user.application;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.eds.demo.user.domain.AppUser;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrentUserProfileService {

  private final AppUserRepository appUserRepository;

  public UserProfile getProfile(String username) {
    AppUser appUser =
        appUserRepository
            .findByUsername(username)
            .orElseThrow(
                () ->
                    new AuthenticationCredentialsNotFoundException("User not found: " + username));

    Set<String> roles =
        appUser.getRoles().stream().map(role -> role.name()).collect(Collectors.toSet());

    String displayName =
        appUser.getDisplayName() == null || appUser.getDisplayName().isBlank()
            ? username
            : appUser.getDisplayName();

    return UserProfile.builder().username(username).displayName(displayName).roles(roles).build();
  }

  // spotless:off
  @Builder
  public record UserProfile(
      String username,

      String displayName,

      Set<String> roles
  ) {}
  // spotless:on
}
