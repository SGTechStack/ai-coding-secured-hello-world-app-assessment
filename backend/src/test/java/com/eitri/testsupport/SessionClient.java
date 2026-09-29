package com.eitri.testsupport;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Test-side cookie jar that uses the same public CSRF and SESSION-cookie seams as the SPA. */
public final class SessionClient {

    private SessionClient() {}

    public static Session fetchCsrf(MockMvcTester mvc) throws Exception {
        MvcTestResult result = mvc.get().uri("/api/v1/csrf").exchange();
        String body = result.getResponse().getContentAsString();
        return new Session(
                requiredSessionCookie(result),
                JsonPath.read(body, "$.headerName"),
                JsonPath.read(body, "$.token"));
    }

    /** {@link #fetchCsrf} for lambdas and helpers that can't declare checked exceptions. */
    public static Session fetchCsrfUnchecked(MockMvcTester mvc) {
        try {
            return fetchCsrf(mvc);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static Cookie requiredSessionCookie(MvcTestResult result) {
        Cookie cookie = result.getResponse().getCookie("SESSION");
        if (cookie == null) {
            throw new AssertionError("Response did not set the SESSION cookie");
        }
        return cookie;
    }

    public static String sessionId(Cookie cookie) {
        return new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
    }

    /**
     * A random address in 10.0.0.0/8, so the per-IP login throttle never couples otherwise unrelated
     * tests that share an application context.
     */
    public static String randomSourceIp() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
    }

    /** Logs in and fetches the session's post-login CSRF token (login rotates it). */
    public static LoggedIn loggedIn(MockMvcTester mvc, String username, String password) throws Exception {
        Session session = fetchCsrf(mvc);
        MvcTestResult login = session.login(
                mvc, "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        if (login.getResponse().getStatus() != 200) {
            throw new AssertionError("Login as " + username + " failed with " + login.getResponse().getStatus());
        }
        MvcTestResult csrf = mvc.get().uri("/api/v1/csrf").cookie(session.cookie()).exchange();
        String body = csrf.getResponse().getContentAsString();
        return new LoggedIn(session.cookie(), JsonPath.read(body, "$.headerName"), JsonPath.read(body, "$.token"));
    }

    /** An authenticated SESSION cookie and its CSRF token, for calling protected endpoints. */
    public record LoggedIn(Cookie cookie, String headerName, String token) {

        public MvcTestResult get(MockMvcTester mvc, String path) {
            return mvc.get().uri(path).cookie(cookie).exchange();
        }

        /** A state-changing request with the CSRF token and an optional JSON body. */
        public MvcTestResult send(MockMvcTester mvc, org.springframework.http.HttpMethod method, String path, String body) {
            var request = mvc.method(method).uri(path).cookie(cookie).header(headerName, token);
            if (body != null) {
                request.contentType(MediaType.APPLICATION_JSON).content(body);
            }
            return request.exchange();
        }
    }

    public static final class Session {
        private Cookie cookie;
        private final String headerName;
        private final String token;
        private String sourceIp = randomSourceIp();

        private Session(Cookie cookie, String headerName, String token) {
            this.cookie = cookie;
            this.headerName = headerName;
            this.token = token;
        }

        public Cookie cookie() {
            return cookie;
        }

        public String headerName() {
            return headerName;
        }

        public String token() {
            return token;
        }

        public String sessionId() {
            return SessionClient.sessionId(cookie);
        }

        public String sourceIp() {
            return sourceIp;
        }

        /** Sends this session's logins from {@code ip} (the servlet remote address). */
        public Session from(String ip) {
            sourceIp = ip;
            return this;
        }

        public MvcTestResult login(MockMvcTester mvc, String body) {
            return login(mvc, body, null);
        }

        public MvcTestResult login(MockMvcTester mvc, String body, String traceparent) {
            String ip = sourceIp;
            var request = mvc.post()
                    .uri("/api/v1/auth/login")
                    .with(servletRequest -> {
                        servletRequest.setRemoteAddr(ip);
                        return servletRequest;
                    })
                    .cookie(cookie)
                    .header(headerName, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body);
            if (traceparent != null) {
                request.header("traceparent", traceparent);
            }
            MvcTestResult result = request.exchange();
            Cookie replacement = result.getResponse().getCookie("SESSION");
            if (replacement != null && !replacement.getValue().isEmpty()) {
                cookie = replacement;
            }
            return result;
        }
    }
}
