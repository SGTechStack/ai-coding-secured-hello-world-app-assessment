package com.example.helloauth.web;

import com.example.helloauth.domain.Account;
import com.example.helloauth.service.AdminAccountService;
import com.example.helloauth.web.dto.ApiPayloads.AccountSummary;
import com.example.helloauth.web.dto.ApiPayloads.UpdateRoleRequest;
import com.example.helloauth.web.dto.ApiPayloads.UpdateStatusRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account administration.
 *
 * <p>There is no {@code @PreAuthorize} anywhere in here on purpose. The whole {@code /api/admin/**}
 * path is gated in the filter chain, so authorization cannot be forgotten on a method added later —
 * whereas a per-method annotation is exactly the kind of thing that gets left off.
 *
 * <p>The actor's identity comes from {@link Authentication}, never from the request body. That is
 * what makes the self-action guards meaningful: a client cannot claim to be someone else in order to
 * delete an account it should not be able to touch.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminAccountService adminAccounts;
    private final Clock clock;

    public AdminUserController(AdminAccountService adminAccounts, Clock clock) {
        this.adminAccounts = adminAccounts;
        this.clock = clock;
    }

    @GetMapping
    public List<AccountSummary> list() {
        Instant now = clock.instant();
        return adminAccounts.listAccounts().stream()
                .map(account -> AccountSummary.from(account, now))
                .toList();
    }

    /** {@code PATCH} rather than {@code PUT}: this changes one field, not the whole account. */
    @PatchMapping("/{id}/status")
    public AccountSummary setStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request,
            Authentication authentication) {
        Account updated =
                adminAccounts.setEnabled(id, request.enabled(), authentication.getName());
        return AccountSummary.from(updated, clock.instant());
    }

    @PatchMapping("/{id}/role")
    public AccountSummary setRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest request,
            Authentication authentication) {
        Account updated = adminAccounts.setRole(id, request.role(), authentication.getName());
        return AccountSummary.from(updated, clock.instant());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
        adminAccounts.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
