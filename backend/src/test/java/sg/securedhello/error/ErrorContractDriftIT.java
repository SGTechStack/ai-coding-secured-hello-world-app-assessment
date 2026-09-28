package sg.securedhello.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The error-contract drift gate (R-AUTH-003), run by Failsafe in {@code verify} on the same precedent as ADR-069:
 * each committed rendering must equal what {@link ErrorContract} generates from {@link ErrorCode} now.
 *
 * <p>After changing the enum, regenerate with {@code -Derror-contract.regenerate=true} and commit the result.
 */
class ErrorContractDriftIT {

    static Stream<Arguments> renderings() {
        return Stream.of(
                Arguments.of(ErrorContract.MARKDOWN, (Supplier<String>) ErrorContract::markdown),
                Arguments.of(ErrorContract.SCHEMA, (Supplier<String>) ErrorContract::jsonSchema));
    }

    @ParameterizedTest
    @MethodSource("renderings")
    void committedRenderingMatchesTheEnum(Path committed, Supplier<String> generator) throws IOException {
        String generated = generator.get();
        if (Boolean.getBoolean("error-contract.regenerate")) {
            Files.createDirectories(committed.getParent());
            Files.writeString(committed, generated, StandardCharsets.UTF_8);
        }
        assertThat(committed).as("%s exists; regenerate with -Derror-contract.regenerate=true", committed).exists();
        String onDisk = Files.readString(committed, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(onDisk)
                .as("%s is out of date with ErrorCode; regenerate with -Derror-contract.regenerate=true", committed)
                .isEqualTo(generated);
    }
}
