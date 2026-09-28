package com.example.helloauth.support;

import com.example.helloauth.service.EmailService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the logging email stub with a recording one.
 *
 * <p>The only production bean any test substitutes. Everything else — the real security filter chain,
 * the real database, the real BCrypt encoder — runs as it does in production, because the behaviours
 * under test are almost all emergent properties of those pieces working together rather than of any
 * single class.
 */
@TestConfiguration
public class TestSupportConfig {

    /**
     * Declared as the concrete type, not as {@link EmailService}, so tests can inject it and read
     * what was captured. It still satisfies every {@code EmailService} injection point.
     */
    @Bean
    @Primary
    public RecordingEmailService recordingEmailService() {
        return new RecordingEmailService();
    }
}
