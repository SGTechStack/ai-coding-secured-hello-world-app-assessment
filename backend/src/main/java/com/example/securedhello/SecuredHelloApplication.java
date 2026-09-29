package com.example.securedhello;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.example.securedhello.config.AdminProperties;
import com.example.securedhello.config.SecurityProperties;

/**
 * Entry point for the Secured Hello World backend.
 */
@SpringBootApplication
@EnableConfigurationProperties({SecurityProperties.class, AdminProperties.class})
public class SecuredHelloApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecuredHelloApplication.class, args);
    }
}
