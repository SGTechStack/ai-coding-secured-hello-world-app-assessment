package com.example.securedhello;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.example.securedhello.support.MutableClock;

import tools.jackson.databind.json.JsonMapper;

/**
 * Sessions and their limits survive a restart: two application contexts, one after the other, on
 * the same file database, driven over real HTTP. Both contexts share one {@link MutableClock}.
 */
class SessionRestartTest {

	private static final String PASSWORD = "Synthetic-Pass-42";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final HttpClient http = HttpClient.newHttpClient();

	@TempDir
	Path databaseDirectory;

	@Test
	void aSessionStaysValidAcrossARestartAndKeepsItsIdleLimit() throws Exception {
		String session;
		try (ConfigurableApplicationContext first = start()) {
			String base = baseUrl(first);
			Csrf csrf = csrf(base, null);
			assertThat(postJson(base + "/api/register", csrf,
					Map.of("username", "testuser123", "email", "testuser123@test.example.com", "password", PASSWORD))
				.statusCode()).isEqualTo(201);
			HttpResponse<String> login = postJson(base + "/api/login", csrf,
					Map.of("username", "testuser123", "password", PASSWORD));
			assertThat(login.statusCode()).isEqualTo(200);
			session = sessionCookie(login);
			assertThat(me(base, session).statusCode()).isEqualTo(200);
		}

		try (ConfigurableApplicationContext second = start()) {
			String base = baseUrl(second);
			assertThat(me(base, session).statusCode()).isEqualTo(200);

			second.getBean(MutableClock.class).advance(Duration.ofMinutes(15));

			assertThat(me(base, session).statusCode()).isEqualTo(401);
		}
	}

	private ConfigurableApplicationContext start() {
		return new SpringApplicationBuilder(BackendApplication.class, SharedClock.class).profiles("test")
			.run("--server.port=0",
					"--spring.datasource.url=jdbc:h2:file:" + databaseDirectory.resolve("sessions") + ";DB_CLOSE_ON_EXIT=FALSE");
	}

	/** One clock for both contexts, so time carries across the restart. */
	@Configuration(proxyBeanMethods = false)
	static class SharedClock {

		private static final MutableClock CLOCK = new MutableClock();

		@Bean
		@Primary
		MutableClock mutableClock() {
			return CLOCK;
		}

	}

	private static String baseUrl(ConfigurableApplicationContext context) {
		return "http://localhost:" + ((WebServerApplicationContext) context).getWebServer().getPort();
	}

	record Csrf(String session, String token) {
	}

	private Csrf csrf(String base, String session) throws IOException, InterruptedException {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + "/api/csrf"));
		if (session != null) {
			request.header("Cookie", session);
		}
		HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(200);
		String issued = sessionCookie(response);
		return new Csrf((issued != null) ? issued : session, JsonPath.read(response.body(), "$.token"));
	}

	private HttpResponse<String> postJson(String url, Csrf csrf, Map<String, String> body)
			throws IOException, InterruptedException {
		return http.send(HttpRequest.newBuilder(URI.create(url))
			.header("Cookie", csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
			.build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> me(String base, String session) throws IOException, InterruptedException {
		return http.send(HttpRequest.newBuilder(URI.create(base + "/api/me")).header("Cookie", session).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	/** The {@code SESSION=<value>} pair from a response's Set-Cookie header, if any. */
	private static String sessionCookie(HttpResponse<String> response) {
		return response.headers()
			.allValues("Set-Cookie")
			.stream()
			.filter((cookie) -> cookie.startsWith("SESSION="))
			.map((cookie) -> cookie.substring(0, cookie.indexOf(';')))
			.findFirst()
			.orElse(null);
	}

}
