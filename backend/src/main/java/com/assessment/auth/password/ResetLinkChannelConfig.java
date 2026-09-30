package com.assessment.auth.password;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Supplies the discarding channel outside {@code dev}.
 *
 * <p>Profile-gated rather than {@code @ConditionalOnMissingBean}: the intent is "there is no place
 * a reset link is rendered in a deployed profile", and a profile condition states that, while a
 * missing-bean condition would quietly accept one if something ever supplied it.
 */
@Configuration
@Profile("!dev")
public class ResetLinkChannelConfig {

  @Bean
  public ResetLinkChannel resetLinkChannel() {
    return ResetLinkChannel.discarding();
  }
}
