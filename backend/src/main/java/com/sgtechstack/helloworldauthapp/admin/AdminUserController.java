package com.sgtechstack.helloworldauthapp.admin;

import com.sgtechstack.helloworldauthapp.auth.UserPrincipal;
import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

/**
 * Every endpoint under {@code /api/admin/**} requires {@code ROLE_ADMIN},
 * enforced in {@code SecurityConfig} — never re-checked here, and never
 * trusted from anything client-supplied. The self-action guard (an admin
 * can't target their own account) is enforced in
 * {@link AdminUserManagementService}, since that's a business rule rather
 * than an authorization rule.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserRepository userRepository;
    private final AdminUserManagementService managementService;

    public AdminUserController(UserRepository userRepository, AdminUserManagementService managementService) {
        this.userRepository = userRepository;
        this.managementService = managementService;
    }

    @GetMapping
    public List<UserSummaryResponse> listUsers() {
        return userRepository.findAllByOrderByCreatedAtAsc()
                .stream()
                .map(UserSummaryResponse::from)
                .toList();
    }

    @PatchMapping("/{id}/enabled")
    public UserSummaryResponse setEnabled(
            @AuthenticationPrincipal UserPrincipal actingAdmin,
            @PathVariable UUID id,
            @Valid @RequestBody SetEnabledRequest request
    ) {
        User updated = managementService.setEnabled(actingAdmin.getId(), id, request.enabled());
        return UserSummaryResponse.from(updated);
    }

    @PatchMapping("/{id}/role")
    public UserSummaryResponse changeRole(
            @AuthenticationPrincipal UserPrincipal actingAdmin,
            @PathVariable UUID id,
            @Valid @RequestBody RoleChangeRequest request
    ) {
        User updated = managementService.changeRole(actingAdmin.getId(), id, request.role());
        return UserSummaryResponse.from(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@AuthenticationPrincipal UserPrincipal actingAdmin, @PathVariable UUID id) {
        managementService.deleteUser(actingAdmin.getId(), id);
    }
}
