package com.example.helloauth.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A browser-shaped HTTP client for the integration tests.
 *
 * <p>Hand-rolled rather than using {@code TestRestTemplate} for one reason: several of the required
 * tests are about cookies specifically — that a session cookie is rejected after logout, that a
 * session is replaced rather than reused at login — and those need a client whose cookie jar can be
 * inspected and tampered with. A client that manages cookies invisibly cannot express them.
 *
 * <p>It also carries the CSRF token the way the real frontend has to, which means the tests exercise
 * the CSRF configuration instead of bypassing it.
 */
public class ApiClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String baseUrl;
    private final HttpClient http =
            HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

    /** The cookie jar, deliberately visible so tests can read and forge what the browser would hold. */
    private final Map<String, String> cookies = new LinkedHashMap<>();

    private String csrfHeaderName;
    private String csrfToken;

    public ApiClient(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    public record Response(int status, String body, HttpHeadersView headers) {

        public JsonNode json() {
            try {
                return JSON.readTree(body);
            } catch (IOException e) {
                throw new AssertionError("Response body was not JSON: " + body, e);
            }
        }

        public String contentType() {
            return headers.first("content-type").orElse("");
        }
    }

    /** Minimal read-only view of response headers, so tests do not depend on the JDK's types. */
    public record HttpHeadersView(Map<String, List<String>> values) {

        public java.util.Optional<String> first(String name) {
            return values.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .flatMap(entry -> entry.getValue().stream())
                    .findFirst();
        }

        public List<String> all(String name) {
            return values.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .flatMap(entry -> entry.getValue().stream())
                    .toList();
        }
    }

    /**
     * Fetches a CSRF token the way the frontend does. Every mutating call below sends it, so a test
     * that forgets this step fails with a 403 rather than silently skipping CSRF.
     */
    public ApiClient primeCsrf() {
        Response response = get("/api/auth/csrf");
        if (response.status() != 200) {
            throw new AssertionError("Could not fetch a CSRF token: " + response.status());
        }
        this.csrfHeaderName = response.json().get("headerName").asText();
        this.csrfToken = response.json().get("token").asText();
        return this;
    }

    public Response get(String path) {
        return send(requestBuilder(path).GET());
    }

    public Response post(String path, Object body) {
        return send(requestBuilder(path).POST(jsonBody(body)));
    }

    public Response patch(String path, Object body) {
        return send(requestBuilder(path).method("PATCH", jsonBody(body)));
    }

    public Response delete(String path) {
        return send(requestBuilder(path).method("DELETE", HttpRequest.BodyPublishers.noBody()));
    }

    /** A CORS preflight, which only a browser would normally send — hence the explicit headers. */
    public Response options(String path, Map<String, String> headers) {
        HttpRequest.Builder builder =
                requestBuilder(path).method("OPTIONS", HttpRequest.BodyPublishers.noBody());
        headers.forEach(builder::header);
        return send(builder);
    }

    /** The current session cookie value, or null. Captured by tests that want to replay it later. */
    public String sessionCookie() {
        return cookies.get("SESSION");
    }

    /** Puts a previously captured session cookie back, which is how the replay test works. */
    public ApiClient withSessionCookie(String value) {
        cookies.put("SESSION", value);
        return this;
    }

    public ApiClient forgetSessionCookie() {
        cookies.remove("SESSION");
        return this;
    }

    private HttpRequest.Builder requestBuilder(String path) {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .timeout(Duration.ofSeconds(20))
                        .header("Accept", "*/*");
        if (!cookies.isEmpty()) {
            builder.header("Cookie", cookieHeader());
        }
        if (csrfToken != null) {
            builder.header(csrfHeaderName, csrfToken);
        }
        return builder;
    }

    private HttpRequest.BodyPublisher jsonBody(Object body) {
        if (body == null) {
            return HttpRequest.BodyPublishers.noBody();
        }
        try {
            return HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body));
        } catch (IOException e) {
            throw new AssertionError("Could not serialise the request body", e);
        }
    }

    private Response send(HttpRequest.Builder builder) {
        HttpRequest request = builder.header("Content-Type", "application/json").build();
        try {
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());
            storeCookies(response);
            return new Response(
                    response.statusCode(),
                    response.body(),
                    new HttpHeadersView(response.headers().map()));
        } catch (IOException e) {
            throw new AssertionError("Request failed: " + request.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Request interrupted: " + request.uri(), e);
        }
    }

    /**
     * Applies {@code Set-Cookie} the way a browser would, including deletion. Honouring
     * {@code Max-Age=0} matters: without it, logout would appear to work while the client kept
     * sending the dead cookie, and the "cookie is cleared" half of story 4 would go untested.
     */
    private void storeCookies(HttpResponse<String> response) {
        for (String setCookie : response.headers().allValues("set-cookie")) {
            String pair = setCookie.split(";", 2)[0];
            int equals = pair.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String name = pair.substring(0, equals).trim();
            String value = pair.substring(equals + 1).trim();
            boolean expired =
                    value.isEmpty()
                            || setCookie.toLowerCase(java.util.Locale.ROOT).contains("max-age=0");
            if (expired) {
                cookies.remove(name);
            } else {
                cookies.put(name, value);
            }
        }
    }

    private String cookieHeader() {
        StringBuilder header = new StringBuilder();
        cookies.forEach(
                (name, value) -> {
                    if (!header.isEmpty()) {
                        header.append("; ");
                    }
                    header.append(name).append('=').append(value);
                });
        return header.toString();
    }
}
