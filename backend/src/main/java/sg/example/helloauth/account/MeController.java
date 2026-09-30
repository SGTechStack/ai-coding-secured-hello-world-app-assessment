package sg.example.helloauth.account;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sg.example.helloauth.api.ApiException;

/** Self-read: the caller's own Account, looked up only by the authenticated identity. */
@RestController
@RequestMapping("${app.api.base-path}")
class MeController {

    private final AccountService accounts;

    MeController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/me")
    MeResponse me(@AuthenticationPrincipal AccountPrincipal principal) {
        return accounts.findActiveById(principal.id())
                .map(account -> new MeResponse(account.getId(), account.getUsername(), account.getEmail(),
                        account.getRole()))
                .orElseThrow(ApiException::unauthenticated);
    }

    record MeResponse(UUID id, String username, String email, Role role) {
    }
}
