package org.eds.demo.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Cache configuration with per-cache TTL.
 *
 * <p>Local profile uses in-memory cache (no TTL, clears on restart). Redis profile uses Redis with
 * configurable per-cache TTL for automatic expiration.
 *
 * <p>TTL resolution: cache-specific property (e.g., {@code app.cache.recommendations.ttl}) falls
 * back to {@code app.cache.default.ttl} if not set.
 */
@Configuration
@EnableCaching
public class CacheConfiguration {

  @Value("${app.cache.default.ttl:PT1H}")
  private Duration defaultTtl;

  @Value("${app.cache.recommendations.ttl:#{null}}")
  private Duration recommendationsTtl;

  @Value("${app.cache.jwks.ttl:#{null}}")
  private Duration jwksTtl;

  /** In-memory cache for local development (no Redis required). */
  @Bean
  @Profile("!feat-redis")
  public CacheManager cacheManager() {
    return new ConcurrentMapCacheManager();
  }

  /** Redis-backed cache with per-cache TTL for dev/qa/preprod/prod environments. */
  @Bean
  @Profile("feat-redis")
  public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
    RedisCacheConfiguration defaults =
        RedisCacheConfiguration.defaultCacheConfig().entryTtl(defaultTtl);

    Map<String, RedisCacheConfiguration> cacheConfigs = new java.util.HashMap<>();
    if (recommendationsTtl != null) {
      cacheConfigs.put("recommendations", defaults.entryTtl(recommendationsTtl));
    }
    if (jwksTtl != null) {
      cacheConfigs.put("jwks", defaults.entryTtl(jwksTtl));
    }

    return RedisCacheManager.builder(connectionFactory)
        .cacheDefaults(defaults)
        .withInitialCacheConfigurations(cacheConfigs)
        .build();
  }
}
