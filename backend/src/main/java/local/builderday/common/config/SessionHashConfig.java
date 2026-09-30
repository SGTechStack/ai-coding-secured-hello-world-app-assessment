package local.builderday.common.config;

import local.builderday.common.audit.SessionIds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Derives the Session hash key at startup from the datasource password, so there is no new secret to configure. It
 * rotates with that password. A blank password stops startup.
 */
@Configuration
public class SessionHashConfig {
  @Bean
  SessionIds sessionIds(@Value("${spring.datasource.password:}") String datasourcePassword) {
    var sessionIds = new SessionIds(datasourcePassword);
    SessionIds.install(sessionIds);
    return sessionIds;
  }
}
