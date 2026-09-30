package local.builderday.common.tls;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.cert.X509Certificate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SelfSignedSslConfigTest {
  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(SslAutoConfiguration.class))
      .withUserConfiguration(SelfSignedSslConfig.class)
      .withPropertyValues("spring.application.name=builderday");

  @Test
  void should_registerAnInMemoryCertificateNamedAfterTheApplication_when_theApplicationStarts() {
    runner.run(context -> {
      var bundle = context.getBean(SslBundles.class).getBundle("self-signed");
      var keyStore = bundle.getStores().getKeyStore();
      var certificate = (X509Certificate) keyStore.getCertificate("builderday");

      assertThat(certificate.getSubjectX500Principal().getName()).isEqualTo("CN=builderday");
      assertThat(certificate.getSubjectAlternativeNames()).isNull();
      assertThat(keyStore.isKeyEntry("builderday")).isTrue();
      assertThat(bundle.getKey().getPassword()).hasSizeGreaterThanOrEqualTo(32);
      assertThat(bundle.getStores().getTrustStore()).isNull();
      assertThat(bundle.createSslContext()).isNotNull();
    });
  }
}
