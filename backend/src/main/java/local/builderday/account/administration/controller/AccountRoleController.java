package local.builderday.account.administration.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import local.builderday.account.administration.controller.dto.ListedAccountResponse;
import local.builderday.account.administration.controller.dto.RoleChangeRequest;
import local.builderday.account.administration.service.AccountRoleService;
import local.builderday.account.administration.service.AccountRoleService.Result;
import local.builderday.account.core.service.UserProfileService;
import local.builderday.common.exception.ApiError;
import local.builderday.common.exception.ProblemDetails;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Changes one Account's role, for Admins only (the {@code /api/admin/**} grant; ADR 0014). {@code PUT
 * /api/admin/users/{id}/role} with body {@code {"role": "<ROLE>"}}: the role is a sub-resource of the Account, replaced
 * whole. The Account is returned in the User-list row shape so the client updates the row in place. A non-UUID path id
 * is a framework 400 {@code INVALID_REQUEST}, never a 500.
 */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class AccountRoleController {
  private final AccountRoleService accountRoleService;
  private final UserProfileService userProfileService;

  AccountRoleController(AccountRoleService accountRoleService, UserProfileService userProfileService) {
    this.accountRoleService = accountRoleService;
    this.userProfileService = userProfileService;
  }

  @PutMapping("/api/admin/users/{id}/role")
  public ResponseEntity<?> changeRole(@PathVariable UUID id, @Valid @RequestBody RoleChangeRequest body,
      @AuthenticationPrincipal UserDetails principal, HttpServletRequest request) {
    UUID callerId = userProfileService.findByUsername(principal.getUsername()).orElseThrow().id();
    return switch (accountRoleService.changeRole(id, body.requestedRole(), callerId, request)) {
      case Result.Updated updated -> ResponseEntity.ok(ListedAccountResponse.from(updated.account()));
      case Result.NotFound ignored -> ProblemDetails.response(ApiError.RESOURCE_NOT_FOUND);
      case Result.SelfTarget ignored -> ProblemDetails.response(ApiError.ADMIN_CANNOT_TARGET_SELF);
      case Result.Deleted ignored -> ProblemDetails.response(ApiError.ACCOUNT_DELETED);
    };
  }
}
