package com.example.securedhello.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.example.securedhello.logging.MdcCopyingTaskDecorator;

/**
 * The background executor for password-reset issuance and its email, so {@code POST
 * /password-reset/request} can return 202 before any lookup: response time never depends on whether
 * the Account exists. Its {@link MdcCopyingTaskDecorator} keeps the queued work's log lines
 * correlated with the request that queued it.
 */
@Configuration
class PasswordResetExecutorConfig {

	static final String BEAN_NAME = "passwordResetExecutor";

	@Bean(BEAN_NAME)
	Executor passwordResetExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(200);
		executor.setThreadNamePrefix("password-reset-");
		executor.setTaskDecorator(new MdcCopyingTaskDecorator());
		executor.initialize();
		return executor;
	}

}
