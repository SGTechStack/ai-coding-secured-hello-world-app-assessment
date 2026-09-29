package com.eitri.auth;

record CurrentUserResponse(String username, Role role) {

    static CurrentUserResponse from(AccountPrincipal principal) {
        return new CurrentUserResponse(principal.username(), principal.role());
    }
}
