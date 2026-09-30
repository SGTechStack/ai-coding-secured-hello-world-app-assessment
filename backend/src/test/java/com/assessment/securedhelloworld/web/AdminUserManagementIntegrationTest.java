package com.assessment.securedhelloworld.web;

import com.assessment.securedhelloworld.domain.Role;
import com.assessment.securedhelloworld.domain.User;
import com.assessment.securedhelloworld.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers PRD Stories 8-12 (ticket 05): admin bootstrap, listing, enable/disable, role change,
 * delete, the shared self-action guard, and the forced-password-change gate.
 *
 * <p>Ordered deliberately: the seeded {@code testadmin} account (from
 * {@code application-test.yml}) starts with {@code forcePasswordChange = true}, and one test
 * clears it (simulating a completed password change, whose actual mechanism belongs to a
 * different ticket) so later tests can exercise the admin endpoints as a "changed password"
 * admin. Session cookies are asserted via the raw "SESSION" Set-Cookie value, not
 * {@code MockHttpSession} — same Spring Session + MockMvc gotcha as {@code AuthIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminUserManagementIntegrationTest {

    private static final String ADMIN_USERNAME = "testadmin";
    private static final String ADMIN_PASSWORD = "TestAdminPassw0rd!123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private Cookie register(String username, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "username", username,
                        "email", username + "@example.com",
                        "password", password))));
        return login(username, password);
    }

    private Cookie login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie sessionCookie = result.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();
        return sessionCookie;
    }

    @Test
    @Order(1)
    void exactlyOneAdminIsSeededFromConfigOnStartup() {
        assertThat(userRepository.existsByRole(Role.ADMIN)).isTrue();
        List<User> admins = userRepository.findAllByOrderByCreatedAtAsc().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .toList();
        assertThat(admins).hasSize(1);
        User admin = admins.get(0);
        assertThat(admin.getUsername()).isEqualTo(ADMIN_USERNAME);
        assertThat(admin.isForcePasswordChange()).isTrue();
    }

    @Test
    @Order(2)
    void plainUserCallingAnyAdminEndpointReceivesForbidden() throws Exception {
        Cookie userSession = register("planeuser", "correct-horse-battery-1");

        mockMvc.perform(get("/api/admin/users").cookie(userSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/admin/users/999").with(csrf()).cookie(userSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void seededAdminBeforePasswordChangeGetsPasswordChangeRequiredBody() throws Exception {
        Cookie adminSession = login(ADMIN_USERNAME, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/admin/users").cookie(adminSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    @Order(4)
    void afterPasswordChangeClearedAdminCanListUsersWithNoPasswordHashLeaked() throws Exception {
        User admin = userRepository.findByUsername(ADMIN_USERNAME).orElseThrow();
        admin.setForcePasswordChange(false);
        userRepository.save(admin);

        // Must re-login: the previously-issued session carries a serialized principal snapshot
        // from before the flag was cleared.
        Cookie adminSession = login(ADMIN_USERNAME, ADMIN_PASSWORD);

        MvcResult result = mockMvc.perform(get("/api/admin/users").cookie(adminSession))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body.toLowerCase()).doesNotContain("passwordhash").doesNotContain("password_hash");
        assertThat(body).doesNotContain("$2a$").doesNotContain("$2b$").doesNotContain("$2y$");

        List<Map<String, Object>> users = objectMapper.readValue(body, new TypeReference<List<Map<String, Object>>>() {
        });
        assertThat(users).anySatisfy(u -> assertThat(u.get("username")).isEqualTo(ADMIN_USERNAME));
        assertThat(users).allSatisfy(u -> assertThat(u).doesNotContainKey("passwordHash"));
    }

    @Test
    @Order(5)
    void adminCannotActOnOwnAccountButCanActOnAnotherAccount() throws Exception {
        User admin = userRepository.findByUsername(ADMIN_USERNAME).orElseThrow();
        admin.setForcePasswordChange(false);
        userRepository.save(admin);
        Cookie adminSession = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        Long adminId = admin.getId();

        // Self-action guard: rejected on all three mutating endpoints.
        mockMvc.perform(patch("/api/admin/users/" + adminId + "/status").with(csrf()).cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SELF_ACTION_FORBIDDEN"));

        mockMvc.perform(patch("/api/admin/users/" + adminId + "/role").with(csrf()).cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SELF_ACTION_FORBIDDEN"));

        mockMvc.perform(delete("/api/admin/users/" + adminId).with(csrf()).cookie(adminSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SELF_ACTION_FORBIDDEN"));

        assertThat(userRepository.findById(adminId)).isPresent();

        // A different, freshly registered account can be disabled, re-roled, and deleted.
        register("targetuser", "correct-horse-battery-2");
        Long targetId = userRepository.findByUsername("targetuser").orElseThrow().getId();

        mockMvc.perform(patch("/api/admin/users/" + targetId + "/status").with(csrf()).cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(patch("/api/admin/users/" + targetId + "/role").with(csrf()).cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(delete("/api/admin/users/" + targetId).with(csrf()).cookie(adminSession))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(targetId)).isEmpty();
    }
}
