package com.example.auth.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the production LoggingEmailService with a recording double in tests. */
@TestConfiguration
public class EmailTestConfig {

    // Concrete return type so tests can autowire RecordingEmailService directly;
    // @Primary makes it win over LoggingEmailService for EmailService injection points.
    @Bean
    @Primary
    RecordingEmailService recordingEmailService() {
        return new RecordingEmailService();
    }
}
