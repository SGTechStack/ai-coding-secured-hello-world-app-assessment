package org.eds.demo.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Behind the cloud load balancer the throttle must see the real client address, not the balancer's.
 * Runs a real embedded Tomcat, because MockMvc bypasses the servlet container's forwarded-header
 * handling. The negative case (header ignored without the profile) is {@link SignInBackoffIT}'s
 * forwarded-for test.
 */
@ActiveProfiles({"test", "feat-load-balancer"})
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:forwarded-for-it;DB_CLOSE_DELAY=-1",
      "app.sign-in.ip-max-failures=2"
    })
class ForwardedForSignInThrottleIT {

  private static final int TOO_MANY_REQUESTS = 429;
  private static final int UNAUTHORIZED = 401;

  @Value("${local.server.port}")
  private int port;

  private final HttpClient client = HttpClient.newHttpClient();

  @Test
  void throttlesPerForwardedClientAddressNotPerLoadBalancer() throws Exception {
    assertThat(failedSignIn("203.0.113.10")).isEqualTo(UNAUTHORIZED);
    assertThat(failedSignIn("203.0.113.10")).isEqualTo(UNAUTHORIZED);
    assertThat(failedSignIn("203.0.113.10")).isEqualTo(TOO_MANY_REQUESTS);

    assertThat(failedSignIn("203.0.113.11")).isEqualTo(UNAUTHORIZED);
  }

  private int failedSignIn(String forwardedFor) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login"))
            .header("Content-Type", "application/json")
            .header("Cookie", "XSRF-TOKEN=t")
            .header("X-XSRF-TOKEN", "t")
            .header("X-Forwarded-For", forwardedFor)
            .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"x\",\"password\":\"y\"}"))
            .build();
    return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
  }
}
