package com.example.demo_app.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.net.HttpCookie;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
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
public final class SpaAuthFlow {

  static final String DEMO_LOGIN =
      """
      {"username": "johndoe", "password": "Password123!"}
      """;

  /**
   * The bootstrap admin the {@code test} profile configures ({@code app.admin.*} in
   * application-test.yml, the same values as {@code dev}); {@code AdminBootstrap} creates it.
   */
  public static final String ADMIN_USERNAME = "admin";

  public static final String ADMIN_PASSWORD = "Dev-Admin-Passw0rd!";

  private static final ObjectMapper JSON = new ObjectMapper();

  /** Source of {@link #uniqueIp()} addresses. */
  private static final AtomicInteger NEXT_IP = new AtomicInteger();

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

  /**
   * Sends the request from client address {@code ip}. MockMvc otherwise uses {@code 127.0.0.1} for
   * every request, and the IP throttle's counters live as long as the cached context: give failing
   * logins their own address so they neither trip nor inherit another test's throttle.
   */
  public static RequestPostProcessor fromIp(String ip) {
    return request -> {
      request.setRemoteAddr(ip);
      return request;
    };
  }

  /**
   * A client address no other request in this JVM has used ({@code 10.x.y.z}), so a test's
   * registrations or failed logins never share throttle counters with another test's.
   */
  public static String uniqueIp() {
    int n = NEXT_IP.incrementAndGet();
    return "10." + (n >> 16 & 0xff) + "." + (n >> 8 & 0xff) + "." + (n & 0xff);
  }

  /** Logs in as the demo user; returns the authenticated session. */
  public static MockHttpSession logIn(MockMvc mvc) throws Exception {
    return (MockHttpSession)
        mvc.perform(loginRequest(mvc, DEMO_LOGIN))
            .andExpect(status().isOk())
            .andReturn()
            .getRequest()
            .getSession(false);
  }

  /** Logs in as the bootstrap admin; returns the authenticated session. */
  public static MockHttpSession logInAsAdmin(MockMvc mvc) throws Exception {
    return logIn(mvc, ADMIN_USERNAME, ADMIN_PASSWORD);
  }

  /** Logs in as {@code username}; returns the authenticated session. */
  public static MockHttpSession logIn(MockMvc mvc, String username, String password)
      throws Exception {
    return (MockHttpSession)
        mvc.perform(loginRequest(mvc, credentialsJson(username, password)))
            .andExpect(status().isOk())
            .andReturn()
            .getRequest()
            .getSession(false);
  }

  /** A login body for {@code username} and {@code password}, JSON-escaped. */
  public static String credentialsJson(String username, String password) {
    return json(Map.of("username", username, "password", password));
  }

  /** A registration body, JSON-escaped (so a first name may hold quotes or markup). */
  public static String registrationJson(
      String username, String email, String firstName, String password) {
    Map<String, String> body = new LinkedHashMap<>();
    body.put("username", username);
    body.put("email", email);
    body.put("firstName", firstName);
    body.put("password", password);
    return json(body);
  }

  /** A CSRF-protected JSON registration POST with {@code body}. */
  public static MockHttpServletRequestBuilder registerRequest(MockMvc mvc, String body)
      throws Exception {
    return withCsrf(mvc, post("/api/v1/auth/register"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  /**
   * Registers a {@code USER} named {@code username} (email {@code <username>@example.com}, first
   * name {@code Test}) from a {@link #uniqueIp()}, so it never counts against another test's
   * registration throttle. Fails unless the API answers {@code 201}.
   */
  public static void register(MockMvc mvc, String username, String password) throws Exception {
    mvc.perform(
            registerRequest(
                    mvc,
                    registrationJson(username, username + "@example.com", "Test", password))
                .with(fromIp(uniqueIp())))
        .andExpect(status().isCreated());
  }

  private static String json(Object value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(e);
    }
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
