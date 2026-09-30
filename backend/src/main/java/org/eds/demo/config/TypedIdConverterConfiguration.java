package org.eds.demo.config;

import java.util.UUID;
import org.eds.demo.user.domain.UserId;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class TypedIdConverterConfiguration implements WebMvcConfigurer {

  @Override
  public void addFormatters(FormatterRegistry registry) {
    registry.addConverter(String.class, UserId.class, s -> new UserId(UUID.fromString(s)));
  }
}
