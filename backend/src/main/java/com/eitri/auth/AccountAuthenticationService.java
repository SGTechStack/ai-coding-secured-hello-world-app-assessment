package com.eitri.auth;

import com.eitri.audit.AuditAccount;
import com.eitri.config.LoginSecurityProperties;
import java.time.Clock;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Serializes authentication and lockout state changes for a known account. */
@Service
class AccountAuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final AccountRepository accounts;
    private final AuthenticationAttemptContext authenticationAttempt;
    private final Clock clock;
    private final LoginSecurityProperties properties;

    AccountAuthenticationService(
            AuthenticationManager authenticationManager,
            AccountRepository accounts,
            AuthenticationAttemptContext authenticationAttempt,
            Clock clock,
            LoginSecurityProperties properties) {
        this.authenticationManager = authenticationManager;
        this.accounts = accounts;
        this.authenticationAttempt = authenticationAttempt;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional
    public Result authenticate(String username, String password) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, password));
            Account account = requireLoadedAccount(username);
            if (!account.isEnabled() || account.isLockedAt(clock.instant())) {
                return Result.failed(account.auditAccount(), false);
            }
            account.authenticationSucceeded();
            return Result.succeeded(authentication, account.auditAccount());
        } catch (AuthenticationServiceException exception) {
            throw exception;
        } catch (AuthenticationException exception) {
            if (authenticationAttempt.knownAccount() == null) {
                return Result.failed(null, false);
            }

            Account account = requireLoadedAccount(username);
            boolean newlyLocked = account.authenticationFailed(
                    clock.instant(),
                    properties.lockoutThreshold(),
                    properties.lockoutDuration(),
                    properties.lockoutWindow());
            return Result.failed(account.auditAccount(), newlyLocked);
        }
    }

    private Account requireLoadedAccount(String username) {
        return accounts.findByUsername(username)
                .orElseThrow(() -> new AuthenticationServiceException("Loaded account is no longer available"));
    }

    /** {@code knownAccount} is null when no account has the submitted username. */
    record Result(Authentication authentication, AuditAccount knownAccount, boolean newlyLocked) {

        static Result succeeded(Authentication authentication, AuditAccount account) {
            return new Result(authentication, account, false);
        }

        static Result failed(AuditAccount knownAccount, boolean newlyLocked) {
            return new Result(null, knownAccount, newlyLocked);
        }

        boolean succeeded() {
            return authentication != null;
        }
    }
}
