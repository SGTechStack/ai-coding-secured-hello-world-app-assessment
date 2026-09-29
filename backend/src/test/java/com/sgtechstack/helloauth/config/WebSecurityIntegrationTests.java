package com.sgtechstack.helloauth.config;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import com.sgtechstack.helloauth.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

/**
 * Cross-cutting web security: CORS allow-list, response headers, default-deny routing.
 */
class WebSecurityIntegrationTests extends IntegrationTest {

	private static final String FRONTEND = "http://localhost:3000";

	@Test
	void corsPreflightAllowsTheFrontendOriginWithCredentials() throws Exception {
		MockHttpServletResponse response = this.mockMvc
			.perform(options("/api/auth/login").header(HttpHeaders.ORIGIN, FRONTEND)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-csrf-token"))
			.andReturn()
			.getResponse();

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(FRONTEND);
		assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
	}

	@Test
	void corsRejectsOtherOrigins() throws Exception {
		MockHttpServletResponse preflight = this.mockMvc
			.perform(options("/api/auth/login").header(HttpHeaders.ORIGIN, "https://evil.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
			.andReturn()
			.getResponse();
		MockHttpServletResponse csrfRead = this.mockMvc
			.perform(get("/api/auth/csrf").header(HttpHeaders.ORIGIN, "https://evil.example"))
			.andReturn()
			.getResponse();

		assertThat(preflight.getStatus()).isEqualTo(403);
		assertThat(preflight.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
		assertThat(csrfRead.getStatus()).isEqualTo(403);
		assertThat(csrfRead.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
	}

	@Test
	void responsesCarrySecurityHeaders() {
		MockHttpServletResponse response = newClient().get("/api/auth/csrf");

		assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
		assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
		assertThat(response.getHeader("Content-Security-Policy")).isEqualTo("default-src 'none'; frame-ancestors 'none'");
		assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
		assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
	}

	@Test
	void unmappedAndInternalPathsAreDenied() {
		var client = newClient();

		assertProblem(client.get("/api/does-not-exist"), 401, "UNAUTHENTICATED");
		assertProblem(client.get("/h2-console"), 401, "UNAUTHENTICATED");
		assertProblem(client.get("/actuator/env"), 401, "UNAUTHENTICATED");
	}

	@Test
	void healthCheckIsPublicAndTerse() {
		MockHttpServletResponse response = newClient().get("/actuator/health");

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.getContentAsByteArray()).asString()
			.contains("\"status\":\"UP\"")
			.doesNotContain("components")
			.doesNotContain("details");
	}

	@Test
	void anonymousRequestsDoNotCreateSessions() {
		MockHttpServletResponse response = newClient().get("/api/hello");

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
	}

}
