package com.example.helloauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
// Ticket-14 token housekeeping: the password-reset janitor's @Scheduled purge.
@EnableScheduling
public class HelloAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelloAuthApplication.class, args);
    }
}
