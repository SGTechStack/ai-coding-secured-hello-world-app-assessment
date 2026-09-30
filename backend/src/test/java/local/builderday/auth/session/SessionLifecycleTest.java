package local.builderday.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import local.builderday.BuilderdayApplication;
import local.builderday.support.TestClocks;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultMatcher;

/** Spring Session JDBC sessions: shared across instances, one per user, bounded by an absolute lifetime. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestClocks.class)
class SessionLifecycleTest {
  private static final String PASSWORD = "Str0ng!Passw0rd";

  @Autowired MockMvc mvc;
  @Autowired UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired TestClocks clocks;

  @BeforeEach
  void setUp() {
    clocks.reset();
    userRepository.deleteAll();
    userRepository.save(new UserEntity(UUID.randomUUID(), "testuser123", "testuser123@test.example.com",
        passwordEncoder.encode(PASSWORD), "USER", true));
  }

  @Test
  void should_expireThePreviousSession_whenTheSameUserLogsInAgain() throws Exception {
    Cookie first = login();
    mvc.perform(landingPage(first)).andExpect(status().isOk());

    Cookie second = login();

    mvc.perform(landingPage(second)).andExpect(status().isOk());
    // The expired-session answer replaces the SPA document, so a page load goes to the login page.
    mvc.perform(get("/home").cookie(first).accept(MediaType.TEXT_HTML)).andExpect(redirectedToLogin());
    mvc.perform(landingPage(first)).andExpect(status().isUnauthorized());
  }

  @Test
  void should_keepASessionJustUnderTheAbsoluteLifetime_andEndItPastTheLimit() throws Exception {
    Cookie session = login();

    clocks.advance(Duration.ofHours(7).plusMinutes(59));
    mvc.perform(landingPage(session)).andExpect(status().isOk());

    clocks.advance(Duration.ofMinutes(2));
    mvc.perform(landingPage(session)).andExpect(status().isUnauthorized());
    mvc.perform(landingPage(session)).andExpect(status().isUnauthorized());
  }

  @Test
  void should_notCreateASession_forPublicDocumentRequests() throws Exception {
    var register = mvc.perform(get("/register").accept(MediaType.TEXT_HTML)).andExpect(status().isOk()).andReturn();
    var loginPage = mvc.perform(get("/login").accept(MediaType.TEXT_HTML)).andExpect(status().isOk()).andReturn();

    assertThat(register.getResponse().getCookie("id")).isNull();
    assertThat(loginPage.getResponse().getCookie("id")).isNull();
  }

  @Test
  void should_notCreateASession_whenAnAnonymousRequestIsRefused() throws Exception {
    for (String path : new String[] {"/api/profile", "/api/anything", "/api/admin/users"}) {
      var refused = mvc.perform(get(path)).andExpect(status().isUnauthorized()).andReturn();

      assertThat(refused.getResponse().getCookie("id")).as(path).isNull();
    }
  }

  @Test
  void should_honourTheSessionOnAnotherApplicationInstance_becauseItIsStoredInTheDatabase() throws Exception {
    Cookie session = login();

    // Command-line arguments outrank application.yml (which fixes the HTTPS port), so the second instance gets a
    // free one.
    try (var otherInstance = new SpringApplicationBuilder(BuilderdayApplication.class)
        .run("--server.port=0", "--server.ssl.enabled=false")) {
      int port = ((WebServerApplicationContext) otherInstance).getWebServer().getPort();
      var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/profile"))
          .header("Cookie", "id=" + session.getValue()).GET().build();
      var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());

      assertThat(response.statusCode()).isEqualTo(200);
    }
  }

  private Cookie login() throws Exception {
    var login = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"testuser123\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isOk()).andReturn();
    return login.getResponse().getCookie("id");
  }

  private static ResultMatcher redirectedToLogin() {
    return redirectedUrl("/login");
  }

  /** The Session restore probe (ADR 0005): 200 while the Session is live, 401 once it has ended. */
  private static RequestBuilder landingPage(Cookie session) {
    return get("/api/profile").cookie(session);
  }
}
