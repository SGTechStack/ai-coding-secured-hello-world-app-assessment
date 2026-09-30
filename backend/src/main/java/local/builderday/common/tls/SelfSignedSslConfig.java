package local.builderday.common.tls;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ssl.SslBundleRegistrar;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundleKey;
import org.springframework.boot.ssl.SslStoreBundle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the {@value #BUNDLE} SSL bundle ({@code server.ssl.bundle}) from a self-signed certificate generated in
 * memory at startup. The public certificate lives on the ALB in front, which does not validate target certificates;
 * this only keeps the ALB-to-application hop encrypted, so there is no trust store and no keystore file.
 *
 * <p>Fresh key pair, certificate and key store password on every boot; no rotation, reload or persistence. Bouncy
 * Castle builds the v3 certificate (BasicConstraints, KeyUsage, no SAN), because the JDK has no public API for it.
 */
@Configuration(proxyBeanMethods = false)
public class SelfSignedSslConfig {
  static final String BUNDLE = "self-signed";
  private static final Logger log = LoggerFactory.getLogger(SelfSignedSslConfig.class);
  private static final int VALIDITY_DAYS = 365;

  @Bean
  SslBundleRegistrar selfSignedSslBundleRegistrar(@Value("${spring.application.name}") String applicationName) {
    return registry -> {
      registry.registerBundle(BUNDLE, selfSigned(applicationName));
      log.info("HTTPS is using an in-memory self-signed certificate generated at startup");
    };
  }

  private static SslBundle selfSigned(String commonName) {
    try {
      var random = new SecureRandom();
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048, random);
      KeyPair keyPair = generator.generateKeyPair();

      // Fresh per boot: the key store only ever lives in this JVM, so nothing needs to know the password.
      byte[] secret = new byte[24];
      random.nextBytes(secret);
      String password = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);

      var keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(null, null);
      keyStore.setKeyEntry(commonName, keyPair.getPrivate(), password.toCharArray(),
          new Certificate[] {certificate(commonName, keyPair, random)});
      return SslBundle.of(SslStoreBundle.of(keyStore, password, null), SslBundleKey.of(password, commonName));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to generate startup self-signed TLS certificate", e);
    }
  }

  private static X509Certificate certificate(String commonName, KeyPair keyPair, SecureRandom random) throws Exception {
    Instant now = Instant.now();
    // Self-signed: issuer and subject are the same distinguished name.
    var dn = new X500Name("CN=" + commonName);
    var builder = new JcaX509v3CertificateBuilder(dn, new BigInteger(64, random),
        Date.from(now.minus(1, ChronoUnit.MINUTES)), Date.from(now.plus(VALIDITY_DAYS, ChronoUnit.DAYS)), dn,
        keyPair.getPublic())
        .addExtension(Extension.basicConstraints, true, new BasicConstraints(false))
        .addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
    return new JcaX509CertificateConverter()
        .getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate())));
  }
}
