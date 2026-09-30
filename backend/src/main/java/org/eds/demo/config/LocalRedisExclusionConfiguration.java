package org.eds.demo.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Disables Redis auto-configuration unless the Redis feature profile is active.
 *
 * <p>The Redis starter is on the classpath for deployments that enable the feat-redis profile,
 * where Redis backs the cache manager. Keeping the exclusions here makes other profiles independent
 * from a running Redis instance. HTTP sessions are stored in the database, not Redis
 * (ADR-DEMO-BE-0001).
 */
@Configuration
@Profile({"!feat-redis"})
@EnableAutoConfiguration(
    exclude = {DataRedisAutoConfiguration.class, DataRedisRepositoriesAutoConfiguration.class})
class LocalRedisExclusionConfiguration {}
