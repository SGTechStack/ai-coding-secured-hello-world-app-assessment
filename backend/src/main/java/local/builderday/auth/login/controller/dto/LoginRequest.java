package local.builderday.auth.login.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank @Size(min = 5, max = 128) @Pattern(regexp = "^[^/\\\\]*$") String username,
    @NotBlank @Size(max = 1024) String password) {}
