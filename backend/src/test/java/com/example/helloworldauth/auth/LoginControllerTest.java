package com.example.helloworldauth.auth;

import com.example.helloworldauth.user.Role;
import com.example.helloworldauth.user.User;
import com.example.helloworldauth.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder encoder;

    @BeforeEach
    void seed() {
        users.deleteAll();
        users.save(new User("alice", "alice@example.com", encoder.encode("correcthorsebattery"), Role.USER));
    }

    private String body(String u, String p) {
        return """
            {"username":"%s","password":"%s"}""".formatted(u, p);
    }

    @Test
    void loginSucceedsAndResetsAttempts() throws Exception {
        User u = users.findByUsername("alice").orElseThrow();
        u.setFailedLoginAttempts(3);
        users.save(u);

        MvcResult result = mockMvc.perform(post("/api/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("alice", "correcthorsebattery")))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("alice");
        assertThat(result.getRequest().getSession(false)).isNotNull();
        assertThat(users.findByUsername("alice").orElseThrow().getFailedLoginAttempts()).isZero();
    }

    @Test
    void wrongPasswordRejectedAndIncrementsAttempts() throws Exception {
        mockMvc.perform(post("/api/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("alice", "wrongpassword!")))
            .andExpect(status().isUnauthorized());

        assertThat(users.findByUsername("alice").orElseThrow().getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void unknownUsernameGivesIdenticalGenericError() throws Exception {
        String wrongPass = mockMvc.perform(post("/api/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("alice", "wrongpassword!")))
            .andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();

        String unknownUser = mockMvc.perform(post("/api/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ghost", "wrongpassword!")))
            .andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();

        assertThat(unknownUser).isEqualTo(wrongPass);
    }

    @Test
    void lockedAccountRejectedEvenWithCorrectPassword() throws Exception {
        User u = users.findByUsername("alice").orElseThrow();
        u.setLockedUntil(Instant.now().plusSeconds(900));
        users.save(u);

        mockMvc.perform(post("/api/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("alice", "correcthorsebattery")))
            .andExpect(status().isUnauthorized());
    }
}
