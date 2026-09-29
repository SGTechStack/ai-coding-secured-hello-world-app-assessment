package com.example.securedhello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import jakarta.servlet.RequestDispatcher;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every error the API can return is a ProblemDetail with a {@code code}, and unexpected failures never
 * expose internals. Uses test-only endpoints behind their own permissive chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ErrorResponseApiTest.FailingEndpoint.class)
class ErrorResponseApiTest {

	private static final String LEAKY_MESSAGE = "SELECT password_hash FROM users WHERE username = 'testuser123'";

	@Autowired
	MockMvc mvc;

	@Test
	void unexpectedExceptionReturnsGeneric500WithoutInternals() throws Exception {
		String body = mvc.perform(get("/api/test-only/failure"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(500))
			.andExpect(jsonPath("$.code").value("internal_error"))
			.andExpect(jsonPath("$.detail").isNotEmpty())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(body).doesNotContain(LEAKY_MESSAGE)
			.doesNotContain("SELECT")
			.doesNotContain("IllegalStateException")
			.doesNotContain("java.")
			.doesNotContain("com.example")
			.doesNotContain("\tat ");
	}

	@Test
	void accessDeniedExceptionIsLeftToSpringSecurityNotTurnedInto500() throws Exception {
		mvc.perform(get("/api/test-only/access-denied").with(user("testuser123")))
			.andExpect(status().isForbidden());
	}

	@Test
	void authenticationExceptionIsLeftToSpringSecurityNotTurnedInto500() throws Exception {
		mvc.perform(get("/api/test-only/authentication-failure")).andExpect(status().isUnauthorized());
	}

	@Test
	void malformedJsonBodyIsA400Validation() throws Exception {
		mvc.perform(post("/api/test-only/echo").contentType(MediaType.APPLICATION_JSON).content("{not json"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("validation"));
	}

	@Test
	void unknownPathIsA404NotFound() throws Exception {
		mvc.perform(get("/api/test-only/no-such-endpoint"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("not_found"));
	}

	@Test
	void wrongMethodIsA405MethodNotAllowed() throws Exception {
		mvc.perform(post("/api/test-only/failure"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.code").value("method_not_allowed"));
	}

	@Test
	void unsupportedContentTypeIsA415() throws Exception {
		mvc.perform(post("/api/test-only/echo").contentType(MediaType.TEXT_PLAIN).content("hello"))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.code").value("unsupported_media_type"));
	}

	@Test
	void unacceptableResponseTypeIsA406WithGenericCode() throws Exception {
		mvc.perform(post("/api/test-only/echo").contentType(MediaType.APPLICATION_JSON)
			.content("{\"a\":1}")
			.accept(MediaType.IMAGE_PNG)).andExpect(status().isNotAcceptable());
	}

	/** Failures outside Spring MVC (for example in a servlet filter) are forwarded to /error. */
	@Test
	void containerErrorDispatchIsAProblemDetailWithoutInternals() throws Exception {
		mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
			.requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException(LEAKY_MESSAGE)))
			.andExpect(status().isInternalServerError())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("internal_error"))
			.andExpect(content().string(not(containsString("SELECT"))));

		mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation"));

		mvc.perform(get("/error")).andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("internal_error"));
	}

	/** A test-only endpoint whose failure carries internals that must never reach the client. */
	@TestConfiguration
	static class FailingEndpoint {

		@Bean
		@Order(0)
		SecurityFilterChain testOnlyChain(HttpSecurity http) throws Exception {
			return http.securityMatcher("/api/test-only/**", "/error")
				.csrf((csrf) -> csrf.disable())
				.authorizeHttpRequests((auth) -> auth.anyRequest().permitAll())
				.exceptionHandling((ex) -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.build();
		}

		@Bean
		FailingController failingController() {
			return new FailingController();
		}

	}

	@RestController
	static class FailingController {

		@GetMapping("/api/test-only/failure")
		String fail() {
			throw new IllegalStateException(LEAKY_MESSAGE);
		}

		@PostMapping(path = "/api/test-only/echo", consumes = MediaType.APPLICATION_JSON_VALUE,
				produces = MediaType.APPLICATION_JSON_VALUE)
		Map<String, Object> echo(@RequestBody Map<String, Object> body) {
			return body;
		}

		@GetMapping("/api/test-only/access-denied")
		String accessDenied() {
			throw new AccessDeniedException("denied inside a controller");
		}

		@GetMapping("/api/test-only/authentication-failure")
		String authenticationFailure() {
			throw new BadCredentialsException("authentication failed inside a controller");
		}

	}

}
