package com.eitri.testsupport;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Logs in against a real servlet container over plain HTTP from {@code 127.0.0.1}. The SESSION cookie
 * is {@code Secure} outside the dev profile, so it is carried by hand instead of through a cookie jar.
 */
public final class RealServerLogin {

    private final HttpClient client = HttpClient.newHttpClient();
    private final int port;

    public RealServerLogin(int port) {
        this.port = port;
    }

    public URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    public HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * One login attempt in a fresh session. A non-null {@code forwardedFor} is sent as
     * {@code X-Forwarded-For}, as a proxy (or a client pretending to be one) would.
     */
    public HttpResponse<String> login(String username, String password, String forwardedFor)
            throws IOException, InterruptedException {
        HttpResponse<String> csrf = send(HttpRequest.newBuilder(uri("/api/v1/csrf")).GET().build());
        String sessionCookie = csrf.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("SESSION="))
                .map(value -> value.split(";", 2)[0])
                .findFirst()
                .orElseThrow(() -> new AssertionError("GET /api/v1/csrf did not set the SESSION cookie"));

        HttpRequest.Builder login = HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header("Cookie", sessionCookie)
                .header(JsonPath.read(csrf.body(), "$.headerName"), JsonPath.read(csrf.body(), "$.token"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
        if (forwardedFor != null) {
            login.header("X-Forwarded-For", forwardedFor);
        }
        return send(login.build());
    }
}
