package com.sgtechstack.helloworldauthapp.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgtechstack.helloworldauthapp.passwordreset.PasswordResetTokenRepository;
import com.sgtechstack.helloworldauthapp.user.Role;
import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminUserControllerTest {

    private static final String ADMIN_USERNAME = "adminuser";
    private static final String ADMIN_PASSWORD = "correct-horse-battery";
    private static final String REGULAR_USERNAME = "regularuser";
    private static final String REGULAR_PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Clear tokens first: they FK-reference users, so a leftover token
        // row from another test class would otherwise block this delete.
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(new User(
                ADMIN_USERNAME, "adminuser@example.com", passwordEncoder.encode(ADMIN_PASSWORD), Role.ADMIN, true));
        userRepository.save(new User(
                REGULAR_USERNAME, "regularuser@example.com", passwordEncoder.encode(REGULAR_PASSWORD), Role.USER,
                true));
    }

    private MockHttpSession loginAndGetSession(String username, String password) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    }

    @Test
    void adminCanListAllUsersWithoutPasswordHashes() throws Exception {
        MockHttpSession session = loginAndGetSession(ADMIN_USERNAME, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/admin/users").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].username").value(hasItems(ADMIN_USERNAME, REGULAR_USERNAME)))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[1].passwordHash").doesNotExist());
    }

    @Test
    void nonAdminReceivesForbiddenFromUserListing() throws Exception {
        MockHttpSession session = loginAndGetSession(REGULAR_USERNAME, REGULAR_PASSWORD);

        mockMvc.perform(get("/api/admin/users").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void unauthenticatedRequestToUserListingIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanDisableAnotherUsersAccountAndThatUserCanNoLongerLogIn() throws Exception {
        MockHttpSession adminSession = loginAndGetSession(ADMIN_USERNAME, ADMIN_PASSWORD);
        User target = userRepository.findByUsernameIgnoreCase(REGULAR_USERNAME).orElseThrow();

        mockMvc.perform(patch("/api/admin/users/{id}/enabled", target.getId())
                        .with(csrf())
                        .session(adminSession)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetEnabledRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        assertThat(userRepository.findByUsernameIgnoreCase(REGULAR_USERNAME).orElseThrow().isEnabled()).isFalse();

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", REGULAR_USERNAME)
                        .param("password", REGULAR_PASSWORD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotDisableTheirOwnAccount() throws Exception {
        MockHttpSession adminSession = loginAndGetSession(ADMIN_USERNAME, ADMIN_PASSWORD);
        User admin = userRepository.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseThrow();

        mockMvc.perform(patch("/api/admin/users/{id}/enabled", admin.getId())
                        .with(csrf())
                        .session(adminSession)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetEnabledRequest(false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("An admin cannot disable or enable their own account"));

        assertThat(userRepository.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void adminCanChangeAnotherUsersRole() throws Exception {
        MockHttpSession adminSession = loginAndGetSession(ADMIN_USERNAME, ADMIN_PASSWORD);
        User target = userRepository.findByUsernameIgnoreCase(REGULAR_USERNAME).orElseThrow();

        mockMvc.perform(patch("/api/admin/users/{id}/role", target.getId())
                        .with(csrf())
                        .session(adminSession)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RoleChangeRequest(Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        assertThat(userRepository.findByUsernameIgnoreCase(REGULAR_USERNAME).orElseThrow().getRole())
                .isEqualTo(Role.ADMIN);
    }

    @Test
    void adminCannotChangeTheirOwnRole() throws Exception {
        MockHttpSession adminSession = loginAndGetSession(ADMIN_USERNAME, ADMIN_PASSWORD);
        User admin = userRepository.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseThrow();

        mockMvc.perform(patch("/api/admin/users/{id}/role", admin.getId())
                        .with(csrf())
                        .session(adminSession)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RoleChangeRequest(Role.USER))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("An admin cannot change the role of their own account"));

        assertThat(userRepository.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseThrow().getRole())
                .isEqualTo(Role.ADMIN);
    }

    @Test
    void adminCanDeleteAnotherUsersAccount() throws Exception {
        MockHttpSession adminSession = loginAndGetSession(ADMIN_USERNAME, ADMIN_PASSWORD);
        User target = userRepository.findByUsernameIgnoreCase(REGULAR_USERNAME).orElseThrow();

        mockMvc.perform(delete("/api/admin/users/{id}", target.getId())
                        .with(csrf())
                        .session(adminSession))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByUsernameIgnoreCase(REGULAR_USERNAME)).isEmpty();
    }

    @Test
    void adminCannotDeleteTheirOwnAccount() throws Exception {
        MockHttpSession adminSession = loginAndGetSession(ADMIN_USERNAME, ADMIN_PASSWORD);
        User admin = userRepository.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseThrow();

        mockMvc.perform(delete("/api/admin/users/{id}", admin.getId())
                        .with(csrf())
                        .session(adminSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("An admin cannot delete their own account"));

        assertThat(userRepository.findByUsernameIgnoreCase(ADMIN_USERNAME)).isPresent();
    }

    @Test
    void nonAdminCannotPerformAnyMutation() throws Exception {
        MockHttpSession regularSession = loginAndGetSession(REGULAR_USERNAME, REGULAR_PASSWORD);
        User admin = userRepository.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseThrow();

        mockMvc.perform(patch("/api/admin/users/{id}/enabled", admin.getId())
                        .with(csrf())
                        .session(regularSession)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetEnabledRequest(false))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/users/{id}", admin.getId())
                        .with(csrf())
                        .session(regularSession))
                .andExpect(status().isForbidden());
    }
}
