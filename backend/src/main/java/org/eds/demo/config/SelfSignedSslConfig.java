package org.eds.demo.config;

import java.nio.file.Path;
import java.util.UUID;
import org.eds.demo.common.util.SelfSignedKeystoreUtil;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Generates a self-signed TLS certificate at startup when the {@code feat-https} profile is active
 * (included in the {@code local} profile group by default).
 *
 * <p>A 2048-bit RSA key pair and a one-year self-signed X.509 certificate are created in-process
 * via BouncyCastle into a temporary PKCS12 keystore each time the application starts. No external
 * tools ({@code keytool}) or subprocesses are required, making this portable across any JRE. The
 * Pass key is randomly generated per run and never written to disk in plain-text. The certificate
 * covers {@code localhost} and {@code 127.0.0.1} via Subject Alternative Names.
 *
 * <p>This profile is also active in the {@code dev}, {@code qa}, {@code preprod}, and {@code prod}
 * profile groups. In those environments an AWS Application Load Balancer (ALB) sits in front of the
 * application. The ALB terminates TLS with a CA-signed certificate and communicates with the
 * backend over HTTPS using the self-signed certificate. ALB does not verify the backend certificate
 * by default, so the self-signed cert is accepted without any additional trust configuration on the
 * ALB side. End-users only ever see the CA-signed certificate presented by the ALB.
 */
@Configuration
@Profile("feat-https")
class SelfSignedSslConfig {

  // FIPS 140-2/140-3 approved, forward secrecy cipher suites (NIST SP 800-52 Rev 2).
  // TLS 1.3 suites first (AES-GCM only — ChaCha20-Poly1305 is NOT FIPS-approved).
  // TLS 1.2 suites use ECDHE for forward secrecy and AES-GCM for authenticated encryption.
  private static final String[] CIPHERS = {
    "TLS_AES_256_GCM_SHA384",
    "TLS_AES_128_GCM_SHA256",
    "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
    "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
    "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
    "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"
  };

  @Bean
  WebServerFactoryCustomizer<TomcatServletWebServerFactory> selfSignedSslCustomizer() {
    return factory -> {
      String randomPass = UUID.randomUUID().toString().replace("-", ""); // secure random
      Path keystoreFile;
      try {
        keystoreFile =
            SelfSignedKeystoreUtil.generateKeystore(
                "localhost",
                "CN=localhost, OU=Internal, O=Internal, L=Singapore, ST=Singapore, C=SG",
                randomPass);
      } catch (Exception e) {
        throw new IllegalStateException("Failed to generate self-signed TLS certificate", e);
      }

      // All SSL config is owned here so the CIS 5.5–5.7 constraints are always enforced
      // regardless of property file presence or ordering.
      Ssl ssl = factory.getSsl();
      if (ssl == null) {
        ssl = new Ssl();
      }
      ssl.setEnabled(true);
      // "file:" prefix required for an absolute filesystem path.
      ssl.setKeyStore("file:" + keystoreFile.toAbsolutePath());
      ssl.setKeyStorePassword(randomPass);
      ssl.setKeyStoreType("PKCS12");
      ssl.setKeyAlias("localhost");
      // CIS 5.5/5.6 – TLS 1.2 and 1.3 only.
      ssl.setEnabledProtocols(new String[] {"TLSv1.2", "TLSv1.3"});
      // CIS 5.7 – FIPS-approved, forward secrecy suites only.
      ssl.setCiphers(CIPHERS);
      factory.setSsl(ssl);
    };
  }
}
