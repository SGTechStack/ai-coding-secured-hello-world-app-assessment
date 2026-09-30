package org.eds.demo.user.api;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.eds.demo.user.application.AccountAdministrationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Account management on the admin chain, which admits only {@code ROLE_ADMIN}. */
@RestController
@RequestMapping("/admin/api/users")
@RequiredArgsConstructor
public class AdminAccountController {

  private final AccountAdministrationService accountAdministrationService;

  @GetMapping
  public List<AccountSummaryResponse> listAccounts() {
    return accountAdministrationService.listAccounts().stream()
        .map(
            account ->
                AccountSummaryResponse.builder()
                    .username(account.username())
                    .role(account.role())
                    .enabled(account.enabled())
                    .createdAt(account.createdAt())
                    .build())
        .toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CreatedAccountResponse createAccount(
      @Valid @RequestBody CreateAccountRequest request, Authentication actor) {
    var created =
        accountAdministrationService.createAccount(
            actor.getName(), request.username(), request.role());
    return CreatedAccountResponse.builder()
        .username(created.username())
        .role(created.role())
        .temporaryPassword(created.temporaryPassword())
        .temporaryPasswordExpiresAt(created.temporaryPasswordExpiresAt())
        .build();
  }
}
