package sg.securedhello.security.csrf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SessionRows;

import tools.jackson.databind.json.JsonMapper;

/**
 * The session-bound, header-only CSRF token (ADR-036) and its bootstrap route (ADR-040).
 *
 * <p>No route that accepts an unsafe method exists yet, so "the request proceeds" means it passes {@code CsrfFilter}
 * and reaches authorization, which answers an anonymous caller with 401 {@code AUTHENTICATION_FAILED} instead of the
 * 403 {@code CSRF_TOKEN_INVALID} a CSRF refusal gets.
 */
class CsrfTokenTest extends CtxDefaultTest {

    private static final String UNSAFE_PATH = "/api/profile/password";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private JdbcTemplate jdbc;

    private SessionRows sessions;

    @BeforeEach
    void setUp() {
        sessions = new SessionRows(jdbc);
    }

    private CsrfSession bootstrap() throws Exception {
        return CsrfSession.bootstrap(mockMvc);
    }

    @Test
    @Proves("T-CSRF-002")
    void theTokenEndpointReturnsTheHeaderNameAndTokenAndIsNotCacheable() throws Exception {
        mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                // Left out on purpose: no discovery path for the parameter the server refuses (ADR-036).
                .andExpect(jsonPath("$.parameterName").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Expires", "0"));
    }

    @Test
    void theTokenIsMaskedDifferentlyOnEveryCallButStaysBoundToTheSession() throws Exception {
        CsrfSession first = bootstrap();
        MvcResult again = mockMvc.perform(get("/api/csrf").cookie(first.cookie())).andReturn();
        String second = JSON.readTree(again.getResponse().getContentAsString()).get("token").asString();

        assertThat(second).isNotEqualTo(first.token());
        mockMvc.perform(post(UNSAFE_PATH).cookie(first.cookie()).header("X-CSRF-TOKEN", second))
                .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
    }

    @Test
    @Proves("T-CSRF-009")
    void theTokenIsReadFromTheHeaderOnly() throws Exception {
        CsrfSession session = bootstrap();

        mockMvc.perform(post(UNSAFE_PATH).cookie(session.cookie()))
                .andExpect(problem(ErrorCode.CSRF_TOKEN_INVALID));
        mockMvc.perform(post(UNSAFE_PATH).cookie(session.cookie()).header("X-CSRF-TOKEN", "not-the-token"))
                .andExpect(problem(ErrorCode.CSRF_TOKEN_INVALID));
        mockMvc.perform(post(UNSAFE_PATH).cookie(session.cookie()).queryParam("_csrf", session.token()))
                .andExpect(problem(ErrorCode.CSRF_TOKEN_INVALID));
        mockMvc.perform(post(UNSAFE_PATH).cookie(session.cookie())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("_csrf=" + session.token()))
                .andExpect(problem(ErrorCode.CSRF_TOKEN_INVALID));

        mockMvc.perform(post(UNSAFE_PATH).cookie(session.cookie()).header("X-CSRF-TOKEN", session.token()))
                .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
    }

    @Test
    void aTokenFromAnotherSessionIsRefused() throws Exception {
        CsrfSession mine = bootstrap();
        CsrfSession theirs = bootstrap();

        mockMvc.perform(post(UNSAFE_PATH).cookie(mine.cookie()).header("X-CSRF-TOKEN", theirs.token()))
                .andExpect(problem(ErrorCode.CSRF_TOKEN_INVALID));
    }

    /** T-CSRF-008 without its audit reason ({@code CSRF_MISSING}); ticket 10 adds that assertion and the ID. */
    @Test
    void anUnsafeRequestWithNoSessionIsRefusedAndCreatesNoSession() throws Exception {
        Set<String> before = sessions.ids();

        MvcResult result = mockMvc.perform(post(UNSAFE_PATH).header("X-CSRF-TOKEN", "anything"))
                .andExpect(problem(ErrorCode.CSRF_TOKEN_INVALID))
                .andReturn();
        mockMvc.perform(post(UNSAFE_PATH)).andExpect(problem(ErrorCode.CSRF_TOKEN_INVALID));

        assertThat(sessions.ids()).isEqualTo(before);
        assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
    }
}
