package com.assessment.auth.user;

import com.assessment.auth.audit.AuditAction;
import com.assessment.auth.audit.AuditEvent;
import com.assessment.auth.audit.AuditLogger;
import com.assessment.auth.audit.AuditReason;
import com.assessment.auth.common.ApiErrorCode;
import com.assessment.auth.common.ApiException;
import com.assessment.auth.common.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.event.Level;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrator user management (stories 1.14–1.20).
 *
 * <p>Every route here is {@code hasRole('USER_MANAGER')} by matrix rows 8–15, with row 16 as a
 * backstop for anything under {@code /users/**} that a future route adds without a row of its own.
 *
 * <p>A plain USER receives 403 <strong>including when requesting their own user id</strong>:
 * Std:429 is explicit that user-management endpoints reject non-administrators even on their own
 * account. {@code GET /currentUser} is the self-read path, and this is not a second one.
 */
@RestController
public class AdminUserController {

  /** The list projection (matrix row 8). Never a password hash. */
  public record UserSummary(
      UUID id, String username, String email, String role, boolean enabled, Instant createdAt) {}

  /**
   * The detail projection (matrix row 10).
   *
   * <p>A <strong>different type</strong> from the self-read payload, deliberately: sharing one DTO
   * is how Std:414's two halves erode into one, and the erosion runs toward disclosure.
   */
  public record UserDetail(
      UUID id,
      String username,
      String email,
      String role,
      boolean enabled,
      Instant createdAt,
      Instant lockedUntil,
      int failedLoginAttempts,
      boolean requirePasswordChange,
      Instant lastLoginAt) {}

  public record CreateUserRequest(
      @NotBlank @Size(max = 100) String username,
      @NotBlank @Email @Size(max = 255) String email,
      @NotBlank String password,
      @NotBlank String role) {}

  public record StatusRequest(@NotNull Boolean enabled) {}

  public record RoleChangeRequest(@NotBlank String role) {}

  /** The plaintext token, returned exactly once and never logged (story 1.20). */
  public record IssuedTokenResponse(String token) {}

  private final UserRepository userRepository;
  private final AccountCreationService accountCreationService;
  private final AdminUserService adminUserService;
  private final AuditLogger auditLogger;

  public AdminUserController(
      UserRepository userRepository,
      AccountCreationService accountCreationService,
      AdminUserService adminUserService,
      AuditLogger auditLogger) {
    this.userRepository = userRepository;
    this.accountCreationService = accountCreationService;
    this.adminUserService = adminUserService;
    this.auditLogger = auditLogger;
  }

  /** Tombstoned users are absent because the row is gone, not because of a filter (story 1.19). */
  @GetMapping("${api.base-path}/users")
  public List<UserSummary> list() {
    return userRepository.findAllByOrderByCreatedAtAsc().stream()
        .map(
            user ->
                new UserSummary(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getRole(),
                    user.isEnabled(),
                    user.getCreatedAt()))
        .toList();
  }

  @GetMapping("${api.base-path}/users/{userId}")
  public UserDetail detail(@PathVariable UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "No such user."));
    return new UserDetail(
        user.getId(),
        user.getUsername(),
        user.getEmail(),
        user.getRole(),
        user.isEnabled(),
        user.getCreatedAt(),
        user.getLockedUntil(),
        user.getFailedLoginAttempts(),
        user.isRequirePasswordChange(),
        user.getLastLoginAt());
  }

  /** Story 1.15. Immediately active, and flagged for a forced password change. */
  @PostMapping("${api.base-path}/users")
  @ResponseStatus(HttpStatus.CREATED)
  public UserSummary create(
      @AuthenticationPrincipal AuthenticatedUser actor, @Valid @RequestBody CreateUserRequest request) {
    User created =
        accountCreationService.create(
            request.username(), request.email(), request.password(), request.role(), true);

    auditLogger.emit(
        AuditEvent.of(
                AuditAction.ACCOUNT_MANAGEMENT, AuditReason.ACCOUNT_CREATED_BY_ADMIN, Level.INFO)
            .actor(actor.id())
            .target(created.getId())
            .build());

    return new UserSummary(
        created.getId(),
        created.getUsername(),
        created.getEmail(),
        created.getRole(),
        created.isEnabled(),
        created.getCreatedAt());
  }

  @PatchMapping("${api.base-path}/users/{userId}/status")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void setStatus(
      @AuthenticationPrincipal AuthenticatedUser actor,
      @PathVariable UUID userId,
      @Valid @RequestBody StatusRequest request) {
    adminUserService.setEnabled(actor.id(), userId, request.enabled());
  }

  @PatchMapping("${api.base-path}/users/{userId}/role")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changeRole(
      @AuthenticationPrincipal AuthenticatedUser actor,
      @PathVariable UUID userId,
      @Valid @RequestBody RoleChangeRequest request) {
    adminUserService.changeRole(actor.id(), userId, request.role());
  }

  @PatchMapping("${api.base-path}/users/{userId}/unlock")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unlock(
      @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID userId) {
    adminUserService.unlock(actor.id(), userId);
  }

  @PatchMapping("${api.base-path}/users/{userId}/resetPassword")
  public IssuedTokenResponse resetPassword(
      @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID userId) {
    return new IssuedTokenResponse(adminUserService.issueResetToken(actor.id(), userId));
  }

  @DeleteMapping("${api.base-path}/users/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID userId) {
    adminUserService.delete(actor.id(), userId);
  }
}
