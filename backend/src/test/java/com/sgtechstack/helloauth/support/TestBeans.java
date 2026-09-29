package com.sgtechstack.helloauth.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the system clock and the email stub with test doubles.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestBeans {

	@Bean
	@Primary
	MutableClock testClock() {
		return new MutableClock();
	}

	@Bean
	@Primary
	RecordingEmailService recordingEmailService() {
		return new RecordingEmailService();
	}

}
