package org.eds.demo.common.util;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import lombok.experimental.UtilityClass;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Generates a temporary PKCS12 keystore containing a self-signed RSA certificate in-process via
 * BouncyCastle. No external tools or subprocesses are required, making this portable across any
 * JRE.
 */
@UtilityClass
public class SelfSignedKeystoreUtil {

  /**
   * Generates a 2048-bit RSA key pair, builds a one-year self-signed X.509 certificate covering
   * {@code localhost} and {@code 127.0.0.1} via Subject Alternative Names, and writes the result to
   * a temporary PKCS12 keystore file. The file is scheduled for deletion on JVM exit.
   *
   * @param alias the key alias to use inside the keystore
   * @param subject the X.500 distinguished name for the certificate subject and issuer
   * @param passkey the keystore and key protection passkey
   * @return path to the generated keystore file in the OS temp directory
   */
  public Path generateKeystore(String alias, String subject, String passkey)
      throws IOException,
          NoSuchAlgorithmException,
          OperatorCreationException,
          CertificateException,
          KeyStoreException {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048, new SecureRandom());
    KeyPair keyPair = kpg.generateKeyPair();

    Instant now = Instant.now();
    X500Name x500Name = new X500Name(subject);
    X509v3CertificateBuilder certBuilder =
        new JcaX509v3CertificateBuilder(
            x500Name,
            BigInteger.valueOf(now.toEpochMilli()),
            Date.from(now),
            Date.from(now.plus(365, ChronoUnit.DAYS)),
            x500Name,
            keyPair.getPublic());
    certBuilder.addExtension(
        Extension.subjectAlternativeName,
        false,
        new GeneralNames(
            new GeneralName[] {
              new GeneralName(GeneralName.dNSName, "localhost"),
              new GeneralName(GeneralName.iPAddress, "127.0.0.1")
            }));

    ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.getPrivate());
    Certificate cert = new JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));

    KeyStore ks = KeyStore.getInstance("PKCS12");
    ks.load(null, passkey.toCharArray());
    ks.setKeyEntry(alias, keyPair.getPrivate(), passkey.toCharArray(), new Certificate[] {cert});

    Path keystoreFile = Files.createTempFile("ssl-", ".p12");
    keystoreFile.toFile().deleteOnExit();
    try (OutputStream os = Files.newOutputStream(keystoreFile)) {
      ks.store(os, passkey.toCharArray());
    }
    return keystoreFile;
  }
}
