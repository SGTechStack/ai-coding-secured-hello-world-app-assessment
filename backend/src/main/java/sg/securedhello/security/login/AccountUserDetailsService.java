package sg.securedhello.security.login;

import java.time.Clock;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import sg.securedhello.user.SignedInUser;
import sg.securedhello.user.UserAccountRepository;

/**
 * Loads the account a sign-in names. An account with no password yet (never activated) is reported as not found, so
 * the provider spends the same dummy {@code matches()} on it as on an unknown username: a {@code null} hash would
 * otherwise be rejected without hashing, and answer faster (ADR-001; R-AUTH-004).
 */
final class AccountUserDetailsService implements UserDetailsService {

    private final UserAccountRepository accounts;
    private final Clock clock;

    AccountUserDetailsService(UserAccountRepository accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return accounts.findByUsername(username)
                .filter(account -> account.getPasswordHash() != null)
                .map(account -> SignedInUser.of(account, clock.instant()))
                .orElseThrow(() -> new UsernameNotFoundException("No account can sign in with that username"));
    }
}
