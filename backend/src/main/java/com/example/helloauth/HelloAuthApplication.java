package com.example.helloauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HelloAuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(HelloAuthApplication.class, args);
    }
}
