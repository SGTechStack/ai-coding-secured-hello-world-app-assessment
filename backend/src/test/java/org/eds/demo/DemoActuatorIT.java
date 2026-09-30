package org.eds.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "management.server.port=0")
class DemoActuatorIT {

  @LocalManagementPort private int managementPort;

  @Test
  void actuatorLinksEndpointIsExposedOnManagementPort() throws Exception {
    HttpResponse<String> response = getActuatorPath("/actuator");

    assertThat(response.statusCode()).isBetween(200, 299);
    assertThat(response.body()).contains("/actuator/health", "/actuator/info");
  }

  @Test
  void healthEndpointIsExposedOnManagementPort() throws Exception {
    HttpResponse<String> response = getActuatorPath("/actuator/health");

    assertThat(response.statusCode()).isBetween(200, 299);
    assertThat(response.body()).contains("\"status\":\"UP\"");
  }

  private HttpResponse<String> getActuatorPath(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + managementPort + path))
            .GET()
            .build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }
}
