package local.builderday.account.passwordreset.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The application's public address, the only base emailed links are built from; never the request's Host header,
 * which would allow reset-link poisoning (ADR 0004).
 *
 * @param publicBaseUri absolute {@code https} URI with a host, in every environment (IM8 dp-3)
 */
@ConfigurationProperties("app")
public record PublicBaseUriProperties(URI publicBaseUri) {
  public PublicBaseUriProperties {
    if (publicBaseUri == null || !"https".equals(publicBaseUri.getScheme()) || publicBaseUri.getHost() == null
        || publicBaseUri.getQuery() != null || publicBaseUri.getFragment() != null) {
      throw new IllegalArgumentException(
          "app.public-base-uri must be an absolute https URI without query or fragment.");
    }
  }

  /** {@code publicBaseUri} joined with {@code path}, which starts with {@code /}. */
  public String resolve(String path) {
    String base = publicBaseUri.toString();
    return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path;
  }
}
