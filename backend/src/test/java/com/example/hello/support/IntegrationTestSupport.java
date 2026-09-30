package com.example.hello.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.example.hello.HelloWorldApplication;
import com.example.hello.auth.IpLoginThrottle;
import com.example.hello.common.CorrelationIdFilter;
import com.example.hello.config.SecurityConfig;
import com.example.hello.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.WebApplicationContext;

/**
 * Full-stack HTTP tests through the real filter chain (correlation id, Spring Session, Spring
 * Security, MVC). The helpers drive the same CSRF handshake the SPA uses: fetch a token, send
 * it back in the header together with the session cookie.
 */
@SpringBootTest(classes = {HelloWorldApplication.class, IntegrationTestSupport.TestOverrides.class})
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

  public static final String SESSION_COOKIE = "SESSION";
  public static final String CSRF_HEADER = SecurityConfig.CSRF_HEADER;
  public static final String ADMIN_USERNAME = "rootadmin";
  public static final String ADMIN_PASSWORD = "Root-Secret-Passw0rd";
  public static final String GOOD_PASSWORD = "Correct-Horse-Battery-9";

  private static final AtomicInteger IP_COUNTER = new AtomicInteger(1);

  @Autowired private WebApplicationContext context;
  @Autowired protected UserRepository userRepository;
  @Autowired protected IpLoginThrottle ipLoginThrottle;
  @Autowired protected RecordingEmailService recordingEmailService;

  protected MockMvc mockMvc;

  @TestConfiguration
  public static class TestOverrides {
    @Bean
    @Primary
    RecordingEmailService recordingEmailService() {
      return new RecordingEmailService();
    }
  }

  /** The browser as the tests see it: a session cookie, the matching CSRF token, an IP. */
  protected record ClientSession(Cookie cookie, String csrf, String ip) {}

  @BeforeEach
  protected void setUpMockMvcAndSharedState() {
    mockMvc =
        webAppContextSetup(context)
            .addFilters(
                context.getBean(CorrelationIdFilter.class),
                context.getBean("springSessionRepositoryFilter", Filter.class),
                context.getBean("springSecurityFilterChain", Filter.class))
            .build();
    ipLoginThrottle.clear();
    recordingEmailService.clear();
  }

  /** Each test gets its own source IP so the IP throttle never bleeds between tests. */
  protected static String uniqueIp() {
    int n = IP_COUNTER.getAndIncrement();
    return "10." + ((n >> 16) & 255) + "." + ((n >> 8) & 255) + "." + (n & 255);
  }

  protected static String uniqueName(String prefix) {
    return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }

  protected static RequestPostProcessor remoteAddr(String ip) {
    return request -> {
      request.setRemoteAddr(ip);
      return request;
    };
  }

  protected ClientSession anonymousSession(String ip) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/auth/csrf").with(remoteAddr(ip)))
            .andExpect(status().isOk())
            .andReturn();
    Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
    assertThat(cookie).as("csrf endpoint must establish a session").isNotNull();
    String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    return new ClientSession(cookie, token, ip);
  }

  /** Re-fetches the CSRF token for an existing session (it rotates on login). */
  protected ClientSession refreshCsrf(ClientSession session) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/auth/csrf").cookie(session.cookie()).with(remoteAddr(session.ip())))
            .andExpect(status().isOk())
            .andReturn();
    Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
    String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    return new ClientSession(cookie != null ? cookie : session.cookie(), token, session.ip());
  }

  protected MockHttpServletRequestBuilder jsonRequest(
      MockHttpServletRequestBuilder builder, ClientSession session, String body) {
    return builder
        .cookie(session.cookie())
        .header(CSRF_HEADER, session.csrf())
        .with(remoteAddr(session.ip()))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  protected MockHttpServletRequestBuilder authenticated(
      MockHttpServletRequestBuilder builder, ClientSession session) {
    return builder.cookie(session.cookie()).header(CSRF_HEADER, session.csrf()).with(remoteAddr(session.ip()));
  }

  protected void register(String username, String email, String password) throws Exception {
    ClientSession session = anonymousSession(uniqueIp());
    mockMvc
        .perform(jsonRequest(post("/api/auth/register"), session, registerJson(username, email, password)))
        .andExpect(status().isCreated());
  }

  /** Registers a fresh USER with {@link #GOOD_PASSWORD} and returns the username. */
  protected String registerUser(String prefix) throws Exception {
    String username = uniqueName(prefix);
    register(username, username + "@example.com", GOOD_PASSWORD);
    return username;
  }

  protected MvcResult attemptLogin(String username, String password, String ip) throws Exception {
    ClientSession session = anonymousSession(ip);
    return mockMvc
        .perform(jsonRequest(post("/api/auth/login"), session, loginJson(username, password)))
        .andReturn();
  }

  protected ClientSession login(String username, String password, String ip) throws Exception {
    MvcResult result = attemptLogin(username, password, ip);
    assertThat(result.getResponse().getStatus()).as("login response").isEqualTo(200);
    Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
    assertThat(cookie).as("login must issue a session cookie").isNotNull();
    return refreshCsrf(new ClientSession(cookie, null, ip));
  }

  protected ClientSession login(String username, String password) throws Exception {
    return login(username, password, uniqueIp());
  }

  protected ClientSession loginAsAdmin() throws Exception {
    return login(ADMIN_USERNAME, ADMIN_PASSWORD, uniqueIp());
  }

  protected static String registerJson(String username, String email, String password) {
    return "{\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
  }

  protected static String loginJson(String username, String password) {
    return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
  }
}
