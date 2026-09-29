package com.example.securedhello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.web.server.autoconfigure.ServerProperties.ForwardHeadersStrategy;
import org.springframework.context.ConfigurableApplicationContext;

/** Bootstrap tests: separate application contexts with different profiles and properties. */
class StartupConfigurationTest {

	private static final String IP_HASH_KEY = "--app.ip-hash.key=synthetic-startup-test-key";

	/** Synthetic Bootstrap Admin values, required outside dev (see BootstrapAdminTest). */
	private static final String[] BOOTSTRAP_ADMIN = { "--app.bootstrap-admin.username=startupadmin",
			"--app.bootstrap-admin.password=Synthetic-Startup-Pass-42",
			"--app.bootstrap-admin.email=startupadmin@test.example.com" };

	/** Runs the app with the given profile; args override every profile's properties files. */
	private static ConfigurableApplicationContext run(String profile, String... args) {
		String[] all = new String[args.length + 2 + BOOTSTRAP_ADMIN.length];
		all[0] = "--server.port=0";
		all[1] = "--spring.datasource.url=jdbc:h2:mem:startup-" + System.nanoTime();
		System.arraycopy(BOOTSTRAP_ADMIN, 0, all, 2, BOOTSTRAP_ADMIN.length);
		System.arraycopy(args, 0, all, 2 + BOOTSTRAP_ADMIN.length, args.length);
		return new SpringApplicationBuilder(BackendApplication.class).profiles(profile).run(all);
	}

	@Test
	void nonDevStartupFailsWithoutCorsAllowlist() {
		assertThatThrownBy(() -> run("prod", IP_HASH_KEY).close())
			.hasStackTraceContaining("app.cors.allowed-origins must list the frontend origins");
	}

	@Test
	void startupFailsWhenCorsAllowlistContainsWildcard() {
		assertThatThrownBy(() -> run("prod", IP_HASH_KEY, "--app.cors.allowed-origins=*").close())
			.hasStackTraceContaining("must be an exact origin without wildcards or paths");
	}

	@Test
	void nonDevStartupFailsWithoutTheIpHashKey() {
		assertThatThrownBy(() -> run("prod", "--app.cors.allowed-origins=https://app.example.invalid").close())
			.hasStackTraceContaining("app.ip-hash.key must be injected from the secrets manager");
	}

	@Test
	void nonDevStartupSucceedsWithTheIpHashKey() {
		try (ConfigurableApplicationContext context = run("prod",
				"--app.cors.allowed-origins=https://app.example.invalid", IP_HASH_KEY)) {
			assertThat(context.isRunning()).isTrue();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "none", "kubernetes" })
	void forwardedHeadersAreNeverTrustedEvenOnADetectedCloudPlatform(String cloudPlatform) {
		try (ConfigurableApplicationContext context = run("prod",
				"--app.cors.allowed-origins=https://app.example.invalid", IP_HASH_KEY,
				"--spring.main.cloud-platform=" + cloudPlatform)) {
			assertThat(context.getBean(ServerProperties.class).getForwardHeadersStrategy())
				.isEqualTo(ForwardHeadersStrategy.NONE);
		}
	}

	@Test
	void startupFailsWhenTheIpThrottleWindowIsNotPositive() {
		assertThatThrownBy(() -> run("test", "--app.ip-throttle.window=0s").close())
			.hasStackTraceContaining("app.ip-throttle");
	}

	@Test
	void devStartupUsesAFixedLocalIpHashKey() {
		try (ConfigurableApplicationContext context = run("dev")) {
			assertThat(context.getEnvironment().getProperty("app.ip-hash.key")).isNotBlank();
		}
	}

	@Test
	void startupFailsWhenBodyLimitIsTooLargeToBufferInMemory() {
		assertThatThrownBy(() -> run("test", "--app.api.max-request-body-bytes=3000000000").close())
			.hasStackTraceContaining("app.api.max-request-body-bytes");
	}

	@Test
	void devStartupDefaultsCorsAllowlistToLocalSpa() {
		try (ConfigurableApplicationContext context = run("dev")) {
			assertThat(context.getEnvironment().getProperty("app.cors.allowed-origins"))
				.isEqualTo("http://localhost:3000");
		}
	}

}
