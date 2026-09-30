package org.eds.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Top-level configuration properties for the application, bound under the {@code app} prefix.
 *
 * <p>Nest additional sub-records here as the application grows, for example {@code app.security.*}
 * or {@code app.cache.*}.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Spa spa, Security security) {

  public static final String DEFAULT_CSP_POLICY =
      "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self'"
          + " data:; connect-src 'self' blob:; worker-src 'self' blob:; object-src 'none';"
          + " frame-ancestors 'none';";

  public AppProperties {
    if (spa == null) {
      spa = new Spa("file:./static/");
    }
    if (security == null) {
      security = new Security(new Security.Csp(DEFAULT_CSP_POLICY));
    }
  }

  /**
   * SPA static asset serving configuration.
   *
   * <p>Override {@code app.spa.static-location} in a profile-specific properties file, for example
   * {@code application-local.properties}, to point at a different path.
   */
  public record Spa(@DefaultValue("file:./static/") String staticLocation) {}

  /** Security configuration properties, bound under {@code app.security.*}. */
  public record Security(@DefaultValue Security.Csp csp) {

    public Security {
      if (csp == null) {
        csp = new Csp(DEFAULT_CSP_POLICY);
      }
    }

    /**
     * Content Security Policy (CSP) configuration.
     *
     * <p>Override {@code app.security.csp.policy-directives} in application properties or via the
     * environment variable {@code APP_SECURITY_CSP_POLICY_DIRECTIVES}.
     */
    public record Csp(@DefaultValue(DEFAULT_CSP_POLICY) String policyDirectives) {}
  }
}
