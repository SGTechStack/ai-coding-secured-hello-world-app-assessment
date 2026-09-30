package org.eds.demo.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfiguration {

  @Value("${rest.client.connect-timeout-seconds:30}")
  private int connectTimeoutSeconds;

  @Value("${rest.client.read-timeout-seconds:30}")
  private int readTimeoutSeconds;

  @Bean
  RestClient restClient() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
    factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
    return RestClient.builder().requestFactory(factory).build();
  }
}
