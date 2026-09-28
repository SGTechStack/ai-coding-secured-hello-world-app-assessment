package sg.securedhello.config;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The bootstrap administrator's credentials, from the environment only (ADR-047). Presence only here; the password
 * policy check belongs to the bootstrap. No default in any profile.
 *
 * @param username the seed administrator's username
 * @param password the seed administrator's initial, forced-change password
 */
@Validated
@ConfigurationProperties("app.admin")
public record AdminSeedProperties(@NotBlank String username, @NotBlank String password) {

    @Override
    public String toString() {
        return "AdminSeedProperties[username=" + username + ", password=<redacted>]";
    }
}
