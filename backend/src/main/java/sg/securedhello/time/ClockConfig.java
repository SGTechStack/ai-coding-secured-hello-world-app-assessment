package sg.securedhello.time;

import java.time.Clock;

import com.github.benmanes.caffeine.cache.Ticker;

import io.github.bucket4j.TimeMeter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one source of time for main code (ADR-066), and the two library clocks built from it ({@link ClockTimes}). Tests
 * replace the {@code clock} bean with a forward-only mutable clock, and the ticker and time meter follow it (T-RL-018).
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    Ticker ticker(Clock clock) {
        return ClockTimes.ticker(clock);
    }

    @Bean
    TimeMeter timeMeter(Clock clock) {
        return ClockTimes.timeMeter(clock);
    }
}
