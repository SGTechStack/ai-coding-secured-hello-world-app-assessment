package com.assessment.auth.user;

import com.assessment.auth.audit.AuditAction;
import com.assessment.auth.audit.AuditEvent;
import com.assessment.auth.audit.AuditLogger;
import com.assessment.auth.audit.AuditReason;
import com.assessment.auth.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.event.Level;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-registration (story 1.5).
 *
 * <p>{@code permitAll} in the matrix <strong>and</strong> CSRF-protected, like every other endpoint
 * — so a caller must fetch a token from {@code GET /csrf} first. No endpoint is CSRF-exempt.
 *
 * <p>The password is <em>not</em> validated by Bean Validation beyond presence: the policy lives in
 * {@code PasswordPolicy} and is called from the service, so that all four write paths share one
 * implementation and adding a fifth without calling it is a visible omission (spec.md S5).
 */
@RestController
public class RegistrationController {

  /**
   * @param password only checked for presence here; the real rules are the shared PasswordPolicy's
   */
  public record RegistrationRequest(
      @NotBlank @Size(max = 100) String username,
      @NotBlank @Email @Size(max = 255) String email,
      @NotBlank String password) {}

  private final AccountCreationService accountCreationService;
  private final AuditLogger auditLogger;

  public RegistrationController(
      AccountCreationService accountCreationService, AuditLogger auditLogger) {
    this.accountCreationService = accountCreationService;
    this.auditLogger = auditLogger;
  }

  @PostMapping("${api.base-path}/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  public void register(
      @jakarta.validation.Valid @RequestBody RegistrationRequest request,
      HttpServletRequest httpRequest) {
    try {
      User created =
          accountCreationService.create(
              request.username(), request.email(), request.password(), Role.USER, false);
      auditLogger.emit(
          AuditEvent.of(
                  AuditAction.ACCOUNT_MANAGEMENT, AuditReason.REGISTRATION_SUCCESS, Level.INFO)
              .actor(created.getId())
              .target(created.getId())
              .sourceIp(httpRequest.getRemoteAddr())
              .build());
    } catch (ApiException ex) {
      // Emitted with no identifier of any kind: the audit trail records that a registration was
      // refused and from where, not which username or email was tried (spec.md S11).
      auditLogger.emit(
          AuditEvent.of(
                  AuditAction.ACCOUNT_MANAGEMENT, AuditReason.REGISTRATION_REJECTED, Level.WARN)
              .outcome("failure")
              .sourceIp(httpRequest.getRemoteAddr())
              .build());
      throw ex;
    }
  }
}
