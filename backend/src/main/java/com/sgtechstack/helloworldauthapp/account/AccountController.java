package com.sgtechstack.helloworldauthapp.account;

import com.sgtechstack.helloworldauthapp.auth.StepUpAuthenticator;
import com.sgtechstack.helloworldauthapp.auth.UserPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The data-subject rights this application implements: access (export) and
 * erasure, both for the caller's own account only.
 *
 * <p>There is no account id in any path here. The subject is always the
 * authenticated principal, taken from {@code @AuthenticationPrincipal} — never
 * from a path variable, a query parameter or a body. That is what makes these
 * endpoints structurally incapable of acting on somebody else's account: there
 * is no parameter to tamper with. An {@code /api/account/{id}} shape would have
 * needed an ownership check on every method, and a forgotten check would be an
 * authorization bypass; this shape has nothing to forget.
 *
 * <p>Guarded by the {@code ACCOUNT_SELF_MANAGE} authority in the YAML matrix,
 * which is mapped to {@code USER} and reaches {@code ADMIN} through the role
 * hierarchy — an admin is a data subject too.
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * The caller's own data, as a downloadable JSON attachment.
     *
     * <p>{@code Content-Disposition: attachment} rather than an inline body, so
     * a browser saves the file instead of rendering it. Both because a file is
     * what "give me my data" means to a person, and because a rendered page of
     * personal data sits in the browser's back-forward cache and the tab's
     * history; a download goes where the user chose to put it.
     */
    @GetMapping("/export")
    public ResponseEntity<AccountExportResponse> export(@AuthenticationPrincipal UserPrincipal principal) {
        AccountExportResponse export = accountService.export(principal.getId());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"my-account-data.json\"")
                // Personal data must not be held by a shared cache, and should
                // not linger in a private one either.
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(export);
    }

    /**
     * Erases the caller's own account. Irreversible, hence the re-proved
     * password — the same requirement, for the same reason, as an admin deleting
     * somebody else.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void erase(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(name = StepUpAuthenticator.CONFIRM_PASSWORD_HEADER, required = false)
            String confirmationPassword
    ) {
        accountService.erase(principal.getId(), confirmationPassword);
    }
}
