package com.eitri.auth;

import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final AccountRepository accounts;
    private final AuthenticationAttemptContext authenticationAttempt;

    AccountUserDetailsService(AccountRepository accounts, AuthenticationAttemptContext authenticationAttempt) {
        this.accounts = accounts;
        this.authenticationAttempt = authenticationAttempt;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = accounts.findByUsername(username.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
        authenticationAttempt.accountLoaded(account.auditAccount());
        return toPrincipal(account);
    }

    private AccountPrincipal toPrincipal(Account account) {
        return new AccountPrincipal(
                account.getId(), account.getUsername(), account.getPasswordHash(), account.getRole());
    }
}
