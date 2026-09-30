package com.example.hello.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.hello.support.IntegrationTestSupport;
import com.example.hello.user.Role;
import com.example.hello.user.User;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Stories 8-11 and the role-enforcement testing requirement. */
class AdminUserIntegrationTest extends IntegrationTestSupport {

  @Test
  @DisplayName("admin lists users without password hashes")
  void adminListsUsers() throws Exception {
    String username = registerUser("listed");
    ClientSession admin = loginAsAdmin();

    MvcResult result =
        mockMvc
            .perform(get("/api/admin/users").cookie(admin.cookie()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.username == '" + username + "')].email").value(username + "@example.com"))
            .andExpect(jsonPath("$[?(@.username == '" + username + "')].role").value("USER"))
            .andExpect(jsonPath("$[?(@.username == '" + username + "')].enabled").value(true))
            .andExpect(jsonPath("$[?(@.username == '" + username + "')].createdAt").exists())
            .andReturn();
    assertThat(result.getResponse().getContentAsString()).doesNotContain("passwordHash").doesNotContain("$2");
  }

  @Test
  @DisplayName("a USER gets 403 on every /api/admin endpoint")
  void userIsForbidden() throws Exception {
    String username = registerUser("plain");
    ClientSession user = login(username, GOOD_PASSWORD);
    UUID someId = userRepository.findByUsername(ADMIN_USERNAME).orElseThrow().getId();

    mockMvc.perform(get("/api/admin/users").cookie(user.cookie())).andExpect(status().isForbidden());
    mockMvc
        .perform(jsonRequest(patch("/api/admin/users/" + someId + "/status"), user, "{\"enabled\":false}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(jsonRequest(patch("/api/admin/users/" + someId + "/role"), user, "{\"role\":\"ADMIN\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(authenticated(delete("/api/admin/users/" + someId), user))
        .andExpect(status().isForbidden());
    assertThat(userRepository.findByUsername(ADMIN_USERNAME).orElseThrow().isEnabled()).isTrue();
  }

  @Test
  @DisplayName("admin disables a user, who then cannot log in, and cannot disable themselves")
  void disableUser() throws Exception {
    String username = registerUser("todisable");
    ClientSession victimSession = login(username, GOOD_PASSWORD);
    UUID targetId = userRepository.findByUsername(username).orElseThrow().getId();
    ClientSession admin = loginAsAdmin();

    mockMvc
        .perform(jsonRequest(patch("/api/admin/users/" + targetId + "/status"), admin, "{\"enabled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false));

    assertThat(attemptLogin(username, GOOD_PASSWORD, uniqueIp()).getResponse().getStatus()).isEqualTo(401);
    // Existing sessions of a disabled user are revoked immediately.
    mockMvc.perform(get("/api/hello").cookie(victimSession.cookie())).andExpect(status().isUnauthorized());

    mockMvc
        .perform(jsonRequest(patch("/api/admin/users/" + targetId + "/status"), admin, "{\"enabled\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true));
    assertThat(login(username, GOOD_PASSWORD).cookie()).isNotNull();

    UUID selfId = userRepository.findByUsername(ADMIN_USERNAME).orElseThrow().getId();
    mockMvc
        .perform(jsonRequest(patch("/api/admin/users/" + selfId + "/status"), admin, "{\"enabled\":false}"))
        .andExpect(status().isBadRequest());
    assertThat(userRepository.findByUsername(ADMIN_USERNAME).orElseThrow().isEnabled()).isTrue();
  }

  @Test
  @DisplayName("admin changes another user's role but cannot demote themselves")
  void changeRole() throws Exception {
    String username = registerUser("promote");
    UUID targetId = userRepository.findByUsername(username).orElseThrow().getId();
    ClientSession admin = loginAsAdmin();

    mockMvc
        .perform(jsonRequest(patch("/api/admin/users/" + targetId + "/role"), admin, "{\"role\":\"ADMIN\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"));
    assertThat(userRepository.findByUsername(username).orElseThrow().getRole()).isEqualTo(Role.ADMIN);

    ClientSession promoted = login(username, GOOD_PASSWORD);
    mockMvc.perform(get("/api/admin/users").cookie(promoted.cookie())).andExpect(status().isOk());

    mockMvc
        .perform(jsonRequest(patch("/api/admin/users/" + targetId + "/role"), admin, "{\"role\":\"USER\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("USER"));

    UUID selfId = userRepository.findByUsername(ADMIN_USERNAME).orElseThrow().getId();
    mockMvc
        .perform(jsonRequest(patch("/api/admin/users/" + selfId + "/role"), admin, "{\"role\":\"USER\"}"))
        .andExpect(status().isBadRequest());
    assertThat(userRepository.findByUsername(ADMIN_USERNAME).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
  }

  @Test
  @DisplayName("admin deletes another user but cannot delete themselves")
  void deleteUser() throws Exception {
    String username = registerUser("doomed");
    User target = userRepository.findByUsername(username).orElseThrow();
    ClientSession admin = loginAsAdmin();

    mockMvc
        .perform(authenticated(delete("/api/admin/users/" + target.getId()), admin))
        .andExpect(status().isNoContent());
    assertThat(userRepository.existsByUsername(username)).isFalse();
    mockMvc
        .perform(authenticated(delete("/api/admin/users/" + target.getId()), admin))
        .andExpect(status().isNotFound());

    UUID selfId = userRepository.findByUsername(ADMIN_USERNAME).orElseThrow().getId();
    mockMvc
        .perform(authenticated(delete("/api/admin/users/" + selfId), admin))
        .andExpect(status().isBadRequest());
    assertThat(userRepository.existsByUsername(ADMIN_USERNAME)).isTrue();
  }

  @Test
  @DisplayName("admin mutations require the CSRF token")
  void mutationsRequireCsrf() throws Exception {
    String username = registerUser("csrfadmin");
    UUID targetId = userRepository.findByUsername(username).orElseThrow().getId();
    ClientSession admin = loginAsAdmin();

    mockMvc
        .perform(
            patch("/api/admin/users/" + targetId + "/status")
                .cookie(admin.cookie())
                .contentType("application/json")
                .content("{\"enabled\":false}"))
        .andExpect(status().isForbidden());
    assertThat(userRepository.findByUsername(username).orElseThrow().isEnabled()).isTrue();
  }
}
