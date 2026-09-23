package com.assessment.securedhelloworld.admin;

import com.assessment.securedhelloworld.user.Role;
import com.assessment.securedhelloworld.user.User;
import com.assessment.securedhelloworld.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminUserManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User createUser(String username, Role role) {
        User user = new User(username, username + "@example.com", passwordEncoder.encode("correct-horse-battery"));
        user.setRole(role);
        return userRepository.save(user);
    }

    private MockHttpSession loginAs(String username) throws Exception {
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "correct-horse-battery")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void listUsersReturnsExpectedFieldsAndOmitsPasswordHash() throws Exception {
        createUser("admin-list-admin", Role.ADMIN);
        createUser("admin-list-target", Role.USER);
        MockHttpSession adminSession = loginAs("admin-list-admin");

        mockMvc.perform(get("/api/admin/users").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'admin-list-target')].email").exists())
                .andExpect(jsonPath("$[?(@.username == 'admin-list-target')].passwordHash").doesNotExist());
    }

    @Test
    void nonAdminReceives403OnAdminEndpoints() throws Exception {
        createUser("admin-nonadmin-user", Role.USER);
        MockHttpSession userSession = loginAs("admin-nonadmin-user");

        mockMvc.perform(get("/api/admin/users").session(userSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanEnableDisableAnotherUsersAccount() throws Exception {
        createUser("admin-toggle-admin", Role.ADMIN);
        User target = createUser("admin-toggle-target", Role.USER);
        MockHttpSession adminSession = loginAs("admin-toggle-admin");

        mockMvc.perform(patch("/api/admin/users/" + target.getId() + "/status")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false)))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());

        User reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.isEnabled()).isFalse();
    }

    @Test
    void adminCannotDisableTheirOwnAccount() throws Exception {
        User admin = createUser("admin-selftoggle-admin", Role.ADMIN);
        MockHttpSession adminSession = loginAs("admin-selftoggle-admin");

        mockMvc.perform(patch("/api/admin/users/" + admin.getId() + "/status")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false)))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isBadRequest());

        User reloaded = userRepository.findById(admin.getId()).orElseThrow();
        assertThat(reloaded.isEnabled()).isTrue();
    }

    @Test
    void adminCanChangeAnotherUsersRole() throws Exception {
        createUser("admin-role-admin", Role.ADMIN);
        User target = createUser("admin-role-target", Role.USER);
        MockHttpSession adminSession = loginAs("admin-role-admin");

        mockMvc.perform(patch("/api/admin/users/" + target.getId() + "/role")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "ADMIN")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());

        User reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void adminCannotChangeTheirOwnRole() throws Exception {
        User admin = createUser("admin-selfrole-admin", Role.ADMIN);
        MockHttpSession adminSession = loginAs("admin-selfrole-admin");

        mockMvc.perform(patch("/api/admin/users/" + admin.getId() + "/role")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "USER")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isBadRequest());

        User reloaded = userRepository.findById(admin.getId()).orElseThrow();
        assertThat(reloaded.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void adminCanDeleteAnotherUsersAccount() throws Exception {
        createUser("admin-delete-admin", Role.ADMIN);
        User target = createUser("admin-delete-target", Role.USER);
        MockHttpSession adminSession = loginAs("admin-delete-admin");

        mockMvc.perform(delete("/api/admin/users/" + target.getId())
                        .session(adminSession)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(target.getId())).isEmpty();
    }

    @Test
    void adminCannotDeleteTheirOwnAccount() throws Exception {
        User admin = createUser("admin-selfdelete-admin", Role.ADMIN);
        MockHttpSession adminSession = loginAs("admin-selfdelete-admin");

        mockMvc.perform(delete("/api/admin/users/" + admin.getId())
                        .session(adminSession)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findById(admin.getId())).isPresent();
    }
}
