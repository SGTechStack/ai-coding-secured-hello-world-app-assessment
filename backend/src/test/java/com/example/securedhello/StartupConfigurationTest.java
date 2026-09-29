package com.example.securedhello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** Bootstrap tests: separate application contexts with different profiles and properties. */
class StartupConfigurationTest {

	/** Runs the app with the given profile; args override every profile's properties files. */
	private static ConfigurableApplicationContext run(String profile, String... args) {
		String[] all = new String[args.length + 2];
		all[0] = "--server.port=0";
		all[1] = "--spring.datasource.url=jdbc:h2:mem:startup-" + System.nanoTime();
		System.arraycopy(args, 0, all, 2, args.length);
		return new SpringApplicationBuilder(BackendApplication.class).profiles(profile).run(all);
	}

	@Test
	void nonDevStartupFailsWithoutCorsAllowlist() {
		assertThatThrownBy(() -> run("prod").close())
			.hasStackTraceContaining("app.cors.allowed-origins must list the frontend origins");
	}

	@Test
	void startupFailsWhenCorsAllowlistContainsWildcard() {
		assertThatThrownBy(() -> run("prod", "--app.cors.allowed-origins=*").close())
			.hasStackTraceContaining("must be an exact origin without wildcards or paths");
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
