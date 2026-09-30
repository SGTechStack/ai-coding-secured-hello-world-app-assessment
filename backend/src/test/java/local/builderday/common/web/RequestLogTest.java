package local.builderday.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import local.builderday.common.audit.SecurityAudit;
import local.builderday.common.audit.SessionIds;
import local.builderday.common.logging.LogFields;
import local.builderday.support.AuditLogCapture;
import local.builderday.support.TestClocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The Request log at the HTTP boundary: what reaches the {@code access} logger for each kind of request, through every
 * servlet filter and the security chain (firewall included).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestClocks.class)
class RequestLogTest {
  private static final String PASSWORD = "Str0ng!Passw0rd";

  @Autowired MockMvc mvc;
  @Autowired UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired TestClocks clocks;
  @Autowired SessionIds sessionIds;
  @Autowired JdbcTemplate jdbcTemplate;

  private AuditLogCapture access;
  private AuditLogCapture audit;
  private Instant now;
  private UUID userId;

  @BeforeEach
  void setUp() {
    now = clocks.freeze();
    userRepository.deleteAll();
    userId = UUID.randomUUID();
    userRepository.save(new UserEntity(userId, "testuser123", "testuser123@test.example.com",
        passwordEncoder.encode(PASSWORD), "USER", true));
    access = new AuditLogCapture(RequestLogFilter.LOGGER);
    audit = new AuditLogCapture(SecurityAudit.LOGGER);
  }

  @AfterEach
  void tearDown() {
    access.close();
    audit.close();
    clocks.reset();
  }

  @Test
  void should_logArrivalAndA401Completion_when_anAnonymousCallerRequestsAProtectedApi() throws Exception {
    var lines = logged(get("/api/profile").header(HttpHeaders.USER_AGENT, "test-agent/1.0"), 401);

    var arrival = lines.getFirst();
    assertThat(arrival.getLevel()).isEqualTo(Level.INFO);
    assertThat(arrival.getFormattedMessage()).isEqualTo("GET /api/profile");
    assertThat(fields(arrival)).containsEntry("event.kind", "event")
        .containsEntry("event.category", List.of("interface")).containsEntry("event.type", List.of("start"))
        .containsEntry("event.action", "access").containsEntry("interface.type", "api")
        .containsEntry("interface.direction", "inbound").containsEntry("http.request.method", "GET")
        .containsEntry("url.path", "/api/profile").containsEntry("user_agent.original", "test-agent/1.0")
        .containsEntry("event.start", now.toString()).containsKey("trace.id")
        .doesNotContainKeys("event.end", "event.duration_ms", "http.response.status_code", "event.outcome", "user.id",
            "session.hash");

    var completion = lines.getLast();
    assertThat(completion.getLevel()).isEqualTo(Level.WARN);
    assertThat(completion.getFormattedMessage()).matches("GET /api/profile 401 \\d+ms");
    assertThat(fields(completion)).containsEntry("event.type", List.of("end"))
        .containsEntry("event.end", now.toString())
        .containsEntry("http.response.status_code", 401).containsEntry("event.outcome", "failure")
        .containsEntry("trace.id", fields(arrival).get("trace.id")).containsKey("event.duration_ms")
        .doesNotContainKeys("event.start", "user.id", "code.function.name", "http.route");
  }

  @Test
  void should_logSuccess_when_aStaticAssetOrAPageIsServed() throws Exception {
    var asset = logged(get("/favicon.ico"), 200).getLast();
    var page = logged(get("/login").accept(MediaType.TEXT_HTML), 200).getLast();

    for (var completion : List.of(asset, page)) {
      assertThat(completion.getLevel()).isEqualTo(Level.INFO);
      assertThat(fields(completion)).containsEntry("http.response.status_code", 200)
          .containsEntry("event.outcome", "success").doesNotContainKeys("code.function.name", "http.route");
    }
  }

