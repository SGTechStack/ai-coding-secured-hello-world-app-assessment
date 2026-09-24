package com.example.demo_app.passwordreset;

import com.example.demo_app.audit.AuditEvent;
import com.example.demo_app.audit.AuditLog;
import com.example.demo_app.security.IpThrottle;
import com.example.demo_app.user.AcceptablePassword;
import com.example.demo_app.web.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Password reset endpoints: anonymous and CSRF-protected like every state-changing endpoint.
 *
 * <p>The request always answers an empty {@code 202}, whether or not the email has an account (a
 * malformed or missing email included), so it can't be used to find out who is registered. Every
 * request counts against the per-IP throttle, which limits inbox flooding and probing at scale.
 *
 * <p>The confirm checks the new password against the policy (Bean Validation) before the token
 * is looked at, so a weak password is a {@code 400 VALIDATION_FAILED} that leaves the link usable.
 */
@RestController
@RequestMapping("/api/v1/auth/password-reset")
class PasswordResetController {

  private final PasswordReset passwordReset;
  private final AuditLog auditLog;
  private final IpThrottle throttle;

  PasswordResetController(
      PasswordReset passwordReset,
      AuditLog auditLog,
      @Qualifier("passwordResetRequestThrottle") IpThrottle throttle) {
    this.passwordReset = passwordReset;
    this.auditLog = auditLog;
    this.throttle = throttle;
  }

  /** Body of the request: the email is deliberately not validated (see the class comment). */
  record ResetRequest(String email) {}

  /**
   * Body of the confirm. The token is only checked against the stored hashes.
   *
   * @param token the value from the link's {@code #token=} fragment
   * @param newPassword must meet the password policy
   */
  record ResetConfirmation(String token, @AcceptablePassword String newPassword) {

    /** Keeps the token and the plaintext password out of any accidental log or error message. */
    @Override
    public String toString() {
      return "ResetConfirmation[token=<redacted>, newPassword=<redacted>]";
    }
  }

  @PostMapping(value = "/request", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.ACCEPTED)
  void request(@RequestBody ResetRequest body, HttpServletRequest request) {
    try {
      throttle.acquire(request);
    } catch (TooManyRequestsException e) {
      auditLog.record(
          AuditEvent.REQUEST_THROTTLED,
          null,
          request,
          Map.of("endpoint", "password-reset-request"));
      throw e;
    }
    passwordReset.request(body.email(), request);
  }

  @PostMapping(value = "/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void confirm(@Valid @RequestBody ResetConfirmation body, HttpServletRequest request) {
    passwordReset.confirm(body.token(), body.newPassword(), request);
  }
}
