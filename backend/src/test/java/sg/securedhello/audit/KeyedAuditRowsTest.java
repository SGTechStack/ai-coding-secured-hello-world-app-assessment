package sg.securedhello.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static sg.securedhello.audit.AuditRowDefinition.row;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import sg.securedhello.audit.AuditRowDefinition.Keying;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;

/**
 * Keyed rows through the running application (ADR-019): the CSRF refusal (row 13), which any unsafe request can
 * produce, is written once per source, reason and keying window with its count, and past the distinct-source cap the
 * window ends with one truncation row (row 46). Each test first closes the window the rest of the shared context left
 * open, and sends from its own documentation-range addresses.
 */
class KeyedAuditRowsTest extends CtxDefaultTest {

    private static final String CSRF_ROW = "CSRF validation failed.";
    private static final String TRUNCATION_ROW = "Keyed audit rows truncated.";
    private static final String UNSAFE_PATH = "/api/profile/password";
    private static final AuditRowDefinition USER_ROW = row("test-user-row", "Test user row.")
            .required(AuditKey.USER_ID).keyed(Keying.USER).build();

    @Autowired
    private AuditEmitter emitter;

    @Autowired
    private AuditProperties properties;

    @Autowired
    private Environment environment;

    @BeforeEach
    void startAFreshWindow() {
        emitter.closeKeyingWindow();
    }

    @AfterEach
    void unbind() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void csrfLessPost(String source) throws Exception {
        mockMvc.perform(post(UNSAFE_PATH).with(request -> {
            request.setRemoteAddr(source);
            return request;
        })).andExpect(problem(ErrorCode.CSRF_TOKEN_INVALID));
    }

    @Test
    @Proves("T-AUD-037")
    void aThousandCsrfRefusalsFromOneSourceAreOneRowCarryingTheirCount() throws Exception {
        Instant firstSeen = clock.instant();
        try (AuditCapture audit = AuditCapture.start()) {
            for (int i = 0; i < 1_000; i++) {
                csrfLessPost("203.0.113.37");
            }
            assertThat(audit.withMessage(CSRF_ROW)).as("nothing is written while the window is open").isEmpty();

            emitter.closeKeyingWindow();

            assertThat(audit.withMessage(CSRF_ROW)).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("event.reason", "CSRF_MISSING")
                    .containsEntry("event.count", 1_000)
                    .containsEntry("event.start", firstSeen.toString())
                    .containsKey("source.ip_hash"));
        }
    }

    @Test
    @Proves("T-AUD-034")
    void pastTheDistinctSourceCapTheWindowEndsWithOneTruncationRowAndNothingFurther() throws Exception {
        int cap = properties.truncation().distinctSources();
        try (AuditCapture audit = AuditCapture.start()) {
            for (int i = 1; i <= cap + 1; i++) {
                csrfLessPost("198.51.100." + i);
            }
            csrfLessPost("198.51.100." + (cap + 1));
            emitter.closeKeyingWindow();

            assertThat(audit.withMessage(CSRF_ROW)).hasSize(cap)
                    .extracting(row -> row.get("source.ip_hash")).doesNotHaveDuplicates();
            assertThat(audit.withMessage(TRUNCATION_ROW)).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("event.reason", "SOURCE_CAP_REACHED")
                    .containsEntry("event.severity", "high")
                    .containsEntry("source.distinct_count", cap)
                    .containsEntry("events.untracked_count", 2)
                    .containsEntry("labels.truncated_rows", List.of("CSRF_REJECTED"))
                    .doesNotContainKeys("source.ip_hash", "url.path"));
            assertThat(audit.rows()).hasSize(cap + 1);
        }
    }

    @Test
    @Proves("T-AUD-033")
    void bothTiersShareTheOneBoundKeyingWindow() throws Exception {
        Duration window = environment.getProperty("app.audit.keying.window", Duration.class);
        assertThat(properties.keying().window()).isEqualTo(window).isEqualTo(Duration.ofMinutes(15));
        try (AuditCapture audit = AuditCapture.start()) {
            csrfLessPost("203.0.113.38");
            bindRequest();
            emitter.write("TEST_USER_ROW", USER_ROW, fields -> fields.put(AuditKey.USER_ID, UUID.randomUUID()));

            clock.advance(window.minusMillis(1));
            emitter.closeKeyingWindowIfDue();
            assertThat(audit.rows()).isEmpty();

            clock.advance(Duration.ofMillis(1));
            emitter.closeKeyingWindowIfDue();
            assertThat(audit.rows()).extracting(row -> row.get("message"))
                    .containsExactly(CSRF_ROW, "Test user row.");
        }
    }

    private static void bindRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.setRemoteAddr("203.0.113.39");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
