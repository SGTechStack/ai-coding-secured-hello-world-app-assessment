package com.example.auth.support;


import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A browser-like client for MockMvc tests: keeps a cookie jar (Spring Session's real {@code
 * SESSION} cookie is written to MockMvc responses and replayed on the next request, exactly as a
 * browser would), fetches the CSRF token from {@code GET /api/auth/csrf} the way the SPA does and
 * sends it as {@code X-CSRF-TOKEN} on state-changing requests, and lets a test pick its source IP.
 *
 * <p>Deliberately not {@code SecurityMockMvcRequestPostProcessors.csrf()}/{@code user()}: those
 * bypass the very mechanisms (session-stored CSRF, session rotation, concurrent-session control)
 * these tests exist to exercise.
 */
public final class ApiSession {

    public static final String SESSION_COOKIE = "SESSION";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final Map<String, String> cookies = new LinkedHashMap<>();
    private String remoteAddr = "127.0.0.1";
    private String csrfHeaderName;
    private String csrfToken;

    public ApiSession(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    /** Sends every subsequent request from {@code ip}. */
    public ApiSession from(String ip) {
        this.remoteAddr = ip;
        return this;
    }

    public ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        cookies.forEach((name, value) -> request.cookie(new Cookie(name, value)));
        String ip = remoteAddr;
        request.with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
        ResultActions actions = mockMvc.perform(request);
        absorbCookies(actions.andReturn());
        return actions;
    }

    public ResultActions get(String url) throws Exception {
        return perform(MockMvcRequestBuilders.get(url));
    }

    public ResultActions post(String url, Object body) throws Exception {
        return perform(withCsrf(json(MockMvcRequestBuilders.post(url), body)));
    }

    public ResultActions patch(String url, Object body) throws Exception {
        return perform(withCsrf(json(MockMvcRequestBuilders.patch(url), body)));
    }

    /** Raw JSON PATCH body, e.g. an unknown enum value or a missing field. */
    public ResultActions patchRaw(String url, String rawJson) throws Exception {
        return perform(withCsrf(MockMvcRequestBuilders.patch(url).contentType(MediaType.APPLICATION_JSON).content(rawJson)));
    }

    public ResultActions delete(String url) throws Exception {
        return perform(withCsrf(MockMvcRequestBuilders.delete(url)));
    }

    /** A state-changing request with no CSRF header at all. */
    public ResultActions postWithoutCsrf(String url, Object body) throws Exception {
        return perform(json(MockMvcRequestBuilders.post(url), body));
    }

    /** Raw JSON body, e.g. for malformed-input tests. */
    public ResultActions postRaw(String url, String rawJson) throws Exception {
        return perform(withCsrf(MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(rawJson)));
    }

    /** Logs in; the server rotates the CSRF token on success, so the cached one is dropped. */
    public ResultActions login(String username, String password) throws Exception {
        ResultActions result = post("/api/auth/login", Map.of("username", username, "password", password));
        csrfToken = null;
        return result;
    }

    public ResultActions logout() throws Exception {
        ResultActions result = perform(withCsrf(MockMvcRequestBuilders.post("/api/auth/logout")));
        csrfToken = null;
        return result;
    }

    public String fetchCsrfToken() throws Exception {
        MvcResult result = get("/api/auth/csrf").andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        csrfHeaderName = body.get("headerName").asString();
        csrfToken = body.get("token").asString();
        return csrfToken;
    }

    /** Adopts an existing session cookie, e.g. one planted directly in the session store. */
    public ApiSession withSessionCookie(String value) {
        cookies.put(SESSION_COOKIE, value);
        csrfToken = null;
        return this;
    }

    public String sessionCookie() {
        return cookies.get(SESSION_COOKIE);
    }

    /** Forces a CSRF refetch on the next state-changing request. */
    public void forgetCsrfToken() {
        csrfToken = null;
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) throws Exception {
        if (csrfToken == null) {
            fetchCsrfToken();
        }
        return request.header(csrfHeaderName, csrfToken);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    private void absorbCookies(MvcResult result) {
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (cookie.getMaxAge() == 0 || Objects.equals(cookie.getValue(), "")) {
                cookies.remove(cookie.getName());
            } else {
                cookies.put(cookie.getName(), cookie.getValue());
            }
        }
    }
}
