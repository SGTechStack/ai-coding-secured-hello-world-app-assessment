package com.eitri.auth;

import com.eitri.audit.AuditAccount;
import java.util.UUID;
import org.springframework.security.core.Authentication;

/** The account behind an authenticated request, for features outside the account module. */
public final class CurrentAccount {

    private CurrentAccount() {}

    public static UUID id(Authentication authentication) {
        return principal(authentication).accountId();
    }

    /** The id and username that name the account in audit lines. */
    public static AuditAccount auditAccount(Authentication authentication) {
        return principal(authentication).auditAccount();
    }

    private static AccountPrincipal principal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            return principal;
        }
        throw new IllegalStateException("request is not authenticated as an account");
    }
}
