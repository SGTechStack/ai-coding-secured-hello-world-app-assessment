package org.eds.demo.config;

import java.util.Arrays;
import java.util.Set;
import org.eds.demo.common.exception.UnsafeProfileException;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

/**
 * Prevents unsafe feature profiles from running in production-like environments.
 *
 * <p>Profiles prefixed with unsafe- are intended for local, test, or development use only. This
 * guard fails startup if one is accidentally combined with qa, preprod, or prod.
 */
@Configuration
public class SecurityProfileValidator {

  private static final Set<String> PROD_LIKE_PROFILES = Set.of("qa", "preprod", "prod");

  @EventListener(ContextRefreshedEvent.class)
  public void validate(ContextRefreshedEvent event) {
    Environment env = event.getApplicationContext().getEnvironment();
    Set<String> activeProfiles = Set.copyOf(Arrays.asList(env.getActiveProfiles()));

    boolean isProdLike = activeProfiles.stream().anyMatch(PROD_LIKE_PROFILES::contains);
    boolean hasUnsafe = activeProfiles.stream().anyMatch(p -> p.startsWith("unsafe-"));

    if (isProdLike && hasUnsafe) {
      throw new UnsafeProfileException(
          "CRITICAL: Application cannot start. Unsafe profiles detected in prod env!");
    }
  }
}
