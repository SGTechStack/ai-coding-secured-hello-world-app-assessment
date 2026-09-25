package com.sgtechstack.helloworldauthapp.admin;

import com.sgtechstack.helloworldauthapp.auth.LockoutPolicy;
import com.sgtechstack.helloworldauthapp.auth.StepUpAuthenticator;
import com.sgtechstack.helloworldauthapp.auth.UserPrincipal;
import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

/**
 * Every endpoint under {@code /api/admin/**} requires an admin authority,
 * enforced in {@code SecurityConfig} from the YAML guard matrix — never
 * re-checked here, and never trusted from anything client-supplied. The
 * business rules (no self-targeting, no stranding the system without an
 * administrator, re-proved password before an irreversible delete) are enforced
 * in {@link AdminUserManagementService}.
 *
 * <p>{@code GET /api/admin/users} no longer returns email addresses. Reading one
 * is a separate call that names a single account and states why — see
 * {@link #readEmail}.
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

    /**
     * Reveals one account's email address.
     *
     * <p>The purpose is a required parameter rather than an optional one. An
     * optional justification is one that is never supplied, and the audit
     * record would then answer "somebody looked" without the part that makes
     * the record worth keeping.
     */
    @GetMapping("/{id}/email")
    public UserEmailResponse readEmail(
            @AuthenticationPrincipal UserPrincipal actingAdmin,
            @PathVariable UUID id,
            @RequestParam(required = false) String purpose
    ) {
        return managementService.readEmail(actingAdmin.getId(), id, purpose);
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

    /**
     * Clears an account's lockout state (see {@link LockoutPolicy}) ahead of
     * its automatic cooldown. Separate from {@link #setEnabled}: enabling a
     * disabled account does not touch {@code lockedUntil}, and this is the
     * only path that does.
     */
    @PostMapping("/{id}/unlock")
    public UserSummaryResponse unlock(
            @AuthenticationPrincipal UserPrincipal actingAdmin,
            @PathVariable UUID id
    ) {
        User updated = managementService.unlockAccount(actingAdmin.getId(), id);
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

    /**
     * Deletes an account.
     *
     * <p>The confirmation password arrives in a header rather than a body
     * because a {@code DELETE} with a body is poorly supported by
     * intermediaries and client libraries. The header is declared optional at
     * this layer and rejected in the service, so a missing header and a wrong
     * password produce the identical response — a caller cannot use the
     * difference to learn whether confirmation is even required.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(
            @AuthenticationPrincipal UserPrincipal actingAdmin,
            @PathVariable UUID id,
            @RequestHeader(name = StepUpAuthenticator.CONFIRM_PASSWORD_HEADER, required = false)
            String confirmationPassword
    ) {
        managementService.deleteUser(actingAdmin.getId(), id, confirmationPassword);
    }
}
