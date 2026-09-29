package sg.securedhello.session.shedding;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.security.session.shedding.*}: the free-space line's constant term (ADR-041). {@code N_max}, {@code k} and
 * {@code b} are planning values in {@link ShedEpisode}, not settings.
 *
 * @param floor the free space kept whatever the row count, 256 MB at the planning values
 */
@Validated
@ConfigurationProperties("app.security.session.shedding")
public record SheddingProperties(@NotNull DataSize floor) {

    @AssertTrue(message = "must not be negative")
    boolean isFloorNonNegative() {
        return floor == null || !floor.isNegative();
    }
}
