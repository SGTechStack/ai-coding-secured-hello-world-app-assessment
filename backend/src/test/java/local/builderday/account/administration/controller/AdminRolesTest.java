package local.builderday.account.administration.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import local.builderday.common.audit.SecurityAudit;
import local.builderday.support.AuditLogCapture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/** {@code GET /api/admin/roles} at the HTTP boundary (Story 10): the roles an Admin may give, for the role popup. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AdminRolesTest {
  private static final String PASSWORD = "Str0ng!Passw0rd";

  @Autowired MockMvc mvc;
  @Autowired UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
    String passwordHash = passwordEncoder.encode(PASSWORD);
    userRepository.save(new UserEntity(UUID.randomUUID(), "janeadmin", null, passwordHash, "ADMIN", true));
    userRepository.save(new UserEntity(UUID.randomUUID(), "johndoe", null, passwordHash, "USER", true));
  }

  @Test
  void should_listTheRoleEnumConstantsInDeclarationOrder_withoutAuditing() throws Exception {
    var admin = login("janeadmin");
    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      mvc.perform(get("/api/admin/roles").cookie(admin)).andExpect(status().isOk())
          .andExpect(content().json("[\"USER\",\"ADMIN\"]", JsonCompareMode.STRICT));
      assertThat(audit.events()).isEmpty();
    }
  }

  @Test
  void should_denyUsersAndVisitors_andRejectUnsupportedMethods() throws Exception {
    mvc.perform(get("/api/admin/roles")).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    mvc.perform(get("/api/admin/roles").cookie(login("johndoe"))).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    mvc.perform(post("/api/admin/roles").with(csrf()).cookie(login("janeadmin")))
        .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
  }

  private Cookie login(String username) throws Exception {
    return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getCookie("id");
  }
}