  @Test
  void should_log404_when_theStaticPathDoesNotExist() throws Exception {
    var completion = logged(get("/assets/missing.js"), 404).getLast();

    assertThat(completion.getLevel()).isEqualTo(Level.WARN);
    assertThat(fields(completion)).containsEntry("http.response.status_code", 404);
  }

  @Test
  void should_answerAndLog400_when_theFirewallRejectsTheUrl() throws Exception {
    for (var uri : List.of("/api//profile", "/favicon.ico;x=1")) {
      var completion = logged(get(URI.create(uri)), 400).getLast();

      assertThat(completion.getLevel()).as(uri).isEqualTo(Level.WARN);
      assertThat(fields(completion)).as(uri).containsEntry("http.response.status_code", 400)
          .containsEntry("event.outcome", "failure");
    }
  }

  @Test
  void should_logThePathWithoutParameters_when_theUrlCarriesASessionIdentifier() throws Exception {
    for (var uri : List.of("/api/profile;jsessionid=raw-session-id/x;y=z", "/api/profile%3Bjsessionid=raw-session-id/x")) {
      var lines = logged(get(URI.create(uri)), 400);

      assertThat(lines).as(uri).allSatisfy(line -> {
        assertThat(fields(line)).containsEntry("url.path", "/api/profile/x");
        assertThat(line.getFormattedMessage() + fields(line)).doesNotContain("raw-session-id", ";", "%3B");
      });
    }
  }

  @Test
  void should_log401_when_theSessionIsPastItsAbsoluteLifetime() throws Exception {
    Cookie session = login(anonymousSession());
    clocks.advance(Duration.ofHours(8).plusMinutes(1));

    var completion = logged(get("/api/profile").cookie(session), 401).getLast();

    assertThat(fields(completion)).containsEntry("http.response.status_code", 401)
        .containsEntry("session.hash", hashOf(session));
  }

  @Test
  void should_carryThePreLoginSessionHashAndTheAuditTraceId_when_aUserLogsIn() throws Exception {
    var anonymous = anonymousSession();

    var lines = logged(loginRequest(anonymous), 200);

    assertThat(lines).allSatisfy(line -> assertThat(fields(line)).containsEntry("session.hash", hashOf(anonymous.cookie())));
    var completion = fields(lines.getLast());
    assertThat(completion).containsEntry("code.function.name", "LoginController#login")
        .containsEntry("http.route", "/api/auth/login");
    var loginAudit = auditEvent("user-login");
    assertThat(loginAudit).containsEntry("trace.id", completion.get("trace.id"));
    assertThat(loginAudit.get("session.hash")).isNotNull().isNotEqualTo(completion.get("session.hash"));
  }

  @Test
  void should_matchTheAuditSessionHash_andNameTheController_when_anAuthenticatedCallIsLogged() throws Exception {
    Cookie session = login(anonymousSession());
    Object auditHash = auditEvent("user-login").get("session.hash");

    var completion = fields(logged(get("/api/profile").cookie(session), 200).getLast());

    assertThat(completion).containsEntry("session.hash", auditHash)
        .containsEntry("code.function.name", "ProfileController#profile").containsEntry("http.route", "/api/profile");
    assertThat((String) completion.get("session.hash")).matches("[0-9a-f]{64}")
        .isNotEqualTo(SessionIds.sha256Hex(sessionId(session)));
  }

  @Test
  void should_logTheReplayedCookiesHash_when_itIsSentAfterLogout() throws Exception {
    var anonymous = anonymousSession();
    Cookie session = login(anonymous);
    var token = csrfToken(session);
    mvc.perform(post("/api/auth/logout").cookie(session).header(token.header(), token.value()))
        .andExpect(status().isNoContent());

    var lines = logged(get("/api/profile").cookie(session), 401);

    assertThat(lines).allSatisfy(line -> assertThat(fields(line)).containsEntry("session.hash", hashOf(session)));
  }

