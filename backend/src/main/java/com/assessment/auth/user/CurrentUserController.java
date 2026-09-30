package com.assessment.auth.user;

import com.assessment.auth.audit.AuditAction;
import com.assessment.auth.audit.AuditEvent;
import com.assessment.auth.audit.AuditLogger;
import com.assessment.auth.audit.AuditReason;
import com.assessment.auth.common.ApiErrorCode;
import com.assessment.auth.common.ApiException;
import com.assessment.auth.common.AuthenticatedUser;
import com.assessment.auth.password.PasswordHistoryService;
import com.assessment.auth.password.PasswordResetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.event.Level;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-read and self-service password change (stories 1.11, 1.12).
 *
 * <p>Both rows are {@code authenticated} rather than role-gated, because self-discovery cannot be
 * gated on a role vocabulary — a user must be able to learn their own role before anything can
 * depend on it.
 */
@RestController
public class CurrentUserController {

  /**
   * The self-read projection.
   *
   * <p><strong>A distinct type from the administrator detail payload, deliberately.</strong>
   * Sharing one DTO is how Std:414's two halves erode into one, and the erosion runs toward
   * disclosure. In particular this payload carries <em>no</em> lock state and <em>no</em>
   * failed-attempt count (Q23a:603) — widening the admin view must not widen this one.
   */
  public record CurrentUserResponse(
      String username,
      String email,
      String role,
      boolean requirePasswordChange,
      Instant createdAt,
      Instant lastLoginAt,
      Instant lastPasswordChangeAt) {}

  public record ChangePasswordRequest(
      @NotBlank String currentPassword, @NotBlank String newPassword) {}

  private final UserRepository userRepository;
  private final PasswordHistoryService passwordHistoryService;
  private final PasswordResetService passwordResetService;
  private final PasswordEncoder passwordEncoder;
  private final AuditLogger auditLogger;
  private final Clock clock;

  public CurrentUserController(
      UserRepository userRepository,
      PasswordHistoryService passwordHistoryService,
      PasswordResetService passwordResetService,
      PasswordEncoder passwordEncoder,
      AuditLogger auditLogger,
      Clock clock) {
    this.userRepository = userRepository;
    this.passwordHistoryService = passwordHistoryService;
    this.passwordResetService = passwordResetService;
    this.passwordEncoder = passwordEncoder;
    this.auditLogger = auditLogger;
    this.clock = clock;
  }

  /**
   * Looked up <strong>solely from the authenticated principal</strong>. There is no client-supplied
   * identifier on this path, so there is nothing to tamper with.
   */
  @GetMapping("${api.base-path}/currentUser")
  public ResponseEntity<CurrentUserResponse> currentUser(
      @AuthenticationPrincipal AuthenticatedUser principal) {

    User user =
        userRepository
            .findById(principal.id())
            .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Account not found."));

    auditLogger.emit(
        AuditEvent.of(AuditAction.SYSTEM, AuditReason.SELF_READ, Level.INFO)
            .actor(user.getId())
            .target(user.getId())
            .build());

    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            new CurrentUserResponse(
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.isRequirePasswordChange(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                passwordHistoryService.lastChangedAt(user.getId()).orElse(null)));
  }

  /**
   * Self-service change.
   *
   * <p>A wrong current password is <strong>400 with {@code CURRENT_PASSWORD_INVALID}</strong>,
   * never 401 or 403: the caller is authenticated and authorized, and answering 401 here would make
   * the SPA's interceptor log them out over a typo.
   *
   * <p>A successful change invalidates <em>every</em> session for the account including the
   * caller's own, which is why the SPA treats success as a logout and why the first boot is three
   * steps.
   */
  @PatchMapping("${api.base-path}/currentUser/changePassword")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody ChangePasswordRequest request) {

    User user =
        userRepository
            .findById(principal.id())
            .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Account not found."));

    if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new ApiException(
          ApiErrorCode.CURRENT_PASSWORD_INVALID, "The current password is incorrect.");
    }

    passwordResetService.applyNewPassword(user, request.newPassword(), clock.instant());
    // story 1.11: a change made this way must also void any reset the user had in flight.
    passwordResetService.invalidatePendingTokens(user.getId());

    auditLogger.emit(
        AuditEvent.of(AuditAction.CREDENTIAL_MANAGEMENT, AuditReason.PASSWORD_CHANGED, Level.INFO)
            .actor(user.getId())
            .target(user.getId())
            .build());
  }
}
