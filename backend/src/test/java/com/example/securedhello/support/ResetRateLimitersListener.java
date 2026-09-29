package com.example.securedhello.support;

import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

import com.example.securedhello.ratelimit.RateLimiters;

/**
 * Test fixture: every test method starts with empty rate limiters, even though the application
 * context (and so the in-memory limiters) is cached between tests. Registered for every Spring test
 * in {@code META-INF/spring.factories}.
 */
public class ResetRateLimitersListener implements TestExecutionListener {

	@Override
	public void beforeTestMethod(TestContext testContext) {
		if (testContext.hasApplicationContext()) {
			testContext.getApplicationContext().getBeanProvider(RateLimiters.class).ifAvailable(RateLimiters::resetAll);
		}
	}

}
