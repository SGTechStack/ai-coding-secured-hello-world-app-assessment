package local.builderday.account.core.controller;

import local.builderday.common.exception.ApiError;
import local.builderday.common.exception.ProblemDetails;
import local.builderday.account.core.controller.dto.ProfileResponse;
import local.builderday.account.core.service.UserProfileService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-read only (App Standard "Secure Self-Read User Endpoint"): the identity comes from the Session, never the
 * request.
 */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class ProfileController {
  private static final String ROLE_PREFIX = "ROLE_";

  private final UserProfileService userProfileService;

  ProfileController(UserProfileService userProfileService) {
    this.userProfileService = userProfileService;
  }

  /**
   * The role is the one this Session was granted, which is what the authorization matrix enforces for it. An account
   * gone since login (its Sessions are deleted with it) answers like no Session at all.
   */
  @GetMapping("/api/profile")
  public ResponseEntity<?> profile(@AuthenticationPrincipal UserDetails principal) {
    return userProfileService.findByUsername(principal.getUsername())
        .<ResponseEntity<?>>map(profile -> ResponseEntity.ok(new ProfileResponse(profile.id(), role(principal))))
        .orElseGet(() -> ProblemDetails.response(ApiError.AUTHENTICATION_REQUIRED));
  }

  /** Every account holds exactly one role. */
  private static String role(UserDetails principal) {
    return principal.getAuthorities().stream().map(GrantedAuthority::getAuthority)
        .filter(authority -> authority.startsWith(ROLE_PREFIX))
        .map(authority -> authority.substring(ROLE_PREFIX.length()))
        .findFirst().orElseThrow();
  }
}
