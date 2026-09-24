package com.sgtechstack.helloworldauthapp.auth;

import com.sgtechstack.helloworldauthapp.passwordreset.PasswordResetTokenRepository;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registration must refuse a username that could corrupt a log record.
 *
 * <p>{@code @Size} bounded the length and nothing bounded the content, so a
 * username could contain newlines, escape sequences or NUL bytes. Since the
 * username is the subject of most audit lines this application writes, that made
 * the audit trail writable by the person it was meant to hold accountable: a
 * newline lets one account's record emit a second, fabricated record underneath
 * it.
 *
 * <p>Validation is the primary fix because it stops the value existing. {@code
 * LogSafe} is the backstop at the call sites, covered separately — both are
 * needed, since login logs an unvalidated username by necessity.
 *
 * <p>The rate limit on registration is 10 requests per hour per caller, and the
 * limiter is a singleton in a cached context shared with other test classes.
 * These tests therefore stay well inside that budget and the class asserts
 * rejections in one request each.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class UsernameValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private RequestRateLimiter requestRateLimiter;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        // Every MockMvc request arrives from the same address and the limiter is
        // a singleton in a cached context, so a neighbouring class's requests
        // would otherwise count against this one's budget.
        requestRateLimiter.reset();
    }

    @Test
    void rejectsAUsernameCarryingCrlf() throws Exception {
        // The forging payload, submitted end to end. JSON escapes the newline on
        // the wire, so this is exactly what an attacker would send and what
        // Jackson would hand to the validator.
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"alice\\r\\nLogin succeeded username=admin",\
                                "email":"crlf@example.com","password":"password-1234-long"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));

        assertThat(userRepository.findAllByOrderByCreatedAtAsc()).isEmpty();
    }

    @Test
    void rejectsAUsernameCarryingOtherControlCharacters() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"ali\\u0000ce","email":"nul@example.com",\
                                "password":"password-1234-long"}"""))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findAllByOrderByCreatedAtAsc()).isEmpty();
    }

    @Test
    void rejectsAUsernameContainingAnAtSignSoUsernamesAndEmailsStayDistinguishable() throws Exception {
        // Excluded so neither can be passed where the other is expected. A
        // username that is a valid email address invites exactly that confusion.
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"alice@example.com","email":"at@example.com",\
                                "password":"password-1234-long"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*]").value(org.hamcrest.Matchers.hasItem(
                        "Username may contain only letters, digits, dots, underscores and hyphens")));
    }

    @Test
    void rejectsAUsernameWithSpacesOrPunctuationOutsideTheAllowList() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"alice smith","email":"space@example.com",\
                                "password":"password-1234-long"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsTheCharactersRealUsernamesActuallyUse() throws Exception {
        // The control case. An allow-list this narrow could easily be too narrow,
        // and a rejection test suite alone would not notice — it would pass if
        // registration rejected everything.
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"samuel.wong-01_x","email":"ok@example.com",\
                                "password":"password-1234-long"}"""))
                .andExpect(status().isCreated());

        assertThat(userRepository.existsByUsernameIgnoreCase("samuel.wong-01_x")).isTrue();
    }

    @Test
    void rejectsAnAbsurdlyLongEmailBeforeItIsStored() throws Exception {
        // 254 is the maximum length of a deliverable address. @Email checks shape
        // and not size, so without this a caller could store a 100KB "address"
        // that no mail server would ever accept.
        String oversized = "a".repeat(300) + "@example.com";

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"lengthtest\",\"email\":\"" + oversized
                                + "\",\"password\":\"password-1234-long\"}"))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.existsByUsernameIgnoreCase("lengthtest")).isFalse();
    }
}
