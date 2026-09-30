package com.example.hello.passwordreset;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    path = "/api/auth/password-reset",
    consumes = MediaType.APPLICATION_JSON_VALUE,
    produces = MediaType.APPLICATION_JSON_VALUE)
public class PasswordResetController {

  public static final String REQUEST_ACCEPTED =
      "If that email address is registered, a password reset link has been sent.";
  public static final String RESET_COMPLETED =
      "Password updated. Please log in with your new password.";

  private final PasswordResetService passwordResetService;

  public PasswordResetController(PasswordResetService passwordResetService) {
    this.passwordResetService = passwordResetService;
  }

  @PostMapping("/request")
  public MessageResponse request(@Valid @RequestBody PasswordResetRequest request) {
    passwordResetService.requestReset(request.email());
    return new MessageResponse(REQUEST_ACCEPTED);
  }

  @PostMapping("/confirm")
  public MessageResponse confirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
    passwordResetService.confirmReset(request.token(), request.newPassword());
    return new MessageResponse(RESET_COMPLETED);
  }
}
