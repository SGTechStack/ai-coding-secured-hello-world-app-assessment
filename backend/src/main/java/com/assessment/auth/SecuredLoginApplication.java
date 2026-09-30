package com.assessment.auth;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Secured login app.
 *
 * <p>Built to the locked plan in {@code .scratch/secured-login-app/handoff/spec.md}. Section
 * references in comments throughout this codebase ("spec.md S4") point at that document.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SecuredLoginApplication {

  /**
   * Pinned explicitly (spec.md S1). Log lines render at UTC+8; every <em>stored</em> timestamp
   * stays UTC via {@code hibernate.jdbc.time_zone} and the {@code Clock} bean, so the two never
   * drift into each other.
   */
  private static final String DISPLAY_TIME_ZONE = "Asia/Singapore";

  public static void main(String[] args) {
    TimeZone.setDefault(TimeZone.getTimeZone(DISPLAY_TIME_ZONE));
    SpringApplication.run(SecuredLoginApplication.class, args);
  }
}
