package com.sgtechstack.helloworldauthapp.auth;

import com.sgtechstack.helloworldauthapp.user.Role;

public record LoginResponse(String username, Role role) {
    public static LoginResponse of(String username, Role role) {
        return new LoginResponse(username, role);
    }
}
