package com.example.auth.config;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables {@code @Async} (used to send password-reset email off the request thread) and copies the
 * caller's MDC -- correlation id, trace id, user id -- onto the worker thread, so async log lines
 * stay correlated with the request that caused them (App-Standards LOG). Boot applies a {@link
 * TaskDecorator} bean to its auto-configured {@code applicationTaskExecutor}.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    public TaskDecorator mdcTaskDecorator() {
        return runnable -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (context == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(context);
                }
                try {
                    runnable.run();
                } finally {
                    if (previous == null) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(previous);
                    }
                }
            };
        };
    }
}
