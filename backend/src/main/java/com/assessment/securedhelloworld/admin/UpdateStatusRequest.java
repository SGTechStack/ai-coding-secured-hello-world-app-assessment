package com.assessment.securedhelloworld.admin;

import jakarta.validation.constraints.NotNull;

public class UpdateStatusRequest {

    @NotNull
    private Boolean enabled;

    public UpdateStatusRequest() {
        // Jackson
    }

    public UpdateStatusRequest(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
