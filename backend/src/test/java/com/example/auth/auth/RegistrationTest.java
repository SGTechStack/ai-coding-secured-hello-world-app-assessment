package com.example.auth.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auth.audit.AuditLogger;
import com.example.auth.security.ratelimit.RateLimiters;
import com.example.auth.support.ApiSession;
import com.example.auth.support.LogCapture;
import com.example.auth.user.PasswordPolicy;
import com.example.auth.user.Role;
import com.example.auth.user.User;
import com.example.auth.user.UserRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.ObjectMapper;

/**
 * Seam 1 (backend HTTP seam): registration through the real filter chain and real database.
 * Usernames are prefixed {@code reg-} and unique per test (the H2 database is shared JVM-wide).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RegistrationTest {

    private static final String REGISTER_URL = "/api/auth/register";
    private static final String RAW_PASSWORD = "SuperSecret123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RateLimiters rateLimiters;

    @BeforeEach
    void resetLimiters() {
        rateLimiters.resetAll();
    }

    private ResultActions register(RegisterRequest request) throws Exception {
        return register(request, "127.0.0.1");
    }

    private ResultActions register(RegisterRequest request, String ip) throws Exception {
        return new ApiSession(mockMvc, objectMapper).from(ip).post(REGISTER_URL, request);
    }

    @Test
    void validRegistrationCreatesAUserAndReturns201() throws Exception {
        try (LogCapture logs = LogCapture.start()) {
            register(new RegisterRequest("reg-newuser", "Reg-NewUser@Example.com", RAW_PASSWORD, "New"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value("reg-newuser"))
                    .andExpect(jsonPath("$.email").value("reg-newuser@example.com"))
                    .andExpect(jsonPath("$.role").value("USER"))
                    .andExpect(jsonPath("$.enabled").value(true))
                    .andExpect(jsonPath("$.createdAt").exists())
                    .andExpect(jsonPath("$.password").doesNotExist());

            User saved = userRepository.findByUsername("reg-newuser").orElseThrow();
            assertThat(logs.audit("User registered").getFirst().mdc())
                    .containsEntry(AuditLogger.USER_ID, saved.getPublicId().toString());
        }

        User saved = userRepository.findByUsername("reg-newuser").orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("reg-newuser@example.com");
        assertThat(saved.getFirstName()).isEqualTo("New");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getPassword()).isNotEqualTo(RAW_PASSWORD);
        assertThat(passwordEncoder.matches(RAW_PASSWORD, saved.getPassword())).isTrue();
    }

    @Test
    void registeredUserCanLogIn() throws Exception {
        register(new RegisterRequest("reg-canlogin", "reg-canlogin@example.com", RAW_PASSWORD, "Can"))
                .andExpect(status().isCreated());

        ApiSession client = new ApiSession(mockMvc, objectMapper);
        client.login("reg-canlogin", RAW_PASSWORD).andExpect(status().isOk());
        client.get("/api/auth/me").andExpect(jsonPath("$.username").value("reg-canlogin"));
    }

    @Test
    void duplicateUsernameReturns409WithTheMergedMessage() throws Exception {
        register(new RegisterRequest("reg-duplicate", "reg-dup-first@example.com", RAW_PASSWORD, "First"))
                .andExpect(status().isCreated());

        register(new RegisterRequest("reg-duplicate", "reg-dup-second@example.com", RAW_PASSWORD, "Second"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(RegistrationService.CONFLICT_MESSAGE));
    }

    @Test
    void usernameDifferingOnlyByCaseReturns409() throws Exception {
        register(new RegisterRequest("reg-CaseUser", "reg-casefirst@example.com", RAW_PASSWORD, "First"))
                .andExpect(status().isCreated());

        register(new RegisterRequest("reg-caseuser", "reg-casesecond@example.com", RAW_PASSWORD, "Second"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(RegistrationService.CONFLICT_MESSAGE));
    }

    @Test
    void duplicateEmailGetsExactlyTheSameResponseAsADuplicateUsername() throws Exception {
        register(new RegisterRequest("reg-emailfirst", "reg-shared@example.com", RAW_PASSWORD, "First"))
                .andExpect(status().isCreated());

        String duplicateEmail = register(
                        new RegisterRequest("reg-emailsecond", "REG-Shared@Example.com", RAW_PASSWORD, "Second"))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        String duplicateUsername = register(
                        new RegisterRequest("reg-emailfirst", "reg-unrelated@example.com", RAW_PASSWORD, "Third"))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        // Anti-enumeration: the body never says which field collided.
        assertThat(duplicateEmail).isEqualTo(duplicateUsername);
        assertThat(userRepository.existsByUsername("reg-emailsecond")).isFalse();
    }

    @Test
    void missingUsernameReturns400InsteadOf500() throws Exception {
        register(new RegisterRequest(null, "reg-missingusername@example.com", RAW_PASSWORD, "First"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void usernameOutsideTheAllowedPatternIsRejected() throws Exception {
        register(new RegisterRequest("ab", "reg-short@example.com", RAW_PASSWORD, "First"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        register(new RegisterRequest("reg with space", "reg-space@example.com", RAW_PASSWORD, "First"))
                .andExpect(status().isBadRequest());
        register(new RegisterRequest("r".repeat(65), "reg-long@example.com", RAW_PASSWORD, "First"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sixtyFourCharacterUsernameIsAccepted() throws Exception {
        String username = "reg-" + "x".repeat(60);
        register(new RegisterRequest(username, "reg-sixtyfour@example.com", RAW_PASSWORD, "First"))
                .andExpect(status().isCreated());
    }

    @Test
    void malformedOrOverlongEmailReturns400InsteadOf500() throws Exception {
        register(new RegisterRequest("reg-malformedemail", "not-an-email", RAW_PASSWORD, "First"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        String tooLong = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(60) + ".com";
        assertThat(tooLong.length()).isGreaterThan(254);
        register(new RegisterRequest("reg-longemail", tooLong, RAW_PASSWORD, "First"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingOrOverlongFirstNameReturns400InsteadOf500() throws Exception {
        register(new RegisterRequest("reg-missingfirstname", "reg-missingfirstname@example.com", RAW_PASSWORD, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        register(new RegisterRequest("reg-longfirstname", "reg-longfirstname@example.com", RAW_PASSWORD, "f".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("First name must be at most 100 characters"));
    }

    @Test
    void missingPasswordReturns400() throws Exception {
        register(new RegisterRequest("reg-nopassword", "reg-nopassword@example.com", null, "First"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password is required"));
    }

    @Test
    void passwordShorterThanTwelveCharactersReturns400() throws Exception {
        register(new RegisterRequest("reg-shortpassword", "reg-shortpw@example.com", "short11chr!", "Short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(PasswordPolicy.MESSAGE));

        assertThat(userRepository.existsByUsername("reg-shortpassword")).isFalse();
    }

    @Test
    void seventyTwoBytePasswordIsAcceptedAndSeventyThreeIsRejected() throws Exception {
        String seventyTwo = "é".repeat(30) + "a".repeat(12);
        assertThat(seventyTwo.getBytes(StandardCharsets.UTF_8)).hasSize(72);
        register(new RegisterRequest("reg-seventytwo", "reg-seventytwo@example.com", seventyTwo, "Max"))
                .andExpect(status().isCreated());
        new ApiSession(mockMvc, objectMapper).login("reg-seventytwo", seventyTwo).andExpect(status().isOk());

        String seventyThree = seventyTwo + "a";
        register(new RegisterRequest("reg-seventythree", "reg-seventythree@example.com", seventyThree, "Over"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PasswordPolicy.MESSAGE));
        assertThat(userRepository.existsByUsername("reg-seventythree")).isFalse();
    }

    @Test
    void malformedJsonIsA400WithAFixedMessage() throws Exception {
        new ApiSession(mockMvc, objectMapper).postRaw(REGISTER_URL, "{\"username\": \"reg-broken\",")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void nonJsonContentTypeIsRejectedAs415() throws Exception {
        ApiSession client = new ApiSession(mockMvc, objectMapper);
        String token = client.fetchCsrfToken();
        client.perform(MockMvcRequestBuilders.post(REGISTER_URL)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello")
                        .header("X-CSRF-TOKEN", token))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void registrationWithoutCsrfTokenIsRejected() throws Exception {
        new ApiSession(mockMvc, objectMapper)
                .postWithoutCsrf(REGISTER_URL, new RegisterRequest("reg-nocsrf", "reg-nocsrf@example.com", RAW_PASSWORD, "No"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        assertThat(userRepository.existsByUsername("reg-nocsrf")).isFalse();
    }

    @Test
    void registrationIsRateLimitedPerSourceIpWithRetryAfter() throws Exception {
        String ip = "10.20.0.1";
        register(new RegisterRequest("reg-ratelimit", "reg-ratelimit@example.com", RAW_PASSWORD, "Rate"), ip)
                .andExpect(status().isCreated());
        // Conflicts are counted too (they probe for existing accounts) and skip the BCrypt cost.
        for (int i = 2; i <= 10; i++) {
            register(new RegisterRequest("reg-ratelimit", "reg-ratelimit" + i + "@example.com", RAW_PASSWORD, "Rate"), ip)
                    .andExpect(status().isConflict());
        }

        try (LogCapture logs = LogCapture.start()) {
            register(new RegisterRequest("reg-ratelimit-11", "reg-ratelimit-11@example.com", RAW_PASSWORD, "Rate"), ip)
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, org.hamcrest.Matchers.matchesPattern("\\d+")));
            assertThat(logs.audit("Request rate limited").getFirst().kv("labels.limiter")).isEqualTo("register_ip");
        }
        assertThat(userRepository.existsByUsername("reg-ratelimit-11")).isFalse();

        long retryAfter = Long.parseLong(
                register(new RegisterRequest("reg-ratelimit-12", "reg-ratelimit-12@example.com", RAW_PASSWORD, "Rate"), ip)
                        .andReturn().getResponse().getHeader(HttpHeaders.RETRY_AFTER));
        assertThat(retryAfter).isBetween(3500L, 3600L);

        // Another source IP is unaffected.
        register(new RegisterRequest("reg-ratelimit-other", "reg-ratelimit-other@example.com", RAW_PASSWORD, "Rate"), "10.20.0.2")
                .andExpect(status().isCreated());
    }

    @Test
    void validationFailuresAreNotCountedAgainstTheRegistrationLimit() throws Exception {
        String ip = "10.20.0.3";
        for (int i = 0; i < 12; i++) {
            register(new RegisterRequest("reg-invalid-" + i, "not-an-email", RAW_PASSWORD, "Bad"), ip)
                    .andExpect(status().isBadRequest());
        }
        register(new RegisterRequest("reg-after-invalid", "reg-after-invalid@example.com", RAW_PASSWORD, "Ok"), ip)
                .andExpect(status().isCreated());
    }

    @Test
    void neitherTheResponseBodyNorTheLogsEverContainTheRawPassword() throws Exception {
        String responseBody;
        try (LogCapture logs = LogCapture.start()) {
            responseBody = register(new RegisterRequest(
                            "reg-logcheck", "reg-logcheck@example.com", "LogCheckPassword123!", "LogCheck"))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(logs.events()).isNotEmpty();
            assertThat(logs.events()).noneMatch(e -> e.everything().contains("LogCheckPassword123!"));
            assertThat(logs.audit()).noneMatch(e -> e.everything().contains("reg-logcheck"));
        }

        assertThat(responseBody).doesNotContain("LogCheckPassword123!");
    }
}
