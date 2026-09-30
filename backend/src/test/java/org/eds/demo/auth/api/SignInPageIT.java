package org.eds.demo.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Over a real server socket: an anonymous visitor can load the SPA sign-in page and its assets, and
 * the session cookie issued at sign-in carries the hardening attributes. MockMvc cannot show either
 * because it neither follows forwards through the security chain nor applies the embedded server's
 * session cookie settings.
 */
@ActiveProfiles("local")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.email.inbound.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:sign-in-page-it;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "app.spa.static-location=classpath:/test-spa/",
      "app.admin.username=page-admin",
      "app.admin.password=test-only-page-secret"
    })
class SignInPageIT {

  private static final String CSRF_TOKEN = "test-csrf-token";

  private final HttpClient client = HttpClient.newBuilder().build();

  @Value("${local.server.port}")
  private int port;

  @Test
  void anonymousVisitorCanLoadTheSignInPageAndItsAssets() throws Exception {
    var page = get("/app/sign-in");
    var asset = get("/app/assets/app.js");

    assertThat(page.statusCode()).isEqualTo(200);
    assertThat(page.body()).contains("test spa shell");
    assertThat(page.headers().allValues("Set-Cookie")).anyMatch(c -> c.startsWith("XSRF-TOKEN="));
    assertThat(asset.statusCode()).isEqualTo(200);
  }

  @Test
  void anonymousVisitorToAProtectedPageIsSentToTheSignInPage() throws Exception {
    var response = get("/app/dashboard");

    assertThat(response.statusCode()).isEqualTo(302);
    assertThat(response.headers().firstValue("Location"))
        .hasValueSatisfying(l -> assertThat(l).endsWith("/app/sign-in"));
  }

  @Test
  void sessionCookieIsHttpOnlyAndSameSiteStrict() throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login"))
            .header("Content-Type", "application/json")
            .header("Cookie", "XSRF-TOKEN=" + CSRF_TOKEN)
            .header("X-XSRF-TOKEN", CSRF_TOKEN)
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"username\":\"page-admin\",\"password\":\"test-only-page-secret\"}"))
            .build();

    var response = client.send(request, BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().allValues("Set-Cookie"))
        .anySatisfy(
            cookie ->
                assertThat(cookie)
                    .startsWith("SESSION=")
                    .contains("HttpOnly")
                    .containsIgnoringCase("SameSite=Strict"));
  }

  private HttpResponse<String> get(String path) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
    return client.send(request, BodyHandlers.ofString());
  }
}
