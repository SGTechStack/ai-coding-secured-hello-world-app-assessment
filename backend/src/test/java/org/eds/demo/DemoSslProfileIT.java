package org.eds.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles({"test", "feat-https"})
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "management.server.port=0")
class DemoSslProfileIT {

  @LocalServerPort private int port;

  @Test
  void featHttpsProfileServesRequestsOverHttps() throws Exception {
    HttpResponse<String> response = getOverHttps("/");

    assertThat(response.uri().getScheme()).isEqualTo("https");
    assertThat(response.statusCode()).isEqualTo(302);
  }

  private HttpResponse<String> getOverHttps(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("https://localhost:" + port + path)).GET().build();
    return insecureHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpClient insecureHttpClient() throws Exception {
    TrustManager[] trustAllManagers = {new TrustAllCertificatesManager()};
    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, trustAllManagers, new SecureRandom());
    return HttpClient.newBuilder().sslContext(sslContext).build();
  }

  private static final class TrustAllCertificatesManager implements X509TrustManager {

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {}

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {}

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }
}
