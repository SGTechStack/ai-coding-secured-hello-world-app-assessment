package sg.example.helloauth.account;

import java.time.Clock;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/** Lets form login authenticate active Accounts, matching the username regardless of case. */
@Component
class AccountUserDetailsService implements UserDetailsService {

    private final AccountService accounts;
    private final Clock clock;

    AccountUserDetailsService(AccountService accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return accounts.findActiveByUsername(username)
                .map(account -> new AccountPrincipal(account, account.isLockedAt(clock.instant())))
                .orElseThrow(() -> new UsernameNotFoundException("No active account"));
    }
}
