package com.example.securedhello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Operations (story 111): Actuator answers only on the management port, which a deployment never
 * routes publicly, and only with {@code health} (status, no details) and {@code prometheus}. The API
 * port serves no Actuator endpoint. Runs against real embedded servers on two ports, because that
 * separation is the whole point and MockMvc has no port. {@code prometheus} also needs the scraper's
 * HTTP Basic credential (IM8 as-13), so the loopback binding is not its only protection; {@code health}
 * stays open for probes, since it answers with the status alone.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ManagementEndpointsTest {

	@LocalServerPort
	int apiPort;

	@LocalManagementPort
	int managementPort;

	@Autowired
	JdbcTemplate jdbc;

	@Value("${app.management.prometheus.username}")
	String scrapeUsername;

	@Value("${app.management.prometheus.password}")
	String scrapePassword;

	private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

	@ParameterizedTest
	@ValueSource(strings = { "/actuator", "/actuator/health", "/actuator/prometheus", "/actuator/env" })
	void theApiPortServesNoActuatorEndpoint(String path) throws Exception {
		HttpResponse<String> response = get(this.apiPort, path);

		// The API chain denies every path it does not name, so nothing here even reveals itself.
		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.body()).doesNotContain("\"UP\"").doesNotContain("http_server_requests");
	}

	@Test
	void theManagementPortIsBoundToLoopbackOnly() throws Exception {
		assertThat(get(this.managementPort, "/actuator/health").statusCode()).isEqualTo(200);

		// Off-host reachability is what a loopback binding removes; a non-loopback local address stands
		// in for that here, since a test cannot dial this host from outside it.
		Optional<Inet4Address> routable = routableLocalAddress();
		assumeThat(routable).as("this host has a non-loopback IPv4 address to try").isPresent();
		String address = routable.get().getHostAddress();

		// The API binds every interface, so this proves the address is dialable. Without it a refusal
		// below could just as well mean a firewall, and a dropped packet would time out — also an
		// IOException — so the differential is what makes the assertion mean "bound to loopback only".
		assertThat(offHost(address, this.apiPort, "/api/csrf").statusCode()).isEqualTo(200);

		assertThatThrownBy(() -> offHost(address, this.managementPort, "/actuator/health"))
			.isInstanceOf(IOException.class);
	}

	@Test
	void aScrapeCreatesNoSession() throws Exception {
		int before = sessionRows();

		HttpResponse<String> response = scrapeResponse(this.scrapeUsername, this.scrapePassword);

		// The management chain is stateless, and nothing on it touches the CSRF token repository, which
		// is what would call getSession() and persist a Spring Session row for an anonymous caller.
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
		assertThat(sessionRows()).isEqualTo(before);
	}

	@Test
	void theApiPortsPublicPathsAreUnchanged() throws Exception {
		assertThat(get(this.apiPort, "/api/csrf").statusCode()).isEqualTo(200);
		assertThat(get(this.apiPort, "/api/hello").statusCode()).isEqualTo(401);
	}

	@Test
	void prometheusRefusesAScrapeWithoutCredentials() throws Exception {
		HttpResponse<String> response = get(this.managementPort, "/actuator/prometheus");

		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(
				(challenge) -> assertThat(challenge).startsWith("Basic"));
		assertThat(response.body()).doesNotContain("http_server_requests").doesNotContain("jvm_");
		assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
	}

	@Test
	void prometheusRefusesAScrapeWithTheWrongCredentials() throws Exception {
		assertThat(scrapeResponse(this.scrapeUsername, this.scrapePassword + "x").statusCode()).isEqualTo(401);
		assertThat(scrapeResponse("someone-else", this.scrapePassword).statusCode()).isEqualTo(401);
	}

	@Test
	void healthOnTheManagementPortReturnsTheStatusAndNothingElse() throws Exception {
		HttpResponse<String> response = get(this.managementPort, "/actuator/health");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
	}

	@ParameterizedTest
	@ValueSource(strings = { "/actuator", "/actuator/env", "/actuator/beans", "/actuator/metrics", "/actuator/info",
			"/actuator/loggers", "/actuator/configprops", "/actuator/heapdump", "/actuator/threaddump",
			"/actuator/health/liveness", "/actuator/health/readiness" })
	void nothingElseIsExposedOnTheManagementPort(String path) throws Exception {
		// Not even the links page: the management port answers on exactly two paths.
		assertThat(get(this.managementPort, path).statusCode()).isEqualTo(404);
	}

	@Test
	void prometheusExposesRequestLatencyTrafficAndErrorsAfterARequest() throws Exception {
		get(this.apiPort, "/api/csrf");
		get(this.apiPort, "/api/hello");

		String scrape = scrape();

		// http.server.requests carries traffic (count), latency (sum, max, buckets) and errors (status).
		assertThat(scrape).contains("http_server_requests_seconds_count")
			.contains("http_server_requests_seconds_sum")
			.contains("http_server_requests_seconds_bucket")
			.contains("uri=\"/api/csrf\"")
			.contains("status=\"200\"")
			.contains("status=\"401\"");
	}

	@Test
	void aReportNeedsNoCsrfTokenAndLeavesNoSessionBehind() throws Exception {
		int before = sessionRows();

		HttpResponse<String> response = report("{\"kind\":\"RENDER_ERROR\",\"path\":\"/login\"}");

		// Bootstrapping a CSRF token is what would persist a Session for every Visitor and crawler.
		assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
		assertThat(sessionRows()).isEqualTo(before);
	}

	@Test
	void prometheusExposesWhatTheSpaReportsAboutItself() throws Exception {
		report("{\"kind\":\"RENDER_ERROR\",\"path\":\"/login\"}");
		report("{\"kind\":\"PAGE_LOAD\",\"path\":\"/login\",\"durationMs\":1500}");

		String scrape = scrape();

		assertThat(scrape).contains("app_client_errors_total{kind=\"RENDER_ERROR\"}")
			.contains("app_client_performance_seconds_count{kind=\"PAGE_LOAD\"}")
			.contains("app_client_performance_seconds_sum{kind=\"PAGE_LOAD\"}");
	}

	@Test
	void prometheusExposesJvmAndConnectionPoolSaturation() throws Exception {
		String scrape = scrape();

		assertThat(scrape).contains("jvm_memory_used_bytes")
			.contains("jvm_threads_live_threads")
			// Connection-pool saturation: how many connections are in use, idle and waited for.
			.contains("hikaricp_connections_active")
			.contains("hikaricp_connections_pending")
			.contains("hikaricp_connections_max");
	}

	private String scrape() throws Exception {
		HttpResponse<String> response = scrapeResponse(this.scrapeUsername, this.scrapePassword);
		assertThat(response.statusCode()).isEqualTo(200);
		return response.body();
	}

	/** A scrape as Prometheus sends it, with HTTP Basic credentials. */
	private HttpResponse<String> scrapeResponse(String username, String password) throws Exception {
		String basic = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
		HttpRequest request = HttpRequest.newBuilder(uri(this.managementPort, "/actuator/prometheus"))
			.header("Authorization", "Basic " + basic)
			.GET()
			.build();
		return this.http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
	}

	private HttpResponse<String> get(int port, String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(uri(port, path)).GET().build();
		return this.http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
	}

	/** Posts a report exactly as the SPA does: no cookie, no CSRF token (ADR 0002). */
	private HttpResponse<String> report(String body) throws Exception {
		HttpRequest post = HttpRequest.newBuilder(uri(this.apiPort, "/api/client-events"))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
			.build();

		HttpResponse<String> response = this.http.send(post, HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(204);
		return response;
	}

	/** Rows Spring Session has persisted. A report must never add one (ADR 0002). */
	private int sessionRows() {
		return this.jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class);
	}

	/** Dials this host by a non-loopback address of its own, which only a server bound to it will answer. */
	private HttpResponse<Void> offHost(String address, int port, String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + address + ":" + port + path))
			.timeout(Duration.ofSeconds(5))
			.GET()
			.build();
		return this.http.send(request, HttpResponse.BodyHandlers.discarding());
	}

	/** An IPv4 address of this host that is not loopback; IPv6 is skipped because a scoped address has no URI form. */
	private static Optional<Inet4Address> routableLocalAddress() throws SocketException {
		return NetworkInterface.networkInterfaces()
			.flatMap(NetworkInterface::inetAddresses)
			.filter(Inet4Address.class::isInstance)
			.map(Inet4Address.class::cast)
			.filter((address) -> !address.isLoopbackAddress() && !address.isLinkLocalAddress()
					&& !address.isAnyLocalAddress())
			.findFirst();
	}

	/** The loopback address by literal, because the management server binds only that (never a name). */
	private static URI uri(int port, String path) {
		return URI.create("http://127.0.0.1:" + port + path);
	}

}