  /**
   * A firewall rejection never reaches the security chain, whose session filters (absolute lifetime, one Session per
   * User) read the Session on every other request, assets included. So only the request log could touch it here.
   */
  @Test
  void should_leaveTheSessionsLastAccessTimeUnchanged_when_aRejectedRequestCarriesTheSessionCookie() throws Exception {
    Cookie session = login(anonymousSession());
    long before = lastAccessTime(session);
    Thread.sleep(20);

    var lines = logged(get(URI.create("/favicon.ico;x=1")).cookie(session), 400);

    assertThat(lines).allSatisfy(line -> assertThat(fields(line)).containsEntry("session.hash", hashOf(session)));
    assertThat(lastAccessTime(session)).isEqualTo(before);
  }

  @Test
  void should_truncateTheUserAgent_when_itIsLongerThan512Characters() throws Exception {
    var lines = logged(get("/favicon.ico").header(HttpHeaders.USER_AGENT, "a".repeat(600)), 200);

    assertThat(lines).allSatisfy(line -> assertThat(fields(line)).containsEntry("user_agent.original", "a".repeat(512)));
  }

  @Test
  void should_recordNoQueryRefererIpOrUsername_when_theRequestCarriesThem() throws Exception {
    Cookie session = login(anonymousSession());

    var lines = logged(get("/api/profile?secret-query=token-value").cookie(session)
        .header(HttpHeaders.REFERER, "https://referer.example/secret-referer")
        .header("X-Forwarded-For", "198.51.100.23"), 200);

    assertThat(lines).allSatisfy(line -> {
      assertThat(fields(line)).containsEntry("url.path", "/api/profile")
          .doesNotContainKeys("source.ip", "client.ip", "url.query", "url.full", "http.request.referrer", "user.name");
      assertThat(line.getFormattedMessage() + fields(line) + line.getMDCPropertyMap())
          .doesNotContain("token-value", "secret-referer", "198.51.100.23", "testuser123");
    });
  }

  @Test
  void should_renderTheCorrelationIdOnceAsTraceId_when_linesAreEncodedAsEcsJson() throws Exception {
    Cookie session = login(anonymousSession());
    logged(get("/api/profile").cookie(session), 200);

    assertThat(access.events()).isNotEmpty().allSatisfy(line -> {
      String json = AuditLogCapture.render("access", "ACCESS", line);
      String traceId = (String) fields(line).get("trace.id");
      assertThat(JsonPath.<String>read(json, "$.trace.id")).isEqualTo(traceId);
      assertThat(json).doesNotContain("traceId").containsOnlyOnce(traceId);
    });
    assertThat(audit.events()).isNotEmpty().allSatisfy(event ->
        assertThat(AuditLogCapture.render("audit", "AUDIT", event)).doesNotContain("traceId").contains("\"trace\":{\"id\""));
  }

  @Test
  void should_attributeTheCompletionToTheUser_when_theyLogInDuringTheRequest() throws Exception {
    var lines = logged(loginRequest(anonymousSession()), 200);

    assertThat(fields(lines.getFirst())).doesNotContainKey("user.id");
    assertThat(fields(lines.getLast())).containsEntry("user.id", userId.toString());
  }

  @Test
  void should_leaveNoStaleUserId_when_anAnonymousRequestFollowsAnAuthenticatedOne() throws Exception {
    Cookie session = login(anonymousSession());

    assertThat(fields(logged(get("/api/profile").cookie(session), 200).getLast()))
        .containsEntry("user.id", userId.toString());
    assertThat(MDC.get("user.id")).isNull();

    assertThat(logged(get("/api/profile"), 401)).allSatisfy(line -> assertThat(fields(line)).doesNotContainKey("user.id"));
  }

  @Test
  void should_keepTheUserIdAndPublishItToMdc_when_theUserLogsOut() throws Exception {
    Cookie session = login(anonymousSession());
    var token = csrfToken(session);

    var completion = logged(post("/api/auth/logout").cookie(session).header(token.header(), token.value()), 204)
        .getLast();

    assertThat(fields(completion)).containsEntry("user.id", userId.toString())
        .containsEntry("session.hash", hashOf(session));
    var logout = audit.events().stream()
        .filter(event -> "user-logout".equals(fields(event).get("event.action"))).reduce((first, last) -> last)
        .orElseThrow();
    assertThat(logout.getMDCPropertyMap()).containsEntry("user.id", userId.toString());
    assertThat(AuditLogCapture.render("audit", "AUDIT", logout)).containsOnlyOnce("\"user\":{\"id\":\"" + userId + "\"}");
  }

