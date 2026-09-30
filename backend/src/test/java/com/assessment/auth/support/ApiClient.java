package com.assessment.auth.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * One browser-shaped HTTP conversation: a cookie jar plus the CSRF token bound to the session in it.
 *
 * <p>This exists because the suite is <strong>full HTTP, not MockMvc</strong> (spec.md S12). MockMvc
 * runs the filter chain but simulates the servlet layer, and this application's correctness lives
 * exactly in what that simulation smooths over: real {@code Set-Cookie} attributes, the CSRF
 * bootstrap round-trip through {@code GET /csrf} into an {@code X-CSRF-TOKEN} header, and a
 * {@code getRemoteAddr()} that returns something the rate limiter can key on.
 *
 * <p><strong>The cookie jar does not enforce the {@code Secure} attribute, and that is the point.</strong>
 * Ticket 09 kept {@code secure: true} in every profile on the basis that a browser treats
 * {@code localhost} as a trustworthy origin and will therefore return the cookie over plain HTTP.
 * An HTTP client has no such policy to apply, so what this jar proves is the server half: the
 * attribute is emitted, and the server accepts the cookie when it comes back. The browser half is
 * discharged by the {@code browser-test} acceptance gate. Splitting it this way is deliberate — a
 * green build here must not be read as having verified browser behaviour it cannot see.
 *
 * <p>Each instance is a separate client. Tests create several deliberately: "a second login expires
 * the first session" and "a cookie replayed after logout is rejected" are only expressible with two
 * independent jars.
 */
public class ApiClient {

  private static final String BASE_PATH = "/api/v1";

  private final RestTemplate rest;
  private final String baseUrl;
  private final Map<String, String> cookies = new LinkedHashMap<>();
  private final List<String> lastSetCookies = new ArrayList<>();

  private String csrfToken;
  private String csrfHeaderName = "X-CSRF-TOKEN";

  public ApiClient(int port) {
    // The JDK factory rather than the default: PATCH is three rows of the authorization matrix, and
    // HttpURLConnection cannot send it -- it fails with a ProtocolException that reads like an
    // application fault.
    this.rest = new RestTemplate(new JdkClientHttpRequestFactory());
    // A 4xx is an assertion subject here, not an exception. Without this the first test that checks
    // for a 401 fails with a thrown HttpClientErrorException instead of an assertion.
    this.rest.setErrorHandler(
        new ResponseErrorHandler() {
          @Override
          public boolean hasError(ClientHttpResponse response) {
            return false;
          }
        });
    this.baseUrl = "http://localhost:" + port + BASE_PATH;
  }

  // ---------------------------------------------------------------- verbs

  public ResponseEntity<String> get(String path) {
    return send(HttpMethod.GET, baseUrl + path, null, true);
  }

  /** For paths outside the API base path — {@code /actuator/**} and {@code /error}. */
  public ResponseEntity<String> getAbsolute(String url) {
    return send(HttpMethod.GET, url, null, true);
  }

  public ResponseEntity<String> post(String path, String jsonBody) {
    return send(HttpMethod.POST, baseUrl + path, jsonBody, true);
  }

  /**
   * A mutating call with no {@code X-CSRF-TOKEN} header.
   *
   * <p>Not a convenience: no endpoint in this application is CSRF-exempt, and that includes login,
   * register and both reset endpoints. Proving it needs a client that can deliberately omit the
   * header while still carrying the session cookie.
   */
  public ResponseEntity<String> postWithoutCsrf(String path, String jsonBody) {
    return send(HttpMethod.POST, baseUrl + path, jsonBody, false);
  }

  public ResponseEntity<String> patch(String path, String jsonBody) {
    return send(HttpMethod.PATCH, baseUrl + path, jsonBody, true);
  }

  public ResponseEntity<String> delete(String path) {
    return send(HttpMethod.DELETE, baseUrl + path, null, true);
  }

  // ---------------------------------------------------------------- auth flows

  /**
   * {@code GET /csrf}, storing the token and the header name it must be sent under.
   *
   * <p>The header name comes from the response rather than a constant, because {@code CsrfController}
   * returns it for exactly that reason — the SPA should not have to hard-code it either.
   */
  public ResponseEntity<String> fetchCsrf() {
    ResponseEntity<String> response = get("/csrf");
    String body = response.getBody();
    if (body != null) {
      csrfToken = extract(body, "token");
      String headerName = extract(body, "headerName");
      if (headerName != null) {
        csrfHeaderName = headerName;
      }
    }
    return response;
  }

