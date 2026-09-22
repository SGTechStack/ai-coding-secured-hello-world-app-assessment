package com.sgtechstack.helloworldauthapp.auth;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AuthControllerRegistrationTest {

    private static final String ENDPOINT = "/api/auth/register";
    private static final String STRONG_PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanUp() {
        // Clear tokens first: they FK-reference users, so a leftover token
        // row from another test class would otherwise block this delete.
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registersAccountWithUniqueUsernameEmailAndStrongPassword() throws Exception {
        String body = objectMapper.writeValueAsString(
                new RegistrationRequest("newuser", "newuser@example.com", STRONG_PASSWORD));

        mockMvc.perform(post(ENDPOINT).with(csrf()).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.enabled").value(true));

        Optional<User> saved = userRepository.findByUsernameIgnoreCase("newuser");
        assertThat(saved).isPresent();
        assertThat(saved.get().getRole()).isEqualTo(Role.USER);
        assertThat(saved.get().isEnabled()).isTrue();
        assertThat(saved.get().getPasswordHash()).isNotEqualTo(STRONG_PASSWORD);
        assertThat(passwordEncoder.matches(STRONG_PASSWORD, saved.get().getPasswordHash())).isTrue();
    }

    @Test
    void rejectsRegistrationWithAlreadyRegisteredUsername() throws Exception {
        userRepository.save(new User(
                "taken", "original@example.com", passwordEncoder.encode(STRONG_PASSWORD), Role.USER, true));

        String body = objectMapper.writeValueAsString(
                new RegistrationRequest("taken", "different@example.com", STRONG_PASSWORD));

        mockMvc.perform(post(ENDPOINT).with(csrf()).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username is already taken"));

        assertThat(userRepository.existsByEmailIgnoreCase("different@example.com")).isFalse();
    }

    @Test
    void rejectsRegistrationWithAlreadyRegisteredEmail() throws Exception {
        userRepository.save(new User(
                "original", "taken@example.com", passwordEncoder.encode(STRONG_PASSWORD), Role.USER, true));

        String body = objectMapper.writeValueAsString(
                new RegistrationRequest("different", "taken@example.com", STRONG_PASSWORD));

        mockMvc.perform(post(ENDPOINT).with(csrf()).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email is already registered"));

        assertThat(userRepository.existsByUsernameIgnoreCase("different")).isFalse();
    }

    @Test
    void rejectsRegistrationWithPasswordFailingStrengthPolicy() throws Exception {
        String body = objectMapper.writeValueAsString(
                new RegistrationRequest("shortpassworduser", "shortpw@example.com", "tooshort1"));

        mockMvc.perform(post(ENDPOINT).with(csrf()).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Password must be at least " + PasswordPolicy.MIN_LENGTH + " characters long"));

        assertThat(userRepository.existsByUsernameIgnoreCase("shortpassworduser")).isFalse();
    }

    @Test
    void rejectsRegistrationWithBlankUsernameOrInvalidEmail() throws Exception {
        String body = objectMapper.writeValueAsString(
                new RegistrationRequest("", "not-an-email", STRONG_PASSWORD));

        mockMvc.perform(post(ENDPOINT).with(csrf()).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }
}
