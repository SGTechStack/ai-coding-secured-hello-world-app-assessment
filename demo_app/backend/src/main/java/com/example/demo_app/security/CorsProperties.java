package com.example.demo_app.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.cors}: the browser origins allowed to call the API with credentials.
 *
 * <p>Each entry must be an exact origin ({@code scheme://host[:port]}, http or https): no
 * wildcards, patterns, {@code null}, paths or trailing slashes. The list must not be empty; the
 * dev and test profiles default it to {@code http://localhost:3000}, and production has no default,
 * so a missing list stops startup.
 *
 * @param allowedOrigins exact origins, e.g. {@code https://app.example.com}
 */
@ConfigurationProperties("app.cors")
record CorsProperties(List<String> allowedOrigins) {

  private static final String PROPERTY = "app.cors.allowed-origins";

  CorsProperties {
    if (allowedOrigins == null || allowedOrigins.isEmpty()) {
      throw new IllegalArgumentException(
          PROPERTY
              + " must list at least one origin (the SPA's, e.g. https://app.example.com);"
              + " set it or APP_CORS_ALLOWED_ORIGINS");
    }
    allowedOrigins.forEach(CorsProperties::requireExactOrigin);
    allowedOrigins = List.copyOf(allowedOrigins);
  }

  private static void requireExactOrigin(String origin) {
    if (!isExactOrigin(origin)) {
      throw new IllegalArgumentException(
          PROPERTY
              + " entries must be exact origins like https://app.example.com"
              + " (no wildcards, null, paths or trailing slash), got: '"
              + origin
              + "'");
    }
  }

  private static boolean isExactOrigin(String origin) {
    if (origin == null || origin.contains("*")) {
      return false;
    }
    try {
      URI uri = new URI(origin);
      return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
          && uri.getHost() != null
          && uri.getRawUserInfo() == null
          && uri.getRawPath().isEmpty()
          && uri.getRawQuery() == null
          && uri.getRawFragment() == null;
    } catch (URISyntaxException e) {
      return false;
    }
  }
}