  /**
   * The full bootstrap: {@code GET /csrf} then {@code POST /auth/login}, then a second
   * {@code GET /csrf}.
   *
   * <p>The third call is load-bearing rather than tidy. A successful login rotates the session id,
   * and the CSRF token was bound to the session that just went away — so without re-fetching, the
   * next mutating call in the test would fail with a 403 that has nothing to do with what the test
   * is asserting.
   */
  public ResponseEntity<String> login(String username, String password) {
    fetchCsrf();
    ResponseEntity<String> response =
        post(
            "/auth/login",
            "{\"username\":" + quote(username) + ",\"password\":" + quote(password) + "}");
    if (response.getStatusCode().is2xxSuccessful()) {
      fetchCsrf();
    }
    return response;
  }

  // ---------------------------------------------------------------- transport

  private ResponseEntity<String> send(
      HttpMethod method, String url, String jsonBody, boolean withCsrf) {

    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON));
    if (jsonBody != null) {
      headers.setContentType(MediaType.APPLICATION_JSON);
    }
    if (!cookies.isEmpty()) {
      headers.add(HttpHeaders.COOKIE, serialiseCookies());
    }
    if (withCsrf && csrfToken != null && method != HttpMethod.GET) {
      headers.add(csrfHeaderName, csrfToken);
    }

    ResponseEntity<String> response =
        rest.exchange(url, method, new HttpEntity<>(jsonBody, headers), String.class);
    absorbCookies(response);
    return response;
  }

  private void absorbCookies(ResponseEntity<String> response) {
    List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
    lastSetCookies.clear();
    if (setCookies == null) {
      return;
    }
    lastSetCookies.addAll(setCookies);
    for (String header : setCookies) {
      String pair = header.split(";", 2)[0];
      int equals = pair.indexOf('=');
      if (equals < 0) {
        continue;
      }
      String name = pair.substring(0, equals).trim();
      String value = pair.substring(equals + 1).trim();
      // A deletion is an empty value or Max-Age=0. Logout sends one, and treating it as a live
      // cookie is how a "post-logout cookie is rejected" test passes for the wrong reason.
      if (value.isEmpty() || header.toLowerCase().contains("max-age=0")) {
        cookies.remove(name);
      } else {
        cookies.put(name, value);
      }
    }
  }

  private String serialiseCookies() {
    return cookies.entrySet().stream()
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .reduce((left, right) -> left + "; " + right)
        .orElse("");
  }

  // ---------------------------------------------------------------- inspection

  public String sessionCookie() {
    return cookies.get("SESSION");
  }

  /** The raw {@code Set-Cookie} headers from the most recent response, attributes included. */
  public List<String> lastSetCookies() {
    return List.copyOf(lastSetCookies);
  }

  /** The raw {@code Set-Cookie} line for one cookie name, or {@code null}. */
  public String lastSetCookie(String name) {
    return lastSetCookies.stream()
        .filter(header -> header.startsWith(name + "="))
        .findFirst()
        .orElse(null);
  }

  public boolean hasCookie(String name) {
    return cookies.containsKey(name);
  }

  /** Puts a specific value in the jar — for replaying a cookie that logout has since killed. */
  public ApiClient withSessionCookie(String value) {
    cookies.put("SESSION", value);
    return this;
  }

  /**
   * Adopts another client's session cookie and CSRF token, so several clients can act on one session.
   *
   * <p>For the concurrent case only, and it is needed rather than convenient. The CSRF repository is
   * session-bound, so parallel requests must all carry the <em>same</em> session cookie or they fail
   * CSRF before reaching what the test is about. One {@code ApiClient} cannot serve them: its cookie
   * jar is a plain map and concurrent writes to it would be the flakiness, not the subject. So each
   * thread gets its own client sharing one session's credentials.
   */
  public ApiClient sharingSessionWith(ApiClient other) {
    this.cookies.putAll(other.cookies);
    this.csrfToken = other.csrfToken;
    this.csrfHeaderName = other.csrfHeaderName;
    return this;
  }

  // ---------------------------------------------------------------- tiny JSON helpers

  /**
   * Pulls one string field out of a flat JSON body.
   *
   * <p>A regex rather than an ObjectMapper deliberately: the only two fields read this way are
   * {@code token} and {@code headerName} from {@code GET /csrf}, and a test harness that deserialises
   * into the application's own DTOs stops being able to notice when those DTOs change shape.
   */
  private static String extract(String json, String field) {
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
    return matcher.find() ? matcher.group(1) : null;
  }

  private static String quote(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
