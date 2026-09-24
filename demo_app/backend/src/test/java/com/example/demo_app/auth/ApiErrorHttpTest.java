package com.example.demo_app.auth;

import static com.example.demo_app.auth.SpaAuthFlow.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * Errors that happen outside Spring MVC, which MockMvc cannot observe: the servlet container
 * forwards them to {@code /error}, where Spring Boot's default controller and whitelabel page are
 * replaced by the standard JSON error body. Also a chunked body with no declared length, which is
 * cut off at the size cap while it is read.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiErrorHttpTest {

  @LocalServerPort private int port;

  @Autowired private RestClient.Builder restClientBuilder;

  @Autowired private ObjectMapper objectMapper;

  private RestClient client;

  @BeforeEach
  void setUp() {
    client =
        restClientBuilder
            .baseUrl("http://localhost:" + port)
            .defaultStatusHandler(status -> true, (request, response) -> {})
            .build();
  }

  @Test
  void requestRejectedByTheFirewallIsJsonNotAnHtmlErrorPage() throws Exception {
    ResponseEntity<String> response =
        client
            .get()
            .uri(URI.create("http://localhost:" + port + "/api/v1/auth/csrf;jsessionid=x"))
            .accept(MediaType.TEXT_HTML)
            .retrieve()
            .toEntity(String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.get("code").asText()).isEqualTo("MALFORMED_REQUEST");
    assertThat(body.get("status").asInt()).isEqualTo(400);
    assertThat(body.has("trace")).isFalse();
    assertThat(body.has("exception")).isFalse();
  }

  @Test
  void errorPathRequestedDirectlyIs404Json() throws Exception {
    ResponseEntity<String> response = client.get().uri("/error").retrieve().toEntity(String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(404);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.get("code").asText()).isEqualTo("NOT_FOUND");
    assertThat(body.get("path").asText()).isEqualTo("/error");
  }

  @Test
  void chunkedBodyOver16KbIsRejectedWith413Json() throws Exception {
    String token = csrfToken(client, null);
    byte[] oversized =
        ("{\"username\": \"" + "a".repeat(20 * 1024) + "\", \"password\": \"x\"}")
            .getBytes(StandardCharsets.UTF_8);

    ResponseEntity<String> response =
        client
            .post()
            .uri("/api/v1/auth/login")
            .header(HttpHeaders.COOKIE, "XSRF-TOKEN=" + token)
            .header("X-XSRF-TOKEN", token)
            .contentType(MediaType.APPLICATION_JSON)
            // A streaming body has no Content-Length, so it is sent chunked.
            .body((StreamingHttpOutputMessage.Body) out -> out.write(oversized))
            .retrieve()
            .toEntity(String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(413);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.get("code").asText()).isEqualTo("PAYLOAD_TOO_LARGE");
  }
}
