package com.example.securedhello;

import static com.example.securedhello.support.LogCapture.field;
import static com.example.securedhello.support.LogCapture.hasField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.securedhello.config.RateLimitProperties;
import com.example.securedhello.support.LogCapture;
import com.example.securedhello.support.MutableClock;
import com.example.securedhello.support.RecordingEmailService;

import tools.jackson.databind.JsonNode;

/**
 * Issue 18: on every public endpoint with a per-address rate limiter, the limiter is acquired before
 * the body is bound and validated. A malformed body therefore spends the caller's quota, and the
 * {@code validation} audit events a burst of malformed bodies can write are bounded by the limiter,
 * not by the number of requests. Addresses are from the documentation ranges.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ MutableClock.Config.class, RecordingEmailService.Config.class })
class RateLimitBeforeValidationApiTest {

	private static final String ADDRESS = "203.0.113.18";

	/** Requests sent beyond the limiter's capacity. */
	private static final int EXTRA = 5;

	@Autowired
	MockMvc mvc;

	@Autowired
	RateLimitProperties limits;

	/**
	 * One rate-limited public endpoint: its path, whether it needs a CSRF token (only
	 * {@code /client-events} is exempt, ADR 0002), a body that fails field validation, and the limit
	 * that must cut a burst off.
	 */
	record Endpoint(String path, boolean csrf, String invalidBody, Function<RateLimitProperties, Integer> capacity) {

		@Override
		public String toString() {
			return path;
		}

	}

	static Stream<Endpoint> endpoints() {
		return Stream.of(
				new Endpoint("/api/client-events", false, "{\"kind\":\"SOMETHING_ELSE\",\"path\":\"/\"}",
						(limits) -> limits.clientEvents().capacity()),
				new Endpoint("/api/register", true, "{\"username\":\"x\",\"email\":\"not-an-email\",\"password\":\"p\"}",
						(limits) -> limits.registration().capacity()),
				new Endpoint("/api/password-reset/request", true, "{\"email\":\"not-an-email\"}",
						(limits) -> limits.resetRequestIp().capacity()),
				new Endpoint("/api/password-reset/confirm", true, "{\"token\":\"\"}",
						(limits) -> limits.resetConfirm().capacity()));
	}

	@ParameterizedTest
	@MethodSource("endpoints")
	void aBurstOfMalformedBodiesIsCutOffByTheLimiter(Endpoint endpoint) throws Exception {
		int capacity = endpoint.capacity().apply(limits);
		LogCapture capture = LogCapture.start();

		for (int n = 0; n < capacity; n++) {
			// Both kinds of malformed body: fields that fail validation, and JSON that does not parse.
			send(endpoint, (n % 2 == 0) ? endpoint.invalidBody() : "{not json")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("validation"));
		}
		for (int n = 0; n < EXTRA; n++) {
			send(endpoint, endpoint.invalidBody()).andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(jsonPath("$.code").value("too_many_requests"))
				.andExpect(jsonPath("$.detail").value("too many requests"));
		}

		assertThat(capture.audit(accessControl("validation"))).hasSize(capacity)
			.allSatisfy((event) -> assertThat(field(event, "url.path")).isEqualTo(endpoint.path()));
		assertThat(capture.audit(accessControl("rate_limited"))).hasSize(EXTRA)
			.allSatisfy((event) -> assertThat(field(event, "source.ip_hash")).matches("[0-9a-f]{64}"));
	}

	private static Predicate<JsonNode> accessControl(String reason) {
		return hasField("event.action", "access-control").and(hasField("event.reason", reason));
	}

	private ResultActions send(Endpoint endpoint, String body) throws Exception {
		MockHttpServletRequestBuilder request = from(post(endpoint.path())).contentType(MediaType.APPLICATION_JSON)
			.content(body);
		if (endpoint.csrf()) {
			MvcResult csrf = mvc.perform(from(get("/api/csrf"))).andExpect(status().isOk()).andReturn();
			request.cookie(csrf.getResponse().getCookie("SESSION"))
				.header("X-CSRF-TOKEN", (String) JsonPath.read(csrf.getResponse().getContentAsString(), "$.token"));
		}
		return mvc.perform(request);
	}

	private static MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder request) {
		return request.with((servletRequest) -> {
			servletRequest.setRemoteAddr(ADDRESS);
			return servletRequest;
		});
	}

}
