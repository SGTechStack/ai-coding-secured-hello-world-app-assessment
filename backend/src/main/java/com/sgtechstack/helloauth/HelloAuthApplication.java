package com.sgtechstack.helloauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// UserDetailsServiceAutoConfiguration is excluded so Boot does not create its default
// in-memory "user" account with a generated password; accounts live only in the users table.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class HelloAuthApplication {

	public static void main(String[] args) {
		SpringApplication.run(HelloAuthApplication.class, args);
	}

}
