package com.sgtechstack.helloauth.support;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import jakarta.servlet.http.Cookie;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * A browser-like client over MockMvc: one client IP, a cookie jar holding the session cookie,
 * and the CSRF token fetched the same way the SPA does. Requests pass through the real filter
 * chain (Spring Session, CORS, CSRF, authorization).
 */
public final class ApiClient {

	public static final String SESSION_COOKIE = "SESSION";

	private final MockMvc mockMvc;

	private final JsonMapper json;

	private final String ip;

	private Cookie sessionCookie;

	private String csrfToken;

	public ApiClient(MockMvc mockMvc, JsonMapper json, String ip) {
		this.mockMvc = mockMvc;
		this.json = json;
		this.ip = ip;
	}

	public MockHttpServletResponse login(String username, String password) {
		MockHttpServletResponse response = post("/api/auth/login",
				Map.of("username", username, "password", password));
		// The server rotates the CSRF token on login.
		this.csrfToken = null;
		return response;
	}

	public MockHttpServletResponse logout() {
		MockHttpServletResponse response = post("/api/auth/logout", null);
		this.csrfToken = null;
		return response;
	}

	public MockHttpServletResponse get(String path) {
		return perform(MockMvcRequestBuilders.get(path));
	}

	public MockHttpServletResponse post(String path, Object body) {
		return withCsrf(MockMvcRequestBuilders.post(path), body);
	}

	public MockHttpServletResponse patch(String path, Object body) {
		return withCsrf(MockMvcRequestBuilders.patch(path), body);
	}

	public MockHttpServletResponse delete(String path) {
		return withCsrf(MockMvcRequestBuilders.delete(path), null);
	}

	public MockHttpServletResponse postWithoutCsrf(String path, Object body) {
		return perform(withBody(MockMvcRequestBuilders.post(path), body));
	}

	public Cookie sessionCookie() {
		return this.sessionCookie;
	}

	public void useSessionCookie(Cookie cookie) {
		this.sessionCookie = cookie;
	}

	public JsonNode json(MockHttpServletResponse response) {
		return this.json.readTree(body(response));
	}

	public static String body(MockHttpServletResponse response) {
		try {
			return response.getContentAsString();
		}
		catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private MockHttpServletResponse withCsrf(MockHttpServletRequestBuilder builder, Object body) {
		if (this.csrfToken == null) {
			this.csrfToken = json(get("/api/auth/csrf")).path("token").asString();
		}
		builder.header("X-CSRF-TOKEN", this.csrfToken);
		return perform(withBody(builder, body));
	}

	private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder builder, Object body) {
		if (body != null) {
			builder.contentType(MediaType.APPLICATION_JSON).content(this.json.writeValueAsString(body));
		}
		return builder;
	}

	private MockHttpServletResponse perform(MockHttpServletRequestBuilder builder) {
		builder.with(request -> {
			request.setRemoteAddr(this.ip);
			return request;
		});
		if (this.sessionCookie != null) {
			builder.cookie(this.sessionCookie);
		}
		MockHttpServletResponse response;
		try {
			response = this.mockMvc.perform(builder).andReturn().getResponse();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
		Cookie updated = response.getCookie(SESSION_COOKIE);
		if (updated != null) {
			this.sessionCookie = (updated.getMaxAge() == 0) ? null : updated;
		}
		return response;
	}

}
