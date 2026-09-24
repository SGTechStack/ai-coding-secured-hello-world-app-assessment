package com.example.demo_app.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.net.HttpCookie;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.RestClient;

/**
 * The SPA's auth steps, shared by the auth API tests: prime the CSRF cookie with {@code GET
 * /api/v1/auth/csrf}, echo it in {@code X-XSRF-TOKEN} on state-changing requests, and log in as the
 * demo user. Covers both MockMvc and real HTTP ({@link RestClient}).
 *
 * <p>Do not use spring-security-test's {@code csrf()} post-processor instead: it permanently swaps
 * the shared {@code CsrfFilter}'s repository for a session-based one, breaking cookie assertions in
 * later tests that share the cached context.
 */
final class SpaAuthFlow {

  static final String DEMO_LOGIN =
      """
      {"username": "johndoe", "password": "Password123!"}
      """;

  private SpaAuthFlow() {}

  /** A freshly issued {@code XSRF-TOKEN} cookie. */
  static Cookie freshCsrfCookie(MockMvc mvc) throws Exception {
    return mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
  }

  /** {@code request} carrying a fresh CSRF cookie and the matching header, as the SPA sends. */
  static MockHttpServletRequestBuilder withCsrf(MockMvc mvc, MockHttpServletRequestBuilder request)
      throws Exception {
    Cookie xsrf = freshCsrfCookie(mvc);
    return request.cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue());
  }

  /** A CSRF-protected JSON login POST with {@code body}. */
  static MockHttpServletRequestBuilder loginRequest(MockMvc mvc, String body) throws Exception {
    return withCsrf(mvc, post("/api/v1/auth/login"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  /** Logs in as the demo user; returns the authenticated session. */
  static MockHttpSession logIn(MockMvc mvc) throws Exception {
    return (MockHttpSession)
        mvc.perform(loginRequest(mvc, DEMO_LOGIN))
            .andExpect(status().isOk())
            .andReturn()
            .getRequest()
            .getSession(false);
  }

  /**
   * Primes the CSRF cookie over real HTTP, sending {@code cookieHeader} if not null; returns the
   * token value.
   */
  static String csrfToken(RestClient client, String cookieHeader) {
    ResponseEntity<Void> primed =
        client
            .get()
            .uri("/api/v1/auth/csrf")
            .headers(
                headers -> {
                  if (cookieHeader != null) {
                    headers.add(HttpHeaders.COOKIE, cookieHeader);
                  }
                })
            .retrieve()
            .toBodilessEntity();
    String xsrfSetCookie = setCookie(primed, "XSRF-TOKEN");
    assertThat(xsrfSetCookie).doesNotContainIgnoringCase("HttpOnly");
    return HttpCookie.parse(xsrfSetCookie).getFirst().getValue();
  }

  /** Logs in as the demo user over real HTTP; returns the {@code JSESSIONID} Set-Cookie. */
  static String logInAndGetSessionCookie(RestClient.Builder restClientBuilder, int port) {
    RestClient client = restClientBuilder.baseUrl("http://localhost:" + port).build();
    String xsrfToken = csrfToken(client, null);

    ResponseEntity<String> login =
        client
            .post()
            .uri("/api/v1/auth/login")
            .header(HttpHeaders.COOKIE, "XSRF-TOKEN=" + xsrfToken)
            .header("X-XSRF-TOKEN", xsrfToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(DEMO_LOGIN)
            .retrieve()
            .toEntity(String.class);

    assertThat(login.getStatusCode().value()).isEqualTo(200);
    return setCookie(login, "JSESSIONID");
  }

  /** The {@code Set-Cookie} header value for cookie {@code name}; fails if absent. */
  static String setCookie(ResponseEntity<?> response, String name) {
    List<String> cookies = response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
    return cookies.stream()
        .filter(c -> c.startsWith(name + "="))
        .findFirst()
        .orElseThrow(() -> new AssertionError(name + " cookie not set; got " + cookies));
  }
}
