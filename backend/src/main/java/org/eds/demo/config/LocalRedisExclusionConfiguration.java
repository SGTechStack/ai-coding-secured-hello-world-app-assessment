package org.eds.demo.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Disables Redis auto-configuration unless the Redis feature profile is active.
 *
 * <p>The Redis session starter is on the classpath for deployments that enable the feat-redis
 * profile. Keeping the exclusions here makes other profiles independent from a running Redis
 * instance while preserving Redis-backed sessions for environments that opt in through feat-redis.
 */
@Configuration
@Profile({"!feat-redis"})
@EnableAutoConfiguration(
    exclude = {
      SessionDataRedisAutoConfiguration.class,
      DataRedisAutoConfiguration.class,
      DataRedisRepositoriesAutoConfiguration.class
    })
class LocalRedisExclusionConfiguration {}
