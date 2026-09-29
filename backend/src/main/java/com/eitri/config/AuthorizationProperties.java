package com.eitri.config;

import com.eitri.auth.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.validation.annotation.Validated;

/**
 * Typed, startup-validated role-to-request authorization rules. Roles bind to the {@link Role} enum, so a
 * matrix entry naming any other role fails startup.
 */
@ConfigurationProperties("app.security.authorization")
@Validated
public record AuthorizationProperties(@NotEmpty List<@NotNull @Valid RoleAccess> matrix) {

    public AuthorizationProperties {
        matrix = matrix == null ? List.of() : List.copyOf(matrix);
    }

    /** Groups equal request matchers so all configured roles are checked by the same security rule. */
    Map<RequestPermission, List<Role>> rolesByRequest() {
        Map<RequestPermission, LinkedHashSet<Role>> grouped = new LinkedHashMap<>();
        for (RoleAccess access : matrix) {
            for (RequestPermission permission : access.permissions()) {
                grouped.computeIfAbsent(permission, ignored -> new LinkedHashSet<>()).add(access.role());
            }
        }

        Map<RequestPermission, List<Role>> result = new LinkedHashMap<>();
        grouped.forEach((permission, roles) -> result.put(permission, List.copyOf(roles)));
        return result;
    }

    public record RoleAccess(@NotNull Role role, @NotEmpty List<@NotNull @Valid RequestPermission> permissions) {

        public RoleAccess {
            permissions = permissions == null ? List.of() : List.copyOf(permissions);
        }
    }

    public record RequestPermission(
            @NotNull HttpMethod method,
            @NotBlank @Pattern(regexp = "/.*", message = "must start with /") String path) {}
}
