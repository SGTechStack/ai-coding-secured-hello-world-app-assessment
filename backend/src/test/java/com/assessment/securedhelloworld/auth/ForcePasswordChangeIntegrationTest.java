package com.assessment.securedhelloworld.auth;

import com.assessment.securedhelloworld.bootstrap.AdminBootstrapProperties;
import com.assessment.securedhelloworld.user.User;
import com.assessment.securedhelloworld.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for IM8 ac-6: the bootstrap admin credential must
 * be changed before any other endpoint is reachable, and the block lifts
 * immediately within the same session once the password is changed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ForcePasswordChangeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdminBootstrapProperties adminBootstrapProperties;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private MockHttpSession loginAsBootstrapAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", adminBootstrapProperties.getUsername(),
                                "password", adminBootstrapProperties.getPassword())))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void bootstrapAdminIsBlockedFromOtherEndpointsUntilPasswordChanged() throws Exception {
        MockHttpSession session = loginAsBootstrapAdmin();

        mockMvc.perform(get("/api/hello").session(session))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PASSWORD_CHANGE_REQUIRED")));

        mockMvc.perform(get("/api/admin/users").session(session))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PASSWORD_CHANGE_REQUIRED")));
    }

    @Test
    void logoutRemainsReachableWhilePasswordChangeIsForced() throws Exception {
        MockHttpSession session = loginAsBootstrapAdmin();

        mockMvc.perform(post("/api/logout").session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void changingPasswordClearsTheFlagAndUnblocksTheSession() throws Exception {
        MockHttpSession session = loginAsBootstrapAdmin();

        mockMvc.perform(post("/api/auth/change-password").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", adminBootstrapProperties.getPassword(),
                                "newPassword", "brand-new-admin-password123")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());

        User reloaded = userRepository.findByUsername(adminBootstrapProperties.getUsername()).orElseThrow();
        assertThat(reloaded.isForcePasswordChange()).isFalse();
        assertThat(passwordEncoder.matches("brand-new-admin-password123", reloaded.getPasswordHash())).isTrue();

        mockMvc.perform(get("/api/hello").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() throws Exception {
        MockHttpSession session = loginAsBootstrapAdmin();

        mockMvc.perform(post("/api/auth/change-password").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "totally-wrong-password",
                                "newPassword", "brand-new-admin-password123")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isBadRequest());

        User reloaded = userRepository.findByUsername(adminBootstrapProperties.getUsername()).orElseThrow();
        assertThat(reloaded.isForcePasswordChange()).isTrue();
    }
}
