package com.eitri.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class ApiExceptionHandlerTest {

    private final MockMvcTester mvc = MockMvcTester.create(MockMvcBuilders
            .standaloneSetup(new ThrowingController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build());

    @Test
    void unexpectedExceptionBecomesAGeneric500() {
        assertThat(mvc.get().uri("/boom"))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson().isStrictlyEqualTo("{\"message\": \"Internal Server Error\"}");
    }

    @Test
    void springMvcExceptionKeepsItsStatusWithAGenericMessage() {
        assertThat(mvc.post().uri("/echo").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().isStrictlyEqualTo("{\"message\": \"Bad Request\"}");
    }

    @Test
    void emptyBodyForAnotherControllerKeepsTheGenericMessage() {
        assertThat(mvc.post().uri("/echo").contentType(MediaType.APPLICATION_JSON).content(""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().isStrictlyEqualTo("{\"message\": \"Bad Request\"}");
    }

    @Test
    void securityExceptionIsLeftToSpringSecurity() {
        assertThat(mvc.get().uri("/denied")).hasFailed()
                .failure().hasRootCauseInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nonStandardStatusGetsAGenericMessage() {
        assertThat(ApiError.of(HttpStatusCode.valueOf(499))).isEqualTo(new ApiError("Error"));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("internal detail that must not leak");
        }

        @PostMapping("/echo")
        Map<String, String> echo(@RequestBody Map<String, String> body) {
            return body;
        }

        @GetMapping("/denied")
        void denied() {
            throw new AccessDeniedException("denied");
        }
    }
}