  @Test
  void should_renderOneUserId_when_aLineSetsItWhileMdcAlsoHoldsOne() {
    var auditLogger = LoggerFactory.getLogger(SecurityAudit.LOGGER);
    try (var ignored = MDC.putCloseable("user.id", "mdc-caller")) {
      LogFields.log(auditLogger.atInfo(), Map.of("user.id", "explicit-subject"), "different");
      LogFields.log(auditLogger.atInfo(), Map.of("user.id", "mdc-caller"), "same");
      assertThat(MDC.get("user.id")).isEqualTo("mdc-caller");
    }

    var rendered = audit.events().stream().map(event -> AuditLogCapture.render("audit", "AUDIT", event)).toList();
    assertThat(rendered.get(0)).containsOnlyOnce("\"user\":{\"id\"").contains("explicit-subject")
        .doesNotContain("mdc-caller");
    assertThat(rendered.get(1)).containsOnlyOnce("\"user\":{\"id\":\"mdc-caller\"}");
  }

  // --- helpers

  /** Performs the request, expects {@code status}, and returns the two Request log entries it produced. */
  private List<ILoggingEvent> logged(MockHttpServletRequestBuilder request, int status) throws Exception {
    int before = access.events().size();
    mvc.perform(request).andExpect(status().is(status));
    var lines = access.events().subList(before, access.events().size());
    assertThat(lines).hasSize(2);
    return lines;
  }

  private record Anonymous(Cookie cookie, String csrfHeader, String csrfToken) {}

  private record CsrfToken(String header, String value) {}

  /** An anonymous Session and its CSRF token, as the SPA gets them from {@code GET /csrf} before logging in. */
  private Anonymous anonymousSession() throws Exception {
    var response = mvc.perform(get("/csrf")).andExpect(status().isOk()).andReturn().getResponse();
    var token = JsonPath.parse(response.getContentAsString());
    return new Anonymous(response.getCookie("id"), token.read("$.headerName"), token.read("$.token"));
  }

  private MockHttpServletRequestBuilder loginRequest(Anonymous anonymous) {
    return post("/api/auth/login").cookie(anonymous.cookie()).header(anonymous.csrfHeader(), anonymous.csrfToken())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"testuser123\",\"password\":\"" + PASSWORD + "\"}");
  }

  private Cookie login(Anonymous anonymous) throws Exception {
    return mvc.perform(loginRequest(anonymous)).andExpect(status().isOk()).andReturn().getResponse().getCookie("id");
  }

  private CsrfToken csrfToken(Cookie session) throws Exception {
    var body = JsonPath.parse(mvc.perform(get("/csrf").cookie(session)).andExpect(status().isOk()).andReturn()
        .getResponse().getContentAsString());
    return new CsrfToken(body.read("$.headerName"), body.read("$.token"));
  }

  /** The Session identifier a cookie carries (Spring Session base64-encodes it). */
  private static String sessionId(Cookie cookie) {
    return new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
  }

  private String hashOf(Cookie cookie) { return sessionIds.hash(sessionId(cookie)); }

  private long lastAccessTime(Cookie session) {
    return jdbcTemplate.queryForObject("SELECT LAST_ACCESS_TIME FROM SPRING_SESSION WHERE SESSION_ID = ?", Long.class,
        sessionId(session));
  }

  private Map<String, Object> auditEvent(String action) {
    return audit.events().stream().map(AuditLogCapture::fields).filter(fields -> action.equals(fields.get("event.action")))
        .reduce((first, last) -> last).orElseThrow();
  }

  private static Map<String, Object> fields(ILoggingEvent event) { return AuditLogCapture.fields(event); }
}
