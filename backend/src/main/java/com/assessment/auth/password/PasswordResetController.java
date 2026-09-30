package com.assessment.auth.password;

import com.assessment.auth.security.AppSecurityProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service password reset (story 1.13).
 *
 * <p>Both endpoints are {@code permitAll} in the matrix and CSRF-protected like everything else,
 * and both are IP-rate-limited by {@code RateLimitFilter} — a recorded deviation from Std:124's
 * per-account rule, because an anonymous endpoint has no account to key on.
 */
@RestController
public class PasswordResetController {

  public record ResetRequest(@NotBlank @Email String email) {}

  public record ResetConfirmation(@NotBlank String token, @NotBlank String newPassword) {}

  private final PasswordResetService passwordResetService;
  private final Duration responseTimeFloor;

  public PasswordResetController(
      PasswordResetService passwordResetService, AppSecurityProperties securityProperties) {
    this.passwordResetService = passwordResetService;
    this.responseTimeFloor = securityProperties.responseTimeFloor();
  }

  /**
   * Always 202, whether or not the email is registered.
   *
   * <p>The response-time floor is applied here for the same reason it is applied to login: without
   * it, "we looked up a user, hashed a token and wrote a row" is measurably slower than "we found
   * nothing", and the identical body would be undone by the clock.
   */
  @PostMapping("${api.base-path}/auth/password-reset/request")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void request(@Valid @RequestBody ResetRequest request) {
    long start = System.nanoTime();
    try {
      passwordResetService.requestReset(request.email());
    } finally {
      floor(start);
    }
  }

  @PostMapping("${api.base-path}/auth/password-reset/confirm")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void confirm(@Valid @RequestBody ResetConfirmation confirmation) {
    passwordResetService.confirmReset(confirmation.token(), confirmation.newPassword());
  }

  /** A partial discharge of Std:247's identical-timing clause; it bounds, it does not equalise. */
  private void floor(long startNanos) {
    if (responseTimeFloor == null) {
      return;
    }
    long remaining = responseTimeFloor.toNanos() - (System.nanoTime() - startNanos);
    if (remaining <= 0) {
      return;
    }
    try {
      Thread.sleep(Duration.ofNanos(remaining));
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }
}
