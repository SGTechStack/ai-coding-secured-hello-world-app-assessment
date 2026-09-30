package org.eds.demo.user.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.eds.demo.common.WebPaths;
import org.eds.demo.user.application.PasswordChangeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Change-password for the signed-in holder; reachable while a password change is required. */
@RestController
@RequiredArgsConstructor
public class ChangePasswordController {

  private final PasswordChangeService passwordChangeService;

  @PostMapping(WebPaths.CHANGE_PASSWORD)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(
      @Valid @RequestBody ChangePasswordRequest request,
      Authentication holder,
      HttpServletRequest httpRequest) {
    passwordChangeService.changePassword(
        holder.getName(),
        request.newPassword(),
        request.confirmPassword(),
        httpRequest.getSession().getId());
  }
}
