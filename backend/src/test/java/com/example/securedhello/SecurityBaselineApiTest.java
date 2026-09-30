package com.example.securedhello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.util.List;
import java.util.Locale;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityBaselineApiTest {

	@Autowired
	MockMvc mvc;

	@Test
	void visitorCallingHelloGets401ProblemDetail() throws Exception {
		mvc.perform(get("/api/hello"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.code").value("authentication_required"));
	}

	@Test
	void csrfEndpointReturnsTokenUncachedAndSetsNoCsrfCookie() throws Exception {
		MvcResult result = mvc.perform(get("/api/csrf"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
			.andExpect(jsonPath("$.token").isNotEmpty())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
			.andReturn();

		List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
		assertThat(setCookies).noneMatch((c) -> c.toLowerCase(Locale.ROOT).contains("csrf"));
	}

	@Test
	void postWithoutCsrfTokenIsForbidden() throws Exception {
		CsrfSession csrf = fetchCsrf();

		mvc.perform(post("/api/hello").cookie(csrf.sessionCookie()))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("csrf_invalid"));
	}

	@Test
	void postWithInvalidCsrfTokenIsForbidden() throws Exception {
		CsrfSession csrf = fetchCsrf();

		mvc.perform(post("/api/hello").cookie(csrf.sessionCookie()).header("X-CSRF-TOKEN", "not-the-token"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("csrf_invalid"));
	}

	@Test
	void postWithValidCsrfTokenPassesCsrfCheck() throws Exception {
		CsrfSession csrf = fetchCsrf();

		// A valid token gets past CSRF; the Visitor is then refused for lack of authentication.
		mvc.perform(post("/api/hello").cookie(csrf.sessionCookie()).header("X-CSRF-TOKEN", csrf.token()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("authentication_required"));
	}

	@Test
	void getWorksWithoutCsrfToken() throws Exception {
		mvc.perform(get("/api/csrf")).andExpect(status().isOk());
	}

	@Test
	void bodylessGetIsNotBufferedOrValidatedEvenWithAMalformedContentType() throws Exception {
		mvc.perform(get("/api/csrf").header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=no-such-charset"))
			.andExpect(status().isOk());
	}

	@Test
	void sessionCookieIsHttpOnlySameSiteLaxAndSecureOutsideDev() throws Exception {
		String sessionCookie = mvc.perform(get("/api/csrf"))
			.andReturn()
			.getResponse()
			.getHeaders(HttpHeaders.SET_COOKIE)
			.stream()
			.filter((c) -> c.startsWith("SESSION="))
			.findFirst()
			.orElseThrow();

		assertThat(sessionCookie).contains("HttpOnly").contains("SameSite=Lax").contains("Secure");
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/csrf", "/api/hello", "/api/no-such-endpoint" })
	void everyResponseCarriesSecurityHeaders(String path) throws Exception {
		mvc.perform(get(path))
			.andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"))
			.andExpect(header().string("Content-Security-Policy", "default-src 'self'; object-src 'none'"))
			.andExpect(header().string("X-Frame-Options", "DENY"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("Permissions-Policy", containsString("camera=()")))
			.andExpect(header().string("Permissions-Policy", containsString("geolocation=()")))
			.andExpect(header().string("Permissions-Policy", containsString("microphone=()")));
	}

	@Test
	void corsPreflightFromAllowedOriginIsAllowedWithCredentials() throws Exception {
		mvc.perform(options("/api/csrf").header(HttpHeaders.ORIGIN, "http://localhost:3000")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-CSRF-TOKEN"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
	}

	@Test
	void corsRequestFromAllowedOriginIsReadable() throws Exception {
		mvc.perform(get("/api/csrf").header(HttpHeaders.ORIGIN, "http://localhost:3000"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
	}

	@Test
	void corsFromUnlistedOriginIsRejected() throws Exception {
		mvc.perform(options("/api/csrf").header(HttpHeaders.ORIGIN, "https://evil.example")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

		mvc.perform(get("/api/csrf").header(HttpHeaders.ORIGIN, "https://evil.example"))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void authenticatedRequestMatchingNoRuleIsDenied() throws Exception {
		mvc.perform(get("/api/no-such-endpoint").with(user("testuser123")))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("access_denied"));
	}

	@Test
	void h2ConsoleIsDeniedOutsideDev() throws Exception {
		mvc.perform(get("/h2-console/"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string("X-Frame-Options", "DENY"));
	}

	@Test
	void oversizedRequestBodyIsRejectedWith400() throws Exception {
		CsrfSession csrf = fetchCsrf();
		String oversized = "{\"padding\":\"" + "x".repeat(20_000) + "\"}";

		mvc.perform(post("/api/hello").cookie(csrf.sessionCookie())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(oversized))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("request_too_large"));
	}

	record CsrfSession(Cookie sessionCookie, String token) {
	}

	/** Gets a session cookie and CSRF token the way the SPA does. */
	private CsrfSession fetchCsrf() throws Exception {
		MvcResult result = mvc.perform(get("/api/csrf")).andExpect(status().isOk()).andReturn();
		Cookie session = result.getResponse().getCookie("SESSION");
		String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
		return new CsrfSession(session, token);
	}

}
