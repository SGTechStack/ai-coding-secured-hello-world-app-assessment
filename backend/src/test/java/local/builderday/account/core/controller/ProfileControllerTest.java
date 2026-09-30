package local.builderday.account.core.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
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

/** The caller's own profile at the HTTP boundary, through the real security chain and Session store. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestClocks.class)
class ProfileControllerTest {
  private static final String PASSWORD = "Str0ng!Passw0rd";
  private static final String PROFILE = "/api/profile";
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID ADMIN_ID = UUID.randomUUID();

  @Autowired MockMvc mvc;
  @Autowired UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired TestClocks clocks;

  @BeforeEach
  void setUp() {
    clocks.reset();
    userRepository.deleteAll();
    userRepository.save(new UserEntity(USER_ID, "johndoe", "johndoe@test.example.com",
        passwordEncoder.encode(PASSWORD), "USER", true));
    userRepository.save(new UserEntity(ADMIN_ID, "janeadmin", "janeadmin@test.example.com",
        passwordEncoder.encode(PASSWORD), "ADMIN", true));
  }

  @Test
  void should_returnOnlyTheCallersIdAndRole_when_theSessionIsLive() throws Exception {
    mvc.perform(get(PROFILE).cookie(login("johndoe"))).andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().json("{\"id\":\"" + USER_ID + "\",\"role\":\"USER\"}", JsonCompareMode.STRICT));
  }

  @Test
  void should_returnTheAdminRole_when_anAdminAsks() throws Exception {
    mvc.perform(get(PROFILE).cookie(login("janeadmin"))).andExpect(status().isOk())
        .andExpect(content().json("{\"id\":\"" + ADMIN_ID + "\",\"role\":\"ADMIN\"}", JsonCompareMode.STRICT));
  }

  @Test
  void should_requireAuthentication_when_thereIsNoSession() throws Exception {
    mvc.perform(get(PROFILE)).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
  }

  @Test
  void should_requireAuthentication_when_theCookieIsReplayedAfterLogout() throws Exception {
    Cookie session = login("johndoe");
    mvc.perform(post("/api/auth/logout").with(csrf()).cookie(session)).andExpect(status().isNoContent());
    mvc.perform(get(PROFILE).cookie(session)).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
  }

  @Test
  void should_rejectAnyMethodButGet_when_theSessionIsLive() throws Exception {
    mvc.perform(post(PROFILE).with(csrf()).cookie(login("johndoe"))).andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
  }

  private Cookie login(String username) throws Exception {
    return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getCookie("id");
  }
}
