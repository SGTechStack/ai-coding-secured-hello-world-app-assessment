package local.builderday.account.administration.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import local.builderday.account.administration.controller.dto.ListedAccountResponse;
import local.builderday.account.administration.controller.dto.StatusToggleRequest;
import local.builderday.account.administration.service.AccountStatusService;
import local.builderday.account.administration.service.AccountStatusService.Result;
import local.builderday.account.core.service.UserProfileService;
import local.builderday.common.exception.ApiError;
import local.builderday.common.exception.ProblemDetails;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sets one Account's {@code enabled} state, for Admins only (the {@code /api/admin/**} grant). {@code PATCH
 * /api/admin/users/{id}} with body {@code {"enabled": <boolean>}} (Story 9), a partial update of one Account in the
 * collection the User list owns. The updated Account is returned in the User-list row shape so the client updates the
 * row in place. A non-UUID path id is a framework 400 {@code INVALID_REQUEST}, never a 500.
 */
@RestController
public class AccountStatusController {
  private final AccountStatusService accountStatusService;
  private final UserProfileService userProfileService;

  AccountStatusController(AccountStatusService accountStatusService, UserProfileService userProfileService) {
    this.accountStatusService = accountStatusService;
    this.userProfileService = userProfileService;
  }

  @PatchMapping("/api/admin/users/{id}")
  public ResponseEntity<?> setStatus(@PathVariable UUID id, @Valid @RequestBody StatusToggleRequest body,
      @AuthenticationPrincipal UserDetails principal, HttpServletRequest request) {
    UUID callerId = userProfileService.findByUsername(principal.getUsername()).orElseThrow().id();
    Result result = accountStatusService.setEnabled(id, body.enabled(), callerId, request);
    return switch (result) {
      case Result.Toggled toggled -> ResponseEntity.ok(ListedAccountResponse.from(toggled.account()));
      case Result.NotFound ignored -> response(ApiError.RESOURCE_NOT_FOUND);
      case Result.SelfTarget ignored -> response(ApiError.ADMIN_CANNOT_TARGET_SELF);
      case Result.Deleted ignored -> response(ApiError.ACCOUNT_DELETED);
    };
  }

  private static ResponseEntity<ProblemDetail> response(ApiError error) {
    return ProblemDetails.response(error);
  }
}
