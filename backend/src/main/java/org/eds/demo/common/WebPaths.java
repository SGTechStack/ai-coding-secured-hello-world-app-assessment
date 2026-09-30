package org.eds.demo.common;

/**
 * URL paths that more than one class must agree on: the SPA shell and its built files, the error
 * page, the REST prefix and the change-password call. Owned here, below both the controllers and
 * the security configuration, so neither has to import the other to share a path.
 */
public final class WebPaths {

  /** Site root; redirects to the SPA or the sign-in page. */
  public static final String ROOT = "/";

  /** The single HTML page of the built SPA; every {@code /app/**} route is forwarded to it. */
  public static final String INDEX_HTML = "/index.html";

  /** Folder of the SPA's built JS/CSS/fonts, served as static files. */
  public static final String ASSETS = "/assets";

  /** Everything under {@link #ASSETS}, as a request matcher. */
  public static final String ASSETS_PATTERN = ASSETS + "/**";

  /** Servlet error dispatch target and public error page. */
  public static final String ERROR = "/error";

  /** All REST endpoints for the SPA. */
  public static final String API_PATTERN = "/api/**";

  /**
   * Change-password for the signed-in holder. Named here so the forced-change filter can let this
   * one call through without depending on the controller.
   */
  public static final String CHANGE_PASSWORD = "/api/v1/me/password";

  private WebPaths() {}
}
