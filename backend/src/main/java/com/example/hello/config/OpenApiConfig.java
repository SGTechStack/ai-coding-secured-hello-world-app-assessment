package com.example.hello.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata. The docs endpoints themselves are enabled only in the dev profile. */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI openApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Secured Hello World API")
                .version("v1")
                .description(
                    "Session-cookie based authentication reference API. All state-changing "
                        + "endpoints require the CSRF token from GET /api/auth/csrf in the "
                        + "X-CSRF-TOKEN header."));
  }
}
