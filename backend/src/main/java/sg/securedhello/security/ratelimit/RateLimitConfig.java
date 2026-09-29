package sg.securedhello.security.ratelimit;

import com.github.benmanes.caffeine.cache.Ticker;

import io.github.bucket4j.TimeMeter;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The limiter and its budget table. Its two filters are not beans, so the servlet container never registers them
 * outside the security chain; {@code SecurityConfig} builds them in their slots.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({RateLimitProperties.class, RequestBodyProperties.class})
public class RateLimitConfig {

    @Bean
    AuthRateLimiter authRateLimiter(RateLimitProperties properties, TimeMeter timeMeter, Ticker ticker) {
        return new AuthRateLimiter(properties, timeMeter, ticker);
    }
}
