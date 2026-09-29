package com.example.hello.auth;

import com.example.hello.user.UserAccount;
import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

public record AuthPrincipal(UUID id, String username, long securityVersion) implements Principal, Serializable {
    @Override public String getName() { return username; }
    public static AuthPrincipal from(UserAccount user) {
        return new AuthPrincipal(user.getId(), user.getUsername(), user.getSecurityVersion());
    }
}
