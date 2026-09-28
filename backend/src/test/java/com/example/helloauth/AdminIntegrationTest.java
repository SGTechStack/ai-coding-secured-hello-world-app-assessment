package com.example.helloauth;

import com.example.helloauth.domain.Role;
import com.example.helloauth.domain.User;
import com.example.helloauth.repo.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper mapper;

    private static final String PW = "AdminModulePass1";

    private UUID adminId;
    private UUID userId;

    @BeforeEach
    void seed() {
        userRepository.findByUsername("bossadmin").ifPresent(userRepository::delete);
        userRepository.findByUsername("regular").ifPresent(userRepository::delete);

        User admin = new User();
        admin.setUsername("bossadmin");
        admin.setEmail("boss@example.com");
        admin.setPasswordHash(passwordEncoder.encode(PW));
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
        adminId = userRepository.save(admin).getId();

        User user = new User();
        user.setUsername("regular");
        user.setEmail("regular@example.com");
        user.setPasswordHash(passwordEncoder.encode(PW));
        user.setRole(Role.USER);
        user.setEnabled(true);
        userId = userRepository.save(user).getId();
    }

    private MockHttpSession loginSession(String username, String ip) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/login")
                        .with(csrf())
                        .session(session)
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk());
        return session;
    }

    // ---------- Story 8: role enforcement ----------

    @Test
    void userCallingAdminEndpointGets403() throws Exception {
        MockHttpSession session = loginSession("regular", "20.0.0.1");
        mvc.perform(get("/api/admin/users").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedAdminEndpointGets401() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanListUsersWithoutPasswordHashes() throws Exception {
        MockHttpSession session = loginSession("bossadmin", "20.0.0.2");
        MvcResult res = mvc.perform(get("/api/admin/users").session(session))
                .andExpect(status().isOk())
                .andReturn();
        String body = res.getResponse().getContentAsString();
        assertThat(body).contains("bossadmin", "regular");
        assertThat(body).doesNotContain("passwordHash");
        assertThat(body).doesNotContain("$2a$"); // no BCrypt hash leaked
    }

    // ---------- Story 9: enable/disable ----------

    @Test
    void adminCannotDisableSelf() throws Exception {
        MockHttpSession session = loginSession("bossadmin", "20.0.0.3");
        mvc.perform(patch("/api/admin/users/" + adminId + "/status")
                        .with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void disabledUserCannotLogin() throws Exception {
        MockHttpSession adminSession = loginSession("bossadmin", "20.0.0.4");
        mvc.perform(patch("/api/admin/users/" + userId + "/status")
                        .with(csrf()).session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/login")
                        .with(csrf()).header("X-Forwarded-For", "20.0.0.5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"regular\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Story 10: role change ----------

    @Test
    void adminCannotDemoteSelf() throws Exception {
        MockHttpSession session = loginSession("bossadmin", "20.0.0.6");
        mvc.perform(patch("/api/admin/users/" + adminId + "/role")
                        .with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanChangeRoleAndInvalidRoleRejected() throws Exception {
        MockHttpSession session = loginSession("bossadmin", "20.0.0.7");
        mvc.perform(patch("/api/admin/users/" + userId + "/role")
                        .with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
        assertThat(userRepository.findById(userId).orElseThrow().getRole()).isEqualTo(Role.ADMIN);

        mvc.perform(patch("/api/admin/users/" + userId + "/role")
                        .with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"SUPERUSER\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- Story 11: delete ----------

    @Test
    void adminCannotDeleteSelf() throws Exception {
        MockHttpSession session = loginSession("bossadmin", "20.0.0.8");
        mvc.perform(delete("/api/admin/users/" + adminId)
                        .with(csrf()).session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanDeleteAnotherUser() throws Exception {
        MockHttpSession session = loginSession("bossadmin", "20.0.0.9");
        mvc.perform(delete("/api/admin/users/" + userId)
                        .with(csrf()).session(session))
                .andExpect(status().isOk());
        assertThat(userRepository.findById(userId)).isEmpty();
    }

    // ---------- Story 12 / IM8 ac-6: forced first-login password change ----------

    @Test
    void seededAdminMustChangePasswordFlagPersists() {
        // The configured seed admin (application-test.yml) is seeded with must_change_password=true.
        User seeded = userRepository.findByUsername("seedadmin").orElseThrow();
        assertThat(seeded.getRole()).isEqualTo(Role.ADMIN);
        assertThat(seeded.isMustChangePassword()).isTrue();
    }

    @Test
    void forcedChangePasswordClearsFlag() throws Exception {
        // Mark regular user as must-change, then change password.
        User u = userRepository.findById(userId).orElseThrow();
        u.setMustChangePassword(true);
        userRepository.save(u);

        MockHttpSession session = loginSession("regular", "20.0.0.10");
        mvc.perform(post("/api/account/change-password")
                        .with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PW + "\",\"newPassword\":\"FreshPass123456\"}"))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(userId).orElseThrow().isMustChangePassword()).isFalse();
    }
}
