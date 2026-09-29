package com.eitri.admin;

import com.eitri.audit.AuditAccount;
import com.eitri.audit.AuditLogger;
import com.eitri.auth.AccountNotFoundException;
import com.eitri.auth.AccountService;
import com.eitri.auth.AccountView;
import com.eitri.auth.CurrentAccount;
import com.eitri.auth.NotAnAdminException;
import com.eitri.auth.Role;
import com.eitri.config.ApiError;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User administration. Access is ADMIN-only through the authorization matrix. Mutations check the body,
 * then that the target exists, then the self-action guard: an admin can't change their own status or
 * role or delete themselves, which also keeps at least one admin in the system.
 */
@RestController
@RequestMapping("${app.api.base-path}/admin/users")
class AdminUserController {

    private static final ApiError NOT_FOUND = new ApiError("User not found");
    private static final ApiError INVALID_REQUEST = ApiError.of(HttpStatus.BAD_REQUEST);

    private final AccountService accounts;
    private final AuditLogger auditLogger;

    AdminUserController(AccountService accounts, AuditLogger auditLogger) {
        this.accounts = accounts;
        this.auditLogger = auditLogger;
    }

    @GetMapping
    List<AccountView> list() {
        return accounts.list();
    }

    // Bound as a map so that only a JSON boolean is accepted (Jackson would coerce "true" or 1).
    @PatchMapping("/{id}/status")
    ResponseEntity<?> changeStatus(
            @PathVariable UUID id, @RequestBody Map<String, Object> body, Authentication authentication) {
        if (!(body.get("enabled") instanceof Boolean enabled)) {
            return ResponseEntity.badRequest().body(INVALID_REQUEST);
        }
        UUID actorId = CurrentAccount.id(authentication);
        Optional<ResponseEntity<?>> rejected =
                rejectMissingOrSelf(id, actorId, "You cannot change the status of your own account");
        if (rejected.isPresent()) {
            return rejected.get();
        }
        AccountView account = accounts.setEnabled(actorId, id, enabled);
        if (enabled) {
            auditLogger.userEnabled(CurrentAccount.auditAccount(authentication), audited(account));
        } else {
            auditLogger.userDisabled(CurrentAccount.auditAccount(authentication), audited(account));
        }
        return ResponseEntity.ok(account);
    }

    @PatchMapping("/{id}/role")
    ResponseEntity<?> changeRole(
            @PathVariable UUID id, @RequestBody Map<String, Object> body, Authentication authentication) {
        Optional<Role> role = Role.fromName(body.get("role"));
        if (role.isEmpty()) {
            return ResponseEntity.badRequest().body(INVALID_REQUEST);
        }
        UUID actorId = CurrentAccount.id(authentication);
        Optional<ResponseEntity<?>> rejected = rejectMissingOrSelf(id, actorId, "You cannot change your own role");
        if (rejected.isPresent()) {
            return rejected.get();
        }
        AccountService.RoleChange change = accounts.changeRole(actorId, id, role.get());
        auditLogger.userRoleChanged(
                CurrentAccount.auditAccount(authentication),
                audited(change.account()),
                change.from().name(),
                change.account().role().name());
        return ResponseEntity.ok(change.account());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<?> delete(@PathVariable UUID id, Authentication authentication) {
        UUID actorId = CurrentAccount.id(authentication);
        Optional<ResponseEntity<?>> rejected =
                rejectMissingOrSelf(id, actorId, "You cannot delete your own account");
        if (rejected.isPresent()) {
            return rejected.get();
        }
        AccountView deleted = accounts.delete(actorId, id);
        auditLogger.userDeleted(CurrentAccount.auditAccount(authentication), audited(deleted));
        return ResponseEntity.noContent().build();
    }

    private static AuditAccount audited(AccountView account) {
        return new AuditAccount(account.id(), account.username());
    }

    @ExceptionHandler(AccountNotFoundException.class)
    ResponseEntity<ApiError> targetVanished() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(NOT_FOUND);
    }

    @ExceptionHandler(NotAnAdminException.class)
    ResponseEntity<ApiError> actorNoLongerAdmin() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of(HttpStatus.FORBIDDEN));
    }

    private Optional<ResponseEntity<?>> rejectMissingOrSelf(UUID targetId, UUID actorId, String selfMessage) {
        if (accounts.find(targetId).isEmpty()) {
            return Optional.of(ResponseEntity.status(HttpStatus.NOT_FOUND).body(NOT_FOUND));
        }
        if (targetId.equals(actorId)) {
            return Optional.of(ResponseEntity.badRequest().body(new ApiError(selfMessage)));
        }
        return Optional.empty();
    }
}
