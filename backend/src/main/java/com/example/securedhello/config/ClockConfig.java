package com.example.securedhello.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one clock every time-based decision reads (lockout and Reset Token expiry, Session timeouts,
 * IP Throttle windows). Tests replace this bean to move time.
 */
@Configuration
class ClockConfig {

	static final ZoneId ZONE = ZoneId.of("Asia/Singapore");

	@Bean
	Clock clock() {
		return Clock.system(ZONE);
	}

}
