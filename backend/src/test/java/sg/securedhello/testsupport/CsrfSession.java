package sg.securedhello.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import tools.jackson.databind.json.JsonMapper;

/**
 * A real CSRF bootstrap for MockMvc tests: the session cookie {@code GET /api/csrf} set, and the token it returned
 * (ADR-036).
 *
 * <p>Use it instead of Spring Security's {@code csrf()} post-processor, which permanently swaps the shared context's
 * token repository for one that creates sessions, so every later test in that context would see sessions ADR-040
 * forbids.
 */
public record CsrfSession(Cookie cookie, String token) {

    /** The request header the token travels in. */
    public static final String HEADER = "X-CSRF-TOKEN";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Fetches a token, which creates the anonymous session it is bound to. */
    public static CsrfSession bootstrap(MockMvc mockMvc) throws Exception {
        return bootstrap(mockMvc, "127.0.0.1");
    }

    /**
     * Fetches a token from {@code remoteAddr}, for tests that isolate their rate-limit state by source address
     * ({@link CtxBudgetTest}).
     */
    public static CsrfSession bootstrap(MockMvc mockMvc, String remoteAddr) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/csrf").with(request -> {
            request.setRemoteAddr(remoteAddr);
            return request;
        })).andReturn();
        assertThat(result.getResponse().getStatus()).as("GET /api/csrf").isEqualTo(200);
        Cookie cookie = result.getResponse().getCookie("SESSION");
        assertThat(cookie).as("session cookie").isNotNull();
        return new CsrfSession(cookie, JSON.readTree(result.getResponse().getContentAsString()).get("token")
                .asString());
    }

    /** A MockMvc post-processor: fetches a token and sends it, with its session cookie. */
    public static RequestPostProcessor validToken(MockMvc mockMvc) throws Exception {
        return bootstrap(mockMvc).inHeader();
    }

    /** Sends this session's cookie and the token in {@value #HEADER}. */
    public RequestPostProcessor inHeader() {
        return request -> {
            request.setCookies(cookie);
            request.addHeader(HEADER, token);
            return request;
        };
    }
}
