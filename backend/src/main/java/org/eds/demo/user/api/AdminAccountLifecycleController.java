package org.eds.demo.user.api;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.eds.demo.user.application.AccountLifecycleService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Enable, disable, Role change and delete of one Account, on the admin chain (ADMIN only). */
@RestController
@RequestMapping("/admin/api/users/{id}")
@RequiredArgsConstructor
public class AdminAccountLifecycleController {

  private final AccountLifecycleService accountLifecycleService;

  @PutMapping("/enabled")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void setEnabled(
      @PathVariable UUID id,
      @Valid @RequestBody SetAccountEnabledRequest request,
      Authentication actor) {
    accountLifecycleService.setEnabled(actor.getName(), id, request.enabled());
  }

  @PutMapping("/role")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changeRole(
      @PathVariable UUID id,
      @Valid @RequestBody ChangeAccountRoleRequest request,
      Authentication actor) {
    accountLifecycleService.changeRole(actor.getName(), id, request.role());
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id, Authentication actor) {
    accountLifecycleService.delete(actor.getName(), id);
  }
}
