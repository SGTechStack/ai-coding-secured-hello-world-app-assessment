package com.example.securedhello.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * A chunked body within the limit reaches the controller intact, whether it is read as a stream or
 * through {@code getReader()}. The wrapper replays bytes only; it does not decode form fields. No
 * production endpoint reads a body yet, so test-only echo endpoints sit behind a test chain that
 * installs the same body-limit filter. Runs on the real server, because MockMvc cannot send chunked
 * bodies.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(BufferedChunkedBodyDeliveryTest.EchoEndpoints.class)
class BufferedChunkedBodyDeliveryTest {

	@LocalServerPort
	int port;

	private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

	@Test
	void chunkedJsonBodyIsDeliveredThroughTheInputStream() throws Exception {
		HttpResponse<String> response = postChunked("/api/test-only/echo-json", "application/json",
				"{\"greeting\":\"hello\"}".getBytes(StandardCharsets.UTF_8));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo("{\"greeting\":\"hello\"}");
	}

	@Test
	void chunkedTextBodyIsDeliveredThroughTheReader() throws Exception {
		HttpResponse<String> response = postChunked("/api/test-only/echo-reader", "text/plain; charset=UTF-8",
				"héllo".getBytes(StandardCharsets.UTF_8));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo("héllo");
	}

	@Test
	void unknownCharsetFallsBackToUtf8ForTheReader() throws Exception {
		HttpResponse<String> response = postChunked("/api/test-only/echo-reader", "text/plain; charset=no-such-charset",
				"héllo".getBytes(StandardCharsets.UTF_8));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo("héllo");
	}

	@Test
	void emptyChunkedBodyIsDeliveredAsEmpty() throws Exception {
		HttpResponse<String> response = postChunked("/api/test-only/echo-reader", null, new byte[0]);

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEmpty();
	}

	private HttpResponse<String> postChunked(String path, String contentType, byte[] body)
			throws IOException, InterruptedException {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
			// A stream of unknown length is sent chunked, with no Content-Length header.
			.POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)));
		if (contentType != null) {
			request.header("Content-Type", contentType);
		}
		return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
	}

	@TestConfiguration
	static class EchoEndpoints {

		@Bean
		@Order(0)
		SecurityFilterChain echoChain(HttpSecurity http) throws Exception {
			return http.securityMatcher("/api/test-only/**")
				.addFilterBefore(new RequestBodyLimitFilter(16_384), CsrfFilter.class)
				.csrf((csrf) -> csrf.disable())
				.authorizeHttpRequests((auth) -> auth.anyRequest().permitAll())
				.build();
		}

		@Bean
		EchoController echoController() {
			return new EchoController();
		}

	}

	@RestController
	static class EchoController {

		@PostMapping(path = "/api/test-only/echo-json", produces = "application/json")
		Map<String, Object> json(@RequestBody Map<String, Object> body) {
			return body;
		}

		@PostMapping(path = "/api/test-only/echo-reader", produces = "text/plain;charset=UTF-8")
		String reader(HttpServletRequest request) throws IOException {
			return request.getReader().lines().collect(Collectors.joining("\n"));
		}

	}

}
