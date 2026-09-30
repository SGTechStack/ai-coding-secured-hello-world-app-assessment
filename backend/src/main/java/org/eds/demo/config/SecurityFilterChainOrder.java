package org.eds.demo.config;

import org.springframework.core.Ordered;

/**
 * Canonical ordering constants for {@link org.springframework.security.web.SecurityFilterChain}
 * beans. Gaps of 10 leave room to insert chains between existing ones without renumbering.
 *
 * <p>The SPA catch-all chain in {@link SecurityConfiguration} carries no {@code @Order} annotation
 * and therefore runs last (lowest precedence).
 */
public final class SecurityFilterChainOrder {

  public static final int ACTUATOR = Ordered.HIGHEST_PRECEDENCE;
  public static final int API = Ordered.HIGHEST_PRECEDENCE + 10;
  public static final int ADMIN_API = Ordered.HIGHEST_PRECEDENCE + 12;
  public static final int INTERNAL = Ordered.HIGHEST_PRECEDENCE + 15;
  public static final int WELL_KNOWN = Ordered.HIGHEST_PRECEDENCE + 18;
  public static final int OPEN_API = Ordered.HIGHEST_PRECEDENCE + 20;
  public static final int H2_CONSOLE = Ordered.HIGHEST_PRECEDENCE + 30;

  private SecurityFilterChainOrder() {}
}
