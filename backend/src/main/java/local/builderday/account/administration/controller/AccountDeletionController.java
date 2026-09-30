package local.builderday.account.administration.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import local.builderday.account.administration.service.AccountDeletionService;
import local.builderday.account.administration.service.AccountDeletionService.Result;
import local.builderday.account.core.service.UserProfileService;
import local.builderday.common.exception.ApiError;
import local.builderday.common.exception.ProblemDetails;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deletes one Account, for Admins only (the {@code /api/admin/**} grant; ADR 0014). {@code DELETE
 * /api/admin/users/{id}} with no body answers {@code 204}; the client refetches the User list itself. A non-UUID path
 * id is a framework 400 {@code INVALID_REQUEST}, never a 500.
 */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class AccountDeletionController {
  private final AccountDeletionService accountDeletionService;
  private final UserProfileService userProfileService;

  AccountDeletionController(AccountDeletionService accountDeletionService, UserProfileService userProfileService) {
    this.accountDeletionService = accountDeletionService;
    this.userProfileService = userProfileService;
  }

  @DeleteMapping("/api/admin/users/{id}")
  public ResponseEntity<?> delete(@PathVariable UUID id, @AuthenticationPrincipal UserDetails principal,
      HttpServletRequest request) {
    UUID callerId = userProfileService.findByUsername(principal.getUsername()).orElseThrow().id();
    return switch (accountDeletionService.delete(id, callerId, request)) {
      case Result.Deleted ignored -> ResponseEntity.noContent().build();
      case Result.NotFound ignored -> ProblemDetails.response(ApiError.RESOURCE_NOT_FOUND);
      case Result.SelfTarget ignored -> ProblemDetails.response(ApiError.ADMIN_CANNOT_TARGET_SELF);
      case Result.AlreadyDeleted ignored -> ProblemDetails.response(ApiError.ACCOUNT_DELETED);
    };
  }
}
