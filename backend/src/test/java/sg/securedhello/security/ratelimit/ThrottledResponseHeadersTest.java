package sg.securedhello.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import sg.securedhello.testsupport.CtxBudgetTest;
import sg.securedhello.testsupport.Proves;

/**
 * The source limiter answers before Spring Security's header writer runs, and its 429 still carries the API's header
 * set (Std §5:498; IM8 as-9; R-HDR-010). On the real budgets ({@code ctx-budget}).
 */
class ThrottledResponseHeadersTest extends CtxBudgetTest {

    @Autowired
    private RateLimitProperties budgets;

    @Test
    @Proves("T-HDR-002")
    void aSourceThrottled429CarriesNosniffFrameOptionsAndTheApiPolicy() throws Exception {
        String source = nextSource();
        MockHttpServletResponse response = null;
        for (int i = 0; i <= budgets.csrf().source().burst(); i++) {
            response = mockMvc.perform(get("/api/csrf").secure(true).with(request -> {
                request.setRemoteAddr(source);
                return request;
            })).andReturn().getResponse();
        }

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Content-Security-Policy"))
                .isEqualTo("default-src 'none'; frame-ancestors 'none'");
        assertThat(response.getHeader("Strict-Transport-Security")).contains("max-age=31536000");
        assertThat(response.getHeaders("X-Content-Type-Options")).as("written once").hasSize(1);
    }
}
