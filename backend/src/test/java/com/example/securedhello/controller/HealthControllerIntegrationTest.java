package com.example.securedhello.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.example.securedhello.HttpIntegrationTest;

/**
 * Integration test for the unauthenticated health endpoint, exercised over the
 * real HTTP boundary via the shared {@link HttpIntegrationTest} harness.
 */
class HealthControllerIntegrationTest extends HttpIntegrationTest {

    private final RestTemplate client = new RestTemplate();

    @BeforeEach
    void setUp() {
        resetClock();
    }

    @Test
    void healthEndpointIsReachableWithoutAuthentication() {
        ResponseEntity<Map> response =
                client.getForEntity(baseUrl() + "/api/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    void healthEndpointReadsTimeFromInjectedClock() {
        ResponseEntity<Map> first =
                client.getForEntity(baseUrl() + "/api/health", Map.class);
        assertThat(first.getBody().get("time")).isEqualTo(FIXED_NOW.toString());

        advanceTime(Duration.ofHours(3));

        ResponseEntity<Map> second =
                client.getForEntity(baseUrl() + "/api/health", Map.class);
        assertThat(second.getBody().get("time"))
                .isEqualTo(FIXED_NOW.plus(Duration.ofHours(3)).toString());
    }

    @Test
    void corsPreflightAllowsFrontendOriginWithCredentials() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        HttpRequest preflight = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/health"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .build();

        HttpResponse<Void> response =
                http.send(preflight, HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .contains("http://localhost:5173");
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials"))
                .contains("true");
    }
}
