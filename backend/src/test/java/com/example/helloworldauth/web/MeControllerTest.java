package com.example.helloworldauth.web;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MeControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder encoder;

    @BeforeEach
    void seed() {
        users.deleteAll();
        users.save(new User("boss", "boss@example.com", encoder.encode("correcthorsebattery"), Role.ADMIN));
    }

    @Test
    @WithMockUser(username = "boss")
    void returnsUsernameAndRoleForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("boss"))
            .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/me"))
            .andExpect(status().isUnauthorized());
    }
}
