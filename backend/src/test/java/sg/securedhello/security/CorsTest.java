package sg.securedhello.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import sg.securedhello.config.OriginsProperties;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;

/** CORS admits the SPA origin alone, with credentials and the CSRF header (ADR-036; ADR-059). */
class CorsTest extends CtxDefaultTest {

    private static final String SPA = "http://localhost:5173";

    @Autowired
    private OriginsProperties origins;

    private static MockHttpServletRequestBuilder preflight(String origin) {
        return options("/api/profile/password")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type, x-csrf-token");
    }

    @Test
    @Proves("T-HDR-007")
    void aPreflightFromTheSpaOriginIsAdmittedWithCredentialsAndTheCsrfHeader() throws Exception {
        MvcResult result = mockMvc.perform(preflight(SPA))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, SPA))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                .containsIgnoringCase("X-CSRF-TOKEN");
    }

    @Test
    @Proves("T-HDR-008")
    void anyOtherOriginIsRefusedInTheEnvelopeAndNothingIsEchoed() throws Exception {
        for (String origin : new String[] {"http://localhost:5174", "https://localhost:5173", "http://evil.example"}) {
            mockMvc.perform(preflight(origin))
                    .andExpect(problem(ErrorCode.ACCESS_DENIED))
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
            mockMvc.perform(get("/api/csrf").header(HttpHeaders.ORIGIN, origin))
                    .andExpect(problem(ErrorCode.ACCESS_DENIED))
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }
    }

    @Test
    @Proves("T-HDR-008")
    void theAllowListIsExactlyTheConfiguredSpaOriginWithNoWildcard() {
        assertThat(CorsPolicy.configuration(origins).getAllowedOrigins()).containsExactly(SPA);
        assertThat(CorsPolicy.configuration(origins).getAllowedOriginPatterns()).isNull();
    }
}
