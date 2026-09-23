package com.assessment.securedhelloworld.admin;

import jakarta.validation.constraints.NotNull;

public class UpdateRoleRequest {

    @NotNull
    private String role;

    public UpdateRoleRequest() {
        // Jackson
    }

    public UpdateRoleRequest(String role) {
        this.role = role;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
