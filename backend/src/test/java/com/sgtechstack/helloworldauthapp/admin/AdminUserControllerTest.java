package com.sgtechstack.helloworldauthapp.admin;

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

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
