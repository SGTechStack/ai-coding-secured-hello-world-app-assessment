package com.example.helloworldauth.admin;

import com.example.helloworldauth.user.Role;
import com.example.helloworldauth.user.User;
import com.example.helloworldauth.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the admin user-list endpoint and the {@code /api/admin/**}
 * role guard (Story 8). Covers: an admin gets 200 with the expected fields and no
 * password hash; an authenticated USER gets 403 (not 401); an unauthenticated
 * caller gets 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seed() {
        users.deleteAll();
        users.save(new User("alice", "alice@example.com",
            passwordEncoder.encode("alice-password-12+"), Role.ADMIN));
        users.save(new User("bob", "bob@example.com",
            passwordEncoder.encode("bob-password-1234+"), Role.USER));
    }

    @Test
    @WithMockUser(username = "adminuser", roles = "ADMIN")
    void adminGetsUserListWithExpectedFieldsAndNoHash() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
            .andExpect(jsonPath("$[?(@.username=='alice')].email").value("alice@example.com"))
            .andExpect(jsonPath("$[?(@.username=='alice')].role").value("ADMIN"))
            .andExpect(jsonPath("$[?(@.username=='alice')].enabled").value(true))
            .andExpect(jsonPath("$[?(@.username=='alice')].createdAt").exists())
            .andExpect(jsonPath("$[?(@.username=='bob')].role").value("USER"))
            // The password hash field must never be serialized.
            .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
            .andReturn();

        // Belt-and-braces: no bcrypt hash prefix or the field name anywhere in the body.
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("passwordHash");
        assertThat(body).doesNotContain("$2a$");
        assertThat(body).doesNotContain("$2b$");
    }

    @Test
    @WithMockUser(username = "regularuser", roles = "USER")
    void authenticatedUserIsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isUnauthorized());
    }
}
