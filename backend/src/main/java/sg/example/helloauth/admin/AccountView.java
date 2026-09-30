package sg.example.helloauth.admin;

import java.time.Instant;
import java.util.UUID;

import sg.example.helloauth.account.Account;
import sg.example.helloauth.account.Role;

/** An Account as an Admin sees it. It never carries the password hash. */
record AccountView(UUID id, String username, String email, Role role, boolean enabled, boolean locked,
        Instant createdAt) {

    static AccountView of(Account account, Instant now) {
        return new AccountView(account.getId(), account.getUsername(), account.getEmail(), account.getRole(),
                account.isEnabled(), account.isLockedAt(now), account.getCreatedAt());
    }
}
