package com.example.demo_app.security;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One {@link IpThrottle} per throttled endpoint, each with its own counters and {@link
 * ThrottleProperties} limit. Inject one by its bean name, e.g. {@code @Qualifier("loginThrottle")}.
 */
@Configuration
@EnableConfigurationProperties(ThrottleProperties.class)
class ThrottleConfig {

  /** Failed logins per IP; {@code AuthController} checks it before authenticating. */
  @Bean
  IpThrottle loginThrottle(ThrottleProperties properties, Clock clock) {
    return new IpThrottle(properties.login(), properties.maxTrackedIps(), clock);
  }

  /** Every registration request per IP; {@code RegistrationController} acquires it first. */
  @Bean
  IpThrottle registrationThrottle(ThrottleProperties properties, Clock clock) {
    return new IpThrottle(properties.registration(), properties.maxTrackedIps(), clock);
  }
}
