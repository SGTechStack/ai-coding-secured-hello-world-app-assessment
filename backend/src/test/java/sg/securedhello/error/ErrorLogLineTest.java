package sg.securedhello.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.EcsJson;
import sg.securedhello.testsupport.Proves;

/**
 * The application's ERROR line for an exception that reached the {@code /error} dispatch (LOG §5; R-AUD-001): one
 * line, one {@code error} object with the throwable's type and stack and the code's classification from the closed
 * sets (R-AUD-039).
 */
class ErrorLogLineTest extends CtxDefaultTest {

    private static final String ESCAPED = "escaped-state-for-the-log-only-7c21";

    /** The closed sets, as the spec and the register record them. */
    private static final List<String> CATEGORIES = List.of("validation", "authentication", "authorization",
            "rate-limit", "conflict", "server");
    private static final List<String> FOLLOW_UP_ACTIONS = List.of("none", "retry-later", "re-authenticate",
            "contact-admin");

    @Test
    @Proves("T-AUD-002")
    void anExceptionAtTheErrorDispatchIsOneErrorLineWithTheClassifiedErrorObject() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> lines = new ListAppender<>();
        lines.start();
        root.addAppender(lines);
        try {
            mockMvc.perform(get("/error").with(request -> {
                request.setDispatcherType(DispatcherType.ERROR);
                request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException(ESCAPED));
                request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
                request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/probe/escape");
                return request;
            })).andExpect(problem(ErrorCode.INTERNAL_ERROR));
        } finally {
            root.detachAppender(lines);
        }

        List<ILoggingEvent> errors = lines.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
        assertThat(errors).singleElement().satisfies(event -> {
            String line = EcsJson.render(event).strip();
            Map<String, Object> row = EcsJson.flatten(line);
            assertThat(row)
                    .containsEntry("error.code", "500")
                    .containsEntry("error.category", "server")
                    .containsEntry("error.follow_up_action", "contact-admin")
                    .containsEntry("error.type", IllegalStateException.class.getName())
                    .containsKey("error.stack_trace");
            assertThat(line).as("one error object, not two").containsOnlyOnce("\"error\":");
        });
    }

    @Test
    void everyCodeHasACategoryAndAFollowUpActionFromTheClosedSets() {
        assertThat(Arrays.stream(ErrorCategory.values()).map(ErrorCategory::value).toList())
                .containsExactlyElementsOf(CATEGORIES);
        assertThat(Arrays.stream(FollowUpAction.values()).map(FollowUpAction::value).toList())
                .containsExactlyElementsOf(FOLLOW_UP_ACTIONS);
        Map<String, String> classified = Arrays.stream(ErrorCode.values()).collect(Collectors.toMap(Enum::name,
                code -> code.category().value() + " " + code.followUpAction().value()));
        assertThat(classified).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("AUTHENTICATION_FAILED", "authentication re-authenticate"),
                Map.entry("PASSWORD_CHANGE_REQUIRED", "authorization none"),
                Map.entry("CSRF_TOKEN_INVALID", "authorization none"),
                Map.entry("ACCESS_DENIED", "authorization contact-admin"),
                Map.entry("VALIDATION_FAILED", "validation none"),
                Map.entry("PASSWORD_REJECTED", "validation none"),
                Map.entry("RESET_TOKEN_INVALID", "validation none"),
                Map.entry("USER_EXISTS", "conflict none"),
                Map.entry("TOO_MANY_REQUESTS", "rate-limit retry-later"),
                Map.entry("MISSING_FACTOR", "authentication re-authenticate"),
                Map.entry("INVALID_FACTOR", "authentication re-authenticate"),
                Map.entry("FACTOR_ENROLMENT_REQUIRED", "authentication none"),
                Map.entry("FACTOR_ALREADY_ENROLLED", "conflict none"),
                Map.entry("FACTOR_DISABLED", "authentication contact-admin"),
                Map.entry("TWO_ADMIN_INVARIANT", "conflict none"),
                Map.entry("INTERNAL_ERROR", "server contact-admin")));
    }
}
