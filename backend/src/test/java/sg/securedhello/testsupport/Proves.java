package sg.securedhello.testsupport;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;

/**
 * Binds a test to the rows of {@code docs/test-plan/test-plan.md} it proves (ADR-068).
 *
 * <p>Each value is a T-ID such as {@code "T-OBS-009"}. The traceability gate reads these values; the {@link Tag}
 * lets the proving tests be selected as a group ({@code -Dgroups=proves}).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Tag("proves")
public @interface Proves {

    /** The test-plan IDs this test proves. */
    String[] value();
}
