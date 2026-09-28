package sg.securedhello.config;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The two deployment origins. Required, with no default; also registered as required properties so an unresolved
 * placeholder fails refresh (R-CFG-019).
 *
 * @param spa the SPA origin, the only source of link origins (REJ-022)
 * @param api the API origin
 */
@Validated
@ConfigurationProperties("app.origins")
public record OriginsProperties(@NotBlank String spa, @NotBlank String api) {
}
