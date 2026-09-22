package com.sgtechstack.helloworldauthapp.auth;

public record LoginResponse(String username) {
    public static LoginResponse forUsername(String username) {
        return new LoginResponse(username);
    }
}
