package sg.example.helloauth.admin;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import sg.example.helloauth.account.AccountPrincipal;
import sg.example.helloauth.account.AccountService;
import sg.example.helloauth.account.Role;

/**
 * Account administration. The URL rules already admit only Admins to {@code /admin/**}; the
 * method-level check holds even if those rules were ever loosened.
 */
@RestController
@RequestMapping("${app.api.base-path}/admin/users")
@PreAuthorize("hasRole('ADMIN')")
class AdminAccountsController {

    static final int MAX_PAGE_SIZE = 100;

    private final AccountService accounts;
    private final AccountAdministration administration;
    private final Clock clock;

    AdminAccountsController(AccountService accounts, AccountAdministration administration, Clock clock) {
        this.accounts = accounts;
        this.administration = administration;
        this.clock = clock;
    }

    /** Every Account that isn't a Tombstone, oldest first. */
    @GetMapping
    PagedModel<AccountView> list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        Instant now = clock.instant();
        return new PagedModel<>(accounts.listActive(page, size).map(account -> AccountView.of(account, now)));
    }

    @PatchMapping("/{id}/status")
    AccountView setStatus(@PathVariable UUID id, @Valid @RequestBody StatusChange change,
            @AuthenticationPrincipal AccountPrincipal admin, HttpServletRequest request) {
        return administration.setEnabled(admin.id(), id, change.enabled(), request);
    }

    @PostMapping("/{id}/unlock")
    AccountView unlock(@PathVariable UUID id, @AuthenticationPrincipal AccountPrincipal admin,
            HttpServletRequest request) {
        return administration.unlock(admin.id(), id, request);
    }

    @PatchMapping("/{id}/role")
    AccountView changeRole(@PathVariable UUID id, @Valid @RequestBody RoleChange change,
            @AuthenticationPrincipal AccountPrincipal admin, HttpServletRequest request) {
        return administration.changeRole(admin.id(), id, change.role(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, @AuthenticationPrincipal AccountPrincipal admin, HttpServletRequest request) {
        administration.delete(admin.id(), id, request);
    }

    record StatusChange(@NotNull Boolean enabled) {
    }

    record RoleChange(@NotNull Role role) {
    }
}
