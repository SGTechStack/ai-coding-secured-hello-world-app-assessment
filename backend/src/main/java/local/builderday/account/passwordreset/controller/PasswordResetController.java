package local.builderday.account.passwordreset.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import local.builderday.common.audit.SecurityAudit;
import local.builderday.common.exception.ApiError;
import local.builderday.common.exception.FieldErrorResponse;
import local.builderday.common.exception.ProblemDetails;
import local.builderday.account.passwordreset.controller.dto.PasswordResetConfirmRequest;
import local.builderday.account.passwordreset.controller.dto.PasswordResetRequest;
import local.builderday.account.passwordreset.controller.dto.PasswordResetResponse;
import local.builderday.account.passwordreset.service.ConfirmResult;
import local.builderday.account.passwordreset.service.PasswordResetService;
import local.builderday.account.passwordreset.service.RequestResult;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Visitor API endpoints of Password reset (ADR 0004, ADR 0001 amendment). CSRF-protected like every other mutation. */
@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {
  static final String REQUESTED_MESSAGE =
      "If an account is registered with that email, a link to reset its password has been sent.";

  private final PasswordResetService passwordResetService;

  PasswordResetController(PasswordResetService passwordResetService) {
    this.passwordResetService = passwordResetService;
  }

  /** The same 200 for every well-formed email, whether or not it belongs to an account. */
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> request(@RequestBody PasswordResetRequest body, HttpServletRequest request) {
    return switch (passwordResetService.requestReset(body.email(), SecurityAudit.RequestContext.capture(request))) {
      case RequestResult.Accepted() -> ResponseEntity.ok(new PasswordResetResponse(REQUESTED_MESSAGE));
      case RequestResult.RateLimited() -> ProblemDetails.response(ApiError.PASSWORD_RESET_UNAVAILABLE);
      case RequestResult.Invalid(var violations) -> rejected("email", violations.stream().map(Enum::name).toList());
    };
  }

  /** 204 once the password is changed; every Session of the account has ended, and none is created here. */
  @PostMapping(path = "/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> confirm(@RequestBody PasswordResetConfirmRequest body, HttpServletRequest request) {
    return switch (passwordResetService.confirm(body.token(), body.newPassword(),
        SecurityAudit.RequestContext.capture(request))) {
      case ConfirmResult.Reset() -> ResponseEntity.noContent().build();
      case ConfirmResult.InvalidToken() -> ProblemDetails.response(ApiError.PASSWORD_RESET_TOKEN_INVALID);
      case ConfirmResult.RateLimited() -> ProblemDetails.response(ApiError.PASSWORD_RESET_UNAVAILABLE);
      case ConfirmResult.Rejected(var codes) -> rejected("newPassword", codes);
    };
  }

  /** {@code PASSWORD_RESET_REJECTED} with one {@code errors} entry per distinct code, all on {@code field}. */
  private static ResponseEntity<?> rejected(String field, List<String> codes) {
    return ProblemDetails.rejected(ApiError.PASSWORD_RESET_REJECTED,
        codes.stream().map(code -> new FieldErrorResponse(field, code)).toList());
  }
}
