package com.sgtechstack.helloauth.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableAsync
@EnableScheduling
public class InfrastructureConfig {

	/** Injected wherever time matters (lockout, token expiry) so tests can control it. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
