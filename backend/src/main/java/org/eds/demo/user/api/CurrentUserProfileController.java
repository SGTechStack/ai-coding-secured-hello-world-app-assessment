package org.eds.demo.user.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eds.demo.user.application.CurrentUserProfileService;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class CurrentUserProfileController {

  private final CurrentUserProfileService currentUserProfileService;

  @GetMapping
  public UserProfileResponse getCurrentUserProfile() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof UserDetails currentUser)) {
      throw new AuthenticationCredentialsNotFoundException("Current user is not authenticated");
    }

    var profile = currentUserProfileService.getProfile(currentUser.getUsername());
    log.info("User profile retrieved: username={}, roles={}", profile.username(), profile.roles());
    return UserProfileResponse.builder()
        .username(profile.username())
        .displayName(profile.displayName())
        .roles(profile.roles())
        .build();
  }
}
