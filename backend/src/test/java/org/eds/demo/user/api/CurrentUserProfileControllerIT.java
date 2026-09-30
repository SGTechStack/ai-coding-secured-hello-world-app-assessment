package org.eds.demo.user.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import org.eds.demo.user.domain.AppUser;
import org.eds.demo.user.domain.Role;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Transactional
class CurrentUserProfileControllerIT {

  @Autowired private MockMvc mockMvc;
  @Autowired private AppUserRepository appUserRepository;

  @Test
  void currentUserProfileReturnsAuthenticatedPrincipal() throws Exception {
    appUserRepository.save(AppUser.create("ada", Set.of(Role.USER_MANAGER, Role.USER)));

    mockMvc
        .perform(get("/api/v1/me").with(user("ada").roles("USER_MANAGER", "USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("ada"))
        .andExpect(jsonPath("$.displayName").value("ada"))
        .andExpect(jsonPath("$.roles").isArray())
        .andExpect(jsonPath("$.roles[?(@ == 'USER_MANAGER')]").exists())
        .andExpect(jsonPath("$.roles[?(@ == 'USER')]").exists());
  }

  @Test
  void currentUserProfileRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
  }
}
