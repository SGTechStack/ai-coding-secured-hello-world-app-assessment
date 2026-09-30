package local.builderday.account.core.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** @param historyLength passwords kept in Password history, the current one included (Standalone User Standard: 3) */
@Validated
@ConfigurationProperties("app.security.password")
public record PasswordHistoryProperties(@Positive int historyLength) {}
