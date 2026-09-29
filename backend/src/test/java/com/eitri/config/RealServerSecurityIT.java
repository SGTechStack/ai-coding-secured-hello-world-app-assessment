package com.eitri.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

/**
 * Security checks that require a real servlet container. MockMvc does not execute ERROR dispatches,
 * so it cannot detect a status rewritten while Tomcat renders an error response.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class RealServerSecurityIT {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void csrfRejectionRemainsForbiddenAfterTomcatsErrorDispatch() throws IOException, InterruptedException {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());

        assertThat(response.statusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void unauthenticatedCsrfFailureRemainsUnauthorizedAfterTomcatsErrorDispatch()
            throws IOException, InterruptedException {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/v1/auth/logout"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());

        assertThat(response.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void containerRenderedErrorsHaveTheMessageOnlyBody() throws IOException, InterruptedException {
        HttpResponse<String> unauthorized = send(HttpRequest.newBuilder(uri("/api/v1/hello")).GET().build());
        HttpResponse<String> forbidden = send(HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());

        assertThat(unauthorized.statusCode()).isEqualTo(401);
        assertThat(unauthorized.body()).isEqualTo("{\"message\":\"Unauthorized\"}");
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(forbidden.body()).isEqualTo("{\"message\":\"Forbidden\"}");
    }

    @Test
    @DisplayName("[assessment/story13-ac7] GET {base}/csrf over forwarded HTTPS carries HSTS")
    void forwardedHttpsCsrfResponseCarriesHsts() throws IOException, InterruptedException {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/v1/csrf"))
                .header("X-Forwarded-Proto", "https")
                .GET()
                .build());

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.headers().firstValue("Strict-Transport-Security"))
                .hasValueSatisfying(value -> assertThat(value).contains("max-age=31536000"));
    }

    @Test
    void forwardedHttpsResponseCarriesHsts() throws IOException, InterruptedException {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/actuator/health"))
                .header("X-Forwarded-Proto", "https")
                .GET()
                .build());

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.headers().firstValue("Strict-Transport-Security"))
                .contains("max-age=31536000 ; includeSubDomains ; preload");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
