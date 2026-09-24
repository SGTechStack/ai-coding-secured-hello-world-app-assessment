package com.example.demo_app.passwordreset;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What a reset link needs: the SPA's origin it points to ({@code app.frontend-url}) and how long
 * it works ({@code app.security.password-reset.token-ttl}, 30 minutes by default in {@code
 * application.yml}). Both are checked at startup, so a missing frontend URL or a non-positive TTL
 * stops the app instead of mailing broken or never-expiring links. Only dev and test default the
 * URL; production sets {@code APP_FRONTEND_URL}.
 *
 * @param frontendUrl the SPA's origin, e.g. {@code https://app.example.com}, no trailing slash
 * @param security holds {@code password-reset.token-ttl}
 */
@ConfigurationProperties("app")
record PasswordResetProperties(String frontendUrl, Security security) {

  PasswordResetProperties {
    if (!isHttpUrl(frontendUrl)) {
      throw new IllegalArgumentException(
          "app.frontend-url must be the SPA's http(s) origin, e.g. https://app.example.com;"
              + " set it or APP_FRONTEND_URL");
    }
    frontendUrl = frontendUrl.replaceAll("/+$", "");
    Duration ttl =
        security == null || security.passwordReset() == null
            ? null
            : security.passwordReset().tokenTtl();
    if (ttl == null || ttl.isNegative() || ttl.isZero()) {
      throw new IllegalArgumentException(
          "app.security.password-reset.token-ttl must be a positive duration, got " + ttl);
    }
  }

  Duration tokenTtl() {
    return security.passwordReset().tokenTtl();
  }

  private static boolean isHttpUrl(String url) {
    if (url == null) {
      return false;
    }
    try {
      URI uri = new URI(url);
      return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
          && uri.getHost() != null;
    } catch (URISyntaxException e) {
      return false;
    }
  }

  /** {@code app.security}: only the reset settings are read here. */
  record Security(PasswordReset passwordReset) {}

  /** {@code app.security.password-reset}. */
  record PasswordReset(Duration tokenTtl) {}
}
