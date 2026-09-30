package com.example.securedhello.support;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Shared HTTP helper for integration tests. Encapsulates the repeated CSRF
 * handshake, login, and authenticated-request wiring so individual tests don't
 * re-implement cookie extraction. Uses the JDK HttpClient factory so PATCH is
 * supported.
 */
public final class AuthTestClient {

    private final RestTemplate rest = new RestTemplate(new JdkClientHttpRequestFactory());
    private final String baseUrl;

    public AuthTestClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public RestTemplate rest() {
        return rest;
    }

    public String url(String path) {
        return baseUrl + path;
    }

    /** Registers a new USER account (public but CSRF-protected endpoint). */
    public void register(String username, String email, String password) {
        Csrf csrf = csrf();
        HttpHeaders headers = jsonHeaders();
        headers.add("X-XSRF-TOKEN", csrf.token());
        headers.add(HttpHeaders.COOKIE, csrf.cookie());
        HttpEntity<Map<String, String>> entity =
                new HttpEntity<>(Map.of("username", username, "email", email, "password", password), headers);
        rest.postForEntity(url("/api/register"), entity, Map.class);
    }

    /** Performs a CSRF handshake, returning the token and its cookies. */
    @SuppressWarnings("unchecked")
    public Csrf csrf() {
        ResponseEntity<Map> response = rest.getForEntity(url("/api/csrf"), Map.class);
        String token = (String) response.getBody().get("token");
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        String xsrfCookie = firstCookie(setCookies, "XSRF-TOKEN=");
        return new Csrf(token, xsrfCookie);
    }

    /**
     * Logs in and returns a {@link Session} carrying the SESSION cookie plus
     * the CSRF token/cookie usable for subsequent state-changing requests.
     *
     * @return the resulting login response and session context
     */
    @SuppressWarnings("unchecked")
    public LoginResult login(String username, String password) {
        Csrf csrf = csrf();
        HttpHeaders headers = jsonHeaders();
        headers.add("X-XSRF-TOKEN", csrf.token());
        headers.add(HttpHeaders.COOKIE, csrf.cookie());
        HttpEntity<Map<String, String>> entity =
                new HttpEntity<>(Map.of("username", username, "password", password), headers);
        ResponseEntity<Map> response =
                rest.exchange(url("/api/login"), HttpMethod.POST, entity, Map.class);
        String sessionCookie = firstCookie(
                response.getHeaders().get(HttpHeaders.SET_COOKIE), "SESSION=");
        return new LoginResult(response, new Session(sessionCookie, csrf.token(), csrf.cookie()));
    }

    /** Headers carrying the session cookie only (for safe GETs). */
    public HttpHeaders sessionHeaders(Session session) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, session.sessionCookie());
        return headers;
    }

    /** Headers carrying session + CSRF, for authenticated state-changing calls. */
    public HttpHeaders authedCsrfHeaders(Session session) {
        HttpHeaders headers = jsonHeaders();
        headers.add(HttpHeaders.COOKIE, session.sessionCookie());
        headers.add(HttpHeaders.COOKIE, session.csrfCookie());
        headers.add("X-XSRF-TOKEN", session.csrfToken());
        return headers;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static String firstCookie(List<String> setCookies, String prefix) {
        if (setCookies == null) {
            return null;
        }
        return setCookies.stream()
                .map(c -> c.split(";", 2)[0])
                .filter(c -> c.startsWith(prefix))
                .findFirst()
                .orElse(null);
    }

    /** A CSRF token paired with its XSRF-TOKEN cookie. */
    public record Csrf(String token, String cookie) {
    }

    /** An authenticated session: the SESSION cookie plus CSRF token/cookie. */
    public record Session(String sessionCookie, String csrfToken, String csrfCookie) {
    }

    /** The login HTTP response together with the derived session context. */
    public record LoginResult(ResponseEntity<Map> response, Session session) {
    }
}
