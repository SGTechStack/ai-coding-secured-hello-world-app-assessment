package local.builderday.account.administration.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import local.builderday.account.administration.controller.dto.ListedAccountResponse;
import local.builderday.account.administration.service.UserListService;
import local.builderday.account.core.service.UserProfileService;
import local.builderday.common.audit.SecurityAudit;
import org.springframework.data.web.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The User list, for Admins only (the {@code /api/admin/**} grant). Every page read is a Security audit event: it
 * exposes other people's personal data (Structured Logging App Standard 3.4), so it records who read how much, never
 * what.
 */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class UserListController {
  private final UserListService userListService;
  private final UserProfileService userProfileService;

  UserListController(UserListService userListService, UserProfileService userProfileService) {
    this.userListService = userListService;
    this.userProfileService = userProfileService;
  }

  /** Out-of-range or non-numeric paging is 400 {@code INVALID_REQUEST} (ADR 0001). */
  @GetMapping("/api/admin/users")
  public PagedModel<ListedAccountResponse> users(@RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, @AuthenticationPrincipal UserDetails principal,
      HttpServletRequest request) {
    var accounts = userListService.page(page, size);
    SecurityAudit.record(request, new SecurityAudit.Event("user-list", "iam", "info", SecurityAudit.Outcome.SUCCESS,
        "success", userProfileService.findIdForAudit(principal.getUsername()),
        Map.of("user_list.page", page, "user_list.size", size, "user_list.count", accounts.getNumberOfElements())));
    return new PagedModel<>(accounts.map(ListedAccountResponse::from));
  }
}
