package com.example.securedhello;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * A body sent without Content-Length (chunked transfer encoding) is still size-checked before CSRF,
 * authentication or any controller. Runs against the real embedded server, because MockMvc always
 * derives a Content-Length from the request content.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ChunkedRequestBodyLimitTest {

	@LocalServerPort
	int port;

	private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

	@Test
	void oversizedChunkedBodyIsRejectedWith400BeforeAuthentication() throws Exception {
		CsrfSession csrf = fetchCsrf();

		byte[] oversized = ("{\"padding\":\"" + "x".repeat(20_000) + "\"}").getBytes(StandardCharsets.UTF_8);
		HttpRequest post = HttpRequest.newBuilder(uri("/api/hello"))
			.header("Cookie", csrf.sessionCookie())
			.header("X-CSRF-TOKEN", csrf.token())
			.header("Content-Type", "application/json")
			.POST(chunked(oversized))
			.build();

		HttpResponse<String> response = http.send(post, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
				(type) -> assertThat(type).startsWith("application/problem+json"));
		assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("request_too_large");
	}

	@Test
	void chunkedBodyWithinLimitPassesTheSizeCheck() throws Exception {
		CsrfSession csrf = fetchCsrf();
		HttpRequest post = HttpRequest.newBuilder(uri("/api/hello"))
			.header("Cookie", csrf.sessionCookie())
			.header("X-CSRF-TOKEN", csrf.token())
			.header("Content-Type", "application/json")
			.POST(chunked("{\"a\":1}".getBytes(StandardCharsets.UTF_8)))
			.build();

		HttpResponse<String> response = http.send(post, HttpResponse.BodyHandlers.ofString());

		// Past the size check and CSRF; the Visitor is then refused for lack of authentication.
		assertThat(response.statusCode()).isEqualTo(401);
		assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("authentication_required");
	}

	@Test
	void chunkedFormPostCarryingCsrfOnlyInItsBodyFailsClosed() throws Exception {
		CsrfSession csrf = fetchCsrf();

		// The buffered wrapper replays bytes only; body form fields are not decoded, so the token is unseen.
		HttpResponse<String> response = postChunkedForm(csrf, "application/x-www-form-urlencoded",
				"_csrf=" + URLEncoder.encode(csrf.token(), StandardCharsets.UTF_8));

		assertThat(response.statusCode()).isEqualTo(403);
		assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("csrf_invalid");
	}

	@Test
	void chunkedBodyWithUnknownCharsetIsNotAServerError(CapturedOutput output) throws Exception {
		CsrfSession csrf = fetchCsrf();

		HttpResponse<String> response = postChunkedForm(csrf,
				"application/x-www-form-urlencoded; charset=no-such-charset", "a=%zz");

		assertThat(response.statusCode()).isEqualTo(403);
		assertThat(output.getAll()).doesNotContain(" ERROR ");
	}

	record CsrfSession(String sessionCookie, String token) {
	}

	/** Gets a session cookie and CSRF token the way the SPA does. */
	private CsrfSession fetchCsrf() throws Exception {
		HttpResponse<String> csrf = http.send(HttpRequest.newBuilder(uri("/api/csrf")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		String sessionCookie = csrf.headers()
			.allValues("Set-Cookie")
			.stream()
			.filter((c) -> c.startsWith("SESSION="))
			.map((c) -> c.substring(0, c.indexOf(';')))
			.findFirst()
			.orElseThrow();
		return new CsrfSession(sessionCookie, JsonPath.read(csrf.body(), "$.token"));
	}

	private HttpResponse<String> postChunkedForm(CsrfSession csrf, String contentType, String form)
			throws Exception {
		HttpRequest post = HttpRequest.newBuilder(uri("/api/hello"))
			.header("Cookie", csrf.sessionCookie())
			.header("Content-Type", contentType)
			.POST(chunked(form.getBytes(StandardCharsets.UTF_8)))
			.build();
		return http.send(post, HttpResponse.BodyHandlers.ofString());
	}

	private static HttpRequest.BodyPublisher chunked(byte[] body) {
		// A stream of unknown length is sent chunked, with no Content-Length header.
		return HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body));
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

}
