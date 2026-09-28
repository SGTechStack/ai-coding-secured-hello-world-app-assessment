package com.assessment.securedhelloworld;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point. See {@code /ARCHITECTURE.md} for the full
 * system design.
 */
@SpringBootApplication
@EnableScheduling
public class SecuredHelloWorldApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecuredHelloWorldApplication.class, args);
    }
}
