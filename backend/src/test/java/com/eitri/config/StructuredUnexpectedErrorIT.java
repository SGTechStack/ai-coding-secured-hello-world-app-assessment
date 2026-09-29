package com.eitri.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.logging.SanitizedLogException;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class StructuredUnexpectedErrorIT {

    @Autowired
    private ApiExceptionHandler handler;

    @Test
    void unexpectedErrorIsNestedSanitizedAndKeepsGenericResponse(CapturedOutput output) throws Exception {
        var response = handler.handleUnexpected(new IllegalStateException(
                "password-canary jdbc:secret://db-host/private-db?credential=secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR));
        String line = output.getAll().lines()
                .filter(candidate -> candidate.contains("\"message\":\"Unhandled exception.\""))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No structured unexpected-error line found"));
        assertThat(JsonPath.<Integer>read(line, "$.error.code")).isEqualTo(500);
        assertThat(JsonPath.<String>read(line, "$.error.category")).isEqualTo("application");
        assertThat(JsonPath.<Boolean>read(line, "$.error.follow_up_action")).isTrue();
        assertThat(JsonPath.<String>read(line, "$.error.type")).isEqualTo(SanitizedLogException.class.getName());
        assertThat(JsonPath.<String>read(line, "$.error.message")).isEqualTo("Unexpected application failure");
        assertThat(JsonPath.<String>read(line, "$.error.stack_trace")).isNotBlank();
        assertThat(line)
                .doesNotContain("password-canary", "jdbc:secret", "db-host", "private-db", "credential=secret")
                .doesNotContain("error_code", "error_category", "error_follow_up_action");
    }
}
