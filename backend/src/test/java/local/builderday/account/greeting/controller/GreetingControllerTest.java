package local.builderday.account.greeting.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.UUID;
import local.builderday.support.TestClocks;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** The Greeting at the HTTP boundary, through the real security chain and Session store. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestClocks.class)
class GreetingControllerTest {
  private static final String PASSWORD = "Str0ng!Passw0rd";
  private static final String HELLO = "/api/hello";

  @Autowired MockMvc mvc;
  @Autowired UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired TestClocks clocks;

  @BeforeEach
  void setUp() {
    clocks.reset();
    userRepository.deleteAll();
    userRepository.save(new UserEntity(UUID.randomUUID(), "johndoe", "johndoe@test.example.com",
        passwordEncoder.encode(PASSWORD), "USER", true));
  }

  @Test
  void should_greetTheUserByCanonicalUsername_when_theSessionIsLive() throws Exception {
    mvc.perform(get(HELLO).cookie(login("johndoe"))).andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().json("{\"message\":\"Hello, johndoe\"}", JsonCompareMode.STRICT));
  }

  @Test
  void should_greetByCanonicalUsername_when_theLoginWasTypedWithOtherCaseAndWhitespace() throws Exception {
    mvc.perform(get(HELLO).cookie(login("  JohnDoe "))).andExpect(status().isOk())
        .andExpect(content().json("{\"message\":\"Hello, johndoe\"}", JsonCompareMode.STRICT));
  }

  @Test
  void should_requireAuthentication_when_thereIsNoSession() throws Exception {
    expectAuthenticationRequired(mvc.perform(get(HELLO)));
  }

  @Test
  void should_requireAuthentication_when_theCookieIsReplayedAfterLogout() throws Exception {
    Cookie session = login("johndoe");
    mvc.perform(post("/api/auth/logout").with(csrf()).cookie(session)).andExpect(status().isNoContent());

    expectAuthenticationRequired(mvc.perform(get(HELLO).cookie(session)));
  }

  @Test
  void should_requireAuthentication_when_theSessionIsPastItsAbsoluteLifetime() throws Exception {
    Cookie session = login("johndoe");

    clocks.advance(Duration.ofHours(8).plusMinutes(1));

    expectAuthenticationRequired(mvc.perform(get(HELLO).cookie(session)));
  }

  @Test
  void should_requireAuthentication_when_theSessionWasReplacedByANewerLogin() throws Exception {
    Cookie first = login("johndoe");
    Cookie second = login("johndoe");

    mvc.perform(get(HELLO).cookie(second)).andExpect(status().isOk());
    expectAuthenticationRequired(mvc.perform(get(HELLO).cookie(first)));
  }

  private Cookie login(String username) throws Exception {
    var login = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isOk()).andReturn();
    return login.getResponse().getCookie("id");
  }

  private static void expectAuthenticationRequired(ResultActions result) throws Exception {
    result.andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
  }
}
