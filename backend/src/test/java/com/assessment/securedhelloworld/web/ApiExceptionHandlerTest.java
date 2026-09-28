package com.assessment.securedhelloworld.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies {@link ApiExceptionHandler}'s catch-all: an unexpected
 * exception is logged at ERROR with the throwable attached, and the
 * HTTP response body never echoes {@link Exception#getMessage()}.
 *
 * <p>Uses a throwing controller defined only in this test class (never
 * packaged into the application) so the assertion exercises the real
 * {@code @RestControllerAdvice} without adding a test-only route to
 * production code.
 */
@WebMvcTest(controllers = ApiExceptionHandlerTest.ThrowingController.class)
class ApiExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(ApiExceptionHandler.class)).addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        ((Logger) LoggerFactory.getLogger(ApiExceptionHandler.class)).detachAppender(logAppender);
    }

    @Test
    void unexpectedExceptionIsLoggedAtErrorAndResponseStaysGeneric() throws Exception {
        String secretDetail = "no-such-table-xyz-internal-detail";

        mockMvc.perform(get("/test-only/boom").param("detail", secretDetail))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString(secretDetail))));

        boolean errorLogged = logAppender.list.stream()
                .anyMatch(event -> event.getLevel() == Level.ERROR
                        && event.getThrowableProxy() != null
                        && event.getFormattedMessage().contains("Unhandled exception"));
        assertThat(errorLogged).isTrue();
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/test-only/boom")
        public String boom(String detail) {
            throw new IllegalStateException(detail);
        }
    }
}
